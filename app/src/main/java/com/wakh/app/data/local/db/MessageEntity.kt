package com.wakh.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageDirection { SENT, RECEIVED }

enum class MessageKind { AUDIO, TEXT, IMAGE, VIDEO, DOCUMENT }

enum class MessageStatus {
    /** Envoi en cours (tentative P2P ou dépôt en file d'attente en cours). */
    SENDING,

    /** Destinataire hors ligne : déposé temporairement sur le serveur, en attente qu'il se reconnecte. */
    QUEUED_OFFLINE,

    /** Livré et confirmé par un accusé de réception du destinataire. */
    SENT_RECEIVED,

    /** Échec (P2P impossible ET dépôt en file d'attente impossible) — peut être réessayé. */
    FAILED,
}

/**
 * Un message, de l'un des cinq types pris en charge — audio, texte,
 * image, vidéo ou document — selon exactement le même principe d'envoi
 * pour tous (voir MessageRepository). [localFilePath] pointe vers le
 * fichier stocké localement pour l'audio/image/vidéo/document ;
 * [textContent] contient le texte pour les messages texte. [durationMs]
 * n'est pertinent que pour l'audio et la vidéo. [fileName] n'est
 * renseigné que pour un document (nom d'origine, ex. "Rapport_Q3.pdf" —
 * nécessaire car [localFilePath] utilise un nom généré). Un seul
 * sous-ensemble de champs est renseigné selon [kind].
 *
 * [contactPhoneNumber] sert en réalité d'identifiant de CONVERSATION, pas
 * uniquement de numéro de contact : pour une conversation individuelle
 * c'est un vrai numéro, pour un groupe c'est la valeur synthétique
 * "group:<id>" (voir util/ConversationId.kt) — ce qui permet de réutiliser
 * tel quel tout le regroupement/tri/lu-non-lu existant, pour les deux cas.
 *
 * [senderPhoneNumber] n'est renseigné QUE pour un message RECEIVED au sein
 * d'un groupe : il indique lequel des membres l'a effectivement envoyé
 * (en 1-à-1, [contactPhoneNumber] suffit déjà à le savoir).
 *
 * [isRead] ne concerne que les messages reçus (RECEIVED) : `false` à la
 * réception, passé à `true` dès l'ouverture de la conversation
 * correspondante (voir MessageRepository.markConversationAsRead). Les
 * messages envoyés (SENT) restent à `true` par défaut — ce suivi est
 * uniquement local, il n'existe pas d'accusé de lecture renvoyé à
 * l'expéditeur (contrairement à l'accusé de livraison "SENT_RECEIVED",
 * qui lui transite bien par le réseau).
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val contactPhoneNumber: String,
    val direction: MessageDirection,
    val kind: MessageKind = MessageKind.AUDIO,
    val localFilePath: String? = null,
    val textContent: String? = null,
    val fileName: String? = null,
    val durationMs: Int = 0,
    val sizeBytes: Long = 0,
    val timestamp: Long,
    val status: MessageStatus,
    val isRead: Boolean = true,
    val senderPhoneNumber: String? = null,
    /** Identifiant côté serveur si le message a transité par la file d'attente hors-ligne. */
    val queuedRemoteId: String? = null,
)
