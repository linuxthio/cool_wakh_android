package com.wakh.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.ui.common.PIN_LENGTH
import com.wakh.app.util.canonicalSenegalesePhoneNumber
import com.wakh.app.util.hasNonSenegaleseCountryCode
import com.wakh.app.util.isValidPhoneNumber
import com.wakh.app.util.stripPhoneNumberSeparators
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    /**
     * Numéro saisi manuellement, tel quel — avec indicatif (ex.
     * "+221771234567") ou sans (ex. "771234567"). Wakh ne ciblant que le
     * Sénégal, l'indicatif "221" est retiré avant utilisation comme
     * identifiant réel (voir [canonicalSenegalesePhoneNumber] dans
     * [submit]) : les deux formes désignent le même compte.
     */
    var phoneNumber by mutableStateOf("")
        private set
    /** Code PIN à 4 chiffres — voir ui/common/PinInputField.kt. */
    var pin by mutableStateOf("")
        private set
    var confirmPin by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** Vrai si le numéro saisi porte un indicatif explicite non sénégalais : demande confirmation avant de continuer. */
    var pendingForeignNumberWarning by mutableStateOf(false)
        private set

    /** Dernier numéro nettoyé pour lequel l'utilisateur a confirmé vouloir continuer malgré l'indicatif non sénégalais. */
    private var foreignNumberConfirmedFor: String? = null

    fun onNameChange(value: String) { name = value; errorMessage = null }
    fun onPhoneChange(value: String) { phoneNumber = value; errorMessage = null; pendingForeignNumberWarning = false }
    fun onPinChange(value: String) { pin = value; errorMessage = null }
    fun onConfirmPinChange(value: String) { confirmPin = value; errorMessage = null }

    fun dismissForeignNumberWarning() { pendingForeignNumberWarning = false }

    fun confirmForeignNumberAndSubmit(onDone: () -> Unit) {
        foreignNumberConfirmedFor = stripPhoneNumberSeparators(phoneNumber)
        pendingForeignNumberWarning = false
        submit(onDone)
    }

    fun submit(onDone: () -> Unit) {
        val trimmedName = name.trim()
        val cleanedPhone = stripPhoneNumberSeparators(phoneNumber)

        if (trimmedName.isBlank()) {
            errorMessage = "Entrez votre nom"
            return
        }
        if (!isValidPhoneNumber(cleanedPhone)) {
            errorMessage = "Numéro de téléphone invalide (ex. +221771234567 ou 771234567)"
            return
        }
        if (hasNonSenegaleseCountryCode(cleanedPhone) && foreignNumberConfirmedFor != cleanedPhone) {
            pendingForeignNumberWarning = true
            return
        }
        if (pin.length != PIN_LENGTH) {
            errorMessage = "Le code PIN doit contenir $PIN_LENGTH chiffres"
            return
        }
        if (pin != confirmPin) {
            errorMessage = "Les codes PIN ne correspondent pas"
            return
        }

        // Wakh ne ciblant que le Sénégal, l'indicatif "221" (avec ou sans
        // "+") est retiré : c'est ce numéro canonique qui sert d'identité
        // réelle, pour que deux saisies du même numéro physique désignent
        // toujours le même compte.
        val normalizedPhone = canonicalSenegalesePhoneNumber(cleanedPhone)

        isLoading = true
        viewModelScope.launch {
            val result = authRepository.register(trimmedName, normalizedPhone, pin)
            isLoading = false
            result.onSuccess { onDone() }
                .onFailure { errorMessage = it.message ?: "Échec de la création du compte" }
        }
    }
}
