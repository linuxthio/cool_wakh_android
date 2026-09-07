package com.wakh.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wakh_user_prefs")

/**
 * Identité locale de l'utilisateur, valide seulement après création de
 * compte (POST /auth/register) ou connexion (POST /auth/login) réussie —
 * un compte est obligatoire pour utiliser le service. Le numéro sert
 * d'identifiant unique pour être contacté par d'autres utilisateurs, et
 * est saisi manuellement (complet, avec son indicatif) à l'inscription.
 *
 * [authToken] est nécessaire pour toute utilisation du service (WebSocket
 * de signalisation et routes REST protégées) — voir SignalingClient,
 * AudioQueueApi, TextQueueApi. [name] reste stocké UNIQUEMENT en local
 * (le serveur ne connaît jamais le nom, seulement le numéro).
 */
data class UserProfile(
    val name: String,
    val phoneNumber: String,
    val authToken: String,
)

/** Nom et numéro mémorisés sur cet appareil — voir UserPreferences.rememberedIdentity. */
data class RememberedIdentity(
    val name: String,
    val phoneNumber: String,
)

/** Préférence d'apparence — voir UserPreferences.themeMode et ui/theme/Theme.kt. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

class UserPreferences(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val NAME = stringPreferencesKey("user_name")
        val PHONE = stringPreferencesKey("user_phone")
        val TOKEN = stringPreferencesKey("auth_token")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /** `null` tant qu'aucun compte n'est créé/connecté sur cet appareil (ou après déconnexion). */
    val profile: Flow<UserProfile?> = appContext.dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME]
        val phone = prefs[Keys.PHONE]
        val token = prefs[Keys.TOKEN]
        if (name.isNullOrBlank() || phone.isNullOrBlank() || token.isNullOrBlank()) {
            null
        } else {
            UserProfile(name = name, phoneNumber = phone, authToken = token)
        }
    }

    /**
     * Nom et numéro de la dernière session réussie sur cet appareil,
     * disponibles MÊME APRÈS déconnexion (contrairement à [profile], qui
     * lui redevient `null`) — permet à l'écran de connexion de ne
     * redemander que le code PIN plutôt que de tout ressaisir. `null` si
     * aucun compte n'a jamais été utilisé sur cet appareil, ou après
     * [forgetRememberedIdentity].
     */
    val rememberedIdentity: Flow<RememberedIdentity?> = appContext.dataStore.data.map { prefs ->
        val name = prefs[Keys.NAME]
        val phone = prefs[Keys.PHONE]
        if (name.isNullOrBlank() || phone.isNullOrBlank()) null else RememberedIdentity(name, phone)
    }

    /**
     * Préférence d'apparence — indépendante du compte (lisible même sur
     * les écrans d'accueil/inscription/connexion, avant toute
     * authentification). `SYSTEM` par défaut (suit le réglage du
     * téléphone) tant que l'utilisateur n'a rien choisi explicitement.
     */
    val themeMode: Flow<ThemeMode> = appContext.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    /** Appelé après une création de compte ou une connexion réussie. */
    suspend fun saveAccount(name: String, phoneNumber: String, authToken: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.PHONE] = phoneNumber
            prefs[Keys.TOKEN] = authToken
        }
    }

    /**
     * Déconnexion : invalide le jeton local (ramène au besoin de se
     * reconnecter), mais garde nom et numéro mémorisés sur cet appareil
     * — voir [rememberedIdentity] — pour qu'une reconnexion ultérieure du
     * même utilisateur ne demande que son code PIN. Le thème n'est de
     * toute façon jamais un réglage de compte, il n'est donc pas non plus
     * concerné par cette déconnexion.
     */
    suspend fun clear() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
        }
    }

    /**
     * "Utiliser un autre compte" depuis l'écran de connexion : oublie
     * complètement l'identité mémorisée sur cet appareil, pour repartir
     * sur un formulaire vierge (nom + numéro + PIN).
     */
    suspend fun forgetRememberedIdentity() {
        appContext.dataStore.edit { prefs ->
            prefs.remove(Keys.NAME)
            prefs.remove(Keys.PHONE)
            prefs.remove(Keys.TOKEN)
        }
    }

    /** Modifie uniquement le nom affiché (écran Paramètres) — reste purement local, jamais envoyé au serveur. */
    suspend fun updateName(name: String) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
        }
    }
}
