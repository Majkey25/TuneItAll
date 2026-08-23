package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.ResponseMode
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.metronome.MetronomeSound
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.ui.theme.ThemeMode
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

enum class SettingsSection {
    GENERAL,
    TUNER,
    METRONOME,
}

@Composable
fun SettingsScreen(
    state: TunerUiState,
    metronomeState: MetronomeUiState = MetronomeUiState(),
    initialSection: SettingsSection = SettingsSection.GENERAL,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onReferencePitchChanged: (ReferencePitch) -> Unit,
    onNotationChanged: (NoteNotation) -> Unit,
    onLayoutChanged: (HeadstockLayout) -> Unit,
    onAudioSettingsChanged: (TunerAudioSettings) -> Unit,
    onMetronomeNumeratorChanged: (Int) -> Unit = {},
    onMetronomeDenominatorChanged: (Int) -> Unit = {},
    onMetronomeSubdivisionChanged: (Int) -> Unit = {},
    onMetronomeAccentChanged: (Int?) -> Unit = {},
    onMetronomeSoundChanged: (MetronomeSound) -> Unit = {},
    onMetronomeVolumeChanged: (Int) -> Unit = {},
    onMetronomeMutedChanged: (Boolean) -> Unit = {},
    onMetronomeCountInChanged: (Int) -> Unit = {},
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("settings_screen"),
    ) {
        SecondaryHeader(stringResource(R.string.settings), onBack)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSection.entries.forEach { candidate ->
                FilterChip(
                    selected = section == candidate,
                    onClick = { section = candidate },
                    label = {
                        Text(
                            stringResource(
                                when (candidate) {
                                    SettingsSection.GENERAL -> R.string.settings_general
                                    SettingsSection.TUNER -> R.string.settings_tuner
                                    SettingsSection.METRONOME -> R.string.settings_metronome
                                },
                            ),
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("settings_section_${candidate.name.lowercase()}"),
                )
            }
        }
        when (section) {
            SettingsSection.GENERAL -> GeneralSettings(
                state = state,
                onThemeModeChanged = onThemeModeChanged,
                onReferencePitchChanged = onReferencePitchChanged,
                onNotationChanged = onNotationChanged,
                onLayoutChanged = onLayoutChanged,
                onOpenAbout = onOpenAbout,
            )

            SettingsSection.TUNER -> AudioSettings(state, onAudioSettingsChanged)
            SettingsSection.METRONOME -> MetronomeSettingsControls(
                state = metronomeState,
                onNumeratorChange = onMetronomeNumeratorChanged,
                onDenominatorChange = onMetronomeDenominatorChanged,
                onSubdivisionChange = onMetronomeSubdivisionChanged,
                onAccentEveryChange = onMetronomeAccentChanged,
                onSoundChange = onMetronomeSoundChanged,
                onVolumeChange = onMetronomeVolumeChanged,
                onMutedChange = onMetronomeMutedChanged,
                onCountInChange = onMetronomeCountInChanged,
            )
        }
    }
}

@Composable
private fun GeneralSettings(
    state: TunerUiState,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onReferencePitchChanged: (ReferencePitch) -> Unit,
    onNotationChanged: (NoteNotation) -> Unit,
    onLayoutChanged: (HeadstockLayout) -> Unit,
    onOpenAbout: () -> Unit,
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { onThemeModeChanged(mode) },
                    label = { Text(themeModeName(mode)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
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

        if (state.tuning.layouts.size > 1) {
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
        }
        OutlinedButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about))
        }
    }
}

@Composable
private fun AudioSettings(
    state: TunerUiState,
    onSettingsChanged: (TunerAudioSettings) -> Unit,
) {
    val settings = state.audioSettings
    val selectedProfile = TunerProfile.entries.firstOrNull { it.settings == settings }
    var advancedExpanded by remember { mutableStateOf(false) }

    Text(stringResource(R.string.tuner_audio), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.tuner_profiles_help), style = MaterialTheme.typography.bodyMedium)
    ProfileRow(
        profiles = listOf(TunerProfile.BALANCED, TunerProfile.QUIET_ROOM),
        selectedProfile = selectedProfile,
        onSettingsChanged = onSettingsChanged,
    )
    ProfileRow(
        profiles = listOf(TunerProfile.NOISY_ROOM, TunerProfile.FAST_RESPONSE),
        selectedProfile = selectedProfile,
        onSettingsChanged = onSettingsChanged,
        showCustom = true,
    )

    SettingsSlider(
        label = stringResource(R.string.microphone_sensitivity),
        help = stringResource(R.string.microphone_sensitivity_help),
        valueText = stringResource(R.string.sensitivity_value, settings.sensitivity.value),
        value = settings.sensitivity.value,
        range = DetectionSensitivity.MIN_VALUE..DetectionSensitivity.MAX_VALUE,
        onValueChanged = { onSettingsChanged(settings.copy(sensitivity = DetectionSensitivity(it))) },
    )

    Text(stringResource(R.string.response), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.response_help), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ResponseMode.entries.forEach { response ->
            FilterChip(
                selected = settings.response == response,
                onClick = { onSettingsChanged(settings.copy(response = response)) },
                label = { Text(responseName(response)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    SettingsSlider(
        label = stringResource(R.string.needle_stability),
        help = stringResource(R.string.needle_stability_help),
        valueText = stringResource(R.string.percent_value, settings.needleStability),
        value = settings.needleStability,
        range = 0..100,
        onValueChanged = { onSettingsChanged(settings.copy(needleStability = it)) },
    )

    OutlinedButton(
        onClick = { advancedExpanded = !advancedExpanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.advanced_audio))
    }
    if (advancedExpanded) {
        AdvancedAudioSettings(state, onSettingsChanged)
    }
    OutlinedButton(
        onClick = { onSettingsChanged(TunerProfile.BALANCED.settings) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.reset_audio_settings))
    }
}

@Composable
private fun ProfileRow(
    profiles: List<TunerProfile>,
    selectedProfile: TunerProfile?,
    onSettingsChanged: (TunerAudioSettings) -> Unit,
    showCustom: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        profiles.forEach { profile ->
            FilterChip(
                selected = selectedProfile == profile,
                onClick = { onSettingsChanged(profile.settings) },
                label = { Text(profileName(profile)) },
                modifier = Modifier.weight(1f),
            )
        }
        if (showCustom) {
            FilterChip(
                selected = selectedProfile == null,
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.profile_custom)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdvancedAudioSettings(
    state: TunerUiState,
    onSettingsChanged: (TunerAudioSettings) -> Unit,
) {
    val settings = state.audioSettings
    SettingsSlider(
        label = stringResource(R.string.noise_rejection),
        help = stringResource(R.string.noise_rejection_help),
        valueText = stringResource(R.string.noise_rejection_value, settings.noiseRejection),
        value = settings.noiseRejection,
        range = 0..100,
        onValueChanged = { onSettingsChanged(settings.copy(noiseRejection = it)) },
    )
    SettingsSlider(
        label = stringResource(R.string.harmonic_protection),
        help = stringResource(R.string.harmonic_protection_help),
        valueText = stringResource(R.string.harmonic_protection_value, settings.harmonicProtection),
        value = settings.harmonicProtection,
        range = 0..100,
        onValueChanged = { onSettingsChanged(settings.copy(harmonicProtection = it)) },
    )
    SettingsSlider(
        label = stringResource(R.string.in_tune_tolerance),
        help = stringResource(R.string.in_tune_tolerance_help),
        valueText = pluralStringResource(
            R.plurals.in_tune_tolerance_value,
            settings.inTuneCents,
            settings.inTuneCents,
        ),
        value = settings.inTuneCents,
        range = 1..10,
        onValueChanged = { onSettingsChanged(settings.copy(inTuneCents = it)) },
    )
    SettingsSlider(
        label = stringResource(R.string.confirmation_time),
        help = stringResource(R.string.confirmation_time_help),
        valueText = stringResource(R.string.confirmation_time_value, settings.confirmationMillis),
        value = settings.confirmationMillis.toInt(),
        range = 100..1_000,
        step = 50,
        onValueChanged = { onSettingsChanged(settings.copy(confirmationMillis = it.toLong())) },
    )
    SettingsSlider(
        label = stringResource(R.string.reading_hold),
        help = stringResource(R.string.reading_hold_help),
        valueText = stringResource(R.string.reading_hold_value, settings.readingHoldMillis),
        value = settings.readingHoldMillis.toInt(),
        range = 0..1_000,
        step = 50,
        onValueChanged = { onSettingsChanged(settings.copy(readingHoldMillis = it.toLong())) },
    )

    Text(stringResource(R.string.audio_input), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.audio_input_help), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AudioInputSource.entries.forEach { source ->
            FilterChip(
                selected = settings.inputSource == source,
                onClick = { onSettingsChanged(settings.copy(inputSource = source)) },
                enabled = source != AudioInputSource.RAW || state.audioInputCapabilities.rawSupported,
                label = { Text(inputSourceName(source)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (!state.audioInputCapabilities.rawSupported) {
        Text(stringResource(R.string.raw_input_unavailable), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        stringResource(
            R.string.active_audio_input,
            state.audioInputCapabilities.activeSource?.let { inputSourceName(it) }
                ?: stringResource(R.string.audio_input_not_listening),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SettingsSlider(
    label: String,
    help: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChanged: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(help, style = MaterialTheme.typography.bodyMedium)
    Text(valueText)
    Slider(
        value = value.toFloat(),
        onValueChange = { raw ->
            val stepped = (((raw - range.first) / step).roundToInt() * step + range.first).coerceIn(range)
            onValueChanged(stepped)
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
private fun profileName(profile: TunerProfile): String = stringResource(
    when (profile) {
        TunerProfile.BALANCED -> R.string.profile_balanced
        TunerProfile.QUIET_ROOM -> R.string.profile_quiet_room
        TunerProfile.NOISY_ROOM -> R.string.profile_noisy_room
        TunerProfile.FAST_RESPONSE -> R.string.profile_fast_response
    },
)

@Composable
private fun responseName(response: ResponseMode): String = stringResource(
    when (response) {
        ResponseMode.FAST -> R.string.response_fast
        ResponseMode.BALANCED -> R.string.profile_balanced
        ResponseMode.STABLE -> R.string.response_stable
    },
)

@Composable
private fun inputSourceName(source: AudioInputSource): String = stringResource(
    when (source) {
        AudioInputSource.AUTO -> R.string.audio_input_auto
        AudioInputSource.RAW -> R.string.audio_input_raw
        AudioInputSource.COMPATIBLE -> R.string.audio_input_compatible
    },
)

@Composable
private fun themeModeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

private fun formatPitch(hertz: Double): String = String.format(Locale.US, "%.1f", hertz)

private const val NORMAL_REFERENCE_MIN = 430.0
private const val NORMAL_REFERENCE_MAX = 450.0
private const val REFERENCE_SLIDER_STEPS = 699
private const val MAX_PITCH_INPUT_LENGTH = 6
