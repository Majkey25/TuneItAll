package com.tuneitall.tuner.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.chordEventAt
import com.tuneitall.tuner.storage.NoteNotation
import kotlin.math.roundToLong

@Composable
fun ChordsScreen(
    state: ChordUiState,
    tunings: List<TuningPreset>,
    notation: NoteNotation,
    onTabSelected: (ChordTab) -> Unit,
    onChordSelected: (Chord) -> Unit,
    onTuningSelected: (String) -> Unit,
    onTransposeChanged: (Int) -> Unit,
    onLoadSong: (Uri) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClearSong: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onLoadSong)
    }
    val selectedTuning = tunings.firstOrNull { it.id == state.selectedTuningId } ?: tunings.first()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chords_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.destination_chords),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChordTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.tab == tab,
                    onClick = { onTabSelected(tab) },
                    label = {
                        Text(stringResource(if (tab == ChordTab.LIBRARY) R.string.chords_library else R.string.song_chords))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("chord_tab_${tab.name.lowercase()}"),
                )
            }
        }
        TuningSelector(tunings, selectedTuning.id, onTuningSelected)
        when (state.tab) {
            ChordTab.LIBRARY -> ChordLibrary(
                chord = state.selectedChord,
                tuning = selectedTuning,
                notation = notation,
                onChordSelected = onChordSelected,
            )

            ChordTab.SONG -> SongChordPanel(
                state = state,
                tuning = selectedTuning,
                notation = notation,
                onChooseAudio = { launcher.launch(arrayOf("audio/*")) },
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onTransposeChanged = onTransposeChanged,
                onClearSong = onClearSong,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChordLibrary(
    chord: Chord,
    tuning: TuningPreset,
    notation: NoteNotation,
    onChordSelected: (Chord) -> Unit,
) {
    Text(stringResource(R.string.chords_library), style = MaterialTheme.typography.titleLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("chord_root_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(12) { root ->
            FilterChip(
                selected = chord.rootPitchClass == root,
                onClick = { onChordSelected(chord.copy(rootPitchClass = root)) },
                label = { Text(formatChord(Chord(root, ChordQuality.MAJOR), notation)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("chord_root_$root"),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChordQuality.entries.forEach { quality ->
            FilterChip(
                selected = chord.quality == quality,
                onClick = { onChordSelected(chord.copy(quality = quality)) },
                label = { Text(chordQualityName(quality)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("chord_quality_${quality.name.lowercase()}"),
            )
        }
    }
    Text(
        formatChord(chord, notation),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().testTag("selected_chord_label"),
        textAlign = TextAlign.Center,
    )
    Text(stringResource(R.string.chord_voicing, tuning.name), style = MaterialTheme.typography.titleMedium)
    ChordDiagram(chord, tuning, notation, Modifier.fillMaxWidth())
}

@Composable
private fun SongChordPanel(
    state: ChordUiState,
    tuning: TuningPreset,
    notation: NoteNotation,
    onChooseAudio: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onTransposeChanged: (Int) -> Unit,
    onClearSong: () -> Unit,
) {
    Text(stringResource(R.string.song_detector_note), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onChooseAudio, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("choose_song")) {
            Text(stringResource(if (state.fileName == null) R.string.import_audio else R.string.replace_audio))
        }
        if (state.fileName != null) {
            OutlinedButton(
                onClick = onClearSong,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("remove_song"),
            ) {
                Text(stringResource(R.string.remove_song))
            }
        }
    }
    state.fileName?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("song_name"))
    }
    if (state.analyzing) {
        Text(stringResource(R.string.song_analysis_progress, state.analysisProgress))
        LinearProgressIndicator(
            progress = { state.analysisProgress / 100f },
            modifier = Modifier.fillMaxWidth().testTag("song_analysis_progress"),
        )
    }
    listOfNotNull(state.analysisError, state.playbackError).distinct().forEach { error ->
        Text(
            songErrorText(error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("song_error_${error.name.lowercase()}"),
        )
    }
    if (state.fileName == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { onTransposeChanged(state.transposeSemitones - 1) },
            enabled = state.transposeSemitones > -12,
            modifier = Modifier.heightIn(min = 48.dp).testTag("transpose_down"),
        ) { Text("−") }
        val transposeLabel = if (state.transposeSemitones > 0) {
            "+${state.transposeSemitones}"
        } else {
            state.transposeSemitones.toString()
        }
        Text(
            stringResource(R.string.transpose) + " $transposeLabel",
            modifier = Modifier.padding(horizontal = 16.dp).testTag("transpose_value"),
        )
        OutlinedButton(
            onClick = { onTransposeChanged(state.transposeSemitones + 1) },
            enabled = state.transposeSemitones < 12,
            modifier = Modifier.heightIn(min = 48.dp).testTag("transpose_up"),
        ) { Text("+") }
    }

    val activeEvent = chordEventAt(state.events, state.positionMillis)
    val activeChord = activeEvent?.chord?.transpose(state.transposeSemitones)
    Text(stringResource(R.string.current_chord), style = MaterialTheme.typography.titleMedium)
    Text(
        activeChord?.let { formatChord(it, notation) } ?: stringResource(R.string.no_current_chord),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().testTag("current_song_chord"),
        textAlign = TextAlign.Center,
    )
    if (activeChord != null) ChordDiagram(activeChord, tuning, notation, Modifier.fillMaxWidth())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onPlayPause,
            enabled = state.prepared,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("song_play_pause"),
        ) {
            Text(stringResource(if (state.playing) R.string.song_pause else R.string.song_play))
        }
        Text(
            "${formatSongTime(state.positionMillis)} / ${formatSongTime(state.durationMillis)}",
            modifier = Modifier.align(Alignment.CenterVertically).testTag("song_position"),
        )
    }
    SongSeekBar(state.positionMillis, state.durationMillis, onSeek)
    if (state.events.isNotEmpty()) {
        Text(stringResource(R.string.chord_timeline), style = MaterialTheme.typography.titleMedium)
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("chord_timeline"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = state.events,
                key = { index, event -> "$index:${event.startMillis}:${event.endMillis}" },
            ) { index, event ->
                val displayedChord = event.chord.transpose(state.transposeSemitones)
                FilterChip(
                    selected = event === activeEvent,
                    onClick = { onSeek(event.startMillis) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatChord(displayedChord, notation), fontWeight = FontWeight.Bold)
                            Text(formatSongTime(event.startMillis), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("song_chord_$index"),
                )
            }
        }
    }
}

@Composable
private fun SongSeekBar(positionMillis: Long, durationMillis: Long, onSeek: (Long) -> Unit) {
    var seeking by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableFloatStateOf(positionMillis.toFloat()) }
    LaunchedEffect(positionMillis, seeking) {
        if (!seeking) value = positionMillis.toFloat()
    }
    Slider(
        value = value.coerceIn(0f, maxOf(1L, durationMillis).toFloat()),
        onValueChange = {
            seeking = true
            value = it
        },
        onValueChangeFinished = {
            onSeek(value.roundToLong())
            seeking = false
        },
        valueRange = 0f..maxOf(1L, durationMillis).toFloat(),
        enabled = durationMillis > 0L,
        modifier = Modifier.fillMaxWidth().testTag("song_seek"),
    )
}

@Composable
private fun songErrorText(error: SongChordError): String = stringResource(
    when (error) {
        SongChordError.NO_AUDIO_TRACK -> R.string.song_error_no_audio
        SongChordError.TOO_LONG -> R.string.song_error_too_long
        SongChordError.UNSUPPORTED_PCM -> R.string.song_error_pcm
        SongChordError.DECODE_FAILED -> R.string.song_error_decode
        SongChordError.NO_CHORDS -> R.string.song_error_no_chords
        SongChordError.PLAYBACK_FAILED -> R.string.song_error_playback
    },
)
