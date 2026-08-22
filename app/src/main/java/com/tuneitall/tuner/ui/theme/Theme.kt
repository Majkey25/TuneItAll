package com.tuneitall.tuner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkBackground = Color(0xFF101010)
private val DarkForeground = Color(0xFFF4F1EA)
private val LightBackground = Color(0xFFFAF9F6)
private val LightForeground = Color(0xFF111111)
private val AccentGreen = Color(0xFF63D17A)

private val TuneItAllDarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = DarkBackground,
    primaryContainer = AccentGreen,
    onPrimaryContainer = DarkBackground,
    inversePrimary = AccentGreen,
    primaryFixed = AccentGreen,
    primaryFixedDim = AccentGreen,
    onPrimaryFixed = DarkBackground,
    onPrimaryFixedVariant = DarkBackground,
    secondary = DarkForeground,
    onSecondary = DarkBackground,
    secondaryContainer = AccentGreen,
    onSecondaryContainer = DarkBackground,
    secondaryFixed = AccentGreen,
    secondaryFixedDim = AccentGreen,
    onSecondaryFixed = DarkBackground,
    onSecondaryFixedVariant = DarkBackground,
    tertiary = AccentGreen,
    onTertiary = DarkBackground,
    tertiaryContainer = AccentGreen,
    onTertiaryContainer = DarkBackground,
    tertiaryFixed = AccentGreen,
    tertiaryFixedDim = AccentGreen,
    onTertiaryFixed = DarkBackground,
    onTertiaryFixedVariant = DarkBackground,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkBackground,
    onSurface = DarkForeground,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = DarkForeground,
    surfaceTint = AccentGreen,
    inverseSurface = DarkForeground,
    inverseOnSurface = DarkBackground,
    outline = DarkForeground,
    outlineVariant = DarkForeground,
    scrim = DarkBackground,
    surfaceBright = DarkBackground,
    surfaceContainer = DarkBackground,
    surfaceContainerHigh = DarkBackground,
    surfaceContainerHighest = DarkBackground,
    surfaceContainerLow = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceDim = DarkBackground,
)

private val TuneItAllLightColors = lightColorScheme(
    primary = AccentGreen,
    onPrimary = LightForeground,
    primaryContainer = AccentGreen,
    onPrimaryContainer = LightForeground,
    inversePrimary = AccentGreen,
    primaryFixed = AccentGreen,
    primaryFixedDim = AccentGreen,
    onPrimaryFixed = LightForeground,
    onPrimaryFixedVariant = LightForeground,
    secondary = LightForeground,
    onSecondary = LightBackground,
    secondaryContainer = AccentGreen,
    onSecondaryContainer = LightForeground,
    secondaryFixed = AccentGreen,
    secondaryFixedDim = AccentGreen,
    onSecondaryFixed = LightForeground,
    onSecondaryFixedVariant = LightForeground,
    tertiary = AccentGreen,
    onTertiary = LightForeground,
    tertiaryContainer = AccentGreen,
    onTertiaryContainer = LightForeground,
    tertiaryFixed = AccentGreen,
    tertiaryFixedDim = AccentGreen,
    onTertiaryFixed = LightForeground,
    onTertiaryFixedVariant = LightForeground,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightBackground,
    onSurface = LightForeground,
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightForeground,
    surfaceTint = AccentGreen,
    inverseSurface = LightForeground,
    inverseOnSurface = LightBackground,
    outline = LightForeground,
    outlineVariant = LightForeground,
    scrim = LightForeground,
    surfaceBright = LightBackground,
    surfaceContainer = LightBackground,
    surfaceContainerHigh = LightBackground,
    surfaceContainerHighest = LightBackground,
    surfaceContainerLow = LightBackground,
    surfaceContainerLowest = LightBackground,
    surfaceDim = LightBackground,
)

private val TuneItAllShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun TuneItAllTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TuneItAllDarkColors else TuneItAllLightColors,
        shapes = TuneItAllShapes,
        content = content,
    )
}
