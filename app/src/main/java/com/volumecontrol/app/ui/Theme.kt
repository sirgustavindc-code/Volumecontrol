package com.volumecontrol.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0B1330),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF15181D),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF1D2128),
    onSurfaceVariant = Color(0xFF9AA3AE),
    outline = Color(0xFF3A414B),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5FD0),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF14171B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171B),
    surfaceVariant = Color(0xFFE9ECF1),
    onSurfaceVariant = Color(0xFF5B6470),
    outline = Color(0xFFB6BDC7),
    error = Color(0xFFB3261E),
)

@Composable
fun VolumeControlTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
