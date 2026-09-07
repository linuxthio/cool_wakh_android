package com.wakh.app.webrtc

import android.content.Context
import android.util.Log
import com.wakh.app.data.local.db.MessageKind
import com.wakh.app.data.remote.IceCandidateDto
import com.wakh.app.data.remote.SignalingClient
import com.wakh.app.data.remote.SignalingEnvelope
import com.wakh.app.data.remote.SignalingType
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription

/**
 * Gère la connexion WebRTC P2P (DataChannel) pour envoyer/recevoir des
 * messages DIRECTEMENT de téléphone à téléphone — audio, image, vidéo ET
 * texte, selon exactement le même principe : le serveur ne voit jamais
 * passer le contenu, seulement les métadonnées de signalisation relayées
 * par [SignalingClient].
 *
 * On n'utilise ni piste audio ni piste vidéo WebRTC : uniquement un
 * DataChannel. Un fichier (audio, image ou vidéo) est découpé en blocs
 * binaires encadrés de contrôle JSON ("meta"/"end", avec le type de média
 * dans [ControlMessage.mediaKind]) ; un message texte tient dans un seul
 * message de contrôle JSON ("text"), pas besoin de découpage. Dans les
 * deux cas, le destinataire renvoie un accusé ("ack") une fois le message
 * persisté en local.
 *
 * Simplification assumée : une seule connexion P2P active à la fois, ce qui
 * est suffisant pour une messagerie asynchrone comme Wakh (on n'envoie/reçoit
 * pas plusieurs messages en parallèle).
 */
class WebRtcManager(
    context: Context,
    private val signalingClient: SignalingClient,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectionMutex = Mutex()

    private var started = false
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private var incomingBuffer: ByteArrayOutputStream? = null
    private var incomingMeta: ControlMessage? = null

    private var pendingAnswer: CompletableDeferred<SessionDescription>? = null

    private val _incomingFiles = MutableSharedFlow<IncomingFileEvent>(extraBufferCapacity = 8)
    val incomingFiles: SharedFlow<IncomingFileEvent> = _incomingFiles

    private val _incomingTexts = MutableSharedFlow<IncomingTextEvent>(extraBufferCapacity = 8)
    val incomingTexts: SharedFlow<IncomingTextEvent> = _incomingTexts

    private val _incomingDeletes = MutableSharedFlow<IncomingDeleteEvent>(extraBufferCapacity = 8)
    val incomingDeletes: SharedFlow<IncomingDeleteEvent> = _incomingDeletes

    /** À appeler une fois l'utilisateur identifié, pour commencer à écouter les offres entrantes. */
    fun start(phoneNumber: String) {
        initFactoryIfNeeded()
        if (started) return
        started = true
        scope.launch {
            signalingClient.incoming.collect { envelope -> handleSignal(envelope) }
        }
    }

    private fun initFactoryIfNeeded() {
        if (peerConnectionFactory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        // Pas de piste audio/vidéo WebRTC : on n'utilise que le DataChannel,
        // donc pas besoin de factories d'encodage/décodage vidéo.
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun iceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    // -------------------------------------------------------------------
    // Envoi (côté appelant) — fichiers (audio/image/vidéo) et texte
    // partagent l'établissement de connexion, seule la charge utile
    // envoyée sur le canal diffère.
    // -------------------------------------------------------------------

    /**
     * Tente d'envoyer [file] directement à [targetNumber] via WebRTC P2P —
     * pour un message audio, une image ou une vidéo selon [mediaKind].
     * Échoue si le destinataire n'est pas joignable ou n'ouvre pas de
     * connexion à temps — à charge de l'appelant (MessageRepository) de
     * proposer un dépôt en file d'attente hors-ligne en repli.
     */
    suspend fun sendFileMessage(
        targetNumber: String,
        file: File,
        mediaKind: MessageKind,
        messageId: String,
        durationMs: Int = 0,
        groupId: String? = null,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Unit> = connectionMutex.withLock {
        runCatching {
            val ackDeferred = CompletableDeferred<Unit>()
            val channel = establishOutgoingChannel(targetNumber, ackDeferred)

            streamFileOverChannel(channel, file, mediaKind, messageId, durationMs, groupId, onProgress)

            withTimeoutOrNull(ACK_TIMEOUT_MS) { ackDeferred.await() }
                ?: error("Pas d'accusé de réception du destinataire")

            signalingClient.send(SignalingEnvelope(type = SignalingType.HANGUP, target = targetNumber))
        }.also { closeCurrentConnection() }
            .onFailure { Log.w(TAG, "Échec de l'envoi P2P (${mediaKind.name}) : ${it.message}") }
    }

    /**
     * Tente d'envoyer [text] directement à [targetNumber] via WebRTC P2P —
     * même principe que [sendFileMessage], mais le contenu tient dans un
     * seul message de contrôle (pas de découpage en blocs nécessaire).
     */
    suspend fun sendTextMessage(
        targetNumber: String,
        text: String,
        messageId: String,
        groupId: String? = null,
    ): Result<Unit> = connectionMutex.withLock {
        runCatching {
            val ackDeferred = CompletableDeferred<Unit>()
            val channel = establishOutgoingChannel(targetNumber, ackDeferred)

            val payload = ControlMessage(type = ControlType.TEXT, messageId = messageId, content = text, groupId = groupId)
            channel.send(textBuffer(json.encodeToString(ControlMessage.serializer(), payload)))

            withTimeoutOrNull(ACK_TIMEOUT_MS) { ackDeferred.await() }
                ?: error("Pas d'accusé de réception du destinataire")

            signalingClient.send(SignalingEnvelope(type = SignalingType.HANGUP, target = targetNumber))
        }.also { closeCurrentConnection() }
            .onFailure { Log.w(TAG, "Échec de l'envoi P2P (texte) : ${it.message}") }
    }

    /**
     * Demande à [targetNumber] de supprimer localement le message
     * [targetMessageId] déjà reçu précédemment — "suppression pour tout
     * le monde", même principe d'établissement de connexion que
     * [sendTextMessage], charge utile minimale (juste l'identifiant).
     */
    suspend fun sendDeleteMessage(
        targetNumber: String,
        targetMessageId: String,
        groupId: String? = null,
    ): Result<Unit> = connectionMutex.withLock {
        runCatching {
            val ackDeferred = CompletableDeferred<Unit>()
            val channel = establishOutgoingChannel(targetNumber, ackDeferred)

            val payload = ControlMessage(type = ControlType.DELETE, messageId = targetMessageId, groupId = groupId)
            channel.send(textBuffer(json.encodeToString(ControlMessage.serializer(), payload)))

            withTimeoutOrNull(ACK_TIMEOUT_MS) { ackDeferred.await() }
                ?: error("Pas d'accusé de réception du destinataire")

            signalingClient.send(SignalingEnvelope(type = SignalingType.HANGUP, target = targetNumber))
        }.also { closeCurrentConnection() }
            .onFailure { Log.w(TAG, "Échec de la demande de suppression P2P : ${it.message}") }
    }

    /**
     * Établit une connexion WebRTC sortante vers [targetNumber] : crée la
     * PeerConnection et le DataChannel, échange offre/réponse via la
     * signalisation, attend que le canal soit ouvert, et retourne le
     * DataChannel prêt à l'emploi. [ackDeferred] est complété par
     * l'observer du canal dès réception d'un message "ack".
     */
    private suspend fun establishOutgoingChannel(
        targetNumber: String,
        ackDeferred: CompletableDeferred<Unit>,
    ): DataChannel {
        val factory = peerConnectionFactory ?: error("WebRTC non initialisé")

        val answerDeferred = CompletableDeferred<SessionDescription>()
        val channelOpenDeferred = CompletableDeferred<Unit>()
        pendingAnswer = answerDeferred

        val pc = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers()),
            object : PeerConnectionObserverAdapter() {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    signalingClient.send(
                        SignalingEnvelope(
                            type = SignalingType.ICE_CANDIDATE,
                            target = targetNumber,
                            candidate = IceCandidateDto(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp),
                        ),
                    )
                }
            },
        ) ?: error("Impossible de créer la connexion WebRTC")
        peerConnection = pc

        val channel = pc.createDataChannel(DATA_CHANNEL_LABEL, DataChannel.Init())
        dataChannel = channel
        channel.registerObserver(buildDataChannelObserver(channelOpenDeferred, ackDeferred))

        val offer = pc.createOfferSuspend(MediaConstraints())
        pc.setLocalDescriptionSuspend(offer)
        signalingClient.send(SignalingEnvelope(type = SignalingType.OFFER, target = targetNumber, sdp = offer.description))

        val answer = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { answerDeferred.await() }
            ?: error("Le destinataire n'a pas répondu à temps")
        pc.setRemoteDescriptionSuspend(answer)

        withTimeoutOrNull(CHANNEL_OPEN_TIMEOUT_MS) { channelOpenDeferred.await() }
            ?: error("Le canal de données ne s'est pas ouvert à temps")

        return channel
    }

    // -------------------------------------------------------------------
    // Réception (côté appelé) + relais des candidats ICE / raccroché
    // -------------------------------------------------------------------

    private suspend fun handleSignal(envelope: SignalingEnvelope) {
        when (envelope.type) {
            SignalingType.OFFER -> handleIncomingOffer(envelope)
            SignalingType.ANSWER -> {
                val sdp = envelope.sdp ?: return
                pendingAnswer?.complete(SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            SignalingType.ICE_CANDIDATE -> {
                val dto = envelope.candidate ?: return
                peerConnection?.addIceCandidate(IceCandidate(dto.sdpMid, dto.sdpMLineIndex, dto.candidate))
            }
            SignalingType.HANGUP -> closeCurrentConnection()
        }
    }

    private suspend fun handleIncomingOffer(envelope: SignalingEnvelope) = connectionMutex.withLock {
        runCatching {
            val fromNumber = envelope.from ?: return@runCatching
            val sdp = envelope.sdp ?: return@runCatching
            val factory = peerConnectionFactory ?: error("WebRTC non initialisé")

            val pc = factory.createPeerConnection(
                PeerConnection.RTCConfiguration(iceServers()),
                object : PeerConnectionObserverAdapter() {
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate ?: return
                        signalingClient.send(
                            SignalingEnvelope(
                                type = SignalingType.ICE_CANDIDATE,
                                target = fromNumber,
                                candidate = IceCandidateDto(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp),
                            ),
                        )
                    }

                    override fun onDataChannel(dc: DataChannel?) {
                        dc ?: return
                        dataChannel = dc
                        dc.registerObserver(buildDataChannelObserver(null, null, fromNumber))
                    }
                },
            ) ?: error("Impossible de créer la connexion WebRTC")
            peerConnection = pc

            pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.OFFER, sdp))
            val answer = pc.createAnswerSuspend(MediaConstraints())
            pc.setLocalDescriptionSuspend(answer)
            signalingClient.send(SignalingEnvelope(type = SignalingType.ANSWER, target = fromNumber, sdp = answer.description))
        }.onFailure {
            Log.w(TAG, "Échec de la réception d'une offre entrante : ${it.message}")
        }
    }

    // -------------------------------------------------------------------
    // DataChannel : envoi/réception des blocs binaires (fichiers) et des
    // messages de contrôle (texte, méta, fin, accusé de réception)
    // -------------------------------------------------------------------

    private fun buildDataChannelObserver(
        channelOpenDeferred: CompletableDeferred<Unit>?,
        ackDeferred: CompletableDeferred<Unit>?,
        fromNumberForReceive: String? = null,
    ) = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            if (dataChannel?.state() == DataChannel.State.OPEN) {
                channelOpenDeferred?.complete(Unit)
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer?) {
            buffer ?: return
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)

            if (!buffer.binary) {
                val text = String(bytes, Charsets.UTF_8)
                val control = runCatching { json.decodeFromString(ControlMessage.serializer(), text) }.getOrNull() ?: return
                when (control.type) {
                    ControlType.META -> {
                        incomingMeta = control
                        incomingBuffer = ByteArrayOutputStream(control.totalBytes?.toInt() ?: 0)
                    }
                    ControlType.END -> {
                        val peer = fromNumberForReceive ?: return
                        scope.launch { finalizeIncomingFile(peer) }
                    }
                    ControlType.TEXT -> {
                        val peer = fromNumberForReceive ?: return
                        val content = control.content ?: return
                        scope.launch { finalizeIncomingText(peer, content, control.messageId, control.groupId) }
                    }
                    ControlType.DELETE -> {
                        val peer = fromNumberForReceive ?: return
                        scope.launch { finalizeIncomingDelete(peer, control.messageId, control.groupId) }
                    }
                    ControlType.ACK -> ackDeferred?.complete(Unit)
                }
            } else {
                incomingBuffer?.write(bytes)
            }
        }
    }

    private suspend fun streamFileOverChannel(
        channel: DataChannel,
        file: File,
        mediaKind: MessageKind,
        messageId: String,
        durationMs: Int,
        groupId: String?,
        onProgress: (sent: Long, total: Long) -> Unit,
    ) {
        val totalBytes = file.length()
        val totalChunks = ((totalBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()

        val meta = ControlMessage(
            type = ControlType.META,
            messageId = messageId,
            fileName = file.name,
            totalBytes = totalBytes,
            chunkSize = CHUNK_SIZE_BYTES,
            totalChunks = totalChunks,
            durationMs = durationMs,
            mediaKind = mediaKind.name,
            groupId = groupId,
        )
        channel.send(textBuffer(json.encodeToString(ControlMessage.serializer(), meta)))

        file.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            var sent = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                // Contrôle de flux simple : on attend si trop de données sont
                // encore en attente d'envoi côté SCTP, pour ne pas saturer le canal.
                while (channel.bufferedAmount() > MAX_BUFFERED_AMOUNT_BYTES) {
                    delay(20)
                }

                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                channel.send(DataChannel.Buffer(ByteBuffer.wrap(chunk), true))
                sent += read
                onProgress(sent, totalBytes)
            }
        }

        val end = ControlMessage(type = ControlType.END, messageId = messageId)
        channel.send(textBuffer(json.encodeToString(ControlMessage.serializer(), end)))
    }

    private suspend fun finalizeIncomingFile(fromNumber: String) {
        val meta = incomingMeta ?: return
        val bytes = incomingBuffer?.toByteArray() ?: return
        incomingMeta = null
        incomingBuffer = null

        val mediaKind = runCatching { MessageKind.valueOf(meta.mediaKind ?: "AUDIO") }.getOrDefault(MessageKind.AUDIO)

        // Le destinataire enregistre le fichier reçu sur son propre téléphone
        // (stockage local uniquement), dans un dossier dédié à chaque type
        // de média — même principe pour les quatre.
        val subdir = when (mediaKind) {
            MessageKind.IMAGE -> "images/received"
            MessageKind.VIDEO -> "videos/received"
            MessageKind.DOCUMENT -> "documents/received"
            else -> "audio/received"
        }
        val extension = extensionForFileName(meta.fileName) ?: defaultExtension(mediaKind)
        val dir = File(appContext.filesDir, subdir).apply { mkdirs() }
        val destFile = File(dir, "${mediaKind.name.lowercase()}_${meta.messageId}$extension")
        destFile.writeBytes(bytes)

        val event = IncomingFileEvent(
            fromNumber = fromNumber,
            file = destFile,
            mediaKind = mediaKind,
            durationMs = meta.durationMs ?: 0,
            remoteMessageId = meta.messageId,
            groupId = meta.groupId,
            originalFileName = meta.fileName,
        )
        _incomingFiles.emit(event)
        // On n'envoie l'accusé de réception qu'une fois le message
        // effectivement persisté en local (Room), voir AppContainer.
        event.onPersisted.await()

        val ack = ControlMessage(type = ControlType.ACK, messageId = meta.messageId)
        dataChannel?.send(textBuffer(json.encodeToString(ControlMessage.serializer(), ack)))
    }

    private suspend fun finalizeIncomingText(fromNumber: String, content: String, messageId: String, groupId: String?) {
        val event = IncomingTextEvent(
            fromNumber = fromNumber,
            content = content,
            remoteMessageId = messageId,
            groupId = groupId,
        )
        _incomingTexts.emit(event)
        // Même principe que pour les fichiers : l'accusé n'est envoyé
        // qu'une fois le message persisté en local (Room).
        event.onPersisted.await()

        val ack = ControlMessage(type = ControlType.ACK, messageId = messageId)
        dataChannel?.send(textBuffer(json.encodeToString(ControlMessage.serializer(), ack)))
    }

    private suspend fun finalizeIncomingDelete(fromNumber: String, targetMessageId: String, groupId: String?) {
        val event = IncomingDeleteEvent(
            fromNumber = fromNumber,
            targetMessageId = targetMessageId,
            groupId = groupId,
        )
        _incomingDeletes.emit(event)
        // Même principe : l'accusé n'est envoyé qu'une fois la suppression
        // effectivement appliquée en local (voir AppContainer).
        event.onPersisted.await()

        val ack = ControlMessage(type = ControlType.ACK, messageId = targetMessageId)
        dataChannel?.send(textBuffer(json.encodeToString(ControlMessage.serializer(), ack)))
    }

    private fun textBuffer(text: String) = DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false)

    private fun extensionForFileName(fileName: String?): String? {
        val name = fileName ?: return null
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else null
    }

    private fun defaultExtension(mediaKind: MessageKind): String = when (mediaKind) {
        MessageKind.IMAGE -> ".jpg"
        MessageKind.VIDEO -> ".mp4"
        MessageKind.DOCUMENT -> ""
        else -> ".m4a"
    }

    private fun closeCurrentConnection() {
        runCatching { dataChannel?.close() }
        runCatching { peerConnection?.close() }
        dataChannel = null
        peerConnection = null
        incomingBuffer = null
        incomingMeta = null
        pendingAnswer = null
    }

    companion object {
        private const val TAG = "WebRtcManager"
        private const val DATA_CHANNEL_LABEL = "wakh-audio"

        /** Taille de bloc conservatrice, sous la limite de message SCTP des anciennes piles WebRTC. */
        private const val CHUNK_SIZE_BYTES = 16 * 1024
        private const val MAX_BUFFERED_AMOUNT_BYTES = 1L * 1024 * 1024

        private const val ANSWER_TIMEOUT_MS = 15_000L
        private const val CHANNEL_OPEN_TIMEOUT_MS = 15_000L
        private const val ACK_TIMEOUT_MS = 30_000L
    }
}

// ---------------------------------------------------------------------
// Wrappers suspend au-dessus des callbacks SDP classiques de libwebrtc
// ---------------------------------------------------------------------

private suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(
            object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description != null) cont.resume(description) else cont.resumeWithException(IllegalStateException("SDP nul"))
                }
                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Échec createOffer"))
                }
            },
            constraints,
        )
    }

private suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(
            object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description != null) cont.resume(description) else cont.resumeWithException(IllegalStateException("SDP nul"))
                }
                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Échec createAnswer"))
                }
            },
            constraints,
        )
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(description: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setLocalDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Échec setLocalDescription"))
                }
            },
            description,
        )
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(description: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setRemoteDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "Échec setRemoteDescription"))
                }
            },
            description,
        )
    }
