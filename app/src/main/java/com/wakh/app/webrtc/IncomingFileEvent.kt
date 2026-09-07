package com.wakh.app.webrtc

import com.wakh.app.data.local.db.MessageKind
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * Émis par [WebRtcManager] dès qu'un fichier (audio, image ou vidéo) a
 * été intégralement reçu et écrit sur le stockage local. L'accusé de
 * réception n'est envoyé à l'expéditeur qu'une fois [onPersisted]
 * complété par le code appelant (typiquement après insertion en base
 * Room) — voir MessageRepository.
 */
data class IncomingFileEvent(
    val fromNumber: String,
    val file: File,
    val mediaKind: MessageKind,
    val durationMs: Int,
    val remoteMessageId: String,
    val groupId: String? = null,
    /** Nom d'origine du fichier — uniquement renseigné pour un document (voir MessageEntity.fileName). */
    val originalFileName: String? = null,
    val onPersisted: CompletableDeferred<Unit> = CompletableDeferred(),
)
