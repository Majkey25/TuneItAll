package com.tuneitall.tuner.ui

import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.storage.NoteNotation

data class NoteParts(
    val letter: String,
    val accidental: String,
    val octave: String,
)

fun noteParts(note: MidiNote, notation: NoteNotation): NoteParts {
    val names = if (notation == NoteNotation.SHARPS) SHARP_NAMES else FLAT_NAMES
    val name = names[note.value % NOTES_PER_OCTAVE]
    return NoteParts(
        letter = name.first().toString(),
        accidental = name.drop(1),
        octave = (note.value / NOTES_PER_OCTAVE - 1).toString(),
    )
}

fun formatNote(note: MidiNote, notation: NoteNotation): String = noteParts(note, notation).run {
    letter + accidental + octave
}

private const val NOTES_PER_OCTAVE = 12
private val SHARP_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val FLAT_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
