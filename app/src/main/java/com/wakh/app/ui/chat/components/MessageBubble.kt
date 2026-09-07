package com.wakh.app.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wakh.app.data.local.db.MessageDirection
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.local.db.MessageKind
import com.wakh.app.data.local.db.MessageStatus
import com.wakh.app.ui.common.WakhImageLoader
import com.wakh.app.ui.theme.BubbleReceived
import com.wakh.app.ui.theme.ErrorRed
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.TextOnBubbleReceived
import com.wakh.app.util.formatDuration
import com.wakh.app.util.formatFileSize
import com.wakh.app.util.formatTimestamp

/**
 * Bulle de message façon messagerie. Le contenu diffère selon
 * [MessageEntity.kind] (lecteur audio, vignette image, vignette vidéo ou
 * simple texte), mais l'habillage (couleur, forme, horodatage, statut)
 * reste identique pour les quatre — même principe d'affichage partout.
 * Un tap sur une image/vidéo déclenche [onMediaClick], qui ouvre la
 * visionneuse plein écran intégrée (voir MediaViewerDialog). Un appui
 * long sur la bulle ouvre un petit menu d'actions ("Partager" via
 * [onShare], "Supprimer" via [onDeleteRequest] — ce dernier ouvre côté
 * ChatScreen la confirmation de suppression, locale ou pour tout le
 * monde selon le cas).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isPlaying: Boolean,
    playbackPositionMs: Int,
    onPlayToggle: () -> Unit,
    onRetry: () -> Unit,
    onMediaClick: () -> Unit,
    onShare: () -> Unit,
    onDeleteRequest: () -> Unit,
    senderLabel: String? = null,
) {
    val isSent = message.direction == MessageDirection.SENT
    val bubbleColor = if (isSent) SkyBlue else BubbleReceived
    val contentColor = if (isSent) Color.White else TextOnBubbleReceived
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isSent) 18.dp else 4.dp,
        bottomEnd = if (isSent) 4.dp else 18.dp,
    )
    var showActionsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .combinedClickable(onClick = {}, onLongClick = { showActionsMenu = true })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (senderLabel != null) {
                    Text(
                        text = senderLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                when (message.kind) {
                    MessageKind.TEXT -> Text(
                        text = message.textContent.orEmpty(),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MessageKind.IMAGE -> ImageContent(message, onMediaClick)
                    MessageKind.VIDEO -> VideoContent(message, onMediaClick)
                    MessageKind.DOCUMENT -> DocumentContent(message, contentColor, onMediaClick)
                    MessageKind.AUDIO -> AudioContent(
                        message = message,
                        isPlaying = isPlaying,
                        playbackPositionMs = playbackPositionMs,
                        contentColor = contentColor,
                        onPlayToggle = onPlayToggle,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isSent) {
                        MessageStatusIcon(message.status, contentColor, onRetry)
                    }
                }
            }

            DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Partager") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        showActionsMenu = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Supprimer") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        showActionsMenu = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageContent(message: MessageEntity, onClick: () -> Unit) {
    val path = message.localFilePath
    AsyncImage(
        model = path,
        contentDescription = "Image — toucher pour agrandir",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = path != null, onClick = onClick),
    )
}

@Composable
private fun VideoContent(message: MessageEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val path = message.localFilePath

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = path != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = path,
            imageLoader = WakhImageLoader.get(context),
            contentDescription = "Vidéo — toucher pour lire",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Lire la vidéo", tint = Color.White)
        }
        Text(
            text = formatDuration(message.durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DocumentContent(message: MessageEntity, contentColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(contentColor.copy(alpha = 0.08f))
            .clickable(enabled = message.localFilePath != null, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(contentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = contentColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = message.fileName ?: "Document",
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = formatFileSize(message.sizeBytes),
                color = contentColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun AudioContent(
    message: MessageEntity,
    isPlaying: Boolean,
    playbackPositionMs: Int,
    contentColor: Color,
    onPlayToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lire",
                tint = contentColor,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            val progress = if (message.durationMs > 0) {
                (playbackPositionMs.toFloat() / message.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDuration(message.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun MessageStatusIcon(status: MessageStatus, tint: Color, onRetry: () -> Unit) {
    when (status) {
        MessageStatus.SENDING -> Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Envoi en cours",
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
        MessageStatus.QUEUED_OFFLINE -> Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = "En attente — le destinataire est hors ligne",
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp),
        )
        MessageStatus.SENT_RECEIVED -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = "Envoyé et reçu",
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        MessageStatus.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Échec de l'envoi",
                tint = ErrorRed,
                modifier = Modifier.size(14.dp),
            )
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Réessayer", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
            }
        }
    }
}
