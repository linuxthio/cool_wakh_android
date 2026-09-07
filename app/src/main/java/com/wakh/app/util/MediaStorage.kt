package com.wakh.app.util

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copie le contenu pointé par [uri] (typiquement renvoyé par le
 * sélecteur photo/vidéo du système) vers le stockage privé de
 * l'application, dans [subdir]. Nécessaire pour respecter le principe
 * "stockage local uniquement" : on ne garde pas de dépendance vers un
 * Uri externe potentiellement révoqué, on en fait une copie locale au
 * même titre qu'un enregistrement audio.
 */
suspend fun copyUriToAppStorage(context: Context, uri: Uri, subdir: String, baseName: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val extension = mimeType
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?.let { ".$it" }
                ?: ""

            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val destFile = File(dir, "$baseName$extension")

            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null

            destFile
        }.getOrNull()
    }

/** Devine un type MIME à partir de l'extension du fichier, pour l'upload en file d'attente. */
fun guessMimeType(file: File, fallback: String): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: fallback
}

/**
 * URI `content://` partageable pour [file], via le FileProvider déclaré
 * dans le manifest (voir res/xml/file_paths.xml) — nécessaire pour ouvrir
 * une image/vidéo reçue avec une application externe (visionneuse,
 * lecteur vidéo) sans exposer directement un chemin file://.
 */
fun shareableUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * Ouvre [file] avec une application installée sur le téléphone, via le
 * sélecteur système "Ouvrir avec..." (`Intent.createChooser`) plutôt
 * qu'une résolution implicite — l'utilisateur choisit explicitement
 * l'application, même s'il n'en a qu'une seule d'installée pour ce type
 * de fichier. Renvoie `false` si aucune application installée ne peut
 * l'ouvrir, ou si l'URI partageable n'a pas pu être générée (à charge de
 * l'appelant d'en informer l'utilisateur, voir ChatScreen : le fichier
 * reste toujours accessible via "Partager").
 */
fun openMediaExternally(context: Context, file: File, mimeTypeFallback: String): Boolean {
    return runCatching {
        val uri = shareableUriFor(context, file)
        val mimeType = guessMimeType(file, mimeTypeFallback)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (viewIntent.resolveActivity(context.packageManager) == null) {
            Log.w("MediaStorage", "Aucune application installée ne peut ouvrir ce type de fichier ($mimeType)")
            return false
        }

        val chooser = Intent.createChooser(viewIntent, "Ouvrir avec").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    }.onFailure {
        Log.w("MediaStorage", "Échec de l'ouverture externe : ${it.message}")
    }.getOrDefault(false)
}

/** Durée d'une vidéo locale en millisecondes, ou 0 si elle n'a pas pu être déterminée. */
suspend fun videoDurationMs(file: File): Int = withContext(Dispatchers.IO) {
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
        }
    }.getOrDefault(0)
}

/** [MediaMetadataRetriever] n'implémente [AutoCloseable] qu'à partir de l'API 29 ; on le gère nous-mêmes en dessous. */
private inline fun <R> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> R): R {
    try {
        return block(this)
    } finally {
        release()
    }
}

/** Résultat de la copie locale d'un document choisi via le sélecteur système. */
data class DocumentInfo(
    val file: File,
    val originalFileName: String,
    val sizeBytes: Long,
)

/**
 * Copie le document pointé par [uri] (sélecteur système, voir
 * ActivityResultContracts.OpenDocument) vers le stockage privé de
 * l'application, en préservant son nom d'origine pour l'affichage —
 * contrairement à [copyUriToAppStorage] qui génère un nom générique,
 * adapté aux images/vidéos mais pas aux documents (l'utilisateur a besoin
 * de reconnaître "Rapport_Q3.pdf").
 */
suspend fun copyDocumentToAppStorage(context: Context, uri: Uri, subdir: String): DocumentInfo? =
    withContext(Dispatchers.IO) {
        runCatching {
            val (displayName, resolvedSize) = queryDisplayNameAndSize(context, uri)
            val safeName = sanitizeFileName(displayName ?: "document_${System.currentTimeMillis()}")

            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            // Préfixe unique en plus du nom d'origine, pour éviter toute
            // collision entre plusieurs documents portant le même nom.
            val destFile = File(dir, "${System.currentTimeMillis()}_$safeName")

            var bytesCopied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> bytesCopied = input.copyTo(output) }
            } ?: return@runCatching null

            DocumentInfo(
                file = destFile,
                originalFileName = displayName ?: destFile.name,
                sizeBytes = resolvedSize ?: bytesCopied,
            )
        }.getOrNull()
    }

/** Nom affiché et taille d'un fichier pointé par [uri], via les colonnes standard [OpenableColumns]. */
private fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String?, Long?> {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null to null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        return name to size
    }
    return null to null
}

private fun sanitizeFileName(name: String): String = name.replace(Regex("[/\\\\]"), "_")

/** Formate une taille en octets pour l'affichage (ex. "2,4 Mo"). */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f Ko".format(kb)
    val mb = kb / 1024.0
    return "%.1f Mo".format(mb)
}
