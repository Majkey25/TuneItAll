package com.tuneitall.tuner.ui

import android.view.Choreographer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.annotation.StringRes
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.metronome.MetronomeSound
import kotlinx.coroutines.delay

@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    phaseProvider: () -> Double,
    onBpmChange: (Int) -> Unit,
    onNumeratorChange: (Int) -> Unit = {},
    onDenominatorChange: (Int) -> Unit = {},
    onSubdivisionChange: (Int) -> Unit = {},
    onAccentEveryChange: (Int?) -> Unit = {},
    onTap: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSoundChange: (MetronomeSound) -> Unit = {},
    onVolumeChange: (Int) -> Unit = {},
    onMutedChange: (Boolean) -> Unit = {},
    onCountInChange: (Int) -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQuickSettings by rememberSaveable { mutableStateOf(false) }
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
        Header(onOpenSettings)
        BpmControl(state, onBpmChange)
        PhysicalMetronome(state.playing, phaseProvider)
        PlaybackControls(state, onTap, onStart, onStop)
        RhythmSummary(state, onClick = { showQuickSettings = true })
        Spacer(Modifier.height(4.dp))
    }
    if (showQuickSettings) {
        MetronomeQuickSettings(
            state = state,
            onDismiss = { showQuickSettings = false },
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
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                modifier = Modifier.size(24.dp).testTag("metronome_settings_icon"),
            )
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
private fun PhysicalMetronome(playing: Boolean, phaseProvider: () -> Double) {
    val ink = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.metronome_physical_description)
    val currentPhaseProvider by rememberUpdatedState(phaseProvider)
    val phase = remember { mutableFloatStateOf(0f) }

    DisposableEffect(playing) {
        val choreographer = Choreographer.getInstance()
        val callback = if (playing) {
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val next = currentPhaseProvider().coerceIn(-1.0, 1.0).toFloat()
                    if (phase.floatValue != next) phase.floatValue = next
                    choreographer.postFrameCallback(this)
                }
            }.also(choreographer::postFrameCallback)
        } else {
            phase.floatValue = 0f
            null
        }
        onDispose { callback?.let(choreographer::removeFrameCallback) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp)
            .semantics { contentDescription = description }
            .testTag("metronome_pendulum"),
    ) {
        Image(
            painter = painterResource(R.drawable.metronome_nikko_body),
            contentDescription = null,
            modifier = Modifier.matchParentSize().testTag("metronome_body_photo"),
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = metronomeArmDegrees(phase.floatValue.toDouble())
                    transformOrigin = TransformOrigin(METRONOME_PIVOT_X_RATIO, METRONOME_PIVOT_Y_RATIO)
                }
                .drawWithCache {
                    val geometry = metronomeArmGeometry(size, density)
                    val round1 = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                    val weightPath = Path().apply {
                        moveTo(geometry.weight.left + 4.dp.toPx(), geometry.weight.top)
                        lineTo(geometry.weight.right - 4.dp.toPx(), geometry.weight.top)
                        lineTo(geometry.weight.right, geometry.weight.bottom)
                        lineTo(geometry.weight.left, geometry.weight.bottom)
                        close()
                    }

                    onDrawBehind {
                        drawLine(accent, geometry.pivot, geometry.armEnd, 3.dp.toPx(), StrokeCap.Round)
                        drawPath(weightPath, accent)
                        drawPath(weightPath, ink, style = Stroke(1.dp.toPx()))
                        drawRoundRect(
                            accent,
                            geometry.weightSlot.topLeft,
                            geometry.weightSlot.size,
                            round1,
                        )
                        drawCircle(accent, 7.dp.toPx(), geometry.pivot)
                        drawCircle(background, 2.dp.toPx(), geometry.pivot)
                }
            },
        )
    }
}

internal data class MetronomeArmGeometry(
    val pivot: Offset,
    val armEnd: Offset,
    val weight: Rect,
    val weightSlot: Rect,
)

internal fun metronomeArmGeometry(size: Size, density: Float): MetronomeArmGeometry {
    require(size.width > 0f && size.height > 0f)
    require(density > 0f)
    val centerX = size.width * METRONOME_PIVOT_X_RATIO
    val weightCenterY = size.height * 0.39f
    return MetronomeArmGeometry(
        pivot = Offset(centerX, size.height * METRONOME_PIVOT_Y_RATIO),
        armEnd = Offset(centerX, size.height * 0.10f),
        weight = Rect(
            left = centerX - 15f * density,
            top = weightCenterY - 8f * density,
            right = centerX + 15f * density,
            bottom = weightCenterY + 8f * density,
        ),
        weightSlot = Rect(
            left = centerX - 1.5f * density,
            top = weightCenterY - 5f * density,
            right = centerX + 1.5f * density,
            bottom = weightCenterY + 5f * density,
        ),
    )
}

internal fun metronomeArmDegrees(phase: Double): Float = phase.coerceIn(-1.0, 1.0).toFloat() * 24f

internal const val METRONOME_PIVOT_X_RATIO = 0.5f
internal const val METRONOME_PIVOT_Y_RATIO = 0.64f

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
        Text(text = summary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MetronomeQuickSettings(
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.metronome_settings_title), style = MaterialTheme.typography.titleLarge)
            MetronomeSettingsControls(
                state = state,
                onNumeratorChange = onNumeratorChange,
                onDenominatorChange = onDenominatorChange,
                onSubdivisionChange = onSubdivisionChange,
                onAccentEveryChange = onAccentEveryChange,
                onSoundChange = onSoundChange,
                onVolumeChange = onVolumeChange,
                onMutedChange = onMutedChange,
                onCountInChange = onCountInChange,
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

@StringRes
internal fun metronomeErrorResource(error: MetronomeError): Int = when (error) {
    MetronomeError.OUTPUT_UNAVAILABLE -> R.string.metronome_error_output_unavailable
    MetronomeError.PLAYBACK_STOPPED -> R.string.metronome_error_playback_stopped
    MetronomeError.STOP_FAILED -> R.string.metronome_error_stop_failed
}
