package com.tuneitall.tuner.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val DarkBackground = Color(0xFF101010)
private val DarkForeground = Color(0xFFF4F1EA)
private val LightBackground = Color(0xFFFAF9F6)
private val LightForeground = Color(0xFF111111)
private val AccentGreen = Color(0xFF63D17A)
private val LightAccentGreen = Color(0xFF166534)

internal val TuneItAllDarkColors = darkColorScheme(
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

internal val TuneItAllLightColors = lightColorScheme(
    primary = LightAccentGreen,
    onPrimary = LightBackground,
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
    tertiary = LightAccentGreen,
    onTertiary = LightBackground,
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
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = remember(context, darkTheme, appearance) { appearanceColorScheme(appearance, darkTheme, context) }
    val shapes = when (appearance.layout) {
        ThemeLayout.CLASSIC -> TuneItAllShapes
        ThemeLayout.MATERIAL -> Shapes()
        ThemeLayout.CUSTOM -> RoundedCornerShape(appearance.cornerRadius.dp).let { Shapes(it, it, it, it, it) }
    }
    val typography = remember(appearance.font) { appearanceTypography(appearance.font) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textScale / 100f)) {
        MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
    }
}

fun appearanceColorScheme(settings: AppearanceSettings, dark: Boolean, context: Context? = null): ColorScheme {
    val base = if (dark) TuneItAllDarkColors else TuneItAllLightColors
    val palette = settings.palette(dark)
    val source = if (palette == ThemePalette.MATERIAL_YOU && context != null && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (palette == ThemePalette.INTONIVA) base else {
        val colors = settings.colors(dark)
        val background = colors.background.asColor()
        val surface = colors.surface.asColor()
        val accent = readableAccent(colors.accent, colors.background).asColor()
        val container = lerp(surface, accent, if (dark) 0.28f else 0.16f)
        val onContainer = readableColor(colors.text, container.rgb()).asColor()
        val onAccent = readableColor(colors.text, accent.rgb()).asColor()
        val text = readableColor(colors.text, colors.background).asColor()
        val surfaceText = readableColor(colors.text, colors.surface).asColor()
        val muted = readableColor(colors.muted, colors.surface).asColor()
        val outline = readableColor(colors.outline, colors.background, 3.0).asColor()
        base.copy(
            primary = accent, onPrimary = onAccent, primaryContainer = container, onPrimaryContainer = onContainer,
            secondary = accent, onSecondary = onAccent, secondaryContainer = container, onSecondaryContainer = onContainer,
            tertiary = accent, onTertiary = onAccent, tertiaryContainer = container, onTertiaryContainer = onContainer,
            primaryFixed = container, primaryFixedDim = container, onPrimaryFixed = onContainer, onPrimaryFixedVariant = onContainer,
            secondaryFixed = container, secondaryFixedDim = container, onSecondaryFixed = onContainer, onSecondaryFixedVariant = onContainer,
            tertiaryFixed = container, tertiaryFixedDim = container, onTertiaryFixed = onContainer, onTertiaryFixedVariant = onContainer,
            background = background, onBackground = text, surface = surface, onSurface = surfaceText,
            surfaceVariant = surface, onSurfaceVariant = muted, surfaceTint = accent,
            inverseSurface = text, inverseOnSurface = background, inversePrimary = container,
            outline = outline, outlineVariant = outline, scrim = Color.Black,
            surfaceBright = surface, surfaceContainer = surface, surfaceContainerHigh = surface,
            surfaceContainerHighest = surface, surfaceContainerLow = background, surfaceContainerLowest = background,
            surfaceDim = background,
        )
    }
    if (!settings.highContrast && !(settings.blackBackground && dark)) return ensureReadableColors(source)
    val background = when {
        settings.highContrast -> if (dark) Color.Black else Color.White
        else -> Color.Black
    }
    val foreground = if (dark) Color.White else Color.Black
    val surface = if (settings.highContrast) background else source.surface
    return ensureReadableColors(source.copy(
        background = background, onBackground = foreground, surface = surface,
        onSurface = readableColor(foreground.rgb(), surface.rgb()).asColor(),
        onSurfaceVariant = readableColor(foreground.rgb(), surface.rgb()).asColor(),
        outline = foreground, outlineVariant = foreground,
        surfaceContainerLowest = background, surfaceDim = background,
        surfaceBright = surface, surfaceVariant = surface, surfaceContainer = surface,
        surfaceContainerLow = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
    ))
}

private fun ensureReadableColors(source: ColorScheme): ColorScheme {
    val primary = readableAccent(source.primary.rgb(), source.background.rgb()).asColor()
    return source.copy(
        primary = primary,
        onPrimary = readableColor(source.onPrimary.rgb(), primary.rgb()).asColor(),
        onBackground = readableColor(source.onBackground.rgb(), source.background.rgb()).asColor(),
        onSurface = readableColor(source.onSurface.rgb(), source.surface.rgb()).asColor(),
        onSurfaceVariant = readableColor(source.onSurfaceVariant.rgb(), source.surface.rgb()).asColor(),
        error = readableColor(source.error.rgb(), source.surface.rgb()).asColor(),
        outline = readableColor(source.outline.rgb(), source.background.rgb(), 3.0).asColor(),
    )
}

private fun appearanceTypography(font: ThemeFont): Typography {
    val base = Typography()
    if (font == ThemeFont.SYSTEM) return base
    val family = if (font == ThemeFont.SERIF) FontFamily.Serif else FontFamily.Monospace
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family), displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family), headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family), headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family), titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family), bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family), bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family), labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

internal fun Int.asColor(): Color = Color(0xFF000000L or toLong())
internal fun Color.rgb(): Int = toArgb() and 0xFFFFFF

@Composable
fun inTuneColor(): Color {
    val background = MaterialTheme.colorScheme.background.rgb()
    return inTuneRgb(background).asColor()
}

@Composable
fun mutedBackgroundColor(): Color = readableColor(MaterialTheme.colorScheme.onSurfaceVariant.rgb(),
    MaterialTheme.colorScheme.background.rgb()).asColor()
