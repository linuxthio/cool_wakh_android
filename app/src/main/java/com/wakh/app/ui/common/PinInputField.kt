package com.wakh.app.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Longueur fixe du code PIN Wakh — inscription, connexion. */
const val PIN_LENGTH = 4

/**
 * Champ de saisie du code PIN : clavier numérique dédié, saisie masquée
 * (comme un mot de passe classique), limité à [PIN_LENGTH] chiffres —
 * tout caractère non numérique est ignoré au fur et à mesure de la
 * frappe plutôt que rejeté après coup, pour une saisie plus fluide.
 */
@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier,
    )
}
