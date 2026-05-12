package com.grameenlight.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF236C3A),
    onPrimary = Color.White,
    secondary = Color(0xFF8A6A1D),
    tertiary = Color(0xFF146C84),
    background = Color(0xFFF6F8F4),
    onBackground = Color(0xFF17211B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211B),
    surfaceVariant = Color(0xFFE4ECE2),
    onSurfaceVariant = Color(0xFF445247),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD8A5),
    onPrimary = Color(0xFF073915),
    secondary = Color(0xFFF0CE71),
    tertiary = Color(0xFF8ED3E6),
    background = Color(0xFF09110E),
    onBackground = Color(0xFFE7F0E8),
    surface = Color(0xFF121C18),
    onSurface = Color(0xFFE7F0E8),
    surfaceVariant = Color(0xFF23302A),
    onSurfaceVariant = Color(0xFFC2CEC4),
    error = Color(0xFFFFB4AB)
)

@Composable
fun GrameenLightTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
