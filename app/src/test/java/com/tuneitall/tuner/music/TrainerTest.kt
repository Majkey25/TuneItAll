package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.TuningCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.random.Random

class TrainerTest {
    @Test
    fun `questions stay in the selected bank and avoid immediate repeats`() {
        var previous: Int? = null
        repeat(100) { seed ->
            val question = noteQuestion(seed, setOf(0, 1, 2), previous)
            assertTrue(question.answerPitchClass != previous)
            assertEquals(setOf(0, 1, 2), question.choices.toSet())
            previous = question.answerPitchClass
        }
        assertEquals(9, noteQuestion(1, setOf(9), 9).answerPitchClass)
        assertFailsWith<IllegalArgumentException> { noteQuestion(1, emptySet()) }
    }

    @Test
    fun `two note practice is random rather than predictable alternation`() {
        val answers = (0..100).map { noteQuestion(it, setOf(0, 2), previous = 0).answerPitchClass }
        assertEquals(setOf(0, 2), answers.toSet())
    }

    @Test
    fun `chord questions use random order without repeating the previous chord`() {
        val chords = (0..11).flatMap { root -> instructionalChordQualities.map { Chord(root, it) } }
        val random = Random(712)
        var previous: Chord? = null
        val seen = mutableSetOf<Chord>()
        repeat(200) {
            val chord = nextTrainerItem(chords, previous, random)
            assertTrue(chord != previous)
            assertTrue(chord in chords)
            seen += chord
            previous = chord
        }
        assertTrue(seen.size > 80)
    }

    @Test
    fun `bank editing moves notes between disjoint sets and supports empty banks`() {
        val sets = NoteTrainingSets().update(NoteBank.LEARNED, setOf(0, 4))
        assertEquals(setOf(2), sets.learning)
        assertEquals(setOf(0, 4), sets.learned)
        assertEquals(emptySet(), sets.update(NoteBank.LEARNING, emptySet()).learning)
        assertFailsWith<IllegalArgumentException> { sets.update(NoteBank.LEARNED, setOf(12)) }
        assertFailsWith<IllegalArgumentException> { NoteTrainingSets(setOf(0), setOf(0)) }
    }

    @Test
    fun `note questions do not follow a fixed chromatic step`() {
        val answers = (1..40).map { noteQuestion(it).answerPitchClass }
        val steps = answers.zipWithNext { a, b -> Math.floorMod(b - a, 12) }.toSet()
        assertTrue(steps.size > 2, "Questions must not cycle through notes in a fixed order")
    }

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
    fun `quiz choices exclude pitch class equivalent chords`() {
        val answer = Chord(0, ChordQuality.MAJOR_SIXTH)
        repeat(256) { seed ->
            val choices = trainerChoices(answer, seed)
            assertEquals(4, choices.size)
            assertEquals(4, choices.toSet().size)
            assertTrue(answer in choices)
            assertEquals(1, choices.count { it.pitchClasses == answer.pitchClasses },
                "Seed $seed offered an ambiguous answer")
        }
    }

    @Test
    fun `C6 quiz choices exclude chords matching the played voicing`() {
        val catalog = ChordShapeCatalog.parse(
            guitarJson = File("src/main/res/raw/chords_db_guitar.json").readText(),
            ukuleleJson = File("src/main/res/raw/chords_db_ukulele.json").readText(),
        )
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val answer = Chord(0, ChordQuality.MAJOR_SIXTH)
        val shape = requireNotNull(catalog.shape(tuning.id, answer))
        val heardPitchClasses = shape.frets.mapIndexedNotNull { index, fret ->
            fret.takeIf { it >= 0 }?.let { (tuning.notesLowToHigh[index].value + it) % 12 }
        }.toSet()
        repeat(256) { seed ->
            val choices = trainerChoices(answer, seed, heardPitchClasses)
            assertEquals(4, choices.size)
            assertEquals(4, choices.toSet().size)
            assertTrue(answer in choices)
            assertTrue(choices.none { it != answer && it.pitchClasses == heardPitchClasses },
                "Seed $seed offered a chord matching the played voicing")
        }
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
