package com.wakh.app.data.remote

import com.wakh.app.BuildConfig
import com.wakh.app.util.sanitizeSignalingHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class AuthResponse(
    val phone_number: String,
    val token: String,
)

/**
 * Création de compte et connexion — obligatoires pour utiliser le
 * service. Un compte Wakh, c'est un numéro de téléphone et un code PIN à
 * 4 chiffres ; le jeton renvoyé sert ensuite à s'authentifier auprès du
 * WebSocket de signalisation et des routes REST protégées.
 */
class AuthApi(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl: String
        get() {
            val scheme = if (BuildConfig.SIGNALING_USE_TLS) "https" else "http"
            return "$scheme://${sanitizeSignalingHost(BuildConfig.SIGNALING_HOST)}"
        }

    suspend fun register(phoneNumber: String, pin: String): Result<AuthResponse> =
        callAuthEndpoint("register", phoneNumber, pin)

    suspend fun login(phoneNumber: String, pin: String): Result<AuthResponse> =
        callAuthEndpoint("login", phoneNumber, pin)

    private suspend fun callAuthEndpoint(
        path: String,
        phoneNumber: String,
        pin: String,
    ): Result<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("phone_number", phoneNumber)
                .add("pin", pin)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/auth/$path")
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.decodeFromString(ErrorResponse.serializer(), rawBody).detail
                    }.getOrNull() ?: "Échec de la requête (HTTP ${response.code})"
                    error(message)
                }
                json.decodeFromString(AuthResponse.serializer(), rawBody)
            }
        }
    }
}

@Serializable
private data class ErrorResponse(val detail: String)
