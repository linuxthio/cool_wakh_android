package com.wakh.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Vrai si le thème sombre est actuellement actif — fourni par [WakhTheme].
 * Permet aux propriétés ci-dessous de basculer clair/sombre sans que les
 * dizaines d'écrans qui les référencent (ex. `SurfaceWhite`, `AppBackground`)
 * n'aient à changer quoi que ce soit : le nom reste le même, seule la
 * valeur résolue change selon le thème actif.
 */
val LocalWakhDarkTheme: CompositionLocal<Boolean> = compositionLocalOf { false }

// Palette "bleu ciel" de Wakh — couleur de marque commune aux deux thèmes.
val SkyBlue = Color(0xFF0EA5E9) // primaire
val SkyBlueDark = Color(0xFF0284C7)
val SkyBlueLight = Color(0xFF7DD3FC)
val BubbleSent = SkyBlue

val ErrorRed = Color(0xFFEF4444)
val OnlineGreen = Color(0xFF22C55E)

// --- Variantes clair --- (voir Theme.kt : réutilisées telles quelles pour le ColorScheme Material)
internal val LightSkyBlueSurfaceTint = Color(0xFFE0F2FE)
internal val LightAppBackground = Color(0xFFF8FBFF) // presque blanc, légèrement teinté de bleu
internal val LightSurfaceWhite = Color(0xFFFFFFFF)
internal val LightOnBackground = Color(0xFF0F172A)
internal val LightOnSurfaceVariant = Color(0xFF64748B)
internal val LightBubbleReceived = Color(0xFFEFF6FF)
internal val LightTextOnBubbleReceived = Color(0xFF0F172A)
internal val LightOfflineGray = Color(0xFF94A3B8)

// --- Variantes sombre ---
internal val DarkSkyBlueSurfaceTint = Color(0xFF1B2F42)
internal val DarkAppBackground = Color(0xFF0B1220)
internal val DarkSurfaceWhite = Color(0xFF111C2E) // "surface" côté sombre : cartes, barres, champs
internal val DarkOnBackground = Color(0xFFE2E8F0)
internal val DarkOnSurfaceVariant = Color(0xFF94A3B8)
internal val DarkBubbleReceived = Color(0xFF1E2A3D)
internal val DarkTextOnBubbleReceived = Color(0xFFE2E8F0)
internal val DarkOfflineGray = Color(0xFF64748B)

// Propriétés composables : mêmes noms qu'avant l'introduction du thème
// sombre, donc aucun site d'appel existant n'a eu besoin d'être modifié.
val SkyBlueSurfaceTint: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkSkyBlueSurfaceTint else LightSkyBlueSurfaceTint

val AppBackground: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkAppBackground else LightAppBackground

val SurfaceWhite: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkSurfaceWhite else LightSurfaceWhite

val BubbleReceived: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkBubbleReceived else LightBubbleReceived

val TextOnBubbleReceived: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkTextOnBubbleReceived else LightTextOnBubbleReceived

val OfflineGray: Color
    @Composable get() = if (LocalWakhDarkTheme.current) DarkOfflineGray else LightOfflineGray
