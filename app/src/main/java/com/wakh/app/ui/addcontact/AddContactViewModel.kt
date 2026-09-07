package com.wakh.app.ui.addcontact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.util.isValidPhoneNumber
import com.wakh.app.util.stripPhoneNumberSeparators
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val contactRepository: ContactRepository,
) : ViewModel() {

    var phoneNumber by mutableStateOf("")
        private set

    var displayName by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onPhoneChange(value: String) {
        phoneNumber = value
        errorMessage = null
    }

    /** Pré-remplit numéro + nom depuis un contact importé du téléphone (voir util/ContactsUtils). */
    fun prefillFromDeviceContact(rawNumber: String, name: String) {
        phoneNumber = rawNumber
        displayName = name
        errorMessage = null
    }

    fun onNameChange(value: String) {
        displayName = value
    }

    fun submit(onDone: () -> Unit) {
        // Aucun indicatif ajouté/deviné : le numéro proposé est utilisé
        // tel quel, une fois les espaces/tirets de présentation retirés.
        val normalizedPhone = stripPhoneNumberSeparators(phoneNumber)
        if (!isValidPhoneNumber(normalizedPhone)) {
            errorMessage = "Numéro de téléphone invalide"
            return
        }
        viewModelScope.launch {
            val name = displayName.trim().ifBlank { normalizedPhone }
            contactRepository.addContact(normalizedPhone, name)
            onDone()
        }
    }
}
