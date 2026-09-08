package com.wakh.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wakh.app.data.local.datastore.ThemeMode
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.local.db.ContactEntity
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.data.repository.ContactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set

    /** Numéro affiché en lecture seule : c'est l'identifiant du compte, il ne peut pas être modifié ici. */
    var phoneNumber by mutableStateOf("")
        private set

    var isSaved by mutableStateOf(false)
        private set

    /** Préférence d'apparence — voir UserPreferences.themeMode (indépendante du compte). */
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    /** Liste des contacts enregistrés, pour permettre leur suppression depuis les paramètres. */
    val contacts: StateFlow<List<ContactEntity>> = contactRepository.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            userPreferences.profile.first()?.let { profile ->
                name = profile.name
                phoneNumber = profile.phoneNumber
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
        isSaved = false
    }

    fun saveName() {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            userPreferences.updateName(trimmed)
            isSaved = true
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** Retire un contact déjà enregistré (voir ContactRepository.removeContact) — l'historique local des messages n'est pas effacé. */
    fun removeContact(contact: ContactEntity) {
        viewModelScope.launch { contactRepository.removeContact(contact) }
    }
}
