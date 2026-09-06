package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ChordFeatureEvidenceTest {
    @Test
    fun `plucked minor added ninth retains its audible ninth`() {
        val rate = 22_050
        val events = StreamingChordAnalyzer(rate).apply { accept(plucked(intArrayOf(45, 48, 52, 59), rate, 2)) }.finish()

        assertEquals(Chord(9, ChordQuality.MINOR_ADD_NINTH), chordEventAt(events, 1_000)?.chord, events.toString())
    }

    @Test
    fun `same root plucked quality changes stay separate`() {
        val cases = listOf(
            listOf(intArrayOf(48, 52, 55, 60), intArrayOf(48, 52, 55, 59), intArrayOf(48, 52, 55, 60)) to
                listOf(ChordQuality.MAJOR, ChordQuality.MAJOR_SEVENTH, ChordQuality.MAJOR),
            listOf(intArrayOf(45, 48, 52, 57), intArrayOf(45, 48, 52, 59), intArrayOf(45, 48, 52, 57)) to
                listOf(ChordQuality.MINOR, ChordQuality.MINOR_ADD_NINTH, ChordQuality.MINOR),
        )
        cases.forEach { (voicings, expected) ->
            val analyzer = StreamingChordAnalyzer(22_050)
            voicings.forEach { analyzer.accept(plucked(it, 22_050, 1)) }
            val events = analyzer.finish()

            assertEquals(expected, events.map { it.chord.quality }, events.toString())
        }
    }

    @Test
    fun `same root quality changes can occur three times per second`() {
        val rate = 22_050
        val samples = concatenate(
            sineChord(rate, 1.0 / 3, 261.6256, 329.6276, 391.9954),
            sineChord(rate, 1.0 / 3, 261.6256, 329.6276, 391.9954, 493.8833),
            sineChord(rate, 1.0 / 3, 261.6256, 329.6276, 391.9954),
        )
        val events = StreamingChordAnalyzer(rate).apply { accept(samples) }.finish()

        assertEquals(listOf(ChordQuality.MAJOR, ChordQuality.MAJOR_SEVENTH, ChordQuality.MAJOR),
            events.map { it.chord.quality }, events.toString())
        assertTrue(events.all { it.chord.rootPitchClass == 0 }, events.toString())
    }

    private fun plucked(notes: IntArray, rate: Int, durationSeconds: Int): FloatArray =
        FloatArray(rate * durationSeconds) { index ->
            val seconds = index.toDouble() / rate
            val envelope = minOf(seconds / 0.005, 1.0) * (0.3 + 0.7 * exp(-seconds / 0.7))
            (0.03 * envelope * notes.sumOf { midi ->
                (1..10).sumOf { harmonic ->
                    0.8.pow(harmonic - 1) / harmonic * sin(2 * PI * midiToHertz(midi) * harmonic * seconds)
                }
            }).toFloat()
        }
}
