package com.wakh.app.webrtc

import kotlinx.coroutines.CompletableDeferred

/**
 * Émis par [WebRtcManager] quand une demande de "suppression pour tout le
 * monde" est reçue en P2P direct — même principe que [IncomingTextEvent] :
 * l'accusé de réception n'est envoyé qu'une fois [onPersisted] complété
 * par le code appelant (après suppression effective en local, voir
 * MessageRepository.handleIncomingDeleteRequest).
 */
data class IncomingDeleteEvent(
    val fromNumber: String,
    val targetMessageId: String,
    val groupId: String? = null,
    val onPersisted: CompletableDeferred<Unit> = CompletableDeferred(),
)
