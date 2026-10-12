package com.tuneitall.tuner.ui

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tuneitall.tuner.ui.theme.asColor
import com.tuneitall.tuner.ui.theme.colorContrast
import com.tuneitall.tuner.ui.theme.readableAccent
import com.tuneitall.tuner.ui.theme.readableColor
import com.tuneitall.tuner.ui.theme.rgb

@Composable
internal fun currentPlaneChipColors(): SelectableChipColors {
    val colors = MaterialTheme.colorScheme
    val label = colors.onSurfaceVariant.takeIf { colorContrast(it.rgb(), colors.background.rgb()) >= 4.5 }
        ?: LocalContentColor.current
    return FilterChipDefaults.filterChipColors(labelColor = label)
}

@Composable
internal fun dialogTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = readableAccent(MaterialTheme.colorScheme.primary.rgb(), MaterialTheme.colorScheme.surfaceContainerHigh.rgb()).asColor(),
)

@Composable
internal fun outlinedButtonColors(plane: Color): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = readableAccent(MaterialTheme.colorScheme.primary.rgb(), plane.rgb()).asColor(),
)

@Composable
internal fun dialogRadioButtonColors(): RadioButtonColors = RadioButtonDefaults.colors(
    selectedColor = controlAccent(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceContainerHigh),
)

private fun controlAccent(preferred: Color, plane: Color): Color =
    if (colorContrast(preferred.rgb(), plane.rgb()) >= 3.0) preferred else readableAccent(preferred.rgb(), plane.rgb()).asColor()

@Composable
internal fun sliderColors(plane: Color): SliderColors {
    val colors = MaterialTheme.colorScheme
    val accent = controlAccent(colors.primary, plane)
    return SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent,
        activeTickColor = readableColor(colors.onPrimary.rgb(), accent.rgb(), 3.0).asColor())
}

@Composable
internal fun switchColors(plane: Color): SwitchColors {
    val colors = MaterialTheme.colorScheme
    val accent = controlAccent(colors.primary, plane)
    return SwitchDefaults.colors(checkedTrackColor = accent,
        checkedThumbColor = readableColor(colors.onPrimary.rgb(), accent.rgb(), 3.0).asColor(),
        uncheckedThumbColor = readableColor(colors.outline.rgb(), colors.surfaceContainerHighest.rgb(), 3.0).asColor(),
        uncheckedBorderColor = readableColor(colors.outline.rgb(), plane.rgb(), 3.0).asColor())
}

@Composable
internal fun outlinedFieldColors(
    plane: Color = MaterialTheme.colorScheme.background,
    foreground: Color = MaterialTheme.colorScheme.onBackground,
): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    val text = readableColor(foreground.rgb(), plane.rgb()).asColor()
    val muted = readableColor(colors.onSurfaceVariant.rgb(), plane.rgb()).asColor()
    val accent = readableAccent(colors.primary.rgb(), plane.rgb()).asColor()
    val error = readableColor(colors.error.rgb(), plane.rgb()).asColor()
    val selection = LocalTextSelectionColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = text, unfocusedTextColor = text, errorTextColor = text,
        cursorColor = accent, errorCursorColor = error,
        selectionColors = TextSelectionColors(controlAccent(selection.handleColor, plane), selection.backgroundColor),
        focusedBorderColor = accent, unfocusedBorderColor = readableColor(colors.outline.rgb(), plane.rgb(), 3.0).asColor(),
        errorBorderColor = error,
        focusedLabelColor = accent, unfocusedLabelColor = muted, errorLabelColor = error,
        focusedSupportingTextColor = muted, unfocusedSupportingTextColor = muted, errorSupportingTextColor = error,
        focusedPrefixColor = muted, unfocusedPrefixColor = muted, errorPrefixColor = muted,
        focusedSuffixColor = muted, unfocusedSuffixColor = muted, errorSuffixColor = muted,
    )
}
