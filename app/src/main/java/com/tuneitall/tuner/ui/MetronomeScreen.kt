package com.tuneitall.tuner.ui

import android.view.Choreographer
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
    val body = MaterialTheme.colorScheme.surfaceVariant
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
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                val geometry = mechanicalMetronomeGeometry(size, density)
                fun polygon(points: List<Offset>) = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                val bodyPath = Path().apply {
                    moveTo(geometry.body[0].x, geometry.body[0].y)
                    lineTo(geometry.body[1].x, geometry.body[1].y)
                    cubicTo(
                        size.width * 0.60f,
                        size.height * 0.30f,
                        size.width * 0.64f,
                        size.height * 0.56f,
                        geometry.body[2].x,
                        geometry.body[2].y,
                    )
                    lineTo(geometry.body[3].x, geometry.body[3].y)
                    cubicTo(
                        size.width * 0.36f,
                        size.height * 0.56f,
                        size.width * 0.40f,
                        size.height * 0.30f,
                        geometry.body[0].x,
                        geometry.body[0].y,
                    )
                    close()
                }
                val sidePath = polygon(geometry.sidePlane)
                val platePath = polygon(geometry.scalePlate)
                val basePath = polygon(geometry.baseFront)
                val outline = 1.5.dp.toPx()
                val round2 = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())

                onDrawBehind {
                    drawPath(bodyPath, body)
                    drawPath(bodyPath, ink.copy(alpha = MECHANICAL_BODY_FILL_ALPHA))
                    drawPath(bodyPath, ink, style = Stroke(outline))
                    drawPath(sidePath, ink.copy(alpha = MECHANICAL_SIDE_PLANE_ALPHA))
                    drawPath(platePath, background)
                    drawPath(platePath, ink, style = Stroke(1.dp.toPx()))
                    geometry.scaleTicks.forEach { tick ->
                        drawLine(ink, tick.start, tick.end, 1.dp.toPx(), StrokeCap.Round)
                    }
                    drawOval(ink.copy(alpha = 0.18f), geometry.topCap.topLeft, geometry.topCap.size)
                    drawOval(ink, geometry.topCap.topLeft, geometry.topCap.size, style = Stroke(outline))
                    drawOval(ink, geometry.topBead.topLeft, geometry.topBead.size)
                    drawPath(basePath, body)
                    drawPath(basePath, ink.copy(alpha = 0.12f))
                    drawPath(basePath, ink, style = Stroke(outline))
                    drawRoundRect(ink, geometry.plinth.topLeft, geometry.plinth.size, round2)
                    drawOval(ink, geometry.leftFoot.topLeft, geometry.leftFoot.size)
                    drawOval(ink, geometry.rightFoot.topLeft, geometry.rightFoot.size)
                    drawLine(
                        ink,
                        geometry.windingStem.start,
                        geometry.windingStem.end,
                        2.dp.toPx(),
                        StrokeCap.Round,
                    )
                    drawCircle(ink, 3.dp.toPx(), geometry.windingHub)
                    drawOval(
                        ink.copy(alpha = 0.28f),
                        geometry.windingKey.topLeft,
                        geometry.windingKey.size,
                    )
                    drawOval(ink, geometry.windingKey.topLeft, geometry.windingKey.size, style = Stroke(outline))
                }
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = metronomeArmDegrees(phase.floatValue.toDouble())
                    transformOrigin = TransformOrigin(0.5f, 0.72f)
                }
                .drawWithCache {
                    val geometry = mechanicalMetronomeGeometry(size, density)
                    val round1 = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                    val weightPath = Path().apply {
                        moveTo(geometry.weight.left + 4.dp.toPx(), geometry.weight.top)
                        lineTo(geometry.weight.right - 4.dp.toPx(), geometry.weight.top)
                        lineTo(geometry.weight.right, geometry.weight.bottom)
                        lineTo(geometry.weight.left, geometry.weight.bottom)
                        close()
                    }

                    onDrawBehind {
                        drawLine(ink, geometry.pivot, geometry.armEnd, 3.dp.toPx(), StrokeCap.Round)
                        drawPath(weightPath, ink.copy(alpha = 0.26f))
                        drawPath(weightPath, ink, style = Stroke(1.dp.toPx()))
                        drawRoundRect(
                            accent,
                            geometry.weightSlot.topLeft,
                            geometry.weightSlot.size,
                            round1,
                        )
                        drawCircle(ink, 7.dp.toPx(), geometry.pivot)
                        drawCircle(background, 2.dp.toPx(), geometry.pivot)
                }
            },
        )
    }
}

internal enum class MechanicalLayer {
    BODY,
    SIDE_PLANE,
    SCALE_PLATE,
    SCALE_TICKS,
    TOP_CAP,
    BASE_FRONT,
    PLINTH,
    FEET,
    WINDING_KEY,
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
    val topCap: Rect,
    val topBead: Rect,
    val baseFront: List<Offset>,
    val plinth: Rect,
    val leftFoot: Rect,
    val rightFoot: Rect,
    val windingStem: MechanicalLine,
    val windingHub: Offset,
    val windingKey: Rect,
    val pivot: Offset,
    val armEnd: Offset,
    val weight: Rect,
    val weightSlot: Rect,
    val layers: List<MechanicalLayer>,
)

internal fun mechanicalMetronomeGeometry(
    size: Size,
    density: Float,
): MechanicalMetronomeGeometry {
    require(size.width > 0f && size.height > 0f)
    require(density > 0f)
    val centerX = size.width * 0.5f
    val tickRatios = listOf(0.22f, 0.285f, 0.35f, 0.415f, 0.48f, 0.545f, 0.61f)
    val weightCenterY = size.height * 0.45f
    return MechanicalMetronomeGeometry(
        body = listOf(
            Offset(size.width * 0.43f, size.height * 0.09f),
            Offset(size.width * 0.57f, size.height * 0.09f),
            Offset(size.width * 0.68f, size.height * 0.77f),
            Offset(size.width * 0.32f, size.height * 0.77f),
        ),
        sidePlane = listOf(
            Offset(size.width * 0.57f, size.height * 0.09f),
            Offset(size.width * 0.68f, size.height * 0.77f),
            Offset(size.width * 0.64f, size.height * 0.75f),
            Offset(size.width * 0.55f, size.height * 0.14f),
        ),
        scalePlate = listOf(
            Offset(size.width * 0.46f, size.height * 0.15f),
            Offset(size.width * 0.54f, size.height * 0.15f),
            Offset(size.width * 0.57f, size.height * 0.65f),
            Offset(size.width * 0.43f, size.height * 0.65f),
        ),
        scaleTicks = tickRatios.mapIndexed { index, ratio ->
            val length = (if (index % 2 == 0) 12f else 8f) * density
            val y = size.height * ratio
            MechanicalLine(Offset(centerX - length / 2f, y), Offset(centerX + length / 2f, y))
        },
        topCap = Rect(
            left = size.width * 0.455f,
            top = size.height * 0.045f,
            right = size.width * 0.545f,
            bottom = size.height * 0.105f,
        ),
        topBead = Rect(
            left = size.width * 0.488f,
            top = size.height * 0.018f,
            right = size.width * 0.512f,
            bottom = size.height * 0.052f,
        ),
        baseFront = listOf(
            Offset(size.width * 0.31f, size.height * 0.76f),
            Offset(size.width * 0.69f, size.height * 0.76f),
            Offset(size.width * 0.78f, size.height * 0.91f),
            Offset(size.width * 0.22f, size.height * 0.91f),
        ),
        plinth = Rect(
            left = size.width * 0.19f,
            top = size.height * 0.89f,
            right = size.width * 0.81f,
            bottom = size.height * 0.89f + 8f * density,
        ),
        leftFoot = Rect(
            left = size.width * 0.23f,
            top = size.height * 0.925f,
            right = size.width * 0.28f,
            bottom = size.height * 0.97f,
        ),
        rightFoot = Rect(
            left = size.width * 0.72f,
            top = size.height * 0.925f,
            right = size.width * 0.77f,
            bottom = size.height * 0.97f,
        ),
        windingStem = MechanicalLine(
            Offset(size.width * 0.66f, size.height * 0.56f),
            Offset(size.width * 0.76f, size.height * 0.62f),
        ),
        windingHub = Offset(size.width * 0.66f, size.height * 0.56f),
        windingKey = Rect(
            left = size.width * 0.745f,
            top = size.height * 0.585f,
            right = size.width * 0.79f,
            bottom = size.height * 0.655f,
        ),
        pivot = Offset(centerX, size.height * 0.72f),
        armEnd = Offset(centerX, size.height * 0.12f),
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
        layers = MechanicalLayer.entries,
    )
}

internal fun metronomeArmDegrees(phase: Double): Float = phase.coerceIn(-1.0, 1.0).toFloat() * 24f

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
