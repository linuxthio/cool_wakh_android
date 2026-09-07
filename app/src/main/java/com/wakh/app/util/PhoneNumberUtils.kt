package com.wakh.app.util

private val PHONE_REGEX = Regex("^\\+?[0-9]{7,15}$")

/**
 * Retire espaces/points/tirets/parenthèses d'un numéro saisi par
 * l'utilisateur — c'est la SEULE transformation appliquée. Le numéro
 * n'est jamais modifié autrement : aucun indicatif n'est ajouté,
 * deviné ou complété, avec ou sans "+". Ce que l'utilisateur propose est
 * ce qui est utilisé, tel quel (une fois les séparateurs de présentation
 * retirés).
 */
fun stripPhoneNumberSeparators(raw: String): String =
    raw.trim().replace(Regex("[\\s.\\-()]"), "")

/** Valide un numéro déjà nettoyé (voir [stripPhoneNumberSeparators]) — le "+" est optionnel. */
fun isValidPhoneNumber(cleaned: String): Boolean = PHONE_REGEX.matches(cleaned)
