package com.wakh.app.ui.groupinfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.local.db.ContactEntity
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Membre affiché : nom résolu depuis les contacts, ou le numéro tel quel si le contact a depuis été retiré (voir Settings). */
data class GroupMemberUi(val phoneNumber: String, val displayName: String)

/**
 * Permet de renommer un groupe existant et d'en ajouter/retirer des
 * membres — purement local (Room), comme le reste du concept de groupe
 * (voir GroupRepository). ChatViewModel observe les mêmes données en
 * continu : les changements faits ici sont donc immédiatement visibles
 * en revenant à la conversation.
 */
class GroupInfoViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    contactRepository: ContactRepository,
) : ViewModel() {

    var groupName by mutableStateOf("")
        private set

    var isNameSaved by mutableStateOf(false)
        private set

    private val allContacts: StateFlow<List<ContactEntity>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val members: StateFlow<List<GroupMemberUi>> = combine(
        groupRepository.observeMembers(groupId),
        allContacts,
    ) { members, contacts ->
        val byPhoneNumber = contacts.associateBy { it.phoneNumber }
        members
            .map { member ->
                GroupMemberUi(
                    phoneNumber = member.phoneNumber,
                    displayName = byPhoneNumber[member.phoneNumber]?.displayName ?: member.phoneNumber,
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Contacts pas encore membres du groupe — proposés dans "Ajouter des membres". */
    val availableContactsToAdd: StateFlow<List<ContactEntity>> = combine(
        allContacts,
        groupRepository.observeMembers(groupId),
    ) { contacts, members ->
        val memberPhoneNumbers = members.map { it.phoneNumber }.toSet()
        contacts.filter { it.phoneNumber !in memberPhoneNumbers }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var showAddMembersSheet by mutableStateOf(false)
        private set

    var selectedToAdd by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Membre dont le retrait est en attente de confirmation par l'écran. */
    var memberPendingRemoval by mutableStateOf<GroupMemberUi?>(null)
        private set

    init {
        viewModelScope.launch {
            groupRepository.observeGroup(groupId).first()?.let { groupName = it.name }
        }
    }

    fun onNameChange(value: String) {
        groupName = value
        isNameSaved = false
    }

    fun saveName() {
        val trimmed = groupName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            groupRepository.renameGroup(groupId, trimmed)
            isNameSaved = true
        }
    }

    fun requestRemoveMember(member: GroupMemberUi) {
        memberPendingRemoval = member
    }

    fun dismissRemoveMember() {
        memberPendingRemoval = null
    }

    fun confirmRemoveMember() {
        val member = memberPendingRemoval ?: return
        memberPendingRemoval = null
        viewModelScope.launch { groupRepository.removeMember(groupId, member.phoneNumber) }
    }

    fun openAddMembers() {
        selectedToAdd = emptySet()
        showAddMembersSheet = true
    }

    fun dismissAddMembers() {
        showAddMembersSheet = false
        selectedToAdd = emptySet()
    }

    fun toggleContactToAdd(phoneNumber: String) {
        selectedToAdd = if (phoneNumber in selectedToAdd) {
            selectedToAdd - phoneNumber
        } else {
            selectedToAdd + phoneNumber
        }
    }

    fun confirmAddMembers() {
        if (selectedToAdd.isEmpty()) {
            showAddMembersSheet = false
            return
        }
        val toAdd = selectedToAdd.toList()
        viewModelScope.launch {
            groupRepository.addMembers(groupId, toAdd)
            showAddMembersSheet = false
            selectedToAdd = emptySet()
        }
    }
}
