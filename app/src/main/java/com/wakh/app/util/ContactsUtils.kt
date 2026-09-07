package com.wakh.app.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Résout l'URI renvoyée par le sélecteur système de contacts
 * (`ACTION_PICK` sur `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`)
 * en un couple (numéro, nom affiché). Ne nécessite AUCUNE permission
 * `READ_CONTACTS` : le sélecteur s'exécute dans l'app Contacts elle-même,
 * qui n'accorde à Wakh qu'un accès temporaire à la seule ligne choisie —
 * même principe de confidentialité que le sélecteur photo/vidéo système
 * déjà utilisé ailleurs dans l'app.
 */
fun resolvePickedContact(context: Context, contactDataUri: Uri): Pair<String, String>? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    )
    context.contentResolver.query(contactDataUri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null

        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

        val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null

        if (number.isNullOrBlank()) return null
        return number to (name?.takeIf { it.isNotBlank() } ?: number)
    }
    return null
}
