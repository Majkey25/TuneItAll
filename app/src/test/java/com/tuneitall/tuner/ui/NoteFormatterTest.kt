package com.tuneitall.tuner.ui

import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.storage.NoteNotation
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteFormatterTest {
    @Test
    fun `formats scientific notes with selected accidental notation`() {
        assertEquals("C4", formatNote(MidiNote(60), NoteNotation.SHARPS))
        assertEquals("C♯4", formatNote(MidiNote(61), NoteNotation.SHARPS))
        assertEquals("D♭4", formatNote(MidiNote(61), NoteNotation.FLATS))
        assertEquals("B0", formatNote(MidiNote(23), NoteNotation.FLATS))
    }
}
