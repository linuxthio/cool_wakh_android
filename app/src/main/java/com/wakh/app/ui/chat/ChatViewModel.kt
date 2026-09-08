package com.wakh.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.audio.AudioPlayer
import com.wakh.app.audio.AudioRecorder
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.local.db.MessageKind
import com.wakh.app.data.remote.SignalingClient
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.data.repository.MessageRepository
import com.wakh.app.util.ConversationId
import com.wakh.app.util.copyDocumentToAppStorage
import com.wakh.app.util.copyUriToAppStorage
import com.wakh.app.util.videoDurationMs
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Taille max acceptée côté client pour un document, avant même de tenter l'envoi (le serveur applique la même limite). */
private const val MAX_DOCUMENT_BYTES = 30L * 1024 * 1024

/**
 * [conversationId] désigne soit un vrai numéro de téléphone (conversation
 * individuelle), soit une valeur synthétique "group:<id>" (conversation
 * de groupe) — voir util/ConversationId.kt. Ce ViewModel gère les deux
 * cas selon exactement le même principe pour l'envoi (audio/image/vidéo/
 * document/texte), seule la présence (1-à-1 uniquement) et la résolution
 * du nom/des membres diffèrent.
 */
class ChatViewModel(
    private val conversationId: String,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val groupRepository: GroupRepository,
    private val signalingClient: SignalingClient,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val appContext: Context,
) : ViewModel() {

    /** `null` pour une conversation individuelle — voir [isGroup]. Exposé pour naviguer vers l'écran d'informations du groupe. */
    val groupId: String? = ConversationId.groupIdOrNull(conversationId)
    val isGroup: Boolean = groupId != null

    val messages: StateFlow<List<MessageEntity>> = messageRepository
        .observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nom du contact, ou nom du groupe. */
    var title by mutableStateOf(conversationId)
        private set

    /** Uniquement pertinent pour un groupe (nombre de membres, hors moi-même). */
    var memberCount by mutableStateOf(0)
        private set

    private val _isOnline = MutableStateFlow(false)
    /** `false` par défaut et jamais mise à jour pour un groupe (pas de statut "en ligne" unique). */
    val isOnline: StateFlow<Boolean> = _isOnline

    var isRecording by mutableStateOf(false)
        private set

    var recordingElapsedMs by mutableStateOf(0)
        private set

    var textInput by mutableStateOf("")
        private set

    /** Vrai pendant la copie locale + l'envoi d'une pièce jointe choisie, pour un léger indicateur de chargement. */
    var isSendingAttachment by mutableStateOf(false)
        private set

    /** Message d'erreur bref (ex. document trop volumineux), affiché puis effacé par l'écran. */
    var attachmentError by mutableStateOf<String?>(null)
        private set

    val activeMessageId = audioPlayer.activeMessageId
    val isPlayingAudio = audioPlayer.isPlaying
    val playbackPositionMs = audioPlayer.positionMs

    /** Numéro -> nom, résolu pour les membres du groupe — utilisé pour étiqueter chaque bulle reçue. */
    private var groupMemberNames: Map<String, String> = emptyMap()

    private var presenceJob: Job? = null
    private var recordingTickerJob: Job? = null

    init {
        val currentGroupId = groupId
        if (currentGroupId != null) {
            // Observés en continu (plutôt qu'une simple lecture ponctuelle) :
            // ce ViewModel reste en mémoire tant que l'écran de discussion
            // n'est pas fermé, y compris pendant un aller-retour vers
            // l'écran d'informations du groupe (renommage, ajout/retrait de
            // membre) — sans Flow, ces changements resteraient invisibles
            // au retour tant que l'utilisateur ne rouvre pas la conversation.
            viewModelScope.launch {
                groupRepository.observeGroup(currentGroupId).collect { group ->
                    group?.let { title = it.name }
                }
            }
            viewModelScope.launch {
                groupRepository.observeMembers(currentGroupId).collect { members ->
                    memberCount = members.size
                    val names = mutableMapOf<String, String>()
                    for (member in members) {
                        contactRepository.getContact(member.phoneNumber)?.let {
                            names[member.phoneNumber] = it.displayName
                        }
                    }
                    groupMemberNames = names
                }
            }
        } else {
            viewModelScope.launch {
                contactRepository.getContact(conversationId)?.let { title = it.displayName }
            }
        }

        // La présence en ligne/hors ligne n'a de sens que pour un contact
        // unique — pas de statut "en ligne" agrégé pour un groupe.
        if (groupId == null) {
            startPresenceLoop()
        }

        // Marque la conversation comme lue dès l'ouverture, et à chaque
        // mise à jour de la liste tant que l'écran reste ouvert (couvre
        // aussi bien les messages déjà présents que ceux qui arrivent en
        // direct pendant la consultation). Purement local — voir
        // MessageRepository.markConversationAsRead.
        viewModelScope.launch {
            messages.collect {
                messageRepository.markConversationAsRead(conversationId)
            }
        }
    }

    /** Nom affiché au-dessus d'une bulle reçue en groupe, ou le numéro si inconnu des contacts. */
    fun senderDisplayName(phoneNumber: String): String = groupMemberNames[phoneNumber] ?: phoneNumber

    private fun startPresenceLoop() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                _isOnline.value = signalingClient.checkPresence(conversationId)
                delay(PRESENCE_POLL_INTERVAL_MS)
            }
        }
    }

    fun startRecording() {
        if (isRecording) return
        audioRecorder.start()
        isRecording = true
        recordingElapsedMs = 0
        recordingTickerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (isActive && isRecording) {
                recordingElapsedMs = (System.currentTimeMillis() - start).toInt()
                delay(100)
            }
        }
    }

    fun stopRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        recordingTickerJob?.cancel()

        val result = audioRecorder.stop() ?: return
        val (file, durationMs) = result

        if (durationMs < MIN_DURATION_MS) {
            file.delete()
            return
        }

        viewModelScope.launch {
            val currentGroupId = groupId
            if (currentGroupId != null) {
                messageRepository.sendGroupMediaMessage(currentGroupId, file, MessageKind.AUDIO, durationMs)
            } else {
                messageRepository.sendVoiceMessage(conversationId, file, durationMs)
            }
        }
    }

    fun cancelRecording() {
        isRecording = false
        recordingTickerJob?.cancel()
        audioRecorder.cancel()
    }

    fun onTextInputChange(value: String) {
        textInput = value
    }

    /** Même principe que [stopRecordingAndSend] pour l'audio : on délègue au repository. */
    fun sendTextMessage() {
        val text = textInput.trim()
        if (text.isEmpty()) return
        textInput = ""
        viewModelScope.launch {
            val currentGroupId = groupId
            if (currentGroupId != null) {
                messageRepository.sendGroupTextMessage(currentGroupId, text)
            } else {
                messageRepository.sendTextMessage(conversationId, text)
            }
        }
    }

    /**
     * Envoie jusqu'à 10 images choisies via le sélecteur système
     * (sélection multiple). Chacune est copiée dans le stockage local
     * avant l'envoi (cohérent avec le principe "aucun stockage cloud"),
     * puis envoyée l'une après l'autre selon le même principe que l'audio.
     */
    fun sendImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isSendingAttachment = true
            for ((index, uri) in uris.withIndex()) {
                val file = copyUriToAppStorage(
                    appContext,
                    uri,
                    "images/sent",
                    "image_${System.currentTimeMillis()}_$index",
                )
                if (file != null) {
                    sendMediaToConversation(file, MessageKind.IMAGE)
                }
            }
            isSendingAttachment = false
        }
    }

    /** Même principe que [sendImages], pour une vidéo (durée extraite localement avant l'envoi). */
    fun sendVideo(uri: Uri) {
        viewModelScope.launch {
            isSendingAttachment = true
            val file = copyUriToAppStorage(appContext, uri, "videos/sent", "video_${System.currentTimeMillis()}")
            if (file != null) {
                val durationMs = videoDurationMs(file)
                sendMediaToConversation(file, MessageKind.VIDEO, durationMs)
            }
            isSendingAttachment = false
        }
    }

    /**
     * Envoie un document (PDF, Word, ZIP...) choisi via le sélecteur
     * système, jusqu'à 30 Mo — même principe que pour les autres pièces
     * jointes, avec en plus la préservation de son nom d'origine pour
     * l'affichage (voir util/MediaStorage.copyDocumentToAppStorage).
     */
    fun sendDocument(uri: Uri) {
        viewModelScope.launch {
            isSendingAttachment = true
            val info = copyDocumentToAppStorage(appContext, uri, "documents/sent")
            if (info == null) {
                isSendingAttachment = false
                attachmentError = "Impossible de lire ce document"
                return@launch
            }
            if (info.sizeBytes > MAX_DOCUMENT_BYTES) {
                info.file.delete()
                isSendingAttachment = false
                attachmentError = "Ce document dépasse la limite de 30 Mo"
                return@launch
            }
            sendMediaToConversation(info.file, MessageKind.DOCUMENT, fileName = info.originalFileName)
            isSendingAttachment = false
        }
    }

    fun clearAttachmentError() {
        attachmentError = null
    }

    /** Signale un échec (ex. aucune application installée ne peut ouvrir ce document) via le même mécanisme d'erreur. */
    fun reportAttachmentError(message: String) {
        attachmentError = message
    }

    private suspend fun sendMediaToConversation(
        file: File,
        kind: MessageKind,
        durationMs: Int = 0,
        fileName: String? = null,
    ) {
        val currentGroupId = groupId
        if (currentGroupId != null) {
            messageRepository.sendGroupMediaMessage(currentGroupId, file, kind, durationMs, fileName)
        } else {
            messageRepository.sendMediaMessage(conversationId, file, kind, durationMs, fileName)
        }
    }

    fun retry(messageId: String) {
        viewModelScope.launch { messageRepository.retry(messageId) }
    }

    /** Suppression locale uniquement (voir MessageRepository.deleteMessage). */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch { messageRepository.deleteMessage(messageId) }
    }

    /** Suppression "pour tout le monde" (voir MessageRepository.deleteForEveryone) — uniquement pour mes propres messages. */
    fun deleteForEveryone(messageId: String) {
        viewModelScope.launch { messageRepository.deleteForEveryone(messageId) }
    }

    fun togglePlayback(message: MessageEntity) {
        val path = message.localFilePath ?: return
        when {
            audioPlayer.activeMessageId.value != message.id -> audioPlayer.play(message.id, path)
            audioPlayer.isPlaying.value -> audioPlayer.pause()
            else -> audioPlayer.resume()
        }
    }

    override fun onCleared() {
        presenceJob?.cancel()
        recordingTickerJob?.cancel()
        audioPlayer.stop()
        super.onCleared()
    }

    companion object {
        private const val MIN_DURATION_MS = 800
        private const val PRESENCE_POLL_INTERVAL_MS = 5000L
    }
}
