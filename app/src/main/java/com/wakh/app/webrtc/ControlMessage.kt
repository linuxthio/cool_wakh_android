package com.wakh.app.webrtc

import kotlinx.serialization.Serializable

/**
 * Petits messages texte de contrôle envoyés sur le DataChannel WebRTC,
 * autour des blocs binaires du fichier transféré (audio, image ou vidéo) :
 *
 *   "meta"  -> annonce le début d'un transfert de fichier (taille, nombre
 *              de blocs, [mediaKind] pour que le destinataire sache s'il
 *              s'agit d'un audio/image/vidéo et le stocke au bon endroit...)
 *   "end"   -> tous les blocs binaires du fichier ont été envoyés
 *   "text"  -> un message texte complet, envoyé en un seul message (pas de
 *              découpage nécessaire vu la taille), avec son contenu inclus
 *              directement dans [content]
 *   "ack"   -> le destinataire confirme avoir enregistré le message en local
 *              (fichier ou texte)
 *   "delete"-> demande au destinataire de supprimer localement le message
 *              [messageId] déjà reçu précédemment ("suppression pour tout
 *              le monde" — voir MessageRepository.deleteForEveryone)
 *
 * Le DataChannel WebRTC est ordonné et fiable par défaut (comme un flux
 * TCP) : les blocs binaires arrivent dans l'ordre d'envoi, donc le
 * destinataire peut simplement les concaténer sans avoir besoin d'indices.
 */
@Serializable
data class ControlMessage(
    val type: String,
    val messageId: String,
    val fileName: String? = null,
    val totalBytes: Long? = null,
    val chunkSize: Int? = null,
    val totalChunks: Int? = null,
    val durationMs: Int? = null,
    val content: String? = null,
    /** "AUDIO" / "IMAGE" / "VIDEO" — accompagne un "meta" de transfert de fichier. */
    val mediaKind: String? = null,
    /** Renseigné si ce message (fichier ou texte) est envoyé dans le cadre d'un groupe — voir util/ConversationId.kt. */
    val groupId: String? = null,
)

object ControlType {
    const val META = "meta"
    const val END = "end"
    const val TEXT = "text"
    const val ACK = "ack"
    const val DELETE = "delete"
}
