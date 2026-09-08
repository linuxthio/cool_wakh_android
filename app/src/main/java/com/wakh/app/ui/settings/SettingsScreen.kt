package com.wakh.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.local.datastore.ThemeMode
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.local.db.ContactEntity
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.ErrorRed
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.SkyBlueSurfaceTint
import com.wakh.app.ui.theme.SurfaceWhite

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    authRepository: AuthRepository,
    contactRepository: ContactRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SimpleViewModelFactory { SettingsViewModel(userPreferences, authRepository, contactRepository) },
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var contactPendingRemoval by remember { mutableStateOf<ContactEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = "Apparence",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            ThemeModeSelector(selected = themeMode, onSelect = viewModel::onThemeModeChange)

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Profil",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Votre nom") },
                singleLine = true,
                trailingIcon = {
                    if (viewModel.isSaved) {
                        Icon(Icons.Default.Check, contentDescription = "Enregistré", tint = SkyBlue)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = viewModel::saveName,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Enregistrer")
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Numéro de téléphone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Le numéro est l'identifiant de votre compte : il ne peut pas être modifié ici.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            if (contacts.isEmpty()) {
                Text(
                    text = "Aucun contact enregistré.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    contacts.forEach { contact ->
                        ContactRow(contact = contact, onRemove = { contactPendingRemoval = contact })
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed)
                Spacer(Modifier.width(8.dp))
                Text("Se déconnecter", color = ErrorRed)
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "À propos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Wakh — un produit Djibysoft",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@linuxthio"))
                    runCatching { context.startActivity(intent) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "TikTok : @linuxthio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyBlue,
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Se déconnecter ?") },
            text = { Text("Vous devrez ressaisir votre numéro et votre mot de passe pour utiliser Wakh à nouveau.") },
            confirmButton = {
                Button(onClick = { showLogoutConfirm = false; viewModel.logout() }) {
                    Text("Se déconnecter")
                }
            },
            dismissButton = {
                Button(onClick = { showLogoutConfirm = false }) { Text("Annuler") }
            },
        )
    }

    contactPendingRemoval?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactPendingRemoval = null },
            title = { Text("Supprimer ${contact.displayName} ?") },
            text = { Text("Ce contact sera retiré de votre liste. L'historique des messages n'est pas effacé.") },
            confirmButton = {
                Button(onClick = {
                    contactPendingRemoval = null
                    viewModel.removeContact(contact)
                }) { Text("Supprimer") }
            },
            dismissButton = {
                Button(onClick = { contactPendingRemoval = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.PersonRemove, contentDescription = "Supprimer ce contact", tint = ErrorRed)
        }
    }
}

/** Sélecteur à trois options (Clair / Sombre / Système) — voir UserPreferences.ThemeMode. */
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeModeOption(
            label = "Clair",
            isSelected = selected == ThemeMode.LIGHT,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.LIGHT) },
        )
        ThemeModeOption(
            label = "Sombre",
            isSelected = selected == ThemeMode.DARK,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.DARK) },
        )
        ThemeModeOption(
            label = "Système",
            isSelected = selected == ThemeMode.SYSTEM,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.SYSTEM) },
        )
    }
}

@Composable
private fun ThemeModeOption(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (isSelected) SkyBlue else SkyBlueSurfaceTint,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
