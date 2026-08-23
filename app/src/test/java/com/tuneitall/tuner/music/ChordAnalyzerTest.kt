package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.sin
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
}
