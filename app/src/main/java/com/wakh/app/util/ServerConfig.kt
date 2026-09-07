package com.wakh.app.util

/**
 * Retire un éventuel schéma ("http://", "https://", "ws://", "wss://")
 * et un slash final accidentellement inclus dans la configuration de
 * l'hôte du serveur (`BuildConfig.SIGNALING_HOST`) — erreur de
 * configuration courante au déploiement : ce champ doit contenir
 * uniquement "domaine.exemple.com" ou "domaine.exemple.com:8000",
 * jamais une URL complète (le schéma correct est déjà déterminé
 * séparément via `BuildConfig.SIGNALING_USE_TLS`).
 *
 * Sans cette protection, une valeur mal configurée comme
 * "https://mon-serveur.com" produirait une URL du type
 * "https://https://mon-serveur.com", provoquant l'erreur Android
 * "Unable to resolve host "https": No address associated with hostname"
 * — le "https" en trop étant interprété comme le nom d'hôte à résoudre.
 */
fun sanitizeSignalingHost(rawHost: String): String =
    rawHost.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("wss://")
        .removePrefix("ws://")
        .trimEnd('/')
