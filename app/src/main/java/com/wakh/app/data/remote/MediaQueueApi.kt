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
data class PendingMediaDto(
    val id: String,
    val senderNumber: String,
    val groupId: String?,
    val mediaKind: String,
    val originalFileName: String?,
    val durationMs: Int,
    val sizeBytes: Long,
    val createdAt: String,
)

@Serializable
private data class PendingMediaListResponse(val pending: List<PendingMediaDtoRaw>)

@Serializable
private data class PendingMediaDtoRaw(
    val id: String,
    val sender_number: String,
    val media_kind: String,
    val duration_ms: Int,
    val size_bytes: Long,
    val created_at: String,
    val group_id: String = "",
    val original_file_name: String = "",
)

@Serializable
private data class UploadMediaResponse(val queued_message_id: String)

/**
 * Dépôt/retrait TEMPORAIRE d'une image ou d'une vidéo quand le
 * destinataire est hors ligne au moment de l'envoi — exactement le même
 * principe que [AudioQueueApi] pour l'audio. Images et vidéos partagent
 * cette même file d'attente côté serveur (voir `mediaKind`).
 *
 * Toutes les requêtes nécessitent d'être connecté (jeton envoyé en
 * en-tête `Authorization: Bearer ...`).
 */
class MediaQueueApi(
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
        mediaKind: String,
        durationMs: Int,
        file: File,
        mimeType: String,
        groupId: String? = null,
        originalFileName: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sender_number", senderNumber)
                .addFormDataPart("recipient_number", recipientNumber)
                .addFormDataPart("media_kind", mediaKind)
                .addFormDataPart("duration_ms", durationMs.toString())
                .addFormDataPart("group_id", groupId.orEmpty())
                .addFormDataPart(
                    "media",
                    originalFileName ?: file.name,
                    file.asRequestBody(mimeType.toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/mediaqueue/upload")
                .header("Authorization", authHeader())
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Échec du dépôt en file d'attente : HTTP ${response.code}")
                }
                val parsed = json.decodeFromString(
                    UploadMediaResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.queued_message_id
            }
        }
    }

    suspend fun listPending(phoneNumber: String): Result<List<PendingMediaDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/mediaqueue/pending/$phoneNumber")
                .header("Authorization", authHeader())
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de la récupération : HTTP ${response.code}")
                val parsed = json.decodeFromString(
                    PendingMediaListResponse.serializer(),
                    response.body?.string().orEmpty(),
                )
                parsed.pending.map {
                    PendingMediaDto(
                        id = it.id,
                        senderNumber = it.sender_number,
                        groupId = it.group_id.takeIf { g -> g.isNotBlank() },
                        mediaKind = it.media_kind,
                        originalFileName = it.original_file_name.takeIf { n -> n.isNotBlank() },
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
                .url("$baseUrl/mediaqueue/download/$entryId")
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
            Unit
        }
    }

    suspend fun ack(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/mediaqueue/ack/$entryId")
                .header("Authorization", authHeader())
                .post("".toRequestBody(null))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Échec de l'accusé de réception : HTTP ${response.code}")
            }
        }
    }
}
