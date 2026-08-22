package com.tuneitall.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.annotation.StringRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.metronome.MetronomeSound
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    onBpmChange: (Int) -> Unit,
    onNumeratorChange: (Int) -> Unit,
    onDenominatorChange: (Int) -> Unit,
    onSubdivisionChange: (Int) -> Unit,
    onAccentEveryChange: (Int?) -> Unit,
    onTap: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSoundChange: (MetronomeSound) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onCountInChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("metronome_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(onOpenSettings = { showSettings = true })
        BpmControl(state, onBpmChange)
        PhysicalMetronome(phase = state.phase)
        PlaybackControls(state, onTap, onStart, onStop)
        RhythmSummary(state, onClick = { showSettings = true })
        Spacer(Modifier.height(4.dp))
    }

    if (showSettings) {
        MetronomeSettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onNumeratorChange = onNumeratorChange,
            onDenominatorChange = onDenominatorChange,
            onSubdivisionChange = onSubdivisionChange,
            onAccentEveryChange = onAccentEveryChange,
            onSoundChange = onSoundChange,
            onVolumeChange = onVolumeChange,
            onMutedChange = onMutedChange,
            onCountInChange = onCountInChange,
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val settingsDescription = stringResource(R.string.metronome_settings_description)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("metronome_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.destination_metronome),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = settingsDescription }
                .testTag("metronome_settings"),
        ) {
            SettingsSlidersIcon()
        }
    }
}

@Composable
private fun SettingsSlidersIcon() {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(24.dp).testTag("metronome_settings_icon")) {
        val stroke = 2.dp.toPx()
        listOf(6f to 0.33f, 17f to 0.50f, 10f to 0.67f).forEach { (knobDp, yRatio) ->
            val y = size.height * yRatio
            val knob = knobDp.dp.toPx()
            drawLine(color, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), stroke, StrokeCap.Round)
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(knob, y))
        }
    }
}

@Composable
private fun BpmControl(state: MetronomeUiState, onBpmChange: (Int) -> Unit) {
    val focusManager = LocalFocusManager.current
    val bpmDescription = stringResource(R.string.metronome_bpm_description)
    var bpmText by rememberSaveable { mutableStateOf(state.settings.bpm.value.toString()) }
    var focused by remember { mutableStateOf(false) }

    fun commitBpm() {
        val bpm = bpmText.toIntOrNull()?.coerceIn(20, 400) ?: state.settings.bpm.value
        bpmText = bpm.toString()
        onBpmChange(bpm)
    }

    LaunchedEffect(state.settings.bpm.value) {
        if (!focused) bpmText = state.settings.bpm.value.toString()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
            RepeatButton(
                label = "−",
                description = stringResource(R.string.metronome_decrease_bpm),
                tag = "metronome_bpm_decrease",
                enabled = state.settings.bpm.value > 20,
                onRepeat = { onBpmChange(state.settings.bpm.value - 1) },
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = bpmText,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        bpmText = value
                        value.toIntOrNull()?.takeIf { it in 20..400 }?.let(onBpmChange)
                    }
                },
                suffix = { Text(stringResource(R.string.metronome_bpm)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    commitBpm()
                    focusManager.clearFocus()
                }),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier
                    .width(156.dp)
                    .onFocusChanged {
                        if (focused && !it.isFocused) commitBpm()
                        focused = it.isFocused
                    }
                    .semantics { contentDescription = bpmDescription }
                    .testTag("metronome_bpm_input"),
            )
            Spacer(Modifier.width(8.dp))
            RepeatButton(
                label = "+",
                description = stringResource(R.string.metronome_increase_bpm),
                tag = "metronome_bpm_increase",
                enabled = state.settings.bpm.value < 400,
                onRepeat = { onBpmChange(state.settings.bpm.value + 1) },
            )
    }
}

@Composable
private fun RepeatButton(
    label: String,
    description: String,
    tag: String,
    enabled: Boolean,
    onRepeat: () -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val currentOnRepeat by rememberUpdatedState(onRepeat)
    LaunchedEffect(held) {
        if (!held) return@LaunchedEffect
        delay(500L)
        while (held) {
            currentOnRepeat()
            delay(80L)
        }
    }
    OutlinedButton(
        onClick = { currentOnRepeat() },
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .pointerInput(enabled) {
                try {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        held = true
                        waitForUpOrCancellation()
                        held = false
                    }
                } finally {
                    held = false
                }
            }
            .semantics { contentDescription = description }
            .testTag(tag),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PhysicalMetronome(phase: Double) {
    val body = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val weightSlotColor = MaterialTheme.colorScheme.onPrimary
    val safePhase = phase.coerceIn(-1.0, 1.0).toFloat()
    val description = stringResource(R.string.metronome_physical_description)
    val phaseDescription = stringResource(R.string.metronome_phase_description, safePhase)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .semantics {
                contentDescription = description
                stateDescription = phaseDescription
                progressBarRangeInfo = ProgressBarRangeInfo(safePhase, -1f..1f)
            }
            .testTag("metronome_pendulum"),
    ) {
        val geometry = mechanicalMetronomeGeometry(size, density, safePhase.toDouble())
        fun polygon(points: List<Offset>) = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        geometry.layers.forEach { layer ->
            when (layer) {
                MechanicalLayer.BODY -> {
                    val path = polygon(geometry.body)
                    drawPath(path, body)
                    drawPath(path, ink.copy(alpha = MECHANICAL_BODY_FILL_ALPHA))
                    drawPath(path, ink, style = Stroke(2.dp.toPx()))
                }

                MechanicalLayer.SIDE_PLANE -> drawPath(
                    polygon(geometry.sidePlane),
                    ink.copy(alpha = MECHANICAL_SIDE_PLANE_ALPHA),
                )

                MechanicalLayer.SCALE_PLATE -> {
                    val path = polygon(geometry.scalePlate)
                    drawPath(path, background)
                    drawPath(path, ink, style = Stroke(1.dp.toPx()))
                }

                MechanicalLayer.SCALE_TICKS -> geometry.scaleTicks.forEach { tick ->
                    drawLine(ink, tick.start, tick.end, 1.dp.toPx(), StrokeCap.Round)
                }

                MechanicalLayer.PLINTH -> drawRoundRect(
                    color = ink,
                    topLeft = geometry.plinth.topLeft,
                    size = geometry.plinth.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )

                MechanicalLayer.ARM -> rotate(geometry.armDegrees, geometry.pivot) {
                    drawLine(ink, geometry.pivot, geometry.armEnd, 3.dp.toPx(), StrokeCap.Round)
                }

                MechanicalLayer.WEIGHT -> rotate(geometry.armDegrees, geometry.pivot) {
                    drawRoundRect(
                        color = accent,
                        topLeft = geometry.weight.topLeft,
                        size = geometry.weight.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                    drawRoundRect(
                        color = ink,
                        topLeft = geometry.weight.topLeft,
                        size = geometry.weight.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                    drawRoundRect(
                        color = weightSlotColor,
                        topLeft = geometry.weightSlot.topLeft,
                        size = geometry.weightSlot.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
                    )
                }

                MechanicalLayer.HUB -> {
                    drawCircle(ink, 7.dp.toPx(), geometry.pivot)
                    drawCircle(background, 2.dp.toPx(), geometry.pivot)
                }
            }
        }
    }
}

internal enum class MechanicalLayer {
    BODY,
    SIDE_PLANE,
    SCALE_PLATE,
    SCALE_TICKS,
    PLINTH,
    ARM,
    WEIGHT,
    HUB,
}

internal const val MECHANICAL_BODY_FILL_ALPHA = 0.06f
internal const val MECHANICAL_SIDE_PLANE_ALPHA = 0.10f

internal data class MechanicalLine(val start: Offset, val end: Offset)

internal data class MechanicalMetronomeGeometry(
    val body: List<Offset>,
    val sidePlane: List<Offset>,
    val scalePlate: List<Offset>,
    val scaleTicks: List<MechanicalLine>,
    val plinth: Rect,
    val pivot: Offset,
    val armEnd: Offset,
    val weight: Rect,
    val weightSlot: Rect,
    val armDegrees: Float,
    val layers: List<MechanicalLayer>,
)

internal fun mechanicalMetronomeGeometry(
    size: Size,
    density: Float,
    phase: Double,
): MechanicalMetronomeGeometry {
    require(size.width > 0f && size.height > 0f)
    require(density > 0f)
    val centerX = size.width * 0.5f
    val tickRatios = listOf(0.22f, 0.285f, 0.35f, 0.415f, 0.48f, 0.545f, 0.61f)
    val weightCenterY = size.height * 0.45f
    return MechanicalMetronomeGeometry(
        body = listOf(
            Offset(size.width * 0.39f, size.height * 0.07f),
            Offset(size.width * 0.61f, size.height * 0.07f),
            Offset(size.width * 0.78f, size.height * 0.91f),
            Offset(size.width * 0.22f, size.height * 0.91f),
        ),
        sidePlane = listOf(
            Offset(size.width * 0.61f, size.height * 0.07f),
            Offset(size.width * 0.78f, size.height * 0.91f),
            Offset(size.width * 0.68f, size.height * 0.89f),
            Offset(size.width * 0.56f, size.height * 0.13f),
        ),
        scalePlate = listOf(
            Offset(size.width * 0.44f, size.height * 0.13f),
            Offset(size.width * 0.56f, size.height * 0.13f),
            Offset(size.width * 0.59f, size.height * 0.68f),
            Offset(size.width * 0.41f, size.height * 0.68f),
        ),
        scaleTicks = tickRatios.mapIndexed { index, ratio ->
            val length = (if (index % 2 == 0) 12f else 8f) * density
            val y = size.height * ratio
            MechanicalLine(Offset(centerX - length / 2f, y), Offset(centerX + length / 2f, y))
        },
        plinth = Rect(
            left = size.width * 0.19f,
            top = size.height * 0.89f,
            right = size.width * 0.81f,
            bottom = size.height * 0.89f + 10f * density,
        ),
        pivot = Offset(centerX, size.height * 0.78f),
        armEnd = Offset(centerX, size.height * 0.13f),
        weight = Rect(
            left = centerX - 16f * density,
            top = weightCenterY - 8f * density,
            right = centerX + 16f * density,
            bottom = weightCenterY + 8f * density,
        ),
        weightSlot = Rect(
            left = centerX - 1.5f * density,
            top = weightCenterY - 5f * density,
            right = centerX + 1.5f * density,
            bottom = weightCenterY + 5f * density,
        ),
        armDegrees = phase.coerceIn(-1.0, 1.0).toFloat() * 24f,
        layers = MechanicalLayer.entries,
    )
}

@Composable
private fun RhythmSummary(state: MetronomeUiState, onClick: () -> Unit) {
    val accent = state.settings.accentEvery?.let {
        stringResource(R.string.metronome_accent_every_summary, it)
    } ?: stringResource(R.string.metronome_accent_off_summary)
    val summary = stringResource(
        R.string.metronome_rhythm_summary,
        state.settings.numerator,
        state.settings.denominator,
        state.settings.subdivision,
        accent,
    )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("metronome_rhythm_summary"),
    ) {
        Text(summary, maxLines = 1)
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
private fun PlaybackControls(state: MetronomeUiState, onTap: () -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    val busy = state.starting || state.stopping
    val status = when {
        state.error != null -> stringResource(metronomeErrorResource(state.error))
        state.starting -> stringResource(R.string.metronome_starting)
        state.stopping -> stringResource(R.string.metronome_stopping)
        state.playing -> stringResource(R.string.metronome_playing)
        else -> null
    }
    status?.let {
        Text(
            text = it,
            color = if (state.error == null) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().testTag("metronome_status"),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onTap,
            enabled = !busy,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("metronome_tap"),
        ) {
            Text(stringResource(R.string.metronome_tap))
        }
        Button(
            onClick = if (state.playing) onStop else onStart,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("metronome_start_stop"),
        ) {
            Text(
                text = when {
                    state.starting -> stringResource(R.string.metronome_starting)
                    state.stopping -> stringResource(R.string.metronome_stopping)
                    state.playing -> stringResource(R.string.metronome_stop)
                    else -> stringResource(R.string.metronome_start)
                },
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("metronome_start_stop_label"),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MetronomeSettingsSheet(
    state: MetronomeUiState,
    onDismiss: () -> Unit,
    onNumeratorChange: (Int) -> Unit,
    onDenominatorChange: (Int) -> Unit,
    onSubdivisionChange: (Int) -> Unit,
    onAccentEveryChange: (Int?) -> Unit,
    onSoundChange: (MetronomeSound) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onCountInChange: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val volumeDescription = stringResource(R.string.metronome_volume_description, state.settings.volume)
        val muteDescription = stringResource(R.string.metronome_mute_description)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.metronome_settings_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.metronome_rhythm), style = MaterialTheme.typography.titleSmall)
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
            Text(stringResource(R.string.metronome_sound), style = MaterialTheme.typography.titleSmall)
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
                text = stringResource(R.string.metronome_volume_value, state.settings.volume),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = state.settings.volume.toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt()) },
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
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("metronome_settings_done"),
            ) {
                Text(stringResource(R.string.metronome_done))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun soundLabel(sound: MetronomeSound): String = stringResource(
    when (sound) {
        MetronomeSound.WOOD -> R.string.metronome_sound_wood
        MetronomeSound.CLICK -> R.string.metronome_sound_click
        MetronomeSound.RIM -> R.string.metronome_sound_rim
    },
)

@StringRes
internal fun metronomeErrorResource(error: MetronomeError): Int = when (error) {
    MetronomeError.OUTPUT_UNAVAILABLE -> R.string.metronome_error_output_unavailable
    MetronomeError.PLAYBACK_STOPPED -> R.string.metronome_error_playback_stopped
    MetronomeError.STOP_FAILED -> R.string.metronome_error_stop_failed
}
