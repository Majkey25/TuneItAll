package com.tuneitall.tuner.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.metronome.MetronomeSound
import kotlin.math.roundToInt

@Composable
fun MetronomeSettingsControls(
    state: MetronomeUiState,
    onNumeratorChange: (Int) -> Unit,
    onDenominatorChange: (Int) -> Unit,
    onSubdivisionChange: (Int) -> Unit,
    onAccentEveryChange: (Int?) -> Unit,
    onSoundChange: (MetronomeSound) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onCountInChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableFloatStateOf(state.settings.volume.toFloat()) }
    LaunchedEffect(state.settings.volume) { volume = state.settings.volume.toFloat() }
    val volumeDescription = stringResource(R.string.metronome_volume_description, volume.roundToInt())
    val muteDescription = stringResource(R.string.metronome_mute_description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("metronome_settings_controls"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.metronome_rhythm), style = MaterialTheme.typography.titleLarge)
        IntChoiceStrip(
            title = stringResource(R.string.metronome_meter_numerator),
            tagPrefix = "metronome_numerator",
            values = (1..12).toList(),
            selected = state.settings.numerator,
            onSelect = onNumeratorChange,
        )
        IntChoiceStrip(
            title = stringResource(R.string.metronome_meter_denominator),
            tagPrefix = "metronome_denominator",
            values = listOf(2, 4, 8, 16),
            selected = state.settings.denominator,
            onSelect = onDenominatorChange,
        )
        IntChoiceStrip(
            title = stringResource(R.string.metronome_subdivision),
            tagPrefix = "metronome_subdivision",
            values = listOf(1, 2, 3, 4),
            selected = state.settings.subdivision,
            onSelect = onSubdivisionChange,
        )
        AccentStrip(state.settings.accentEvery, onAccentEveryChange)
        HorizontalDivider()
        Text(stringResource(R.string.metronome_sound), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("metronome_sound_strip"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetronomeSound.entries.forEach { sound ->
                ChoiceChip(
                    label = soundLabel(sound),
                    selected = sound == state.settings.sound,
                    tag = "metronome_sound_${sound.name.lowercase()}",
                    onClick = { onSoundChange(sound) },
                )
            }
        }
        Text(
            text = stringResource(R.string.metronome_volume_value, volume.roundToInt()),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { onVolumeChange(volume.roundToInt()) },
            valueRange = 0f..100f,
            steps = 99,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = volumeDescription }
                .testTag("metronome_volume"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.muted,
                    role = Role.Switch,
                    onValueChange = onMutedChange,
                )
                .semantics { contentDescription = muteDescription }
                .testTag("metronome_mute"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.metronome_mute))
            Switch(checked = state.muted, onCheckedChange = null)
        }
        IntChoiceStrip(
            title = stringResource(R.string.metronome_count_in),
            tagPrefix = "metronome_count_in",
            values = listOf(0, 1, 2, 4),
            selected = state.settings.countIn,
            onSelect = onCountInChange,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun IntChoiceStrip(
    title: String,
    tagPrefix: String,
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .testTag("${tagPrefix}_strip"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                ChoiceChip(
                    label = value.toString(),
                    selected = value == selected,
                    tag = "${tagPrefix}_$value",
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun AccentStrip(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.metronome_accent),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("metronome_accent_strip"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(
                label = stringResource(R.string.metronome_off),
                selected = selected == null,
                tag = "metronome_accent_off",
                onClick = { onSelect(null) },
            )
            (2..12).forEach { value ->
                ChoiceChip(
                    label = value.toString(),
                    selected = selected == value,
                    tag = "metronome_accent_$value",
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onBackground,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = 52.dp)
            .testTag(tag),
    )
}

@Composable
private fun soundLabel(sound: MetronomeSound): String = stringResource(
    when (sound) {
        MetronomeSound.DEEP -> R.string.metronome_sound_deep
        MetronomeSound.WOOD -> R.string.metronome_sound_wood
        MetronomeSound.CLICK -> R.string.metronome_sound_click
        MetronomeSound.RIM -> R.string.metronome_sound_rim
        MetronomeSound.BRIGHT -> R.string.metronome_sound_bright
    },
)
