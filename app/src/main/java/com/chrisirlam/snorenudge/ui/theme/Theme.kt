package com.chrisirlam.snorenudge.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-first colour palette suitable for night-time use
val SnorePrimary = Color(0xFF4FC3F7)        // light blue
val SnoreOnPrimary = Color(0xFF003C4D)
val SnorePrimaryContainer = Color(0xFF004F63)
val SnoreSecondary = Color(0xFF80DEEA)
val SnoreBackground = Color(0xFF0A0A0A)     // near-black
val SnoreSurface = Color(0xFF121212)
val SnoreSurfaceVariant = Color(0xFF1E1E1E)
val SnoreOnSurface = Color(0xFFE0E0E0)
val SnoreOnSurfaceVariant = Color(0xFFB0B0B0)
val SnoreError = Color(0xFFCF6679)
val SnoreSuccess = Color(0xFF66BB6A)
val SnoreWarning = Color(0xFFFFB74D)

private val DarkColorScheme = darkColorScheme(
    primary = SnorePrimary,
    onPrimary = SnoreOnPrimary,
    primaryContainer = SnorePrimaryContainer,
    secondary = SnoreSecondary,
    background = SnoreBackground,
    surface = SnoreSurface,
    surfaceVariant = SnoreSurfaceVariant,
    onSurface = SnoreOnSurface,
    onSurfaceVariant = SnoreOnSurfaceVariant,
    error = SnoreError
)

@Composable
fun SnoreNudgeTheme(content: @Composable () -> Unit) {
    // Always dark — this is a night-time app
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
