package com.wakh.app.data.remote

import android.util.Log
import com.wakh.app.BuildConfig
import com.wakh.app.util.sanitizeSignalingHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Client du serveur de signalisation. Ne transporte QUE des métadonnées
 * WebRTC (SDP/ICE) et des informations de présence — jamais de contenu de
 * message. Se reconnecte automatiquement en cas de coupure.
 *
 * La connexion nécessite un compte créé et une connexion réussie (voir
 * AuthRepository) : le jeton obtenu est transmis en paramètre de requête
 * `?token=...`. Si le serveur le rejette (compte supprimé, déconnexion
 * ailleurs, etc.), il ferme la connexion avec le code 4401 — voir
 * [authInvalidated].
 */
class SignalingClient(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var myPhoneNumber: String? = null
    private var myToken: String? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false

    private val _incoming = MutableSharedFlow<SignalingEnvelope>(extraBufferCapacity = 64)
    val incoming: SharedFlow<SignalingEnvelope> = _incoming

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState

    /** Émis quand le serveur rejette le jeton d'authentification (code de fermeture 4401). */
    private val _authInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authInvalidated: SharedFlow<Unit> = _authInvalidated

    fun connect(phoneNumber: String, token: String) {
        if (myPhoneNumber == phoneNumber && myToken == token && _connectionState.value) return
        myPhoneNumber = phoneNumber
        myToken = token
        shouldReconnect = true
        openSocket(phoneNumber, token)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "bye")
        webSocket = null
        _connectionState.value = false
    }

    fun send(envelope: SignalingEnvelope) {
        val text = json.encodeToString(envelope)
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            Log.w(TAG, "Impossible d'envoyer sur le websocket (déconnecté) : ${envelope.type}")
        }
    }

    /** Interroge le serveur pour savoir si [target] est actuellement joignable. */
    suspend fun checkPresence(target: String, timeoutMs: Long = 4000): Boolean {
        if (!_connectionState.value) return false
        send(SignalingEnvelope(type = SignalingType.PRESENCE_CHECK, target = target))
        val status = withTimeoutOrNull(timeoutMs) {
            incoming.first { it.type == SignalingType.PRESENCE_STATUS && it.target == target }
        }
        return status?.online ?: false
    }

    private fun openSocket(phoneNumber: String, token: String) {
        val scheme = if (BuildConfig.SIGNALING_USE_TLS) "wss" else "ws"
        val request = Request.Builder()
            .url("$scheme://${sanitizeSignalingHost(BuildConfig.SIGNALING_HOST)}/ws/$phoneNumber?token=$token")
            .build()

        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "Connecté au serveur de signalisation")
                    _connectionState.value = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val envelope = runCatching { json.decodeFromString(SignalingEnvelope.serializer(), text) }
                        .getOrNull() ?: return
                    _incoming.tryEmit(envelope)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (code == WS_CLOSE_UNAUTHORIZED) {
                        Log.w(TAG, "Authentification rejetée par le serveur")
                        shouldReconnect = false
                        _authInvalidated.tryEmit(Unit)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = false
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Connexion de signalisation perdue : ${t.message}")
                    _connectionState.value = false
                    scheduleReconnect()
                }
            },
        )
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            val phone = myPhoneNumber
            val token = myToken
            if (phone != null && token != null) openSocket(phone, token)
        }
    }

    companion object {
        private const val TAG = "SignalingClient"
        private const val RECONNECT_DELAY_MS = 3000L

        /** Doit correspondre à WS_CLOSE_UNAUTHORIZED côté serveur (signaling/fastapi_app.py). */
        private const val WS_CLOSE_UNAUTHORIZED = 4401
    }
}
