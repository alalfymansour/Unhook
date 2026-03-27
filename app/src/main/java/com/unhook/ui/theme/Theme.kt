package com.unhook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UnhookDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2D7CFF),
    onPrimary = Color(0xFFEAF2FF),
    secondary = Color(0xFF6BA6FF),
    onSecondary = Color(0xFF001533),
    tertiary = Color(0xFF5FC7FF),
    onTertiary = Color(0xFF001823),
    background = Color(0xFF060B16),
    onBackground = Color(0xFFE8EEFF),
    surface = Color(0xFF0E1628),
    onSurface = Color(0xFFE8EEFF),
    surfaceVariant = Color(0xFF1A2742),
    onSurfaceVariant = Color(0xFFBECCE9),
)

@Composable
fun UnhookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UnhookDarkColorScheme,
        content = content,
    )
}
