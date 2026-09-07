package com.wakh.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.audio.AudioPlayer
import com.wakh.app.audio.AudioRecorder
import com.wakh.app.data.local.db.MessageDirection
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.local.db.MessageKind
import com.wakh.app.data.remote.SignalingClient
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.data.repository.MessageRepository
import com.wakh.app.ui.chat.components.ChatTopBar
import com.wakh.app.ui.chat.components.InputBar
import com.wakh.app.ui.chat.components.MediaViewerDialog
import com.wakh.app.ui.chat.components.MessageBubble
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.util.openMediaExternally
import com.wakh.app.util.shareFile
import com.wakh.app.util.shareText
import java.io.File

/** Nombre max d'images sélectionnables en une fois, voir InputBar / PickMultipleVisualMedia. */
private const val MAX_IMAGES_AT_ONCE = 10

@Composable
fun ChatScreen(
    conversationId: String,
    messageRepository: MessageRepository,
    contactRepository: ContactRepository,
    groupRepository: GroupRepository,
    signalingClient: SignalingClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val viewModel: ChatViewModel = viewModel(
        key = conversationId,
        factory = SimpleViewModelFactory {
            ChatViewModel(
                conversationId = conversationId,
                messageRepository = messageRepository,
                contactRepository = contactRepository,
                groupRepository = groupRepository,
                signalingClient = signalingClient,
                audioRecorder = AudioRecorder(context),
                audioPlayer = AudioPlayer(),
                appContext = context.applicationContext,
            )
        },
    )

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val activeMessageId by viewModel.activeMessageId.collectAsStateWithLifecycle()
    val isPlayingAudio by viewModel.isPlayingAudio.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPositionMs.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasAudioPermission = granted }

    // Sélecteur photo système, jusqu'à MAX_IMAGES_AT_ONCE à la fois
    // (aucune permission requise). Le contenu choisi est copié dans le
    // stockage local par le ViewModel avant d'être envoyé, selon le même
    // principe que l'audio.
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_AT_ONCE),
    ) { uris: List<Uri> -> viewModel.sendImages(uris) }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let { viewModel.sendVideo(it) } }

    // Sélecteur de document système, tous types de fichiers confondus
    // (PDF, Word, ZIP...) — aucune permission requise non plus.
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.sendDocument(it) } }

    // Média (image/vidéo) actuellement ouvert en plein écran, ou `null`
    // si la visionneuse est fermée. Un document ne passe pas par cette
    // visionneuse : il s'ouvre directement avec une application externe
    // (voir onMediaClick ci-dessous).
    var viewerTarget by remember { mutableStateOf<MessageEntity?>(null) }

    // Message dont la suppression est en attente de confirmation.
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = viewModel.title,
                subtitle = if (viewModel.isGroup) {
                    val count = viewModel.memberCount
                    "$count membre" + if (count > 1) "s" else ""
                } else {
                    if (isOnline) "En ligne" else "Hors ligne"
                },
                online = if (viewModel.isGroup) null else isOnline,
                onBack = onBack,
            )
        },
        containerColor = AppBackground,
        bottomBar = {
            InputBar(
                textValue = viewModel.textInput,
                onTextChange = viewModel::onTextInputChange,
                onSendText = viewModel::sendTextMessage,
                isRecording = viewModel.isRecording,
                recordingElapsedMs = viewModel.recordingElapsedMs,
                onStartRecording = {
                    if (hasAudioPermission) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopAndSendRecording = viewModel::stopRecordingAndSend,
                onCancelRecording = viewModel::cancelRecording,
                onAttachImage = {
                    pickImagesLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onAttachVideo = {
                    pickVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                onAttachDocument = { pickDocumentLauncher.launch(arrayOf("*/*")) },
                isSendingAttachment = viewModel.isSendingAttachment,
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages, key = { it.id }) { message ->
                val isActiveMessage = activeMessageId == message.id
                val senderLabel = if (viewModel.isGroup && message.direction == MessageDirection.RECEIVED) {
                    message.senderPhoneNumber?.let { viewModel.senderDisplayName(it) }
                } else {
                    null
                }
                MessageBubble(
                    message = message,
                    isPlaying = isActiveMessage && isPlayingAudio,
                    playbackPositionMs = if (isActiveMessage) playbackPosition else 0,
                    onPlayToggle = { viewModel.togglePlayback(message) },
                    onRetry = { viewModel.retry(message.id) },
                    onMediaClick = {
                        if (message.kind == MessageKind.DOCUMENT) {
                            message.localFilePath?.let { path ->
                                val opened = openMediaExternally(context, File(path), "application/octet-stream")
                                if (!opened) {
                                    viewModel.reportAttachmentError(
                                        "Aucune application installée ne peut ouvrir ce document. Vous pouvez le partager avec une autre application (menu \"Partager\").",
                                    )
                                }
                            }
                        } else {
                            viewerTarget = message
                        }
                    },
                    onShare = {
                        when (message.kind) {
                            MessageKind.TEXT -> message.textContent?.let { shareText(context, it) }
                            MessageKind.AUDIO -> message.localFilePath?.let { shareFile(context, File(it), "audio/mp4") }
                            MessageKind.IMAGE -> message.localFilePath?.let { shareFile(context, File(it), "image/jpeg") }
                            MessageKind.VIDEO -> message.localFilePath?.let { shareFile(context, File(it), "video/mp4") }
                            MessageKind.DOCUMENT -> message.localFilePath?.let {
                                shareFile(context, File(it), "application/octet-stream")
                            }
                        }
                    },
                    onDeleteRequest = { messageToDelete = message },
                    senderLabel = senderLabel,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    viewerTarget?.let { target ->
        MediaViewerDialog(message = target, onDismiss = { viewerTarget = null })
    }

    messageToDelete?.let { target ->
        DeleteMessageDialog(
            canDeleteForEveryone = target.direction == MessageDirection.SENT,
            onDeleteForMe = {
                viewModel.deleteMessage(target.id)
                messageToDelete = null
            },
            onDeleteForEveryone = {
                viewModel.deleteForEveryone(target.id)
                messageToDelete = null
            },
            onDismiss = { messageToDelete = null },
        )
    }

    viewModel.attachmentError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearAttachmentError,
            title = { Text("Envoi impossible") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearAttachmentError) { Text("OK") }
            },
        )
    }
}

/**
 * Confirmation de suppression : deux options pour un message que j'ai
 * envoyé ("pour moi" ou "pour tout le monde" — voir
 * MessageRepository.deleteForEveryone), une seule pour un message reçu
 * (on ne peut supprimer que sa propre copie de ce que quelqu'un d'autre
 * a envoyé).
 */
@Composable
private fun DeleteMessageDialog(
    canDeleteForEveryone: Boolean,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer ce message ?") },
        text = {
            if (canDeleteForEveryone) {
                Column {
                    Text(
                        "Choisissez de le supprimer uniquement sur cet appareil, ou chez tous les destinataires.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onDeleteForEveryone, modifier = Modifier.fillMaxWidth()) {
                        Text("Supprimer pour tout le monde", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDeleteForMe, modifier = Modifier.fillMaxWidth()) {
                        Text("Supprimer pour moi")
                    }
                }
            } else {
                Text("Le message sera supprimé de cet appareil uniquement — l'expéditeur conserve sa propre copie.")
            }
        },
        confirmButton = {
            if (!canDeleteForEveryone) {
                TextButton(onClick = onDeleteForMe) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
