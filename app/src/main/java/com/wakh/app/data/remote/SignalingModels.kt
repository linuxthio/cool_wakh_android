package com.wakh.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enveloppe générique du protocole WebSocket de signalisation, qui reflète
 * exactement le schéma du serveur (voir wakh-server/signaling/fastapi_app.py).
 * Un seul type de message avec des champs optionnels, pour rester simple à
 * sérialiser/désérialiser des deux côtés.
 */
@Serializable
data class SignalingEnvelope(
    val type: String,
    val target: String? = null,
    val from: String? = null,
    val sdp: String? = null,
    val candidate: IceCandidateDto? = null,
    val online: Boolean? = null,
    val count: Int? = null,
    val message: String? = null,
    @SerialName("in_response_to") val inResponseTo: String? = null,
)

@Serializable
data class IceCandidateDto(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
)

object SignalingType {
    const val PRESENCE_CHECK = "presence_check"
    const val PRESENCE_STATUS = "presence_status"
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice_candidate"
    const val HANGUP = "hangup"
    const val PENDING_AUDIO = "pending_audio"
    const val PENDING_TEXT = "pending_text"
    const val ERROR = "error"
}
