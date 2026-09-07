package com.wakh.app.data.remote

import com.wakh.app.BuildConfig
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.util.sanitizeSignalingHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class PendingTextDto(
    val id: String,
    val senderNumber: String,
    val groupId: String?,
    val content: String,
    val createdAt: String,
)

@Serializable
private data class PendingTextListResponse(val pending: List<PendingTextDtoRaw>)

@Serializable
private data class PendingTextDtoRaw(
    val id: String,
    val sender_number: String,
    val content: String,
    val created_at: String,
    val group_id: String = "",
)

@Serializable
private data class UploadTextResponse(val queued_message_id: String)

/**
 * Dépôt/retrait TEMPORAIRE d'un message texte quand le destinataire est
 * hors ligne au moment de l'envoi — exactement le même principe que
 * [AudioQueueApi] pour l'audio : le message est supprimé côté serveur dès
 * l'accusé de réception (voir [ack]). Plus simple que l'audio car le
 * contenu tient directement dans la réponse de [listPending] : pas
 * d'étape de téléchargement séparée nécessaire.
 *
 * Toutes les requêtes nécessitent d'être connecté (jeton envoyé en
 * en-tête `Authorization: Bearer ...`).
 */
class TextQueueApi(
    private val httpClient: OkHttpClient,
    private val userPreferences: UserPreferences,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl: String
        get() {
            val scheme = if (BuildConfig.SIGNALING_USE_TLS) "https" else "http"
            return "$scheme://${sanitizeSignalingHost(BuildConfig.SIGNALING_HOST)}"
        }

    private suspend fun authHeader(): String = "Bearer ${userPreferences.profile.first()?.authToken.orEmpty()}"

    suspend fun uploadQueued(
        senderNumber: String,
        recipientNumber: String,
        content: String,
        groupId: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("sender_number", senderNumber)
                .add("recipient_number", recipientNumber)
                .add("content", content)
                .add("group_id", groupId.orEmpty())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/textqueue/upload")
                .header("Authorization", authHeader())
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Échec du dépôt en file d'attente : HTTP ${response.code}")
                }
                val parsed = json.decodeFromString(
                    UploadTextResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.queued_message_id
            }
        }
    }

    suspend fun listPending(phoneNumber: String): Result<List<PendingTextDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/textqueue/pending/$phoneNumber")
                .header("Authorization", authHeader())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de la récupération : HTTP ${response.code}")
                val parsed = json.decodeFromString(
                    PendingTextListResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.pending.map {
                    PendingTextDto(
                        id = it.id,
                        senderNumber = it.sender_number,
                        groupId = it.group_id.takeIf { g -> g.isNotBlank() },
                        content = it.content,
                        createdAt = it.created_at,
                    )
                }
            }
        }
    }

    suspend fun ack(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/textqueue/ack/$entryId")
                .header("Authorization", authHeader())
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de l'accusé de réception : HTTP ${response.code}")
            }
        }
    }
}
