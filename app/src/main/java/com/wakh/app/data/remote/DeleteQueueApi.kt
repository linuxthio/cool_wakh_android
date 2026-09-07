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
data class PendingDeleteDto(
    val id: String,
    val senderNumber: String,
    val groupId: String?,
    val targetMessageId: String,
    val createdAt: String,
)

@Serializable
private data class PendingDeleteListResponse(val pending: List<PendingDeleteDtoRaw>)

@Serializable
private data class PendingDeleteDtoRaw(
    val id: String,
    val sender_number: String,
    val target_message_id: String,
    val created_at: String,
    val group_id: String = "",
)

@Serializable
private data class UploadDeleteResponse(val queued_message_id: String)

/**
 * Dépôt/retrait TEMPORAIRE d'une demande de "suppression pour tout le
 * monde" quand le destinataire est hors ligne au moment de la demande —
 * exactement le même principe que [TextQueueApi], en plus simple : aucun
 * contenu, juste un identifiant de message à supprimer chez le
 * destinataire.
 */
class DeleteQueueApi(
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
        targetMessageId: String,
        groupId: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("sender_number", senderNumber)
                .add("recipient_number", recipientNumber)
                .add("target_message_id", targetMessageId)
                .add("group_id", groupId.orEmpty())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/deletequeue/upload")
                .header("Authorization", authHeader())
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Échec du dépôt en file d'attente : HTTP ${response.code}")
                }
                val parsed = json.decodeFromString(
                    UploadDeleteResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.queued_message_id
            }
        }
    }

    suspend fun listPending(phoneNumber: String): Result<List<PendingDeleteDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/deletequeue/pending/$phoneNumber")
                .header("Authorization", authHeader())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de la récupération : HTTP ${response.code}")
                val parsed = json.decodeFromString(
                    PendingDeleteListResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.pending.map {
                    PendingDeleteDto(
                        id = it.id,
                        senderNumber = it.sender_number,
                        groupId = it.group_id.takeIf { g -> g.isNotBlank() },
                        targetMessageId = it.target_message_id,
                        createdAt = it.created_at,
                    )
                }
            }
        }
    }

    suspend fun ack(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/deletequeue/ack/$entryId")
                .header("Authorization", authHeader())
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de l'accusé de réception : HTTP ${response.code}")
            }
        }
    }
}
