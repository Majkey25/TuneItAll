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
    fun `an ambiguous weak seventh does not force a different bass root`() {
        val rate = 48_000
        val frequencies = intArrayOf(45, 48, 52, 55).map(::midiToHertz)
        for (seventh in listOf(0.04, 0.08)) {
            val amplitudes = doubleArrayOf(0.70, 0.48, 0.30, seventh)
            val samples = FloatArray(rate * 2) { frame ->
                (0.0055 * frequencies.indices.sumOf { note ->
                    amplitudes[note] * sin(2 * PI * frequencies[note] * frame / rate)
                }).toFloat()
            }
            val events = StreamingChordAnalyzer(rate).apply { accept(samples) }.finish()
            assertEquals(Chord(9, ChordQuality.MINOR), chordEventAt(events, 1000L)?.chord, events.toString())
        }
    }

    @Test
    fun `spread major voicings retain their observed root across all pitch classes`() {
        val rate = 48_000
        val intervals = intArrayOf(0, 7, 12, 16)
        val amplitudes = doubleArrayOf(0.70, 0.48, 0.30, 0.18)
        val failures = mutableListOf<String>()
        for (root in 52..63) for (gain in listOf(1.0, 0.01)) {
            val frequencies = intervals.map { midiToHertz(root + it) }
            val samples = FloatArray(rate * 2) { frame ->
                (0.55 * gain * frequencies.indices.sumOf { note ->
                    amplitudes[note] * sin(2 * PI * frequencies[note] * frame / rate)
                }).toFloat()
            }
            val events = StreamingChordAnalyzer(rate).apply { accept(samples) }.finish()
            val expected = Chord(root % 12, ChordQuality.MAJOR)
            if (chordEventAt(events, 1000L)?.chord != expected) failures += "$expected gain=$gain: $events"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `sustained ninths remain audible in major and minor voicings`() {
        val failures = mutableListOf<String>()
        for ((quality, third) in listOf(ChordQuality.ADD_NINTH to 4, ChordQuality.MINOR_ADD_NINTH to 3)) {
            for (transpose in listOf(-5, 0, 4)) for (gain in listOf(1f, 0.01f)) {
                val root = 45 + transpose
                val frequencies = intArrayOf(0, third, 7, 14).map { midiToHertz(root + it) }.toDoubleArray()
                val samples = sineChord(22_050, 2, *frequencies).apply { indices.forEach { this[it] *= gain } }
                val events = StreamingChordAnalyzer(22_050).apply { accept(samples) }.finish()
                val expected = Chord(Math.floorMod(root, 12), quality)
                if (chordEventAt(events, 1_000)?.chord != expected) failures += "$expected gain=$gain: $events"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

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
