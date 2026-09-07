package com.wakh.app.util

/**
 * Un identifiant de conversation est soit un vrai numéro de téléphone
 * (conversation individuelle), soit une valeur synthétique "group:<id>"
 * (conversation de groupe). Cette convention permet de réutiliser tel
 * quel tout le stockage/regroupement/tri existant (Room, MessageDao) qui
 * opère sur `MessageEntity.contactPhoneNumber`, sans distinguer les deux
 * cas au niveau du schéma.
 */
object ConversationId {
    private const val GROUP_PREFIX = "group:"

    fun forGroup(groupId: String): String = "$GROUP_PREFIX$groupId"

    /** `null` si [conversationId] ne désigne pas une conversation de groupe. */
    fun groupIdOrNull(conversationId: String): String? =
        conversationId.takeIf { it.startsWith(GROUP_PREFIX) }?.removePrefix(GROUP_PREFIX)

    fun isGroup(conversationId: String): Boolean = conversationId.startsWith(GROUP_PREFIX)
}
