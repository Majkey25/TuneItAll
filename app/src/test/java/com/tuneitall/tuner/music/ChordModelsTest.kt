package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals

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
