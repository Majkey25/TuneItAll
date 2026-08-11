package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.storage.NoteNotation
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: TunerUiState,
    onReferencePitchChanged: (ReferencePitch) -> Unit,
    onNotationChanged: (NoteNotation) -> Unit,
    onLayoutChanged: (HeadstockLayout) -> Unit,
    onSensitivityChanged: (DetectionSensitivity) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf(formatPitch(state.referencePitch.hertz)) }
    var extremeConfirmed by remember { mutableStateOf(false) }
    val parsed = input.replace(',', '.').toDoubleOrNull()
    val inAllowedRange = parsed != null && parsed.isFinite() && parsed in ReferencePitch.MIN_HERTZ..ReferencePitch.MAX_HERTZ
    val extreme = inAllowedRange && parsed !in NORMAL_REFERENCE_MIN..NORMAL_REFERENCE_MAX

    LaunchedEffect(state.referencePitch) {
        input = formatPitch(state.referencePitch.hertz)
        extremeConfirmed = false
    }

    fun updateInput(hertz: Double) {
        input = formatPitch(hertz.coerceIn(ReferencePitch.MIN_HERTZ, ReferencePitch.MAX_HERTZ))
        extremeConfirmed = false
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SecondaryHeader(stringResource(R.string.settings), onBack)
        Text(stringResource(R.string.reference_pitch), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.reference_pitch_help), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { updateInput((parsed ?: state.referencePitch.hertz) - 0.1) },
                modifier = Modifier.weight(1f),
            ) { Text("−0.1") }
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.take(MAX_PITCH_INPUT_LENGTH)
                    extremeConfirmed = false
                },
                label = { Text("Hz") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = input.isNotBlank() && !inAllowedRange,
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedButton(
                onClick = { updateInput((parsed ?: state.referencePitch.hertz) + 0.1) },
                modifier = Modifier.weight(1f),
            ) { Text("+0.1") }
        }
        Slider(
            value = (parsed?.takeIf { inAllowedRange } ?: state.referencePitch.hertz).toFloat(),
            onValueChange = { updateInput(round(it * 10.0) / 10.0) },
            valueRange = ReferencePitch.MIN_HERTZ.toFloat()..ReferencePitch.MAX_HERTZ.toFloat(),
            steps = REFERENCE_SLIDER_STEPS,
        )
        if (input.isNotBlank() && !inAllowedRange) {
            Text(
                text = stringResource(
                    R.string.reference_pitch_range_error,
                    ReferencePitch.MIN_HERTZ,
                    ReferencePitch.MAX_HERTZ,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (extreme) {
            Text(
                text = stringResource(R.string.reference_pitch_caution),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onReferencePitchChanged(ReferencePitch(ReferencePitch.DEFAULT_HERTZ)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.reset_440))
            }
            Button(
                enabled = inAllowedRange,
                onClick = {
                    val hertz = requireNotNull(parsed)
                    if (extreme && !extremeConfirmed) {
                        extremeConfirmed = true
                    } else {
                        onReferencePitchChanged(ReferencePitch(hertz))
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (extreme && !extremeConfirmed) {
                        stringResource(R.string.review_value)
                    } else if (extreme) {
                        stringResource(R.string.confirm_value)
                    } else {
                        stringResource(R.string.apply)
                    },
                )
            }
        }

        val sensitivityLabel = stringResource(R.string.microphone_sensitivity)
        Text(sensitivityLabel, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.microphone_sensitivity_help), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sensitivity_value, state.sensitivity.value))
        Slider(
            value = state.sensitivity.value.toFloat(),
            onValueChange = { onSensitivityChanged(DetectionSensitivity(it.roundToInt())) },
            valueRange = DetectionSensitivity.MIN_VALUE.toFloat()..DetectionSensitivity.MAX_VALUE.toFloat(),
            steps = SENSITIVITY_SLIDER_STEPS,
            modifier = Modifier.semantics { contentDescription = sensitivityLabel },
        )
        OutlinedButton(
            onClick = { onSensitivityChanged(DetectionSensitivity.DEFAULT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reset_sensitivity))
        }

        Text(stringResource(R.string.note_notation), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NoteNotation.entries.forEach { notation ->
                FilterChip(
                    selected = state.notation == notation,
                    onClick = { onNotationChanged(notation) },
                    label = { Text(if (notation == NoteNotation.SHARPS) "♯" else "♭") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(stringResource(R.string.headstock_layout), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            state.tuning.layouts.forEach { layout ->
                FilterChip(
                    selected = state.headstockLayout == layout,
                    onClick = { onLayoutChanged(layout) },
                    label = { Text(layoutName(layout)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about))
        }
    }
}

private fun formatPitch(hertz: Double): String = String.format(Locale.US, "%.1f", hertz)

private const val NORMAL_REFERENCE_MIN = 430.0
private const val NORMAL_REFERENCE_MAX = 450.0
private const val REFERENCE_SLIDER_STEPS = 699
private const val MAX_PITCH_INPUT_LENGTH = 6
private const val SENSITIVITY_SLIDER_STEPS = 99
