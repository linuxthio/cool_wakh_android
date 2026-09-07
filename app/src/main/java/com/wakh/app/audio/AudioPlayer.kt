package com.wakh.app.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lit les messages vocaux reçus via [MediaPlayer], avec une position de
 * lecture exposée en [StateFlow] pour animer la barre de progression de la
 * bulle de message.
 *
 * Deux états distincts sont exposés :
 *   - [activeMessageId] : quel message est actuellement chargé dans le
 *     lecteur (qu'il soit en lecture ou en pause) ;
 *   - [isPlaying] : si ce message est actuellement en train de jouer.
 * Cette distinction est nécessaire pour que "basculer" (play/pause) sur un
 * message en pause reprenne la lecture plutôt que de la re-mettre en pause.
 */
class AudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _activeMessageId = MutableStateFlow<String?>(null)
    val activeMessageId: StateFlow<String?> = _activeMessageId

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0)
    val positionMs: StateFlow<Int> = _positionMs

    fun play(messageId: String, filePath: String) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    _isPlaying.value = false
                    _activeMessageId.value = null
                    _positionMs.value = 0
                    progressJob?.cancel()
                }
                prepare()
                start()
            }
            _activeMessageId.value = messageId
            _isPlaying.value = true
            startProgressLoop()
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de lire le message vocal : ${e.message}")
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun resume() {
        mediaPlayer?.start()
        _isPlaying.value = true
        startProgressLoop()
    }

    fun stop() {
        progressJob?.cancel()
        mediaPlayer?.apply {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        _activeMessageId.value = null
        _isPlaying.value = false
        _positionMs.value = 0
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _positionMs.value = mediaPlayer?.currentPosition ?: 0
                delay(100)
            }
        }
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
