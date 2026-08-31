package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChordAnalyzerTest {
    @Test
    fun `template matcher recognizes major minor and dominant seventh chroma`() {
        assertEquals(Chord(0, ChordQuality.MAJOR), matchChord(chroma(0, 4, 7))?.chord)
        assertEquals(Chord(9, ChordQuality.MINOR), matchChord(chroma(9, 0, 4))?.chord)
        assertEquals(Chord(7, ChordQuality.DOMINANT_SEVENTH), matchChord(chroma(7, 11, 2, 5))?.chord)
        assertEquals(null, matchChord(DoubleArray(12)))
    }

    @Test
    fun `template matcher recognizes a distorted E power chord`() {
        val chroma = DoubleArray(12) { 0.02 }.apply {
            this[4] = 1.0
            this[11] = 0.86
            this[8] = 0.18
        }

        assertEquals(Chord(4, ChordQuality.POWER), matchChord(chroma)?.chord)
    }

    @Test
    fun `streaming analyzer finds a stable synthetic C major segment`() {
        val sampleRate = 11_025
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(sineChord(sampleRate, seconds = 2, frequencies = doubleArrayOf(261.63, 329.63, 392.0)))

        val events = analyzer.finish()

        assertTrue(events.isNotEmpty())
        assertEquals(Chord(0, ChordQuality.MAJOR), events.maxBy(ChordEvent::durationMillis).chord)
        assertTrue(events.maxOf(ChordEvent::durationMillis) >= 1_000L)
    }

    @Test
    fun `streaming analyzer preserves a clear C to G chord change`() {
        val sampleRate = 11_025
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(sineChord(sampleRate, seconds = 3, frequencies = doubleArrayOf(261.63, 329.63, 392.0)))
        analyzer.accept(sineChord(sampleRate, seconds = 3, frequencies = doubleArrayOf(196.0, 246.94, 293.66)))

        val events = analyzer.finish()
        val cIndex = events.indexOfFirst { it.chord == Chord(0, ChordQuality.MAJOR) }
        val gIndex = events.indexOfLast { it.chord == Chord(7, ChordQuality.MAJOR) }

        assertTrue(cIndex >= 0, events.toString())
        assertTrue(gIndex > cIndex, events.toString())
    }

    @Test
    fun `streaming analyzer recognizes a clipped E power chord`() {
        val sampleRate = 48_000
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(distortedPowerChord(sampleRate, seconds = 4, rootHertz = 82.41))

        val event = analyzer.finish().maxBy(ChordEvent::durationMillis)

        assertEquals(Chord(4, ChordQuality.POWER), event.chord)
    }

    @Test
    fun `streaming analyzer rejects input beyond its bounded duration`() {
        val analyzer = StreamingChordAnalyzer(sampleRate = 8_000, maxDurationSeconds = 1)

        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(8_001)) }
    }

    private fun chroma(vararg pitchClasses: Int) = DoubleArray(12) { index ->
        if (index in pitchClasses) 1.0 else 0.02
    }

    private fun sineChord(sampleRate: Int, seconds: Int, frequencies: DoubleArray): FloatArray =
        FloatArray(sampleRate * seconds) { frame ->
            val value = frequencies.sumOf { frequency -> sin(2.0 * PI * frequency * frame / sampleRate) }
            (value / frequencies.size).toFloat()
        }

    private fun distortedPowerChord(sampleRate: Int, seconds: Int, rootHertz: Double): FloatArray =
        FloatArray(sampleRate * seconds) { frame ->
            val root = sin(2.0 * PI * rootHertz * frame / sampleRate)
            val fifth = sin(2.0 * PI * rootHertz * 1.5 * frame / sampleRate)
            (0.75 * tanh(3.5 * (root + 0.8 * fifth))).toFloat()
        }
}
