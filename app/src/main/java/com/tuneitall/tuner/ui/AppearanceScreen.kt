package com.tuneitall.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemeColors
import com.tuneitall.tuner.ui.theme.ThemeFont
import com.tuneitall.tuner.ui.theme.ThemeLayout
import com.tuneitall.tuner.ui.theme.ThemeMode
import com.tuneitall.tuner.ui.theme.ThemePalette
import com.tuneitall.tuner.ui.theme.appearanceColorScheme
import com.tuneitall.tuner.ui.theme.asColor
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(
    appearance: AppearanceSettings,
    themeMode: ThemeMode,
    onAppearanceChanged: (AppearanceSettings) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    var editingDark by rememberSaveable { mutableStateOf(themeMode == ThemeMode.DARK) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("appearance_screen"),
    ) {
        SecondaryHeader(stringResource(R.string.appearance), onBack)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)
            .verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == themeMode, onClick = { onThemeModeChanged(mode) },
                    label = { Text(themeModeName(mode)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("appearance_mode_${mode.name}"),
                )
            }
        }
        Text(stringResource(R.string.appearance_themes), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.appearance_themes_help), style = MaterialTheme.typography.bodyMedium)
        BoxWithConstraints {
            val columns = if (maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePalette.entries.chunked(columns).forEach { palettes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        palettes.forEach { palette ->
                            ThemeCard(palette, appearance, Modifier.weight(1f)) { dark ->
                                editingDark = dark
                                showCustom = palette == ThemePalette.CUSTOM
                                onAppearanceChanged(if (dark) appearance.copy(darkTheme = palette) else appearance.copy(lightTheme = palette))
                            }
                        }
                        if (palettes.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text(stringResource(R.string.appearance_material_help), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { showCustom = !showCustom },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("appearance_edit_custom"),
        ) { Text(stringResource(R.string.appearance_custom_colors)) }
        if (showCustom) {
            CustomColorsEditor(
                colors = if (editingDark) appearance.customDark else appearance.customLight,
                dark = editingDark,
                onDarkChanged = { editingDark = it },
                onColorsChanged = { colors ->
                    onAppearanceChanged(if (editingDark) appearance.copy(customDark = colors, darkTheme = ThemePalette.CUSTOM)
                    else appearance.copy(customLight = colors, lightTheme = ThemePalette.CUSTOM))
                },
            )
        }
        Text(stringResource(R.string.appearance_layout), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeLayout.entries.forEach { layout ->
                FilterChip(
                    selected = appearance.layout == layout, onClick = { onAppearanceChanged(appearance.copy(layout = layout)) },
                    label = { Text(stringResource(when (layout) {
                        ThemeLayout.CLASSIC -> R.string.appearance_classic
                        ThemeLayout.MATERIAL -> R.string.appearance_material
                        ThemeLayout.CUSTOM -> R.string.profile_custom
                    })) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("appearance_layout_${layout.name}"),
                )
            }
        }
        if (appearance.layout == ThemeLayout.CUSTOM) {
            AppearanceSlider(stringResource(R.string.appearance_corners), appearance.cornerRadius, 0..28, "appearance_corners") {
                onAppearanceChanged(appearance.copy(cornerRadius = it))
            }
        }
        Text(stringResource(R.string.appearance_text), style = MaterialTheme.typography.titleLarge)
        AppearanceSlider(stringResource(R.string.appearance_text_size), appearance.textScale, 85..130, "appearance_text_scale", "%") {
            onAppearanceChanged(appearance.copy(textScale = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeFont.entries.forEach { font ->
                FilterChip(
                    selected = appearance.font == font, onClick = { onAppearanceChanged(appearance.copy(font = font)) },
                    label = { Text(stringResource(when (font) {
                        ThemeFont.SYSTEM -> R.string.theme_system
                        ThemeFont.SERIF -> R.string.appearance_serif
                        ThemeFont.MONOSPACE -> R.string.appearance_monospace
                    })) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("appearance_font_${font.name}"),
                )
            }
        }
        AppearanceSwitch(stringResource(R.string.appearance_contrast), appearance.highContrast, "appearance_contrast") {
            onAppearanceChanged(appearance.copy(highContrast = it))
        }
        AppearanceSwitch(stringResource(R.string.appearance_black), appearance.blackBackground, "appearance_black") {
            onAppearanceChanged(appearance.copy(blackBackground = it))
        }
        OutlinedButton(
            onClick = { onAppearanceChanged(AppearanceSettings()); onThemeModeChanged(ThemeMode.SYSTEM) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("appearance_reset"),
        ) { Text(stringResource(R.string.appearance_reset)) }
        }
    }
}

@Composable
private fun ThemeCard(palette: ThemePalette, appearance: AppearanceSettings, modifier: Modifier, onSelect: (Boolean) -> Unit) {
    val context = LocalContext.current
    val name = themePaletteName(palette)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(10.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(false, true).forEach { dark ->
                val colors = appearanceColorScheme(appearance.copy(lightTheme = palette, darkTheme = palette), dark, context)
                val selected = appearance.palette(dark) == palette
                val modeName = stringResource(if (dark) R.string.theme_dark else R.string.theme_light)
                val description = stringResource(R.string.appearance_theme_choice, name, modeName)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = { onSelect(dark) })
                        .semantics { contentDescription = description }.testTag("appearance_theme_${palette.name}_${if (dark) "dark" else "light"}")
                        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.small).padding(6.dp),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(52.dp)) {
                        drawRect(colors.background)
                        drawRect(colors.surface, Offset(0f, 0f), Size(size.width, size.height * .22f))
                        drawLine(colors.onSurface, Offset(size.width * .15f, size.height * .12f), Offset(size.width * .65f, size.height * .12f), 2.dp.toPx())
                        drawLine(colors.outline, Offset(size.width * .12f, size.height * .48f), Offset(size.width * .88f, size.height * .48f), 1.dp.toPx())
                        drawCircle(colors.primary, 4.dp.toPx(), Offset(size.width * .5f, size.height * .48f))
                        drawRect(colors.primaryContainer, Offset(size.width * .15f, size.height * .7f), Size(size.width * .7f, size.height * .17f))
                    }
                    Text(modeName, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun themePaletteName(palette: ThemePalette): String = stringResource(when (palette) {
    ThemePalette.INTONIVA -> R.string.app_name
    ThemePalette.MATERIAL_YOU -> R.string.appearance_material_you
    ThemePalette.OCEAN -> R.string.appearance_ocean
    ThemePalette.EMBER -> R.string.appearance_ember
    ThemePalette.IRIS -> R.string.appearance_iris
    ThemePalette.SLATE -> R.string.appearance_slate
    ThemePalette.PAPER -> R.string.appearance_paper
    ThemePalette.CUSTOM -> R.string.profile_custom
})

@Composable
internal fun themeModeName(mode: ThemeMode): String = stringResource(when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
})

@Composable
private fun AppearanceSlider(label: String, value: Int, range: IntRange, tag: String, suffix: String = "", onChange: (Int) -> Unit) {
    Text("$label: $value$suffix", style = MaterialTheme.typography.titleMedium)
    Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1, modifier = Modifier.testTag(tag).semantics { contentDescription = label })
}

@Composable
private fun AppearanceSwitch(label: String, value: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value, role = Role.Switch, onValueChange = onChange).testTag(tag),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(value, onCheckedChange = null)
    }
}

private enum class ColorRole(val label: Int) {
    ACCENT(R.string.appearance_accent), BACKGROUND(R.string.appearance_background), SURFACE(R.string.appearance_surface),
    TEXT(R.string.appearance_foreground), MUTED(R.string.appearance_muted), OUTLINE(R.string.appearance_outline);

    fun color(colors: ThemeColors): Int = when (this) {
        ACCENT -> colors.accent; BACKGROUND -> colors.background; SURFACE -> colors.surface
        TEXT -> colors.text; MUTED -> colors.muted; OUTLINE -> colors.outline
    }
    fun replace(colors: ThemeColors, rgb: Int): ThemeColors = when (this) {
        ACCENT -> colors.copy(accent = rgb); BACKGROUND -> colors.copy(background = rgb); SURFACE -> colors.copy(surface = rgb)
        TEXT -> colors.copy(text = rgb); MUTED -> colors.copy(muted = rgb); OUTLINE -> colors.copy(outline = rgb)
    }
}

@Composable
private fun CustomColorsEditor(colors: ThemeColors, dark: Boolean, onDarkChanged: (Boolean) -> Unit, onColorsChanged: (ThemeColors) -> Unit) {
    var editingRole by rememberSaveable { mutableStateOf<ColorRole?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(false, true).forEach { candidate ->
            FilterChip(selected = dark == candidate, onClick = { onDarkChanged(candidate) },
                label = { Text(stringResource(if (candidate) R.string.appearance_custom_dark else R.string.appearance_custom_light)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("custom_palette_${if (candidate) "dark" else "light"}"))
        }
    }
    Text(stringResource(R.string.appearance_custom_help), style = MaterialTheme.typography.bodySmall)
    ColorRole.entries.forEach { role ->
        OutlinedButton(onClick = { editingRole = role }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("custom_color_${role.name}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(28.dp).background(role.color(colors).asColor(), MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(role.label), style = MaterialTheme.typography.bodyLarge)
                    Text(String.format(Locale.ROOT, "#%06X", role.color(colors)), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    editingRole?.let { role ->
        AppearanceColorDialog(stringResource(role.label), role.color(colors), onDismiss = { editingRole = null }) {
            onColorsChanged(role.replace(colors, it))
            editingRole = null
        }
    }
}
