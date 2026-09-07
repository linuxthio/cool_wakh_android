package com.wakh.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.wakh.app.ui.theme.ErrorRed
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.SurfaceWhite
import com.wakh.app.util.formatDuration

/**
 * Barre de saisie unifiée en bas de l'écran de discussion : bouton pièce
 * jointe (photo/vidéo), champ texte avec bouton d'envoi quand il contient
 * du texte, ou bouton d'enregistrement vocal circulaire sinon — même
 * geste presser-maintenir qu'avant pour l'audio, et même principe
 * d'envoi pour les quatre types de message.
 *
 * Important : [RecordButton] n'est appelé qu'à UN SEUL endroit du code,
 * jamais dans une branche if/else qui changerait selon [isRecording] —
 * sinon Compose recréerait le composable en cours de geste et
 * interromprait l'enregistrement au moment même où l'utilisateur relâche
 * le bouton. Seul le contenu autour de lui (champ texte vs minuteur) varie.
 */
@Composable
fun InputBar(
    textValue: String,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    isRecording: Boolean,
    recordingElapsedMs: Int,
    onStartRecording: () -> Unit,
    onStopAndSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachDocument: () -> Unit,
    isSendingAttachment: Boolean,
) {
    Surface(color = SurfaceWhite, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isRecording) {
                AttachButton(
                    enabled = !isSendingAttachment,
                    onAttachImage = onAttachImage,
                    onAttachVideo = onAttachVideo,
                    onAttachDocument = onAttachDocument,
                )
                Spacer(Modifier.width(4.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ErrorRed),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatDuration(recordingElapsedMs), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = onCancelRecording) {
                            Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyBlue,
                        ),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Point d'appel unique, en dehors de tout if/else portant sur
            // isRecording, pour préserver le geste en cours (voir doc ci-dessus).
            if (isRecording || textValue.isBlank()) {
                RecordButton(
                    isRecording = isRecording,
                    onPressStart = onStartRecording,
                    onPressEnd = onStopAndSendRecording,
                    onPressCancel = onCancelRecording,
                )
            } else {
                SendButton(onClick = onSendText)
            }
        }
    }
}

@Composable
private fun AttachButton(
    enabled: Boolean,
    onAttachImage: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachDocument: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.AttachFile, contentDescription = "Joindre une photo, une vidéo ou un document", tint = SkyBlue)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Photo (jusqu'à 10)") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAttachImage()
                },
            )
            DropdownMenuItem(
                text = { Text("Vidéo") },
                leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAttachVideo()
                },
            )
            DropdownMenuItem(
                text = { Text("Document") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAttachDocument()
                },
            )
        }
    }
}

@Composable
private fun SendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(SkyBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Envoyer",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}
