package com.kareem.awarex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8E34A),
    onPrimary = Color(0xFF172000),
    secondary = Color(0xFF9BAA74),
    background = Color(0xFF090A0B),
    onBackground = Color(0xFFF3F4EF),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFF3F4EF),
    surfaceVariant = Color(0xFF181B1D),
    onSurfaceVariant = Color(0xFFB7BAB2),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF526800),
    onPrimary = Color.White,
    secondary = Color(0xFF5E664B),
    background = Color(0xFFF8F9F3),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE8EBD9),
    onSurfaceVariant = Color(0xFF45483D),
    error = Color(0xFFBA1A1A)
)

@Composable
fun AwareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
