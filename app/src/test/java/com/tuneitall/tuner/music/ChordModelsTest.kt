package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.TuningCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordModelsTest {
    @Test
    fun `chord qualities expose exact pitch classes and transpose safely`() {
        val cMajor = Chord(rootPitchClass = 0, quality = ChordQuality.MAJOR)

        assertEquals(setOf(0, 4, 7), cMajor.pitchClasses)
        assertEquals(Chord(11, ChordQuality.MAJOR), cMajor.transpose(-1))
        assertEquals(Chord(1, ChordQuality.MAJOR), cMajor.transpose(13))
        assertEquals(setOf(0, 4, 7, 10), Chord(0, ChordQuality.DOMINANT_SEVENTH).pitchClasses)
    }

    @Test
    fun `generated voicing fits selected tuning and contains every chord tone`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val chord = Chord(0, ChordQuality.MAJOR)

        val voicing = requireNotNull(findPlayableVoicing(tuning.notesLowToHigh, chord))
        val soundedPitchClasses = voicing.frets.mapIndexedNotNull { index, fret ->
            fret.takeIf { it >= 0 }?.let { (tuning.notesLowToHigh[index].value + it) % 12 }
        }
        val fretted = voicing.frets.filter { it > 0 }

        assertEquals(tuning.notesLowToHigh.size, voicing.frets.size)
        assertTrue(soundedPitchClasses.all(chord.pitchClasses::contains))
        assertTrue(soundedPitchClasses.toSet().containsAll(chord.pitchClasses))
        assertTrue(soundedPitchClasses.size >= 3)
        assertTrue(fretted.isEmpty() || requireNotNull(fretted.maxOrNull()) - requireNotNull(fretted.minOrNull()) <= 4)
        assertTrue(requireNotNull(fretted.maxOrNull()) <= 3, voicing.toString())
        assertEquals(-1, voicing.frets.first(), voicing.toString())
    }

    @Test
    fun `voicing generation works for extended guitar tuning`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-9-standard"))
        val voicing = requireNotNull(findPlayableVoicing(tuning.notesLowToHigh, Chord(9, ChordQuality.MINOR)))

        assertEquals(9, voicing.frets.size)
        assertTrue(voicing.frets.count { it >= 0 } >= 3)
    }

    @Test
    fun `timeline lookup respects event boundaries and gaps`() {
        val c = ChordEvent(0L, 1_000L, Chord(0, ChordQuality.MAJOR), 0.9)
        val g = ChordEvent(1_200L, 2_000L, Chord(7, ChordQuality.MAJOR), 0.8)
        val events = listOf(c, g)

        assertEquals(c, chordEventAt(events, 0L))
        assertEquals(c, chordEventAt(events, 999L))
        assertEquals(null, chordEventAt(events, 1_000L))
        assertEquals(null, chordEventAt(events, 1_100L))
        assertEquals(g, chordEventAt(events, 1_200L))
        assertEquals(null, chordEventAt(events, 2_000L))
    }
}
