package com.wakh.app.data.remote

import com.wakh.app.BuildConfig
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.util.sanitizeSignalingHost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class PendingAudioDto(
    val id: String,
    val senderNumber: String,
    val groupId: String?,
    val durationMs: Int,
    val sizeBytes: Long,
    val createdAt: String,
)

@Serializable
private data class PendingListResponse(val pending: List<PendingAudioDtoRaw>)

@Serializable
private data class PendingAudioDtoRaw(
    val id: String,
    val sender_number: String,
    val duration_ms: Int,
    val size_bytes: Long,
    val created_at: String,
    val group_id: String = "",
)

@Serializable
private data class UploadResponse(val queued_message_id: String)

/**
 * Dépôt/retrait TEMPORAIRE d'un message vocal quand le destinataire est hors
 * ligne au moment de l'envoi (mode hybride). Le fichier est supprimé côté
 * serveur dès l'accusé de réception (voir [ack]) — ce n'est pas un
 * historique de conversation.
 *
 * Toutes les requêtes nécessitent d'être connecté (voir [UserPreferences],
 * jeton envoyé en en-tête `Authorization: Bearer ...`).
 */
class AudioQueueApi(
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
        durationMs: Int,
        file: File,
        groupId: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sender_number", senderNumber)
                .addFormDataPart("recipient_number", recipientNumber)
                .addFormDataPart("duration_ms", durationMs.toString())
                .addFormDataPart("group_id", groupId.orEmpty())
                .addFormDataPart(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/audioqueue/upload")
                .header("Authorization", authHeader())
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Échec du dépôt en file d'attente : HTTP ${response.code}")
                }
                val parsed = json.decodeFromString(
                    UploadResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.queued_message_id
            }
        }
    }

    suspend fun listPending(phoneNumber: String): Result<List<PendingAudioDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/audioqueue/pending/$phoneNumber")
                .header("Authorization", authHeader())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de la récupération : HTTP ${response.code}")
                val parsed = json.decodeFromString(
                    PendingListResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.pending.map {
                    PendingAudioDto(
                        id = it.id,
                        senderNumber = it.sender_number,
                        groupId = it.group_id.takeIf { g -> g.isNotBlank() },
                        durationMs = it.duration_ms,
                        sizeBytes = it.size_bytes,
                        createdAt = it.created_at,
                    )
                }
            }
        }
    }

    suspend fun download(entryId: String, destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/audioqueue/download/$entryId")
                .header("Authorization", authHeader())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec du téléchargement : HTTP ${response.code}")
                val body = response.body ?: error("Réponse vide")
                destination.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
            }
        }
    }

    suspend fun ack(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/audioqueue/ack/$entryId")
                .header("Authorization", authHeader())
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de l'accusé de réception : HTTP ${response.code}")
            }
        }
    }
}
