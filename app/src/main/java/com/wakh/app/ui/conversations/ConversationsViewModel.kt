package com.wakh.app.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.local.db.ContactEntity
import com.wakh.app.data.local.db.GroupEntity
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.data.repository.MessageRepository
import com.wakh.app.util.ConversationId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Une conversation est soit individuelle (un contact), soit de groupe. */
sealed class ConversationTarget {
    data class Direct(val contact: ContactEntity) : ConversationTarget()
    data class Group(val group: GroupEntity, val memberCount: Int) : ConversationTarget()
}

data class ConversationUi(
    val conversationId: String,
    val displayName: String,
    val target: ConversationTarget,
    val lastMessage: MessageEntity?,
    val unreadCount: Int = 0,
    /** Nom résolu de l'expéditeur du dernier message, uniquement pertinent pour un groupe. */
    val lastMessageSenderName: String? = null,
)

class ConversationsViewModel(
    contactRepository: ContactRepository,
    messageRepository: MessageRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    val conversations: StateFlow<List<ConversationUi>> = combine(
        contactRepository.observeContacts(),
        groupRepository.observeGroups(),
        groupRepository.observeAllMemberCounts(),
        messageRepository.observeLastMessagePerContact(),
        messageRepository.observeUnreadCounts(),
    ) { contacts, groups, memberCounts, lastMessages, unreadCounts ->
        val lastByConversation = lastMessages.associateBy { it.contactPhoneNumber }
        val unreadByConversation = unreadCounts.associate { it.contactPhoneNumber to it.unreadCount }
        val memberCountByGroup = memberCounts.associate { it.groupId to it.memberCount }
        val nameByPhoneNumber = contacts.associate { it.phoneNumber to it.displayName }

        val direct = contacts.map { contact ->
            ConversationUi(
                conversationId = contact.phoneNumber,
                displayName = contact.displayName,
                target = ConversationTarget.Direct(contact),
                lastMessage = lastByConversation[contact.phoneNumber],
                unreadCount = unreadByConversation[contact.phoneNumber] ?: 0,
            )
        }

        val groupConversations = groups.map { group ->
            val conversationId = ConversationId.forGroup(group.id)
            val lastMessage = lastByConversation[conversationId]
            ConversationUi(
                conversationId = conversationId,
                displayName = group.name,
                target = ConversationTarget.Group(group, memberCountByGroup[group.id] ?: 0),
                lastMessage = lastMessage,
                unreadCount = unreadByConversation[conversationId] ?: 0,
                lastMessageSenderName = lastMessage?.senderPhoneNumber?.let { nameByPhoneNumber[it] ?: it },
            )
        }

        (direct + groupConversations).sortedByDescending {
            it.lastMessage?.timestamp ?: when (val target = it.target) {
                is ConversationTarget.Direct -> target.contact.addedAt
                is ConversationTarget.Group -> target.group.createdAt
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
