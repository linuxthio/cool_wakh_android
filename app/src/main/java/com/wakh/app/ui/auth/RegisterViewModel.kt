package com.wakh.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.ui.common.PIN_LENGTH
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
     * "+221771234567") ou sans (ex. "771234567"). Jamais modifié au-delà
     * du retrait des espaces/tirets de présentation, voir [submit].
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

    fun onNameChange(value: String) { name = value; errorMessage = null }
    fun onPhoneChange(value: String) { phoneNumber = value; errorMessage = null }
    fun onPinChange(value: String) { pin = value; errorMessage = null }
    fun onConfirmPinChange(value: String) { confirmPin = value; errorMessage = null }

    fun submit(onDone: () -> Unit) {
        val trimmedName = name.trim()
        // Aucun indicatif ajouté/deviné : le numéro proposé par
        // l'utilisateur est utilisé tel quel, une fois les espaces/tirets
        // de présentation retirés.
        val normalizedPhone = stripPhoneNumberSeparators(phoneNumber)

        if (trimmedName.isBlank()) {
            errorMessage = "Entrez votre nom"
            return
        }
        if (!isValidPhoneNumber(normalizedPhone)) {
            errorMessage = "Numéro de téléphone invalide (ex. +221771234567 ou 771234567)"
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

        isLoading = true
        viewModelScope.launch {
            val result = authRepository.register(trimmedName, normalizedPhone, pin)
            isLoading = false
            result.onSuccess { onDone() }
                .onFailure { errorMessage = it.message ?: "Échec de la création du compte" }
        }
    }
}
