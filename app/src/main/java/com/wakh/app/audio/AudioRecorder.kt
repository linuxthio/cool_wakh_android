package com.wakh.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Enregistre un message vocal au format AAC/.m4a via [MediaRecorder],
 * dans le stockage local de l'application (jamais de cloud).
 */
class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    /** Démarre l'enregistrement et renvoie le fichier de destination. */
    fun start(): File {
        val dir = File(appContext.filesDir, "audio/sent").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")

        val newRecorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = newRecorder
        outputFile = file
        startTimeMs = System.currentTimeMillis()
        return file
    }

    /** Arrête l'enregistrement. Renvoie le fichier et sa durée en ms, ou `null` en cas d'échec. */
    fun stop(): Pair<File, Int>? {
        val file = outputFile
        val currentRecorder = recorder
        recorder = null
        outputFile = null
        if (file == null || currentRecorder == null) return null

        return try {
            currentRecorder.stop()
            currentRecorder.release()
            val durationMs = (System.currentTimeMillis() - startTimeMs).toInt()
            file to durationMs
        } catch (e: Exception) {
            Log.w(TAG, "Échec de l'arrêt de l'enregistrement : ${e.message}")
            file.delete()
            null
        }
    }

    /** Annule l'enregistrement en cours et supprime le fichier partiel. */
    fun cancel() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Échec de l'annulation propre de l'enregistrement : ${e.message}")
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
