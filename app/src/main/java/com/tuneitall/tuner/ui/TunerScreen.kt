package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

        Text(
            text = currentNote?.let { formatNote(it, state.notation) } ?: "—",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 82.sp, lineHeight = 88.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (state.reading?.inTune == true) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )

        CentsRail(
            cents = state.reading?.cents,
            inTune = state.reading?.inTune == true,
            idleText = stringResource(
                if (state.mode == TunerMode.CHROMATIC) R.string.play_any_note else R.string.no_pitch_detected,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.reading?.let { stringResource(R.string.frequency_value, it.hertz) }
                    ?: stringResource(R.string.no_frequency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = state.reading?.let { stringResource(R.string.cents_value, it.cents) }
                    ?: stringResource(R.string.no_cents),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }

        if (state.referencePitch.hertz != ReferencePitch.DEFAULT_HERTZ) {
            Text(
                text = stringResource(R.string.reference_pitch_value, state.referencePitch.hertz),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        MicrophoneStatus(state, onOpenApplicationSettings)

        if (state.mode != TunerMode.CHROMATIC) {
            Text(
                text = stringResource(R.string.tap_string_to_hear),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TunerTopBar(
    state: TunerUiState,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    val favoriteDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.mode == TunerMode.CHROMATIC) {
            Text(
                text = stringResource(R.string.chromatic_tuner),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        } else {
            TextButton(
                onClick = onOpenLibrary,
                colors = textButtonColors,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "${state.tuning.name}  ›",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = onToggleFavorite,
                colors = textButtonColors,
                modifier = Modifier.semantics { contentDescription = favoriteDescription },
            ) {
                Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
            }
        }
        TextButton(onClick = onOpenSettings, colors = textButtonColors) {
            Text(stringResource(R.string.settings), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ModeSelector(selected: TunerMode, onSelected: (TunerMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
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
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MicrophoneStatus(state: TunerUiState, onOpenApplicationSettings: () -> Unit) {
    when {
        state.microphonePermanentlyDenied -> {
            Text(stringResource(R.string.microphone_explanation), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenApplicationSettings) { Text(stringResource(R.string.open_settings)) }
        }

        !state.microphoneGranted -> Text(
            text = stringResource(R.string.microphone_explanation),
            style = MaterialTheme.typography.bodySmall,
        )

        state.error != null -> Text(
            text = audioErrorText(state.error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )

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
