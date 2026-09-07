package com.wakh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val WakhLightColorScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = LightSkyBlueSurfaceTint,
    onPrimaryContainer = SkyBlueDark,
    secondary = SkyBlueDark,
    onSecondary = Color.White,
    background = LightAppBackground,
    onBackground = LightOnBackground,
    surface = LightSurfaceWhite,
    onSurface = LightOnBackground,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
    outline = LightSkyBlueSurfaceTint,
)

private val WakhDarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = DarkSkyBlueSurfaceTint,
    onPrimaryContainer = SkyBlueLight,
    secondary = SkyBlueLight,
    onSecondary = DarkAppBackground,
    background = DarkAppBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurfaceWhite,
    onSurface = DarkOnBackground,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ErrorRed,
    onError = Color.White,
    outline = DarkSkyBlueSurfaceTint,
)

/**
 * [darkTheme] pilote à la fois le [MaterialTheme.colorScheme] standard ET
 * les propriétés personnalisées de Color.kt (`SurfaceWhite`,
 * `AppBackground`, `BubbleReceived`...) via [LocalWakhDarkTheme] — les
 * deux mécanismes restent synchronisés puisqu'ils partagent les mêmes
 * valeurs sources (voir les `internal val Light*`/`Dark*` de Color.kt).
 */
@Composable
fun WakhTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWakhDarkTheme  provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) WakhDarkColorScheme else WakhLightColorScheme,
            typography = WakhTypography,
            content = content,
        )
    }
}
