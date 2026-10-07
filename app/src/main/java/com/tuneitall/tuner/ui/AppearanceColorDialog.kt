package com.tuneitall.tuner.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.ui.theme.asColor
import com.tuneitall.tuner.ui.theme.parseHexColor
import java.util.Locale

@Composable
internal fun AppearanceColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(0xFF000000.toInt() or initial, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var hex by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%06X", initial)) }
    val parsed = parseHexColor(hex)
    fun updateHex() {
        hex = String.format(Locale.ROOT, "%06X", AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(52.dp).background((parsed ?: initial).asColor(), MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small))
                OutlinedTextField(
                    value = hex, onValueChange = { input ->
                        hex = input.removePrefix("#").take(7).uppercase(Locale.ROOT)
                        parseHexColor(hex)?.let { value ->
                            val hsv = FloatArray(3)
                            AndroidColor.colorToHSV(0xFF000000.toInt() or value, hsv)
                            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                        }
                    },
                    label = { Text(stringResource(R.string.appearance_hex)) }, prefix = { Text("#") },
                    singleLine = true, isError = parsed == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    supportingText = { if (parsed == null) Text(stringResource(R.string.appearance_hex_error)) },
                    modifier = Modifier.fillMaxWidth().testTag("appearance_color_hex"),
                )
                ColorSlider(R.string.appearance_hue, hue, 0f..360f) { hue = it; updateHex() }
                ColorSlider(R.string.appearance_saturation, saturation, 0f..1f) { saturation = it; updateHex() }
                ColorSlider(R.string.appearance_brightness, brightness, 0f..1f) { brightness = it; updateHex() }
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.testTag("appearance_color_apply")) {
            Text(stringResource(R.string.apply))
        } },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Text(stringResource(android.R.string.cancel))
        } },
    )
}

@Composable
private fun ColorSlider(labelId: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val label = stringResource(labelId)
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
}
