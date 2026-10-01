package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tuneitall.tuner.R
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.NoteTrainingSets
import com.tuneitall.tuner.music.instructionalChordQualities
import com.tuneitall.tuner.music.midiToHertz
import com.tuneitall.tuner.music.nextTrainerItem
import com.tuneitall.tuner.music.trainerChoices
import com.tuneitall.tuner.music.voicingFrequencies
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private enum class TrainerExercise { CHORDS, NOTES }
internal enum class TrainerMode { QUIZ, LEARN }

@Composable
fun TrainerScreen(
    stats: TrainerStats,
    tunings: List<TuningPreset>,
    notation: NoteNotation,
    catalog: ChordShapeCatalog,
    onRecord: (Boolean) -> Unit,
    onReset: () -> Unit,
    noteSets: NoteTrainingSets,
    onNoteSetsChanged: (NoteTrainingSets) -> Unit,
) {
    var exercise by rememberSaveable { mutableStateOf(TrainerExercise.CHORDS) }
    var mode by rememberSaveable { mutableStateOf(TrainerMode.QUIZ) }
    var audioFailed by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    val player = remember { ReferenceTonePlayer() }
    val dispatcher = remember { Dispatchers.IO.limitedParallelism(1) }
    val scope = rememberCoroutineScope()
    var audioJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun stopAudio() {
        audioJob?.cancel()
        repeating = false
        player.stop()
    }

    fun play(repeat: Boolean = false, action: () -> Unit) {
        stopAudio()
        repeating = repeat
        audioJob = scope.launch {
            try {
                withContext(dispatcher) {
                    ensureActive()
                    action()
                }
                audioFailed = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: RuntimeException) {
                repeating = false
                audioFailed = true
            }
        }
    }

    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopAudio()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            audioJob?.cancel()
            player.close()
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("trainer_screen"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.destination_trainer), Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            var scoreMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { scoreMenu = true }) {
                    Text(stringResource(R.string.trainer_score, stats.correct, stats.attempts),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("trainer_score"))
                    Icon(painterResource(R.drawable.ic_expand_more),
                        stringResource(R.string.trainer_reset_score), Modifier.size(18.dp))
                }
                DropdownMenu(expanded = scoreMenu, onDismissRequest = { scoreMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.trainer_reset_score)) }, enabled = stats.attempts > 0,
                        onClick = { onReset(); scoreMenu = false })
                }
            }
        }
        TrainerChoiceRow(
            TrainerExercise.entries, exercise,
            label = { stringResource(if (it == TrainerExercise.CHORDS) R.string.trainer_chords else R.string.trainer_notes) },
            tag = { "trainer_exercise_${it.name.lowercase()}" },
            onSelected = { stopAudio(); exercise = it },
        )
        TrainerChoiceRow(
            TrainerMode.entries, mode,
            label = {
                stringResource(when {
                    it == TrainerMode.QUIZ -> R.string.trainer_quiz
                    exercise == TrainerExercise.NOTES -> R.string.trainer_listen
                    else -> R.string.trainer_learn
                })
            },
            tag = { "trainer_mode_${it.name.lowercase()}" },
            onSelected = { stopAudio(); mode = it },
        )
        if (audioFailed) Text(stringResource(R.string.audio_initialization_failed), color = MaterialTheme.colorScheme.error)
        when (exercise) {
            TrainerExercise.CHORDS -> ChordTrainer(
                mode, tunings, notation, catalog, onRecord, ::stopAudio,
                onPlay = { tuning, chord ->
                    catalog.shape(tuning.id, chord)?.let { shape ->
                        play { player.playChord(voicingFrequencies(tuning.notesLowToHigh, shape)) }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TrainerExercise.NOTES -> NoteTrainer(
                mode, noteSets, notation, onNoteSetsChanged, onRecord,
                onPlay = { pitch -> play { player.play(midiToHertz(60 + pitch)) } },
                onRepeat = { a, b -> play(repeat = true) { player.repeatNotes(midiToHertz(60 + a), midiToHertz(60 + b)) } },
                onStop = ::stopAudio, repeating = repeating, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun <T> TrainerChoiceRow(
    choices: List<T>, selected: T, label: @Composable (T) -> String,
    tag: (T) -> String, onSelected: (T) -> Unit,
) {
    SecondaryTabRow(selectedTabIndex = choices.indexOf(selected), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { choice ->
            Tab(
                selected = selected == choice, onClick = { onSelected(choice) },
                text = { Text(label(choice)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(tag(choice)),
            )
        }
    }
}

@Composable
internal fun ChordTrainer(
    mode: TrainerMode, tunings: List<TuningPreset>, notation: NoteNotation, catalog: ChordShapeCatalog,
    onRecord: (Boolean) -> Unit, onStop: () -> Unit, onPlay: (TuningPreset, Chord) -> Unit,
    modifier: Modifier,
) {
    val supported = remember(tunings, catalog) { tunings.filter { catalog.supports(it.id) } }
    require(supported.isNotEmpty())
    var tuningId by rememberSaveable { mutableStateOf("guitar-6-standard") }
    val tuning = supported.firstOrNull { it.id == tuningId } ?: supported.first()
    val chords = remember(tuning.id, catalog) {
        (0..11).flatMap { root -> instructionalChordQualities.map { Chord(root, it) } }
            .filter { catalog.shape(tuning.id, it) != null }
    }
    var lessonIndex by rememberSaveable(tuning.id) { mutableIntStateOf(Random.nextInt(chords.size)) }
    var quizIndex by rememberSaveable(tuning.id) { mutableIntStateOf(Random.nextInt(chords.size)) }
    var choiceSeed by rememberSaveable { mutableIntStateOf(Random.nextInt()) }
    var selected by remember(mode, tuning.id) { mutableStateOf<Chord?>(null) }
    var revealed by remember(mode, tuning.id) { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showDiagram by remember(mode, tuning.id) { mutableStateOf(false) }
    val chord = chords[if (mode == TrainerMode.LEARN) lessonIndex else quizIndex]
    val choices = remember(chord, choiceSeed) { trainerChoices(chord, choiceSeed) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("trainer_content"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TuningSelector(supported, tuning.id, onSelected = { onStop(); tuningId = it })
            if (mode == TrainerMode.LEARN) {
                Text(formatChord(chord, notation), Modifier.fillMaxWidth().testTag("trainer_chord_label"),
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().testTag("trainer_choose_chord")) {
                    Text(stringResource(R.string.trainer_choose_chord))
                }
                ChordDiagram(chord, tuning, notation, catalog, Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.trainer_question), style = MaterialTheme.typography.titleLarge)
                TrainerAnswerGrid(choices, selected == null && !revealed, { formatChord(it, notation) },
                    { "trainer_answer_${formatChord(it, notation)}" }) {
                    if (selected == null && !revealed) { selected = it; onRecord(it == chord) }
                }
                if (selected != null || revealed) {
                    if (selected != null) TrainerFeedback(selected == chord, formatChord(chord, notation), "trainer_feedback")
                    else Text(stringResource(R.string.trainer_answer_revealed, formatChord(chord, notation)),
                        Modifier.testTag("trainer_feedback"), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showDiagram = !showDiagram }) { Text(stringResource(R.string.show_chord_diagrams)) }
                    if (showDiagram) ChordDiagram(chord, tuning, notation, catalog, Modifier.fillMaxWidth())
                } else {
                    TextButton(onClick = { revealed = true }, modifier = Modifier.testTag("trainer_reveal")) {
                        Text(stringResource(R.string.trainer_show_answer))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().testTag("trainer_controls"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(tuning, chord) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_play")) {
                Text(stringResource(R.string.trainer_play_chord))
            }
            OutlinedButton(
                onClick = {
                    onStop()
                    val nextIndex = nextTrainerItem(chords.indices.toList(),
                        if (mode == TrainerMode.LEARN) lessonIndex else quizIndex)
                    if (mode == TrainerMode.LEARN) lessonIndex = nextIndex
                    else {
                        quizIndex = nextIndex
                        choiceSeed = Random.nextInt()
                        selected = null
                        revealed = false
                        showDiagram = false
                    }
                    onPlay(tuning, chords[nextIndex])
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_next_question"),
            ) { Text(stringResource(R.string.trainer_next)) }
        }
    }
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text(stringResource(R.string.trainer_choose_chord)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChordPicker(chords[lessonIndex], notation) { choice ->
                    val index = chords.indexOf(choice)
                    if (index >= 0) { onStop(); lessonIndex = index }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
internal fun <T> TrainerAnswerGrid(
    choices: List<T>, enabled: Boolean, label: (T) -> String, tag: (T) -> String, onAnswer: (T) -> Unit,
) {
    choices.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { choice ->
                OutlinedButton(onClick = { onAnswer(choice) }, enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(tag(choice))) { Text(label(choice)) }
            }
            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun TrainerFeedback(correct: Boolean, answer: String, tag: String) {
    Text(
        if (correct) stringResource(R.string.trainer_correct_answer, answer) else stringResource(R.string.trainer_incorrect, answer),
        color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
        textAlign = TextAlign.Center,
    )
}
