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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.repository.AuthRepository
import com.wakh.app.ui.common.PinInputField
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.SurfaceWhite

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegistered: () -> Unit,
) {
    val viewModel: RegisterViewModel = viewModel(
        factory = SimpleViewModelFactory { RegisterViewModel(authRepository) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créer un compte") },
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
                text = "Votre numéro de téléphone sert d'identifiant unique pour être contacté sur Wakh. Choisissez un code PIN à 4 chiffres pour protéger votre compte.",
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
            Text(
                text = "Avec l'indicatif (+221771234567) ou sans (771234567) : les deux désignent le même compte.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            PinInputField(
                value = viewModel.pin,
                onValueChange = viewModel::onPinChange,
                label = "Code PIN (4 chiffres)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PinInputField(
                value = viewModel.confirmPin,
                onValueChange = viewModel::onConfirmPinChange,
                label = "Confirmer le code PIN",
                modifier = Modifier.fillMaxWidth(),
            )

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
                onClick = { viewModel.submit(onRegistered) },
                enabled = !viewModel.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Créer mon compte")
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
                TextButton(onClick = { viewModel.confirmForeignNumberAndSubmit(onRegistered) }) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissForeignNumberWarning) { Text("Annuler") }
            },
        )
    }
}
