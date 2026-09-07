package com.wakh.app

import android.content.Context
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.local.db.AppDatabase
import com.wakh.app.data.remote.AudioQueueApi
import com.wakh.app.data.remote.AuthApi
import com.wakh.app.data.remote.DeleteQueueApi
import com.wakh.app.data.remote.MediaQueueApi
import com.wakh.app.data.remote.SignalingClient
import com.wakh.app.data.remote.TextQueueApi
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.data.repository.MessageRepository
import com.wakh.app.webrtc.WebRtcManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Conteneur manuel de dépendances (pas de Hilt, pour garder le projet
 * simple) instancié une seule fois dans [WakhApplication]. Câble aussi les
 * flux "de fond" propres au mode hybride, pour l'audio, l'image, la vidéo,
 * le document ET le texte, en conversation individuelle OU de groupe,
 * selon exactement le même principe pour tous :
 *   - persistance locale d'un message reçu en P2P direct, puis envoi de
 *     l'accusé de réception ;
 *   - récupération automatique des messages déposés en attente côté
 *     serveur dès qu'on est notifié ("pending_audio" / "pending_text" /
 *     "pending_media" / "pending_delete") d'une reconnexion ;
 *   - déconnexion automatique si le serveur invalide le jeton
 *     d'authentification en cours (compte supprimé, jeton expiré...).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val userPreferences = UserPreferences(appContext)

    private val database = AppDatabase.getInstance(appContext)
    val contactDao = database.contactDao()
    val messageDao = database.messageDao()
    val groupDao = database.groupDao()

    val signalingClient = SignalingClient(okHttpClient, json)
    private val audioQueueApi = AudioQueueApi(okHttpClient, userPreferences)
    private val textQueueApi = TextQueueApi(okHttpClient, userPreferences)
    private val mediaQueueApi = MediaQueueApi(okHttpClient, userPreferences)
    private val deleteQueueApi = DeleteQueueApi(okHttpClient, userPreferences)
    private val authApi = AuthApi(okHttpClient)
    val webRtcManager = WebRtcManager(appContext, signalingClient)

    val authRepository = AuthRepository(authApi, userPreferences)
    val contactRepository = ContactRepository(contactDao)
    val groupRepository = GroupRepository(groupDao)
    val messageRepository = MessageRepository(
        messageDao = messageDao,
        signalingClient = signalingClient,
        webRtcManager = webRtcManager,
        audioQueueApi = audioQueueApi,
        textQueueApi = textQueueApi,
        mediaQueueApi = mediaQueueApi,
        deleteQueueApi = deleteQueueApi,
        groupRepository = groupRepository,
        userPreferences = userPreferences,
        appContext = appContext,
    )

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Un fichier (audio, image, vidéo ou document) reçu en P2P direct
        // est d'abord persisté en local (Room) ; l'accusé de réception
        // n'est envoyé qu'une fois cette persistance confirmée (voir
        // WebRtcManager.finalizeIncomingFile). groupId est transmis tel
        // quel : non-null si le fichier a été reçu dans le cadre d'un
        // groupe, ce qui détermine la conversation où il apparaît.
        applicationScope.launch {
            webRtcManager.incomingFiles.collect { event ->
                messageRepository.handleIncomingP2PFile(
                    fromNumber = event.fromNumber,
                    file = event.file,
                    kind = event.mediaKind,
                    durationMs = event.durationMs,
                    remoteMessageId = event.remoteMessageId,
                    groupId = event.groupId,
                    fileName = event.originalFileName,
                )
                event.onPersisted.complete(Unit)
            }
        }

        // Même principe pour un message texte reçu en P2P direct.
        applicationScope.launch {
            webRtcManager.incomingTexts.collect { event ->
                messageRepository.handleIncomingP2PText(
                    fromNumber = event.fromNumber,
                    content = event.content,
                    remoteMessageId = event.remoteMessageId,
                    groupId = event.groupId,
                )
                event.onPersisted.complete(Unit)
            }
        }

        // Même principe pour une demande de "suppression pour tout le
        // monde" reçue en P2P direct (voir MessageRepository.deleteForEveryone).
        applicationScope.launch {
            webRtcManager.incomingDeletes.collect { event ->
                messageRepository.handleIncomingDeleteRequest(event.targetMessageId)
                event.onPersisted.complete(Unit)
            }
        }

        // Dès que le serveur signale des messages/demandes en attente
        // (déposés pendant qu'on était hors ligne), on les récupère
        // automatiquement — audio, texte, médias et suppressions selon le
        // même principe. Le tri vers la bonne conversation (individuelle
        // ou de groupe) se fait dans MessageRepository à partir du
        // group_id porté par chaque entrée.
        applicationScope.launch {
            signalingClient.incoming.collect { envelope ->
                val me = userPreferences.profile.first() ?: return@collect
                when (envelope.type) {
                    "pending_audio" -> messageRepository.fetchQueuedMessages(me.phoneNumber)
                    "pending_text" -> messageRepository.fetchQueuedTextMessages(me.phoneNumber)
                    "pending_media" -> messageRepository.fetchQueuedMedia(me.phoneNumber)
                    "pending_delete" -> messageRepository.fetchQueuedDeletes(me.phoneNumber)
                }
            }
        }

        // Le serveur a rejeté le jeton d'authentification (compte
        // supprimé, jeton invalidé ailleurs...) : on efface l'identité
        // locale, ce qui ramène automatiquement à l'écran d'accueil
        // (voir WakhNavGraph).
        applicationScope.launch {
            signalingClient.authInvalidated.collect {
                userPreferences.clear()
            }
        }
    }
}
