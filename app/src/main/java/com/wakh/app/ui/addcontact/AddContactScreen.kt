package com.wakh.app.ui.addcontact

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakh.app.data.repository.ContactRepository
import com.wakh.app.ui.common.SimpleViewModelFactory
import com.wakh.app.ui.theme.AppBackground
import com.wakh.app.ui.theme.SkyBlue
import com.wakh.app.ui.theme.SurfaceWhite
import com.wakh.app.util.resolvePickedContact

@Composable
fun AddContactScreen(
    contactRepository: ContactRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AddContactViewModel = viewModel(
        factory = SimpleViewModelFactory { AddContactViewModel(contactRepository) },
    )

    // Sélecteur système de contacts : aucune permission READ_CONTACTS
    // requise (voir util/ContactsUtils.kt), même principe de
    // confidentialité que le sélecteur photo/vidéo utilisé dans le chat.
    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                resolvePickedContact(context, uri)?.let { (number, name) ->
                    viewModel.prefillFromDeviceContact(number, name)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un contact") },
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
                text = "Entrez le numéro de téléphone de la personne à ajouter, ou importez-le directement depuis vos contacts. Ce numéro est son identifiant unique sur Wakh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                    runCatching { pickContactLauncher.launch(intent) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = SkyBlue)
                Spacer(Modifier.width(8.dp))
                Text("Importer depuis les contacts", color = SkyBlue)
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "ou saisissez-le manuellement",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Numéro de téléphone") },
                placeholder = { Text("+221771234567 ou 771234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.displayName,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nom (optionnel)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            viewModel.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.submit(onDone) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Ajouter")
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
                TextButton(onClick = { viewModel.confirmForeignNumberAndSubmit(onDone) }) {
                    Text("Continuer")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissForeignNumberWarning) { Text("Annuler") }
            },
        )
    }
}
