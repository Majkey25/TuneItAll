package com.tuneitall.tuner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.audio.AudioInputError
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.ui.components.CentsRail
import com.tuneitall.tuner.ui.components.Headstock
import com.tuneitall.tuner.ui.theme.TuneItAllTheme

@Composable
fun TunerScreen(
    state: TunerUiState,
    onModeSelected: (TunerMode) -> Unit,
    onStringSelected: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestMicrophonePermission: () -> Unit = {},
    onRetryAudio: () -> Unit = {},
) {
    val currentNote = state.reading?.target ?: state.tuning.notesLowToHigh
        .getOrNull(state.selectedString)
        ?.takeIf { state.mode == TunerMode.MANUAL }
    val favorite = state.tuning.id in state.favoriteIds
    val readingStringIndex = state.reading?.target?.let(state.tuning.notesLowToHigh::indexOf)?.takeIf { it >= 0 }
    val activeStringIndex = when (state.mode) {
        TunerMode.AUTO -> readingStringIndex
        TunerMode.MANUAL -> readingStringIndex ?: state.selectedString
        TunerMode.CHROMATIC -> null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TunerTopBar(
            state = state,
            favorite = favorite,
            onToggleFavorite = onToggleFavorite,
            onOpenLibrary = onOpenLibrary,
            onOpenSettings = onOpenSettings,
        )

        ModeSelector(selected = state.mode, onSelected = onModeSelected)

        TunerStatusRow(state)

        DetectedNote(
            note = currentNote,
            notation = state.notation,
            confirmed = state.tuningConfirmed,
        )

        CentsRail(
            cents = state.reading?.cents,
            inTune = state.reading?.inTune == true,
            idleText = stringResource(
                if (state.mode == TunerMode.CHROMATIC) R.string.play_any_note else R.string.no_pitch_detected,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        MicrophoneStatus(state, onRequestMicrophonePermission, onRetryAudio, onOpenApplicationSettings)

        if (state.mode != TunerMode.CHROMATIC) {
            Text(
                text = stringResource(R.string.tap_string_to_hear),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )

            Headstock(
                layout = state.headstockLayout,
                notes = state.tuning.notesLowToHigh,
                selectedIndex = activeStringIndex,
                confirmed = state.tuningConfirmed,
                notation = state.notation,
                onStringSelected = onStringSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TunerStatusRow(state: TunerUiState) {
    val modeText = stringResource(
        when (state.mode) {
            TunerMode.AUTO -> R.string.mode_auto
            TunerMode.MANUAL -> R.string.mode_manual
            TunerMode.CHROMATIC -> R.string.mode_chromatic
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tuner_status"),
    ) {
        Text(
            text = if (state.mode == TunerMode.CHROMATIC) {
                stringResource(R.string.chromatic_tuner)
            } else {
                state.tuning.name
            },
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .weight(1.2f)
                .testTag("tuner_status_tuning"),
        )
        Text(
            text = modeText,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .weight(0.8f)
                .testTag("tuner_status_mode"),
        )
        Text(
            text = stringResource(R.string.reference_pitch_value, state.referencePitch.hertz),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .weight(1.3f)
                .testTag("tuner_status_reference"),
        )
    }
}

@Composable
private fun DetectedNote(
    note: com.tuneitall.tuner.model.MidiNote?,
    notation: NoteNotation,
    confirmed: Boolean,
) {
    val parts = note?.let { noteParts(it, notation) }
    val color = MaterialTheme.colorScheme.onBackground
    val description = note?.let { formatNote(it, notation) } ?: "—"
    val confirmationProgress by animateFloatAsState(
        targetValue = if (confirmed) 1f else 0f,
        animationSpec = tween(220),
        label = "tuner confirmation",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(184.dp)
            .testTag("detected_note")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = confirmationProgress
                    scaleX = 0.8f + confirmationProgress * 0.2f
                    scaleY = scaleX
                }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                .testTag("tuner_confirmation_feedback"),
        )
        if (parts == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 82.sp, lineHeight = 88.sp),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = parts.letter,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 82.sp, lineHeight = 88.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier
                            .width(72.dp)
                            .testTag("detected_note_letter"),
                    )
                    Text(
                        text = parts.accidental,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier
                            .width(24.dp)
                            .testTag("detected_note_accidental"),
                    )
                    Text(
                        text = parts.octave,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        modifier = Modifier
                            .width(32.dp)
                            .testTag("detected_note_octave"),
                    )
                }
            }
        }
    }
}

@Composable
private fun TunerTopBar(
    state: TunerUiState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    val favoriteDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite)
    val chooseTuningDescription = stringResource(R.string.choose_tuning_current, state.tuning.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.mode == TunerMode.CHROMATIC) {
            Box(modifier = Modifier.weight(1f))
        } else {
            OutlinedButton(
                onClick = onOpenLibrary,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .testTag("tuning_picker")
                    .semantics {
                        contentDescription = chooseTuningDescription
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = state.tuning.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.choose_tuning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(
                onClick = onToggleFavorite,
                colors = textButtonColors,
                modifier = Modifier.semantics { contentDescription = favoriteDescription },
            ) {
                Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
            }
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("tuner_settings"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier.size(24.dp).testTag("tuner_settings_icon"),
            )
        }
    }
}

internal data class ResolvedChipColors(
    val container: Color,
    val content: Color,
)

@Composable
internal fun modeSelectorSelectedColors(): ResolvedChipColors = ResolvedChipColors(
    container = MaterialTheme.colorScheme.primary,
    content = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun ModeSelector(selected: TunerMode, onSelected: (TunerMode) -> Unit) {
    val selectedColors = modeSelectorSelectedColors()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mode_selector"),
    ) {
        TunerMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        text = stringResource(
                            when (mode) {
                                TunerMode.AUTO -> R.string.mode_auto
                                TunerMode.MANUAL -> R.string.mode_manual
                                TunerMode.CHROMATIC -> R.string.mode_chromatic
                            },
                        ),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selectedColors.container,
                    selectedLabelColor = selectedColors.content,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun MicrophoneStatus(
    state: TunerUiState,
    onRequestMicrophonePermission: () -> Unit,
    onRetryAudio: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
) {
    when {
        state.microphonePermanentlyDenied -> {
            Text(stringResource(R.string.microphone_explanation), style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onOpenApplicationSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            ) {
                Text(stringResource(R.string.open_settings))
            }
        }

        !state.microphoneGranted -> {
            Text(stringResource(R.string.microphone_explanation), style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onRequestMicrophonePermission,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.testTag("retry_microphone"),
            ) {
                Text(stringResource(R.string.retry_microphone))
            }
        }

        state.error != null -> {
            Text(
                text = audioErrorText(state.error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onRetryAudio,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.testTag("retry_audio"),
            ) {
                Text(stringResource(R.string.retry_microphone))
            }
        }

        else -> Unit
    }
}

@Composable
private fun audioErrorText(error: AudioInputError): String = when (error) {
    AudioInputError.PermissionMissing -> stringResource(R.string.microphone_permission_missing)
    is AudioInputError.InitializationFailed -> stringResource(R.string.audio_initialization_failed)
    is AudioInputError.ReadFailed -> stringResource(R.string.audio_read_failed, error.errorCode)
}

private val previewTuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
private val previewState = TunerUiState(
    mode = TunerMode.AUTO,
    tuning = previewTuning,
    selectedString = 0,
    headstockLayout = HeadstockLayout.SPLIT_3_3,
    referencePitch = ReferencePitch(440.0),
    notation = NoteNotation.SHARPS,
    favoriteIds = emptySet(),
    customTunings = emptyList(),
    reading = null,
    microphoneGranted = true,
    microphonePermanentlyDenied = false,
    listening = true,
    referenceTonePlaying = false,
    error = null,
)

@Preview(name = "Phone", widthDp = 432, heightDp = 936, showBackground = true)
@Preview(name = "Compact", widthDp = 360, heightDp = 720, showBackground = true)
@Preview(name = "Large font", widthDp = 432, heightDp = 936, fontScale = 1.3f, showBackground = true)
@Composable
private fun TunerScreenPreview() {
    TuneItAllTheme(darkTheme = true) {
        TunerScreen(
            state = previewState,
            onModeSelected = {},
            onStringSelected = {},
            onToggleFavorite = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            onOpenApplicationSettings = {},
        )
    }
}

@Preview(name = "Light", widthDp = 432, heightDp = 936, showBackground = true)
@Composable
private fun TunerScreenLightPreview() {
    TuneItAllTheme(darkTheme = false) {
        TunerScreen(
            state = previewState,
            onModeSelected = {},
            onStringSelected = {},
            onToggleFavorite = {},
            onOpenLibrary = {},
            onOpenSettings = {},
            onOpenApplicationSettings = {},
        )
    }
}
