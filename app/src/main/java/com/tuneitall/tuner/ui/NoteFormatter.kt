package com.tuneitall.tuner.ui

import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.storage.NoteNotation

fun formatNote(note: MidiNote, notation: NoteNotation): String {
    val names = if (notation == NoteNotation.SHARPS) SHARP_NAMES else FLAT_NAMES
    return names[note.value % NOTES_PER_OCTAVE] + (note.value / NOTES_PER_OCTAVE - 1)
}

private const val NOTES_PER_OCTAVE = 12
private val SHARP_NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val FLAT_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
