package com.tuneitall.tuner.music

import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.ui.formatChord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChordModelsTest {
    @Test
    fun `chord qualities expose exact pitch classes and transpose safely`() {
        val cMajor = Chord(rootPitchClass = 0, quality = ChordQuality.MAJOR)

        assertEquals(setOf(0, 4, 7), cMajor.pitchClasses)
        assertEquals(Chord(11, ChordQuality.MAJOR), cMajor.transpose(-1))
        assertEquals(Chord(1, ChordQuality.MAJOR), cMajor.transpose(13))
        assertEquals(setOf(0, 4, 7, 10), Chord(0, ChordQuality.DOMINANT_SEVENTH).pitchClasses)
        assertEquals(setOf(4, 11), Chord(4, ChordQuality.POWER).pitchClasses)
    }

    @Test
    fun `common qualities expose exact pitch classes`() {
        assertEquals(setOf(0, 5, 7), Chord(0, ChordQuality.SUSPENDED_FOURTH).pitchClasses)
        assertEquals(setOf(0, 3, 6), Chord(0, ChordQuality.DIMINISHED).pitchClasses)
        assertEquals(setOf(0, 4, 8), Chord(0, ChordQuality.AUGMENTED).pitchClasses)
        assertEquals(setOf(0, 4, 7, 11), Chord(0, ChordQuality.MAJOR_SEVENTH).pitchClasses)
        assertEquals(setOf(0, 3, 7, 10), Chord(0, ChordQuality.MINOR_SEVENTH).pitchClasses)
        assertEquals(setOf(0, 3, 6, 10), Chord(0, ChordQuality.HALF_DIMINISHED_SEVENTH).pitchClasses)
        assertEquals(setOf(0, 2, 4, 7), Chord(0, ChordQuality.ADD_NINTH).pitchClasses)
    }

    @Test
    fun `transposition moves root and inversion bass`() {
        assertEquals(
            Chord(2, ChordQuality.MAJOR, bassPitchClass = 6),
            Chord(0, ChordQuality.MAJOR, bassPitchClass = 4).transpose(2),
        )
        assertFailsWith<IllegalArgumentException> {
            Chord(0, ChordQuality.MAJOR, bassPitchClass = 12)
        }
    }

    @Test
    fun `formatting includes quality and inversion bass`() {
        assertEquals("Cmaj7/E", formatChord(Chord(0, ChordQuality.MAJOR_SEVENTH, 4), NoteNotation.SHARPS))
        assertEquals("D♭m7/A♭", formatChord(Chord(1, ChordQuality.MINOR_SEVENTH, 8), NoteNotation.FLATS))
    }

    @Test
    fun `timeline lookup respects event boundaries and gaps`() {
        val c = ChordEvent(0L, 1_000L, Chord(0, ChordQuality.MAJOR), 0.9)
        val g = ChordEvent(1_700L, 2_500L, Chord(7, ChordQuality.MAJOR), 0.8)
        val events = listOf(c, g)

        assertEquals(c, chordEventAt(events, 0L))
        assertEquals(c, chordEventAt(events, 999L))
        assertEquals(c, chordEventAt(events, 1_100L))
        assertEquals(c, chordEventAt(events, 1_500L))
        assertEquals(null, chordEventAt(events, 1_501L))
        assertEquals(g, chordEventAt(events, 1_700L))
        assertEquals(null, chordEventAt(events, 2_500L))
    }
}
