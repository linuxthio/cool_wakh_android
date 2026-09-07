package com.wakh.app.ui.creategroup

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreateGroupViewModel(
    contactRepository: ContactRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    val contacts: StateFlow<List<ContactEntity>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var groupName by mutableStateOf("")
        private set

    var selectedPhoneNumbers by mutableStateOf<Set<String>>(emptySet())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isCreating by mutableStateOf(false)
        private set

    fun onNameChange(value: String) {
        groupName = value
        errorMessage = null
    }

    fun toggleMember(phoneNumber: String) {
        selectedPhoneNumbers = if (phoneNumber in selectedPhoneNumbers) {
            selectedPhoneNumbers - phoneNumber
        } else {
            selectedPhoneNumbers + phoneNumber
        }
        errorMessage = null
    }

    fun submit(onCreated: (String) -> Unit) {
        val trimmedName = groupName.trim()
        if (trimmedName.isBlank()) {
            errorMessage = "Entrez un nom de groupe"
            return
        }
        if (selectedPhoneNumbers.isEmpty()) {
            errorMessage = "Choisissez au moins un contact"
            return
        }

        isCreating = true
        viewModelScope.launch {
            val groupId = groupRepository.createGroup(trimmedName, selectedPhoneNumbers.toList())
            isCreating = false
            onCreated(groupId)
        }
    }
}
