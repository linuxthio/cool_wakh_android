package com.wakh.app.ui.common

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

/**
 * Coil ne charge pas automatiquement le décodeur de vignettes vidéo
 * (module coil-video) sur l'ImageLoader par défaut : il faut l'enregistrer
 * explicitement. Utilisé pour afficher une vignette d'aperçu des messages
 * vidéo dans les bulles de discussion, à partir du fichier local.
 */
object WakhImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context.applicationContext)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
            .also { instance = it }
    }
}
