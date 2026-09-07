package com.wakh.app.webrtc

import kotlinx.coroutines.CompletableDeferred

/**
 * Émis par [WebRtcManager] dès qu'un message texte complet a été reçu.
 * Même principe que [IncomingFileEvent] pour l'audio : l'accusé de
 * réception n'est envoyé à l'expéditeur qu'une fois [onPersisted]
 * complété par le code appelant (après insertion en base Room).
 */
data class IncomingTextEvent(
    val fromNumber: String,
    val content: String,
    val remoteMessageId: String,
    val groupId: String? = null,
    val onPersisted: CompletableDeferred<Unit> = CompletableDeferred(),
)
