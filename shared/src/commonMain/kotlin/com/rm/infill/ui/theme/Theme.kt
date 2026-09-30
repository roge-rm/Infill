package com.rm.infill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalInfillColors = staticCompositionLocalOf { DarkColors }

object Infill {
    val colors: InfillColors
        @Composable @ReadOnlyComposable get() = LocalInfillColors.current
}

/** Follows the device's light or dark setting. Material's scheme is built from the palette. */
@Composable
fun InfillTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.page, onBackground = c.text,
            surface = c.chrome, onSurface = c.text, onSurfaceVariant = c.textDim,
            outline = c.chromeEdge,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.page, onBackground = c.text,
            surface = c.chrome, onSurface = c.text, onSurfaceVariant = c.textDim,
            outline = c.chromeEdge,
        )
    }
    CompositionLocalProvider(LocalInfillColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
