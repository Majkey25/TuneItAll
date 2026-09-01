package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SongAnalysisModelsTest {
    @Test
    fun `note ranges enforce exact supported instruments`() {
        assertEquals(21..108, NoteRange.ANY.midiRange)
        assertEquals(40..88, NoteRange.GUITAR.midiRange)
        assertEquals(28..72, NoteRange.BASS.midiRange)
        assertEquals(55..100, NoteRange.VIOLIN.midiRange)
        assertEquals(21..108, NoteRange.PIANO.midiRange)
    }

    @Test
    fun `song events expose duration and reject invalid boundaries`() {
        val note: SongEvent = NoteEvent(100L, 500L, 69, 0.9)
        val chord: SongEvent = ChordEvent(0L, 1_000L, Chord(0, ChordQuality.MAJOR), 0.8)

        assertEquals(400L, note.durationMillis)
        assertEquals(1_000L, chord.durationMillis)
        assertFailsWith<IllegalArgumentException> { NoteEvent(100L, 100L, 69, 0.9) }
        assertFailsWith<IllegalArgumentException> { NoteEvent(0L, 100L, 128, 0.9) }
        assertFailsWith<IllegalArgumentException> { NoteEvent(0L, 100L, 69, 1.1) }
    }

    @Test
    fun `song timeline lookup supports notes and short gaps`() {
        val a = NoteEvent(0L, 1_000L, 69, 0.9)
        val c = NoteEvent(1_400L, 2_000L, 72, 0.8)

        assertEquals(a, songEventAt(listOf(a, c), 1_200L))
        assertEquals(c, songEventAt(listOf(a, c), 1_500L))
    }
}
