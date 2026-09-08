package com.wakh.app.ui.groupinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.data.repository.GroupRepository
import com.wakh.app.ui.common.ContactAvatar
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.ErrorRed
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.SurfaceWhite

/**
 * Informations et gestion d'un groupe existant : renommage, ajout et
 * retrait de membres — voir GroupRepository. Purement local ; le serveur
 * n'a aucune notion de groupe, ces changements ne concernent donc que cet
 * appareil (README, section Groupes).
 */
@Composable
fun GroupInfoScreen(
    groupId: String,
    groupRepository: GroupRepository,
    contactRepository: ContactRepository,
    onBack: () -> Unit,
) {
    val viewModel: GroupInfoViewModel = viewModel(
        key = groupId,
        factory = SimpleViewModelFactory { GroupInfoViewModel(groupId, groupRepository, contactRepository) },
    )
    val members by viewModel.members.collectAsStateWithLifecycle()
    val availableContacts by viewModel.availableContactsToAdd.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informations du groupe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite),
            )
        },
        containerColor = AppBackground,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text(
                text = "Nom du groupe",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.groupName,
                onValueChange = viewModel::onNameChange,
                singleLine = true,
                trailingIcon = {
                    if (viewModel.isNameSaved) {
                        Icon(Icons.Default.Check, contentDescription = "Enregistré", tint = SkyBlue)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::saveName, shape = RoundedCornerShape(16.dp)) {
                Text("Enregistrer")
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Membres (${members.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))

            OutlinedButton(onClick = viewModel::openAddMembers, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = SkyBlue)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter des membres", color = SkyBlue)
            }

            Spacer(Modifier.height(8.dp))

            if (members.isEmpty()) {
                Text(
                    text = "Ce groupe n'a plus aucun membre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(members, key = { it.phoneNumber }) { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ContactAvatar(name = member.displayName, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = member.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.requestRemoveMember(member) }) {
                                Icon(Icons.Default.PersonRemove, contentDescription = "Retirer du groupe", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    viewModel.memberPendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveMember,
            title = { Text("Retirer ${member.displayName} ?") },
            text = { Text("Cette personne ne recevra plus les messages envoyés dans ce groupe. L'historique existant n'est pas effacé.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoveMember) {
                    Text("Retirer", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveMember) { Text("Annuler") }
            },
        )
    }

    if (viewModel.showAddMembersSheet) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddMembers,
            title = { Text("Ajouter des membres") },
            text = {
                if (availableContacts.isEmpty()) {
                    Text("Tous vos contacts sont déjà membres de ce groupe.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(availableContacts, key = { it.phoneNumber }) { contact ->
                            val checked = contact.phoneNumber in viewModel.selectedToAdd
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContactAvatar(name = contact.displayName, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(contact.displayName, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggleContactToAdd(contact.phoneNumber) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (availableContacts.isNotEmpty()) {
                    TextButton(onClick = viewModel::confirmAddMembers) { Text("Ajouter") }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAddMembers) { Text("Annuler") }
            },
        )
    }
}
