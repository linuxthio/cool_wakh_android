package com.wakh.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.local.datastore.UserPreferences
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.ui.common.PinInputField
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.SurfaceWhite

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val viewModel: LoginViewModel = viewModel(
        factory = SimpleViewModelFactory { LoginViewModel(authRepository, userPreferences) },
    )
    val remembered by viewModel.rememberedIdentity.collectAsStateWithLifecycle()
    val showFullForm = remembered == null || viewModel.useDifferentAccount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Se connecter") },
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
            if (showFullForm) {
                Text(
                    text = "Connectez-vous avec le numéro et le code PIN de votre compte Wakh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Votre nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = viewModel.phoneNumber,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("Numéro de téléphone") },
                    placeholder = { Text("+221771234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text = "Bon retour, ${remembered?.name} !",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = remembered?.phoneNumber.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
            }

            PinInputField(
                value = viewModel.pin,
                onValueChange = viewModel::onPinChange,
                label = "Code PIN (4 chiffres)",
                modifier = Modifier.fillMaxWidth(),
            )

            if (!showFullForm) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::useDifferentAccount) {
                    Text("Utiliser un autre compte")
                }
            }

            viewModel.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submit(onLoggedIn) },
                enabled = !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Se connecter")
                }
            }
        }
    }

    if (viewModel.pendingForeignNumberWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissForeignNumberWarning,
            title = { Text("Numéro non sénégalais") },
            text = {
                Text(
                    "Ce numéro ne semble pas être un numéro sénégalais (+221). " +
                        "Wakh est destiné aux numéros sénégalais. Voulez-vous continuer quand même ?",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmForeignNumberAndSubmit(onLoggedIn) }) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissForeignNumberWarning) { Text("Annuler") }
            },
        )
    }
}
