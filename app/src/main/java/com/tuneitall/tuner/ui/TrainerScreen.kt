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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.instructionalChordQualities
import com.tuneitall.tuner.music.NoteQuestion
import com.tuneitall.tuner.music.midiToHertz
import com.tuneitall.tuner.music.noteQuestion
import com.tuneitall.tuner.music.trainerChoices
import com.tuneitall.tuner.music.voicingFrequencies
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TrainerExercise {
    CHORDS,
    NOTES,
}

private enum class TrainerMode {
    LEARN,
    QUIZ,
}

@Composable
fun TrainerScreen(
    stats: TrainerStats,
    tunings: List<TuningPreset>,
    notation: NoteNotation,
    catalog: ChordShapeCatalog,
    onRecord: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val supportedTunings = remember(tunings, catalog) { tunings.filter { catalog.supports(it.id) } }
    require(supportedTunings.isNotEmpty())
    var exercise by rememberSaveable { mutableStateOf(TrainerExercise.CHORDS) }
    var mode by rememberSaveable { mutableStateOf(TrainerMode.LEARN) }
    var selectedTuningId by rememberSaveable { mutableStateOf(DEFAULT_TRAINER_TUNING) }
    var chordIndex by rememberSaveable { mutableIntStateOf(0) }
    var questionSeed by rememberSaveable { mutableIntStateOf(1) }
    var selectedChordAnswer by remember { mutableStateOf<Chord?>(null) }
    var selectedNoteAnswer by remember { mutableStateOf<Int?>(null) }
    var audioFailed by remember { mutableStateOf(false) }
    val chords = remember { buildList { repeat(12) { root -> instructionalChordQualities.forEach { add(Chord(root, it)) } } } }
    val tuning = supportedTunings.firstOrNull { it.id == selectedTuningId } ?: supportedTunings.first()
    val tonePlayer = remember { ReferenceTonePlayer() }
    val scope = rememberCoroutineScope()
    DisposableEffect(tonePlayer) { onDispose(tonePlayer::close) }

    fun playChord(chord: Chord) {
        val voicing = catalog.shape(tuning.id, chord) ?: return
        val frequencies = voicingFrequencies(tuning.notesLowToHigh, voicing)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { tonePlayer.playChord(frequencies) }
                audioFailed = false
            } catch (_: CancellationException) {
                Unit
            } catch (_: RuntimeException) {
                audioFailed = true
            }
        }
    }

    fun playNote(question: NoteQuestion) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { tonePlayer.play(midiToHertz(question.midiNote)) }
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
        ChoiceRow(
            choices = TrainerExercise.entries,
            selected = exercise,
            label = { stringResource(if (it == TrainerExercise.CHORDS) R.string.trainer_chords else R.string.trainer_notes) },
            tag = { "trainer_exercise_${it.name.lowercase()}" },
            onSelected = {
                exercise = it
                selectedChordAnswer = null
                selectedNoteAnswer = null
            },
        )
        if (exercise == TrainerExercise.CHORDS) {
            ChoiceRow(
                choices = TrainerMode.entries,
                selected = mode,
                label = { stringResource(if (it == TrainerMode.LEARN) R.string.trainer_learn else R.string.trainer_quiz) },
                tag = { "trainer_mode_${it.name.lowercase()}" },
                onSelected = {
                    mode = it
                    selectedChordAnswer = null
                },
            )
            TuningSelector(supportedTunings, tuning.id, { selectedTuningId = it })
        }
        Text(stringResource(R.string.trainer_score, stats.correct, stats.attempts), Modifier.testTag("trainer_score"))
        if (audioFailed) Text(stringResource(R.string.audio_initialization_failed), color = MaterialTheme.colorScheme.error)

        when (exercise) {
            TrainerExercise.CHORDS -> when (mode) {
                TrainerMode.LEARN -> ChordLesson(
                    chord = chords[Math.floorMod(chordIndex, chords.size)],
                    tuning = tuning,
                    notation = notation,
                    catalog = catalog,
                    onPrevious = { chordIndex = Math.floorMod(chordIndex - 1, chords.size) },
                    onPlay = ::playChord,
                    onNext = { chordIndex = (chordIndex + 1) % chords.size },
                )

                TrainerMode.QUIZ -> ChordQuiz(
                    answer = chords[Math.floorMod(questionSeed * CHORD_QUESTION_STEP, chords.size)],
                    choices = trainerChoices(chords[Math.floorMod(questionSeed * CHORD_QUESTION_STEP, chords.size)], questionSeed),
                    selectedAnswer = selectedChordAnswer,
                    tuning = tuning,
                    notation = notation,
                    catalog = catalog,
                    onPlay = ::playChord,
                    onAnswer = { choice, answer ->
                        if (selectedChordAnswer == null) {
                            selectedChordAnswer = choice
                            onRecord(choice == answer)
                        }
                    },
                    onNext = {
                        questionSeed++
                        selectedChordAnswer = null
                    },
                )
            }

            TrainerExercise.NOTES -> {
                val question = noteQuestion(questionSeed)
                NoteQuiz(
                    question = question,
                    selectedAnswer = selectedNoteAnswer,
                    notation = notation,
                    onPlay = { playNote(question) },
                    onAnswer = { choice ->
                        if (selectedNoteAnswer == null) {
                            selectedNoteAnswer = choice
                            onRecord(choice == question.answerPitchClass)
                        }
                    },
                    onNext = {
                        questionSeed++
                        selectedNoteAnswer = null
                    },
                )
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

@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                onClick = { onSelected(choice) },
                label = { Text(label(choice)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(tag(choice)),
            )
        }
    }
}

@Composable
private fun ChordLesson(
    chord: Chord,
    tuning: TuningPreset,
    notation: NoteNotation,
    catalog: ChordShapeCatalog,
    onPrevious: () -> Unit,
    onPlay: (Chord) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        formatChord(chord, notation),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().testTag("trainer_chord_label"),
        textAlign = TextAlign.Center,
    )
    ChordDiagram(chord, tuning, notation, catalog, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrevious, Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.trainer_previous))
        }
        Button(onClick = { onPlay(chord) }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_play")) {
            Text(stringResource(R.string.trainer_play_chord))
        }
        OutlinedButton(onClick = onNext, Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.trainer_next))
        }
    }
}

@Composable
private fun ChordQuiz(
    answer: Chord,
    choices: List<Chord>,
    selectedAnswer: Chord?,
    tuning: TuningPreset,
    notation: NoteNotation,
    catalog: ChordShapeCatalog,
    onPlay: (Chord) -> Unit,
    onAnswer: (Chord, Chord) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        stringResource(R.string.trainer_question),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = { onPlay(answer) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_play"),
    ) { Text(stringResource(R.string.trainer_play_chord)) }
    AnswerGrid(
        choices = choices,
        enabled = selectedAnswer == null,
        label = { formatChord(it, notation) },
        tag = { "trainer_answer_${formatChord(it, notation)}" },
        onAnswer = { onAnswer(it, answer) },
    )
    selectedAnswer?.let { selected ->
        Feedback(selected == answer, formatChord(answer, notation), R.string.trainer_incorrect, "trainer_feedback")
        ChordDiagram(answer, tuning, notation, catalog, Modifier.fillMaxWidth())
        NextButton(onNext, "trainer_next_question")
    }
}

@Composable
private fun NoteQuiz(
    question: NoteQuestion,
    selectedAnswer: Int?,
    notation: NoteNotation,
    onPlay: () -> Unit,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    Text(
        stringResource(R.string.trainer_note_question),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_note_play"),
    ) { Text(stringResource(R.string.trainer_play_note)) }
    AnswerGrid(
        choices = question.choices,
        enabled = selectedAnswer == null,
        label = { formatPitchClass(it, notation) },
        tag = { "trainer_note_answer_$it" },
        onAnswer = onAnswer,
    )
    selectedAnswer?.let { selected ->
        Feedback(
            selected == question.answerPitchClass,
            formatPitchClass(question.answerPitchClass, notation),
            R.string.trainer_note_incorrect,
            "trainer_note_feedback",
        )
        NextButton(onNext, "trainer_note_next")
    }
}

@Composable
private fun <T> AnswerGrid(
    choices: List<T>,
    enabled: Boolean,
    label: (T) -> String,
    tag: (T) -> String,
    onAnswer: (T) -> Unit,
) {
    choices.chunked(2).forEach { rowChoices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowChoices.forEach { choice ->
                OutlinedButton(
                    onClick = { onAnswer(choice) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(tag(choice)),
                ) { Text(label(choice)) }
            }
        }
    }
}

@Composable
private fun Feedback(correct: Boolean, answer: String, incorrectString: Int, tag: String) {
    Text(
        if (correct) stringResource(R.string.trainer_correct) else stringResource(incorrectString, answer),
        color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NextButton(onNext: () -> Unit, tag: String) {
    Button(onClick = onNext, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Text(stringResource(R.string.trainer_next))
    }
}

private const val DEFAULT_TRAINER_TUNING = "guitar-6-standard"
private const val CHORD_QUESTION_STEP = 7
