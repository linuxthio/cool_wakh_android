package com.wakh.app.util

private val PHONE_REGEX = Regex("^\\+?[0-9]{7,15}$")

/** Indicatif du Sénégal — seul pays ciblé par Wakh, il ne varie jamais d'un utilisateur à l'autre. */
private const val SENEGAL_CALLING_CODE = "221"

/** Longueur d'un numéro local sénégalais une fois l'indicatif retiré (ex. "771234567"). */
private const val SENEGAL_LOCAL_NUMBER_LENGTH = 9

/**
 * Retire espaces/points/tirets/parenthèses d'un numéro saisi par
 * l'utilisateur — c'est la SEULE transformation de présentation
 * appliquée avant validation/canonicalisation. Le numéro n'est jamais
 * modifié autrement à ce stade.
 */
fun stripPhoneNumberSeparators(raw: String): String =
    raw.trim().replace(Regex("[\\s.\\-()]"), "")

/** Valide un numéro déjà nettoyé (voir [stripPhoneNumberSeparators]) — le "+" est optionnel. */
fun isValidPhoneNumber(cleaned: String): Boolean = PHONE_REGEX.matches(cleaned)

/**
 * Vrai si [cleaned] (déjà nettoyé des séparateurs) porte un indicatif
 * international explicite ("+" ou "00") différent de celui du Sénégal
 * ("221"). Un numéro saisi sans indicatif explicite est considéré comme
 * un numéro local sénégalais et ne déclenche jamais cet avertissement.
 */
fun hasNonSenegaleseCountryCode(cleaned: String): Boolean {
    val international = when {
        cleaned.startsWith("+") -> cleaned.removePrefix("+")
        cleaned.startsWith("00") -> cleaned.removePrefix("00")
        else -> return false
    }
    return !international.startsWith(SENEGAL_CALLING_CODE)
}

/**
 * Numéro canonique utilisé comme identifiant réel (inscription, connexion,
 * ajout de contact) : Wakh ne cible que des numéros sénégalais, dont
 * l'indicatif ne varie jamais — il est donc retiré s'il est présent, pour
 * que "771234567" et "+221771234567" (ou "221771234567") désignent
 * toujours le même compte/contact. Un numéro avec un autre indicatif
 * (voir [hasNonSenegaleseCountryCode]) n'est volontairement pas modifié
 * ici : il n'a pas d'indicatif sénégalais à retirer.
 */
fun canonicalSenegalesePhoneNumber(cleaned: String): String {
    val digitsOnly = cleaned.removePrefix("+")
    val expectedLength = SENEGAL_CALLING_CODE.length + SENEGAL_LOCAL_NUMBER_LENGTH
    return if (digitsOnly.length == expectedLength && digitsOnly.startsWith(SENEGAL_CALLING_CODE)) {
        digitsOnly.removePrefix(SENEGAL_CALLING_CODE)
    } else {
        cleaned
    }
}
