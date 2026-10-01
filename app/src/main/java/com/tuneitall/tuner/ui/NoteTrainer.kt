package com.tuneitall.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.R
import com.tuneitall.tuner.music.NoteBank
import com.tuneitall.tuner.music.NoteTrainingSets
import com.tuneitall.tuner.music.noteQuestion
import com.tuneitall.tuner.storage.NoteNotation
import kotlin.random.Random

@Composable
internal fun NoteTrainer(
    mode: TrainerMode,
    sets: NoteTrainingSets,
    notation: NoteNotation,
    onSetsChanged: (NoteTrainingSets) -> Unit,
    onRecord: (Boolean) -> Unit,
    onPlay: (Int) -> Unit,
    onRepeat: (Int, Int) -> Unit,
    onStop: () -> Unit,
    repeating: Boolean,
    modifier: Modifier,
) {
    var bank by rememberSaveable { mutableStateOf(NoteBank.LEARNING) }
    val notes = sets.notes(bank)
    val sortedNotes = remember(notes) { notes.sorted() }
    var editing by remember { mutableStateOf(false) }
    var bankMenu by remember { mutableStateOf(false) }
    var showComparison by remember(mode, bank, notes) { mutableStateOf(false) }
    var seed by rememberSaveable(bank, notes) { mutableIntStateOf(Random.nextInt()) }
    var previous by rememberSaveable(bank, notes) { mutableStateOf<Int?>(null) }
    var selected by remember(mode, bank, notes, seed) { mutableStateOf<Int?>(null) }
    var revealed by remember(mode, bank, notes, seed) { mutableStateOf(false) }
    val question = remember(seed, notes, previous) { if (notes.size >= 2) noteQuestion(seed, notes, previous) else null }
    var first by rememberSaveable(bank, notes, mode) { mutableIntStateOf(sortedNotes.firstOrNull() ?: 0) }
    var second by rememberSaveable(bank, notes, mode) { mutableIntStateOf(sortedNotes.getOrNull(1) ?: first) }
    val compareVisible = notes.isNotEmpty() && mode == TrainerMode.LEARN

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { bankMenu = true }, modifier = Modifier.testTag("trainer_bank_menu")) {
                    Text(stringResource(if (bank == NoteBank.LEARNING) R.string.trainer_learning_bank else R.string.trainer_learned_bank))
                    Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                    NoteBank.entries.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(stringResource(if (choice == NoteBank.LEARNING) R.string.trainer_learning_bank else R.string.trainer_learned_bank)) },
                            onClick = { onStop(); bank = choice; bankMenu = false },
                            modifier = Modifier.testTag("trainer_bank_${choice.name.lowercase()}"),
                        )
                    }
                }
            }
            TextButton(onClick = { onStop(); editing = true }, modifier = Modifier.testTag("trainer_edit_notes")) {
                Text(stringResource(R.string.trainer_edit_notes))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("trainer_note_content"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.trainer_set_count, notes.size), style = MaterialTheme.typography.labelMedium)
            if (notes.isEmpty()) {
                Text(stringResource(R.string.trainer_empty_bank))
            } else if (mode == TrainerMode.LEARN) {
                Text(stringResource(R.string.trainer_tap_note), style = MaterialTheme.typography.bodyMedium)
                MusicChoiceGrid(
                    sortedNotes, 4, selected = { false }, label = { formatMidiNote(60 + it, notation) },
                    tag = { "trainer_listen_$it" }, onSelected = onPlay,
                )
            } else if (question == null) {
                Text(stringResource(R.string.trainer_two_notes))
            } else {
                Text(stringResource(R.string.trainer_note_question), style = MaterialTheme.typography.titleLarge)
                TrainerAnswerGrid(
                    question.choices, selected == null && !revealed,
                    label = { formatPitchClass(it, notation) }, tag = { "trainer_note_answer_$it" },
                ) { choice ->
                    if (selected == null && !revealed) {
                        onStop()
                        selected = choice
                        first = question.answerPitchClass
                        second = if (choice != first) choice else sortedNotes.first { it != first }
                        onRecord(choice == question.answerPitchClass)
                    }
                }
                selected?.let {
                    TrainerFeedback(it == question.answerPitchClass,
                        formatMidiNote(question.midiNote, notation), "trainer_note_feedback")
                }
                if (revealed) Text(
                    stringResource(R.string.trainer_answer_revealed, formatMidiNote(question.midiNote, notation)),
                    Modifier.testTag("trainer_note_feedback"), style = MaterialTheme.typography.titleMedium,
                )
                if (selected != null || revealed) {
                    TextButton(onClick = { onStop(); showComparison = true },
                        modifier = Modifier.testTag("trainer_compare_notes")) { Text(stringResource(R.string.trainer_compare)) }
                } else {
                    TextButton(
                        onClick = {
                            revealed = true
                            first = question.answerPitchClass
                            second = sortedNotes.first { it != first }
                        },
                        modifier = Modifier.testTag("trainer_note_reveal"),
                    ) { Text(stringResource(R.string.trainer_show_answer)) }
                }
            }
            if (compareVisible) {
                Text(stringResource(R.string.trainer_compare), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotePicker("A", first, sortedNotes, notation, Modifier.weight(1f)) { onStop(); first = it }
                    NotePicker("B", second, sortedNotes, notation, Modifier.weight(1f)) { onStop(); second = it }
                }
            }
        }
        Column(Modifier.fillMaxWidth().testTag("trainer_note_controls"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (compareVisible) {
                NoteComparisonPlayback(first, second, onPlay, onRepeat, onStop, repeating)
            }
            if (mode == TrainerMode.QUIZ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { question?.let { onPlay(it.answerPitchClass) } }, enabled = question != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_note_play"),
                    ) { Text(stringResource(R.string.trainer_play_note)) }
                    OutlinedButton(
                        onClick = {
                            val nextSeed = Random.nextInt()
                            val nextQuestion = noteQuestion(nextSeed, notes, question?.answerPitchClass)
                            onStop()
                            selected = null
                            revealed = false
                            previous = question?.answerPitchClass
                            seed = nextSeed
                            onPlay(nextQuestion.answerPitchClass)
                        },
                        enabled = question != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_note_next"),
                    ) { Text(stringResource(R.string.trainer_next)) }
                }
            }
        }
    }
    if (editing) NoteSetEditor(
        bank, notes, notation,
        onDismiss = { editing = false },
        onSave = { onStop(); onSetsChanged(sets.update(bank, it)); editing = false },
    )
    if (showComparison) AlertDialog(
        onDismissRequest = { onStop(); showComparison = false },
        title = { Text(stringResource(R.string.trainer_compare)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotePicker("A", first, sortedNotes, notation, Modifier.weight(1f)) { onStop(); first = it }
                    NotePicker("B", second, sortedNotes, notation, Modifier.weight(1f)) { onStop(); second = it }
                }
                NoteComparisonPlayback(first, second, onPlay, onRepeat, onStop, repeating)
            }
        },
        confirmButton = {
            TextButton(onClick = { onStop(); showComparison = false }, modifier = Modifier.testTag("trainer_close_compare")) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun NoteComparisonPlayback(
    first: Int, second: Int, onPlay: (Int) -> Unit, onRepeat: (Int, Int) -> Unit,
    onStop: () -> Unit, repeating: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPlay(first) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_play_a")) {
            Text(stringResource(R.string.trainer_hear_slot, "A"))
        }
        OutlinedButton(onClick = { onPlay(second) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("trainer_play_b")) {
            Text(stringResource(R.string.trainer_hear_slot, "B"))
        }
    }
    Button(
        onClick = { if (repeating) onStop() else onRepeat(first, second) },
        enabled = first != second,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_repeat"),
    ) { Text(stringResource(if (repeating) R.string.trainer_stop_repeat else R.string.trainer_repeat)) }
}

@Composable
private fun NotePicker(
    slot: String, selected: Int, notes: List<Int>, notation: NoteNotation, modifier: Modifier, onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trainer_pick_$slot")) {
            Text("$slot · ${formatMidiNote(60 + selected, notation)} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            notes.forEach { note ->
                DropdownMenuItem(
                    text = { Text(formatMidiNote(60 + note, notation)) },
                    onClick = { onSelected(note); expanded = false },
                    modifier = Modifier.testTag("trainer_pick_${slot}_$note"),
                )
            }
        }
    }
}

@Composable
private fun NoteSetEditor(
    bank: NoteBank, notes: Set<Int>, notation: NoteNotation,
    onDismiss: () -> Unit, onSave: (Set<Int>) -> Unit,
) {
    var draft by remember { mutableStateOf(notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (bank == NoteBank.LEARNING) R.string.trainer_learning_bank else R.string.trainer_learned_bank)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.trainer_edit_notes_help))
                MusicChoiceGrid(
                    (0..11).toList(), 3, selected = { it in draft },
                    label = { formatPitchClass(it, notation) }, tag = { "trainer_set_note_$it" },
                    onSelected = { draft = if (it in draft) draft - it else draft + it },
                )
                Row {
                    TextButton(onClick = { draft = (0..11).toSet() }, modifier = Modifier.testTag("trainer_set_all")) {
                        Text(stringResource(R.string.trainer_all_notes))
                    }
                    TextButton(onClick = { draft = emptySet() }, modifier = Modifier.testTag("trainer_set_none")) {
                        Text(stringResource(R.string.trainer_no_notes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, modifier = Modifier.testTag("trainer_set_save")) {
                Text(stringResource(R.string.trainer_save_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
