package com.wakh.app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.local.db.ContactEntity
import com.wakh.app.data.local.db.MessageDirection
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.data.repository.MessageRepository
import com.wakh.app.ui.common.ContactAvatar
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.SkyBlueDark
import com.wakh.app.ui.theme.SkyBlueSurfaceTint
import com.wakh.app.ui.theme.SurfaceWhite
import com.wakh.app.util.formatTimestamp
import com.wakh.app.util.messageSummary

@Composable
fun ConversationsListScreen(
    contactRepository: ContactRepository,
    messageRepository: MessageRepository,
    groupRepository: GroupRepository,
    onOpenChat: (String) -> Unit,
    onAddContact: () -> Unit,
    onCreateGroup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ConversationsViewModel = viewModel(
        factory = SimpleViewModelFactory { ConversationsViewModel(contactRepository, messageRepository, groupRepository) },
    )
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wakh", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = SkyBlue,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Ajouter")
                }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ajouter un contact") },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onAddContact()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Créer un groupe") },
                        leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onCreateGroup()
                        },
                    )
                }
            }
        },
        containerColor = AppBackground,
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyConversationsPlaceholder(
                modifier = Modifier.padding(padding).fillMaxSize(),
                onAddContact = onAddContact,
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(conversations, key = { it.conversationId }) { convo ->
                    ConversationRow(
                        convo,
                        onClick = { onOpenChat(convo.conversationId) },
                        onRemoveContact = (convo.target as? ConversationTarget.Direct)?.let { direct ->
                            { viewModel.removeContact(direct.contact) }
                        },
                    )
                    HorizontalDivider(color = SkyBlueSurfaceTint)
                }
            }
        }
    }
}

/**
 * Une conversation individuelle (pas un groupe, voir [onRemoveContact])
 * peut être retirée de ses contacts par un appui long : menu "Supprimer
 * le contact" puis confirmation. Cela ne supprime que le contact
 * (Room, [ContactEntity]) — l'historique local des messages échangés
 * avec ce numéro n'est pas effacé.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(convo: ConversationUi, onClick: () -> Unit, onRemoveContact: (() -> Unit)? = null) {
    val hasUnread = convo.unreadCount > 0
    var showActionsMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onRemoveContact?.let { { showActionsMenu = true } },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationAvatar(target = convo.target, displayName = convo.displayName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(convo.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = conversationPreview(convo),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                convo.lastMessage?.let {
                    Text(
                        text = formatTimestamp(it.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasUnread) {
                    Spacer(Modifier.height(4.dp))
                    UnreadBadge(count = convo.unreadCount)
                }
            }
        }

        if (onRemoveContact != null) {
            DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Supprimer le contact") },
                    leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                    onClick = {
                        showActionsMenu = false
                        showRemoveConfirm = true
                    },
                )
            }
        }
    }

    if (showRemoveConfirm && onRemoveContact != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Supprimer ${convo.displayName} ?") },
            text = { Text("Ce contact sera retiré de votre liste. L'historique des messages n'est pas effacé.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveContact()
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Annuler") }
            },
        )
    }
}

/** Aperçu du dernier message : "Nom : message" pour un groupe reçu, sinon comportement habituel. */
private fun conversationPreview(convo: ConversationUi): String {
    val message = convo.lastMessage ?: return when (val target = convo.target) {
        is ConversationTarget.Direct -> "Aucun message pour le moment"
        is ConversationTarget.Group -> {
            val count = target.memberCount
            "$count membre" + if (count > 1) "s" else ""
        }
    }
    val base = messageSummary(message)
    return if (convo.target is ConversationTarget.Group && message.direction == MessageDirection.RECEIVED) {
        val sender = convo.lastMessageSenderName ?: message.senderPhoneNumber.orEmpty()
        "$sender : $base"
    } else {
        base
    }
}

@Composable
private fun ConversationAvatar(target: ConversationTarget, displayName: String) {
    when (target) {
        is ConversationTarget.Direct -> ContactAvatar(name = displayName)
        is ConversationTarget.Group -> Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(SkyBlueSurfaceTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = SkyBlueDark)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .background(SkyBlue, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyConversationsPlaceholder(modifier: Modifier = Modifier, onAddContact: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(80.dp)
                    .width(80.dp)
                    .background(SkyBlueSurfaceTint, shape = RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Aucune conversation pour l'instant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ajoutez un contact par son numéro de téléphone pour lui envoyer un premier message, ou créez un groupe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddContact, shape = RoundedCornerShape(16.dp)) {
                Text("Ajouter un contact")
            }
        }
    }
}
