package com.wakh.app.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.local.datastore.ThemeMode
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.repository.AuthRepository
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
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SimpleViewModelFactory { SettingsViewModel(userPreferences, authRepository) },
    )
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
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
