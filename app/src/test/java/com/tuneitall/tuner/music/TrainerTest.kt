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
        val voicing = ChordVoicing(listOf(-1, 3, 2, 0, 1, 0))

        val frequencies = voicingFrequencies(tuning.notesLowToHigh, voicing)

        assertTrue(frequencies.size in 3..voicing.frets.count { it >= 0 })
        assertEquals(frequencies.size, frequencies.toSet().size)
        assertTrue(frequencies.all { it in 60.0..2_000.0 })
    }

    @Test
    fun `note question is deterministic and contains one answer among four choices`() {
        val first = noteQuestion(seed = 5)
        val second = noteQuestion(seed = 5)

        assertEquals(first, second)
        assertEquals(4, first.choices.size)
        assertEquals(4, first.choices.toSet().size)
        assertEquals(1, first.choices.count { it == first.answerPitchClass })
        assertEquals(60 + first.answerPitchClass, first.midiNote)
    }

    @Test
    fun `middle C converts to concert frequency`() {
        assertEquals(261.6256, midiToHertz(60), 0.001)
    }
}
