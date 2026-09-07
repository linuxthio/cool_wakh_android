package com.wakh.app.data.repository

import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.remote.AuthApi

class AuthRepository(
    private val authApi: AuthApi,
    private val userPreferences: UserPreferences,
) {
    /** Crée le compte (numéro détecté automatiquement, code PIN à 4 chiffres) puis sauvegarde l'identité locale. */
    suspend fun register(name: String, phoneNumber: String, pin: String): Result<Unit> =
        authApi.register(phoneNumber, pin).map { response ->
            userPreferences.saveAccount(name, response.phone_number, response.token)
        }

    /** Connecte le compte existant puis sauvegarde l'identité locale. */
    suspend fun login(name: String, phoneNumber: String, pin: String): Result<Unit> =
        authApi.login(phoneNumber, pin).map { response ->
            userPreferences.saveAccount(name, response.phone_number, response.token)
        }

    suspend fun logout() {
        userPreferences.clear()
    }
}
