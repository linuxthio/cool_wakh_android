package com.wakh.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wakh.app.ui.theme.OfflineGray
import com.wakh.app.ui.theme.OnlineGreen
import com.wakh.app.ui.theme.SurfaceWhite

/**
 * [online] est `null` pour une conversation de groupe (pas de statut
 * "en ligne" unique pour plusieurs personnes) : dans ce cas, [subtitle]
 * est affiché seul, sans pastille — typiquement le nombre de membres.
 * [onOpenGroupInfo] n'est fourni (non nul) que pour un groupe : un appui
 * sur le titre ouvre alors l'écran d'informations du groupe (renommage,
 * ajout/retrait de membres).
 */
@Composable
fun ChatTopBar(
    title: String,
    subtitle: String,
    online: Boolean?,
    onBack: () -> Unit,
    onOpenGroupInfo: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Column(
                modifier = if (onOpenGroupInfo != null) {
                    Modifier.clickable(onClick = onOpenGroupInfo)
                } else {
                    Modifier
                },
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (online != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (online) OnlineGreen else OfflineGray),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
    )
}
