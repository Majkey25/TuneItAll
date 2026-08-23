package com.tuneitall.tuner.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.findPlayableVoicing
import com.tuneitall.tuner.music.trainerChoices
import com.tuneitall.tuner.music.voicingFrequencies
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TrainerMode {
    LEARN,
    QUIZ,
}

@Composable
fun TrainerScreen(
    stats: TrainerStats,
    tunings: List<TuningPreset>,
    notation: NoteNotation,
    onRecord: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(TrainerMode.LEARN) }
    var selectedTuningId by rememberSaveable { mutableStateOf(DEFAULT_TRAINER_TUNING) }
    var chordIndex by rememberSaveable { mutableIntStateOf(0) }
    var questionSeed by rememberSaveable { mutableIntStateOf(1) }
    var selectedAnswer by remember { mutableStateOf<Chord?>(null) }
    var audioFailed by remember { mutableStateOf(false) }
    val chords = remember { buildList { repeat(12) { root -> ChordQuality.entries.forEach { add(Chord(root, it)) } } } }
    val tuning = tunings.firstOrNull { it.id == selectedTuningId } ?: tunings.first()
    val tonePlayer = remember { ReferenceTonePlayer() }
    val scope = rememberCoroutineScope()
    DisposableEffect(tonePlayer) { onDispose(tonePlayer::close) }

    fun playChord(chord: Chord) {
        scope.launch {
            try {
                val frequencies = withContext(Dispatchers.Default) {
                    val voicing = findPlayableVoicing(tuning.notesLowToHigh, chord) ?: return@withContext null
                    voicingFrequencies(tuning.notesLowToHigh, voicing)
                } ?: return@launch
                withContext(Dispatchers.IO) { tonePlayer.playChord(frequencies) }
                audioFailed = false
            } catch (_: CancellationException) {
                Unit
            } catch (_: RuntimeException) {
                audioFailed = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("trainer_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.destination_trainer),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainerMode.entries.forEach { trainerMode ->
                FilterChip(
                    selected = mode == trainerMode,
                    onClick = {
                        mode = trainerMode
                        selectedAnswer = null
                    },
                    label = {
                        Text(stringResource(if (trainerMode == TrainerMode.LEARN) R.string.trainer_learn else R.string.trainer_quiz))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_mode_${trainerMode.name.lowercase()}"),
                )
            }
        }
        TuningSelector(tunings, tuning.id, { selectedTuningId = it })
        Text(
            stringResource(R.string.trainer_score, stats.correct, stats.attempts),
            modifier = Modifier.testTag("trainer_score"),
        )
        if (audioFailed) Text(stringResource(R.string.audio_initialization_failed), color = MaterialTheme.colorScheme.error)

        when (mode) {
            TrainerMode.LEARN -> {
                val chord = chords[Math.floorMod(chordIndex, chords.size)]
                Text(
                    formatChord(chord, notation),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().testTag("trainer_chord_label"),
                    textAlign = TextAlign.Center,
                )
                ChordDiagram(chord, tuning, notation, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { chordIndex = Math.floorMod(chordIndex - 1, chords.size) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.trainer_previous)) }
                    Button(
                        onClick = { playChord(chord) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_play"),
                    ) { Text(stringResource(R.string.trainer_play_chord)) }
                    OutlinedButton(
                        onClick = { chordIndex = (chordIndex + 1) % chords.size },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.trainer_next)) }
                }
            }

            TrainerMode.QUIZ -> {
                val answer = chords[Math.floorMod(questionSeed * QUESTION_STEP, chords.size)]
                val choices = trainerChoices(answer, questionSeed)
                Text(
                    stringResource(R.string.trainer_question),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                ChordDiagram(answer, tuning, notation, Modifier.fillMaxWidth(), showLabel = false)
                Button(
                    onClick = { playChord(answer) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_play"),
                ) { Text(stringResource(R.string.trainer_play_chord)) }
                choices.chunked(2).forEach { rowChoices ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowChoices.forEach { choice ->
                            OutlinedButton(
                                onClick = {
                                    if (selectedAnswer == null) {
                                        selectedAnswer = choice
                                        onRecord(choice == answer)
                                    }
                                },
                                enabled = selectedAnswer == null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("trainer_answer_${formatChord(choice, notation)}"),
                            ) { Text(formatChord(choice, notation)) }
                        }
                    }
                }
                selectedAnswer?.let { selected ->
                    Text(
                        if (selected == answer) {
                            stringResource(R.string.trainer_correct)
                        } else {
                            stringResource(R.string.trainer_incorrect, formatChord(answer, notation))
                        },
                        color = if (selected == answer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().testTag("trainer_feedback"),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            questionSeed++
                            selectedAnswer = null
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_next_question"),
                    ) { Text(stringResource(R.string.trainer_next)) }
                }
            }
        }
        if (stats.attempts > 0) {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.trainer_reset_score))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private const val DEFAULT_TRAINER_TUNING = "guitar-6-standard"
private const val QUESTION_STEP = 7
