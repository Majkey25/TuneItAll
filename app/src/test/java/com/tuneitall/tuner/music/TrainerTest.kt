package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.TuningCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainerTest {
    @Test
    fun `quiz choices are deterministic unique and include the answer`() {
        val answer = Chord(4, ChordQuality.MINOR)

        val first = trainerChoices(answer, seed = 42)
        val second = trainerChoices(answer, seed = 42)

        assertEquals(first, second)
        assertEquals(4, first.size)
        assertEquals(4, first.toSet().size)
        assertTrue(answer in first)
    }

    @Test
    fun `voicing frequencies follow selected tuning`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val voicing = requireNotNull(findPlayableVoicing(tuning.notesLowToHigh, Chord(0, ChordQuality.MAJOR)))

        val frequencies = voicingFrequencies(tuning.notesLowToHigh, voicing)

        assertTrue(frequencies.size in 3..voicing.frets.count { it >= 0 })
        assertEquals(frequencies.size, frequencies.toSet().size)
        assertTrue(frequencies.all { it in 60.0..2_000.0 })
    }
}
