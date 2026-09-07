package com.wakh.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.local.datastore.RememberedIdentity
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.ui.common.PIN_LENGTH
import com.wakh.app.util.canonicalSenegalesePhoneNumber
import com.wakh.app.util.hasNonSenegaleseCountryCode
import com.wakh.app.util.isValidPhoneNumber
import com.wakh.app.util.stripPhoneNumberSeparators
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Si cet appareil a déjà été utilisé pour se connecter (voir
 * [UserPreferences.rememberedIdentity]), la connexion ne demande que le
 * code PIN — nom et numéro sont réutilisés tels quels. Sinon (nouvel
 * appareil, ou après "Utiliser un autre compte"), le formulaire complet
 * (nom + numéro + PIN) est affiché.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    val rememberedIdentity: StateFlow<RememberedIdentity?> = userPreferences.rememberedIdentity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Vrai si l'utilisateur a choisi "Utiliser un autre compte" malgré une identité mémorisée. */
    var useDifferentAccount by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set
    var phoneNumber by mutableStateOf("")
        private set
    /** Code PIN à 4 chiffres — voir ui/common/PinInputField.kt. */
    var pin by mutableStateOf("")
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

    fun dismissForeignNumberWarning() { pendingForeignNumberWarning = false }

    fun confirmForeignNumberAndSubmit(onDone: () -> Unit) {
        foreignNumberConfirmedFor = stripPhoneNumberSeparators(phoneNumber)
        pendingForeignNumberWarning = false
        submit(onDone)
    }

    /** Bascule vers le formulaire complet sans effacer l'identité mémorisée tant que la connexion n'a pas réussi. */
    fun useDifferentAccount() {
        useDifferentAccount = true
        name = ""
        phoneNumber = ""
        pin = ""
        errorMessage = null
    }

    fun submit(onDone: () -> Unit) {
        val remembered = rememberedIdentity.value
        val effectiveName: String
        val effectivePhone: String

        if (remembered != null && !useDifferentAccount) {
            effectiveName = remembered.name
            effectivePhone = remembered.phoneNumber
        } else {
            effectiveName = name.trim()
            val cleanedPhone = stripPhoneNumberSeparators(phoneNumber)
            if (effectiveName.isBlank()) {
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
            // Wakh ne ciblant que le Sénégal, l'indicatif "221" est
            // retiré : même numéro canonique qu'à l'inscription, pour que
            // deux saisies du même numéro physique désignent le même compte.
            effectivePhone = canonicalSenegalesePhoneNumber(cleanedPhone)
        }

        if (pin.length != PIN_LENGTH) {
            errorMessage = "Le code PIN doit contenir $PIN_LENGTH chiffres"
            return
        }

        isLoading = true
        viewModelScope.launch {
            val result = authRepository.login(effectiveName, effectivePhone, pin)
            isLoading = false
            result.onSuccess { onDone() }
                .onFailure { errorMessage = it.message ?: "Échec de la connexion" }
        }
    }
}
