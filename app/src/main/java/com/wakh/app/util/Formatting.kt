package com.wakh.app.util

import com.wakh.app.data.local.db.MessageDirection
import com.wakh.app.data.local.db.MessageEntity
import com.wakh.app.data.local.db.MessageKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

fun formatDuration(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatTimestamp(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)

fun messageSummary(message: MessageEntity): String {
    val prefix = if (message.direction == MessageDirection.SENT) "Vous : " else ""
    return when (message.kind) {
        MessageKind.TEXT -> "$prefix${message.textContent.orEmpty()}"
        MessageKind.AUDIO -> "$prefix message vocal (${formatDuration(message.durationMs)})"
        MessageKind.IMAGE -> "${prefix}Photo"
        MessageKind.VIDEO -> "${prefix}Vidéo (${formatDuration(message.durationMs)})"
    }
}
