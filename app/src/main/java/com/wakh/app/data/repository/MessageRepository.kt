package com.wakh.app.data.repository

import android.content.Context
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.local.db.MessageDao
import com.wakh.app.data.local.db.MessageDirection
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.local.db.MessageKind
import com.wakh.app.data.local.db.MessageStatus
import com.wakh.app.data.local.db.UnreadCount
import com.wakh.app.data.remote.AudioQueueApi
import com.wakh.app.data.remote.DeleteQueueApi
import com.wakh.app.data.remote.MediaQueueApi
import com.wakh.app.data.remote.SignalingClient
import com.wakh.app.data.remote.TextQueueApi
import com.wakh.app.util.ConversationId
import com.wakh.app.util.guessMimeType
import com.wakh.app.webrtc.WebRtcManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Orchestre l'envoi des messages — audio, image, vidéo ET texte, en
 * conversation individuelle OU de groupe, selon EXACTEMENT le même
 * principe pour tous :
 *   1. Vérifie si le destinataire est en ligne (presence_check).
 *   2. Si oui : tente un envoi P2P direct via WebRTC DataChannel.
 *   3. Si le destinataire est hors ligne, ou si le P2P échoue malgré la
 *      présence détectée : dépose le contenu temporairement sur le
 *      serveur (mode hybride), à récupérer quand il se reconnectera.
 *   4. Si les deux échouent : statut FAILED, avec possibilité de réessayer.
 *
 * Pour un GROUPE, ce cycle est simplement répété individuellement pour
 * chaque membre (le serveur n'a aucune notion de "groupe" — voir
 * GroupRepository) : un seul MessageEntity représente le message dans le
 * fil de discussion, avec un statut agrégé (voir [attemptGroupTextDelivery]
 * / [attemptGroupMediaDelivery] pour le détail de cette simplification
 * assumée : pas de suivi de livraison par membre, pas d'accusés de
 * réception individualisés dans l'UI).
 *
 * Gère aussi la réception : persistance locale (Room, + fichier pour les
 * types binaires) des messages reçus, que ce soit en direct (P2P) ou
 * récupérés depuis la file d'attente hors-ligne à la reconnexion.
 *
 * Note d'architecture : l'audio a sa propre file d'attente serveur
 * ([AudioQueueApi]) et le texte la sienne ([TextQueueApi]), tandis que
 * l'image et la vidéo partagent la même ([MediaQueueApi]) — voir les
 * apps Django `audioqueue`/`textqueue`/`mediaqueue` côté serveur.
 */
class MessageRepository(
    private val messageDao: MessageDao,
    private val signalingClient: SignalingClient,
    private val webRtcManager: WebRtcManager,
    private val audioQueueApi: AudioQueueApi,
    private val textQueueApi: TextQueueApi,
    private val mediaQueueApi: MediaQueueApi,
    private val deleteQueueApi: DeleteQueueApi,
    private val groupRepository: GroupRepository,
    private val userPreferences: UserPreferences,
    private val appContext: Context,
) {
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeForContact(conversationId)

    fun observeLastMessagePerContact(): Flow<List<MessageEntity>> =
        messageDao.observeLastMessagePerContact()

    /** Nombre de messages non lus par conversation (individuelle ou de groupe). */
    fun observeUnreadCounts(): Flow<List<UnreadCount>> =
        messageDao.observeUnreadCounts()

    /**
     * Marque comme lus tous les messages reçus de cette conversation.
     * Purement local : aucun accusé de lecture n'est renvoyé à
     * l'expéditeur (contrairement à l'accusé de livraison qui, lui,
     * transite réellement sur le réseau).
     */
    suspend fun markConversationAsRead(conversationId: String) {
        messageDao.markConversationRead(conversationId)
    }

    /**
     * Supprime un message localement (base + fichier associé s'il y en a
     * un). Suppression "pour moi" uniquement : les autres participants
     * conservent leur propre copie, il n'existe pas de mécanisme pour la
     * supprimer à distance chez eux.
     */
    suspend fun deleteMessage(messageId: String) {
        val message = messageDao.getById(messageId) ?: return
        message.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        messageDao.deleteById(messageId)
    }

    /**
     * Supprime [messageId] "pour tout le monde" : demande à chaque
     * destinataire (le contact, ou tous les membres du groupe) de le
     * supprimer localement — même principe P2P/file d'attente que l'envoi
     * d'un message — puis le supprime aussi de mon côté. Uniquement
     * pertinent pour un message que J'AI envoyé (SENT) : on ne peut pas
     * forcer la suppression d'un message reçu chez son expéditeur.
     */
    suspend fun deleteForEveryone(messageId: String) {
        val message = messageDao.getById(messageId) ?: return
        val me = userPreferences.profile.first() ?: return
        if (message.direction != MessageDirection.SENT) {
            deleteMessage(messageId)
            return
        }

        val groupId = ConversationId.groupIdOrNull(message.contactPhoneNumber)
        if (groupId != null) {
            val members = groupRepository.getMemberPhoneNumbers(groupId).filter { it != me.phoneNumber }
            for (member in members) {
                requestRemoteDelete(member, messageId, groupId)
            }
        } else {
            requestRemoteDelete(message.contactPhoneNumber, messageId, null)
        }

        deleteMessage(messageId)
    }

    /** Demande à [targetNumber] de supprimer [targetMessageId] — P2P si en ligne, sinon file d'attente. */
    private suspend fun requestRemoteDelete(targetNumber: String, targetMessageId: String, groupId: String?) {
        var delivered = false
        if (signalingClient.checkPresence(targetNumber)) {
            delivered = webRtcManager.sendDeleteMessage(targetNumber, targetMessageId, groupId).isSuccess
        }
        if (!delivered) {
            val me = userPreferences.profile.first() ?: return
            deleteQueueApi.uploadQueued(
                senderNumber = me.phoneNumber,
                recipientNumber = targetNumber,
                targetMessageId = targetMessageId,
                groupId = groupId,
            )
        }
    }

    /**
     * Appelé quand [WebRtcManager] reçoit une demande de suppression en
     * P2P direct : applique la suppression locale comme si l'utilisateur
     * l'avait demandée lui-même (même code que [deleteMessage]).
     */
    suspend fun handleIncomingDeleteRequest(targetMessageId: String) {
        deleteMessage(targetMessageId)
    }

    /**
     * Récupère les demandes de suppression déposées en attente côté
     * serveur pendant qu'on était hors ligne — même principe que
     * [fetchQueuedTextMessages].
     */
    suspend fun fetchQueuedDeletes(myNumber: String) {
        val pending = deleteQueueApi.listPending(myNumber).getOrNull() ?: return
        for (item in pending) {
            deleteMessage(item.targetMessageId)
            deleteQueueApi.ack(item.id)
        }
    }

    // -------------------------------------------------------------------
    // Envoi — conversation individuelle
    // -------------------------------------------------------------------

    suspend fun sendVoiceMessage(contactPhoneNumber: String, file: File, durationMs: Int) {
        sendMediaMessage(contactPhoneNumber, file, MessageKind.AUDIO, durationMs)
    }

    /** Même principe que [sendVoiceMessage], pour une image, une vidéo ou un document. */
    suspend fun sendMediaMessage(
        contactPhoneNumber: String,
        file: File,
        kind: MessageKind,
        durationMs: Int = 0,
        fileName: String? = null,
    ) {
        val me = userPreferences.profile.first() ?: return
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = messageId,
            contactPhoneNumber = contactPhoneNumber,
            direction = MessageDirection.SENT,
            kind = kind,
            localFilePath = file.absolutePath,
            durationMs = durationMs,
            fileName = fileName,
            sizeBytes = file.length(),
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
        )
        messageDao.upsert(entity)
        attemptMediaDelivery(entity, me.phoneNumber)
    }

    /** Même principe que [sendVoiceMessage], pour un message texte. */
    suspend fun sendTextMessage(contactPhoneNumber: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val me = userPreferences.profile.first() ?: return
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = messageId,
            contactPhoneNumber = contactPhoneNumber,
            direction = MessageDirection.SENT,
            kind = MessageKind.TEXT,
            textContent = trimmed,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
        )
        messageDao.upsert(entity)
        attemptTextDelivery(entity, me.phoneNumber)
    }

    // -------------------------------------------------------------------
    // Envoi — conversation de groupe (fan-out vers chaque membre)
    // -------------------------------------------------------------------

    /** Même principe que [sendMediaMessage], pour un groupe : voir la note de classe sur le fan-out. */
    suspend fun sendGroupMediaMessage(
        groupId: String,
        file: File,
        kind: MessageKind,
        durationMs: Int = 0,
        fileName: String? = null,
    ) {
        val me = userPreferences.profile.first() ?: return
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = messageId,
            contactPhoneNumber = ConversationId.forGroup(groupId),
            direction = MessageDirection.SENT,
            kind = kind,
            localFilePath = file.absolutePath,
            durationMs = durationMs,
            fileName = fileName,
            sizeBytes = file.length(),
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
        )
        messageDao.upsert(entity)
        attemptGroupMediaDelivery(groupId, entity, me.phoneNumber)
    }

    /** Même principe que [sendTextMessage], pour un groupe : voir la note de classe sur le fan-out. */
    suspend fun sendGroupTextMessage(groupId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val me = userPreferences.profile.first() ?: return
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            id = messageId,
            contactPhoneNumber = ConversationId.forGroup(groupId),
            direction = MessageDirection.SENT,
            kind = MessageKind.TEXT,
            textContent = trimmed,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
        )
        messageDao.upsert(entity)
        attemptGroupTextDelivery(groupId, entity, me.phoneNumber)
    }

    // -------------------------------------------------------------------
    // Réessai
    // -------------------------------------------------------------------

    suspend fun retry(messageId: String) {
        val message = messageDao.getById(messageId) ?: return
        val me = userPreferences.profile.first() ?: return
        messageDao.updateStatus(messageId, MessageStatus.SENDING)

        val groupId = ConversationId.groupIdOrNull(message.contactPhoneNumber)
        if (groupId != null) {
            // Simplification assumée : on retente l'envoi vers TOUS les
            // membres, y compris ceux déjà servis avec succès la première
            // fois (pas de suivi par membre — voir la doc de classe).
            when (message.kind) {
                MessageKind.TEXT -> attemptGroupTextDelivery(groupId, message, me.phoneNumber)
                MessageKind.AUDIO, MessageKind.IMAGE, MessageKind.VIDEO, MessageKind.DOCUMENT ->
                    attemptGroupMediaDelivery(groupId, message, me.phoneNumber)
            }
        } else {
            when (message.kind) {
                MessageKind.TEXT -> attemptTextDelivery(message, me.phoneNumber)
                MessageKind.AUDIO, MessageKind.IMAGE, MessageKind.VIDEO, MessageKind.DOCUMENT ->
                    attemptMediaDelivery(message, me.phoneNumber)
            }
        }
    }

    // -------------------------------------------------------------------
    // Livraison — conversation individuelle
    // -------------------------------------------------------------------

    /**
     * Gère l'envoi audio/image/vidéo : le P2P est identique pour les
     * trois (WebRtcManager.sendFileMessage), seul le repli en file
     * d'attente hors-ligne diffère selon le type (audioqueue vs
     * mediaqueue côté serveur).
     */
    private suspend fun attemptMediaDelivery(message: MessageEntity, myNumber: String) {
        val path = message.localFilePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        val online = signalingClient.checkPresence(message.contactPhoneNumber)
        if (online) {
            val result = webRtcManager.sendFileMessage(
                targetNumber = message.contactPhoneNumber,
                file = file,
                mediaKind = message.kind,
                messageId = message.id,
                durationMs = message.durationMs,
            )
            if (result.isSuccess) {
                messageDao.updateStatus(message.id, MessageStatus.SENT_RECEIVED)
                return
            }
        }

        val uploadResult = uploadToQueue(
            kind = message.kind,
            senderNumber = myNumber,
            recipientNumber = message.contactPhoneNumber,
            durationMs = message.durationMs,
            file = file,
            groupId = null,
            fileName = message.fileName,
        )
        uploadResult.onSuccess { queuedId ->
            messageDao.updateStatusWithQueuedId(message.id, MessageStatus.QUEUED_OFFLINE, queuedId)
        }.onFailure {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
        }
    }

    /** Même principe que [attemptMediaDelivery], pour un message texte. */
    private suspend fun attemptTextDelivery(message: MessageEntity, myNumber: String) {
        val text = message.textContent
        if (text.isNullOrBlank()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        val online = signalingClient.checkPresence(message.contactPhoneNumber)
        if (online) {
            val result = webRtcManager.sendTextMessage(
                targetNumber = message.contactPhoneNumber,
                text = text,
                messageId = message.id,
            )
            if (result.isSuccess) {
                messageDao.updateStatus(message.id, MessageStatus.SENT_RECEIVED)
                return
            }
        }

        val uploadResult = textQueueApi.uploadQueued(
            senderNumber = myNumber,
            recipientNumber = message.contactPhoneNumber,
            content = text,
        )
        uploadResult.onSuccess { queuedId ->
            messageDao.updateStatusWithQueuedId(message.id, MessageStatus.QUEUED_OFFLINE, queuedId)
        }.onFailure {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
        }
    }

    // -------------------------------------------------------------------
    // Livraison — conversation de groupe (fan-out)
    // -------------------------------------------------------------------

    /**
     * Envoie [message] individuellement à chaque membre du groupe (sauf
     * moi-même), selon exactement le même principe présence/P2P/file
     * d'attente que [attemptMediaDelivery].
     *
     * Simplification de statut assumée : le message passe à SENT_RECEIVED
     * dès qu'AU MOINS UN membre l'a reçu (P2P ou file d'attente réussie),
     * et à FAILED seulement si TOUS les membres ont échoué. Il n'y a pas
     * de suivi de livraison par membre dans l'UI (pas de "livré à 3/5
     * personnes") — c'est un choix de simplification assumé pour ce
     * projet, pas une contrainte technique du protocole.
     */
    private suspend fun attemptGroupMediaDelivery(groupId: String, message: MessageEntity, myNumber: String) {
        val path = message.localFilePath
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        val members = groupRepository.getMemberPhoneNumbers(groupId).filter { it != myNumber }
        if (members.isEmpty()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        var anySucceeded = false
        for (member in members) {
            var delivered = false
            if (signalingClient.checkPresence(member)) {
                val result = webRtcManager.sendFileMessage(
                    targetNumber = member,
                    file = file,
                    mediaKind = message.kind,
                    messageId = message.id,
                    durationMs = message.durationMs,
                    groupId = groupId,
                )
                delivered = result.isSuccess
            }
            if (!delivered) {
                delivered = uploadToQueue(
                    kind = message.kind,
                    senderNumber = myNumber,
                    recipientNumber = member,
                    durationMs = message.durationMs,
                    file = file,
                    groupId = groupId,
                    fileName = message.fileName,
                ).isSuccess
            }
            if (delivered) anySucceeded = true
        }

        messageDao.updateStatus(message.id, if (anySucceeded) MessageStatus.SENT_RECEIVED else MessageStatus.FAILED)
    }

    /** Même principe que [attemptGroupMediaDelivery], pour un message texte. */
    private suspend fun attemptGroupTextDelivery(groupId: String, message: MessageEntity, myNumber: String) {
        val text = message.textContent
        if (text.isNullOrBlank()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        val members = groupRepository.getMemberPhoneNumbers(groupId).filter { it != myNumber }
        if (members.isEmpty()) {
            messageDao.updateStatus(message.id, MessageStatus.FAILED)
            return
        }

        var anySucceeded = false
        for (member in members) {
            var delivered = false
            if (signalingClient.checkPresence(member)) {
                val result = webRtcManager.sendTextMessage(
                    targetNumber = member,
                    text = text,
                    messageId = message.id,
                    groupId = groupId,
                )
                delivered = result.isSuccess
            }
            if (!delivered) {
                delivered = textQueueApi.uploadQueued(
                    senderNumber = myNumber,
                    recipientNumber = member,
                    content = text,
                    groupId = groupId,
                ).isSuccess
            }
            if (delivered) anySucceeded = true
        }

        messageDao.updateStatus(message.id, if (anySucceeded) MessageStatus.SENT_RECEIVED else MessageStatus.FAILED)
    }

    /** Dépôt en file d'attente hors-ligne, quel que soit le type binaire (audio vs image/vidéo/document). */
    private suspend fun uploadToQueue(
        kind: MessageKind,
        senderNumber: String,
        recipientNumber: String,
        durationMs: Int,
        file: File,
        groupId: String?,
        fileName: String? = null,
    ): Result<String> = when (kind) {
        MessageKind.AUDIO -> audioQueueApi.uploadQueued(
            senderNumber = senderNumber,
            recipientNumber = recipientNumber,
            durationMs = durationMs,
            file = file,
            groupId = groupId,
        )
        MessageKind.IMAGE, MessageKind.VIDEO, MessageKind.DOCUMENT -> mediaQueueApi.uploadQueued(
            senderNumber = senderNumber,
            recipientNumber = recipientNumber,
            mediaKind = kind.name,
            durationMs = durationMs,
            file = file,
            mimeType = guessMimeType(file, defaultMimeType(kind)),
            groupId = groupId,
            originalFileName = fileName,
        )
        MessageKind.TEXT -> error("uploadToQueue ne gère pas TEXT (voir textQueueApi directement)")
    }

    // -------------------------------------------------------------------
    // Réception
    // -------------------------------------------------------------------

    /**
     * Appelé quand [WebRtcManager] vient de recevoir un fichier en direct
     * (P2P) — audio, image ou vidéo, en conversation individuelle ou de
     * groupe selon [groupId].
     */
    suspend fun handleIncomingP2PFile(
        fromNumber: String,
        file: File,
        kind: MessageKind,
        durationMs: Int,
        remoteMessageId: String,
        groupId: String?,
        fileName: String? = null,
    ) {
        val conversationId = groupId?.let { ConversationId.forGroup(it) } ?: fromNumber
        messageDao.upsert(
            MessageEntity(
                id = remoteMessageId,
                contactPhoneNumber = conversationId,
                direction = MessageDirection.RECEIVED,
                kind = kind,
                localFilePath = file.absolutePath,
                durationMs = durationMs,
                fileName = fileName,
                sizeBytes = file.length(),
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT_RECEIVED,
                isRead = false,
                senderPhoneNumber = if (groupId != null) fromNumber else null,
            ),
        )
    }

    /** Même principe que [handleIncomingP2PFile], pour un message texte reçu en direct (P2P). */
    suspend fun handleIncomingP2PText(
        fromNumber: String,
        content: String,
        remoteMessageId: String,
        groupId: String?,
    ) {
        val conversationId = groupId?.let { ConversationId.forGroup(it) } ?: fromNumber
        messageDao.upsert(
            MessageEntity(
                id = remoteMessageId,
                contactPhoneNumber = conversationId,
                direction = MessageDirection.RECEIVED,
                kind = MessageKind.TEXT,
                textContent = content,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.SENT_RECEIVED,
                isRead = false,
                senderPhoneNumber = if (groupId != null) fromNumber else null,
            ),
        )
    }

    /**
     * Récupère les messages vocaux déposés en attente côté serveur pendant
     * qu'on était hors ligne (mode hybride), typiquement appelé à la
     * réception d'une notification "pending_audio" juste après reconnexion.
     */
    suspend fun fetchQueuedMessages(myNumber: String) {
        val pending = audioQueueApi.listPending(myNumber).getOrNull() ?: return
        for (item in pending) {
            val dir = File(appContext.filesDir, "audio/received").apply { mkdirs() }
            val destFile = File(dir, "voice_${item.id}.m4a")

            val downloaded = audioQueueApi.download(item.id, destFile)
            if (downloaded.isSuccess) {
                val conversationId = item.groupId?.let { ConversationId.forGroup(it) } ?: item.senderNumber
                messageDao.upsert(
                    MessageEntity(
                        id = item.id,
                        contactPhoneNumber = conversationId,
                        direction = MessageDirection.RECEIVED,
                        kind = MessageKind.AUDIO,
                        localFilePath = destFile.absolutePath,
                        durationMs = item.durationMs,
                        sizeBytes = destFile.length(),
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.SENT_RECEIVED,
                        isRead = false,
                        senderPhoneNumber = if (item.groupId != null) item.senderNumber else null,
                    ),
                )
                // Confirmation de réception : le serveur supprime immédiatement
                // sa copie temporaire, rien n'est conservé après livraison.
                audioQueueApi.ack(item.id)
            }
        }
    }

    /**
     * Même principe que [fetchQueuedMessages], pour les messages texte en
     * attente — plus simple, pas d'étape de téléchargement séparée : le
     * contenu est déjà inclus dans la réponse de la liste.
     */
    suspend fun fetchQueuedTextMessages(myNumber: String) {
        val pending = textQueueApi.listPending(myNumber).getOrNull() ?: return
        for (item in pending) {
            val conversationId = item.groupId?.let { ConversationId.forGroup(it) } ?: item.senderNumber
            messageDao.upsert(
                MessageEntity(
                    id = item.id,
                    contactPhoneNumber = conversationId,
                    direction = MessageDirection.RECEIVED,
                    kind = MessageKind.TEXT,
                    textContent = item.content,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.SENT_RECEIVED,
                    isRead = false,
                    senderPhoneNumber = if (item.groupId != null) item.senderNumber else null,
                ),
            )
            textQueueApi.ack(item.id)
        }
    }

    /**
     * Même principe que [fetchQueuedMessages], pour les images/vidéos en
     * attente (file d'attente unique, distinguée par `mediaKind`).
     */
    suspend fun fetchQueuedMedia(myNumber: String) {
        val pending = mediaQueueApi.listPending(myNumber).getOrNull() ?: return
        for (item in pending) {
            val kind = runCatching { MessageKind.valueOf(item.mediaKind) }.getOrNull() ?: continue
            val subdir = when (kind) {
                MessageKind.IMAGE -> "images/received"
                MessageKind.VIDEO -> "videos/received"
                MessageKind.DOCUMENT -> "documents/received"
                else -> continue // mediaqueue ne transporte que IMAGE/VIDEO/DOCUMENT
            }
            val extension = when (kind) {
                MessageKind.IMAGE -> ".jpg"
                MessageKind.VIDEO -> ".mp4"
                MessageKind.DOCUMENT -> item.originalFileName
                    ?.let { name -> name.lastIndexOf('.').takeIf { it >= 0 }?.let { name.substring(it) } }
                    ?: ""
                else -> ""
            }
            val dir = File(appContext.filesDir, subdir).apply { mkdirs() }
            val destFile = File(dir, "${kind.name.lowercase()}_${item.id}$extension")

            val downloaded = mediaQueueApi.download(item.id, destFile)
            if (downloaded.isSuccess) {
                val conversationId = item.groupId?.let { ConversationId.forGroup(it) } ?: item.senderNumber
                messageDao.upsert(
                    MessageEntity(
                        id = item.id,
                        contactPhoneNumber = conversationId,
                        direction = MessageDirection.RECEIVED,
                        kind = kind,
                        localFilePath = destFile.absolutePath,
                        durationMs = item.durationMs,
                        fileName = item.originalFileName,
                        sizeBytes = destFile.length(),
                        timestamp = System.currentTimeMillis(),
                        status = MessageStatus.SENT_RECEIVED,
                        isRead = false,
                        senderPhoneNumber = if (item.groupId != null) item.senderNumber else null,
                    ),
                )
                mediaQueueApi.ack(item.id)
            }
        }
    }

    private fun defaultMimeType(kind: MessageKind): String = when (kind) {
        MessageKind.IMAGE -> "image/jpeg"
        MessageKind.VIDEO -> "video/mp4"
        else -> "application/octet-stream"
    }
}
