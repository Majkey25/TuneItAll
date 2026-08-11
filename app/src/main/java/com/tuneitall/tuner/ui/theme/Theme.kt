package com.tuneitall.tuner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TuneItAllColors = darkColorScheme(
    primary = Color(0xFFF7F7F4),
    onPrimary = Color(0xFF090A09),
    secondaryContainer = Color(0xFFF7F7F4),
    onSecondaryContainer = Color(0xFF090A09),
    tertiary = Color(0xFF52D273),
    onTertiary = Color(0xFF041008),
    background = Color(0xFF090A09),
    onBackground = Color(0xFFF7F7F4),
    surface = Color(0xFF111211),
    onSurface = Color(0xFFF7F7F4),
    surfaceVariant = Color(0xFF1C1E1C),
    onSurfaceVariant = Color(0xFFB8BCB8),
)

private val TuneItAllLightColors = lightColorScheme(
    primary = Color(0xFF111211),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFF111211),
    onSecondaryContainer = Color(0xFFFAFAF8),
    tertiary = Color(0xFF16753A),
    onTertiary = Color.White,
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF111211),
    surface = Color.White,
    onSurface = Color(0xFF111211),
    surfaceVariant = Color(0xFFEDEEEB),
    onSurfaceVariant = Color(0xFF4D514D),
)

@Composable
fun TuneItAllTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TuneItAllColors else TuneItAllLightColors,
        content = content,
    )
}
