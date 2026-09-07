package com.wakh.app.ui.addcontact

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.util.canonicalSenegalesePhoneNumber
import com.wakh.app.util.hasNonSenegaleseCountryCode
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

    /** Vrai si le numéro saisi porte un indicatif explicite non sénégalais : demande confirmation avant de continuer. */
    var pendingForeignNumberWarning by mutableStateOf(false)
        private set

    /** Dernier numéro nettoyé pour lequel l'utilisateur a confirmé vouloir continuer malgré l'indicatif non sénégalais. */
    private var foreignNumberConfirmedFor: String? = null

    fun onPhoneChange(value: String) {
        phoneNumber = value
        errorMessage = null
        pendingForeignNumberWarning = false
    }

    /** Pré-remplit numéro + nom depuis un contact importé du téléphone (voir util/ContactsUtils). */
    fun prefillFromDeviceContact(rawNumber: String, name: String) {
        phoneNumber = rawNumber
        displayName = name
        errorMessage = null
        pendingForeignNumberWarning = false
    }

    fun onNameChange(value: String) {
        displayName = value
    }

    fun dismissForeignNumberWarning() { pendingForeignNumberWarning = false }

    fun confirmForeignNumberAndSubmit(onDone: () -> Unit) {
        foreignNumberConfirmedFor = stripPhoneNumberSeparators(phoneNumber)
        pendingForeignNumberWarning = false
        submit(onDone)
    }

    fun submit(onDone: () -> Unit) {
        val cleanedPhone = stripPhoneNumberSeparators(phoneNumber)
        if (!isValidPhoneNumber(cleanedPhone)) {
            errorMessage = "Numéro de téléphone invalide"
            return
        }
        if (hasNonSenegaleseCountryCode(cleanedPhone) && foreignNumberConfirmedFor != cleanedPhone) {
            pendingForeignNumberWarning = true
            return
        }
        // Wakh ne ciblant que le Sénégal, l'indicatif "221" est retiré :
        // même numéro canonique qu'à l'inscription/connexion, pour que ce
        // contact corresponde bien au compte du même numéro physique.
        val normalizedPhone = canonicalSenegalesePhoneNumber(cleanedPhone)
        viewModelScope.launch {
            val name = displayName.trim().ifBlank { normalizedPhone }
            contactRepository.addContact(normalizedPhone, name)
            onDone()
        }
    }
}
