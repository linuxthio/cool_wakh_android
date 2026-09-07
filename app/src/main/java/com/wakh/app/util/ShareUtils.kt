package com.wakh.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

private const val TAG = "ShareUtils"

/** Partage un message texte via la feuille de partage système (Gmail, WhatsApp, SMS...). */
fun shareText(context: Context, text: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Log.w(TAG, "Aucune application ne peut recevoir ce partage : ${it.message}")
    }
}

/**
 * Partage un fichier (audio, image, vidéo ou document reçu/envoyé) via la
 * feuille de partage système, en utilisant la même URI `content://`
 * partageable que pour l'ouverture externe (voir shareableUriFor) — le
 * fichier ne quitte jamais le stockage local de l'app tant que
 * l'utilisateur n'a pas explicitement choisi une destination.
 */
fun shareFile(context: Context, file: File, mimeTypeFallback: String) {
    runCatching {
        val uri = shareableUriFor(context, file)
        val mimeType = guessMimeType(file, mimeTypeFallback)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Log.w(TAG, "Aucune application ne peut recevoir ce partage : ${it.message}")
    }
}
