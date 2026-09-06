package com.tuneitall.tuner.music

import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ChordEmissionRegressionTest {
    @Test
    fun `rapid chord timeline stays aligned with the audio`() {
        val notes = arrayOf(
            doubleArrayOf(261.6256, 329.6276, 391.9954),
            doubleArrayOf(195.9977, 246.9417, 293.6648),
            doubleArrayOf(220.0, 261.6256, 329.6276),
        )
        val chords = listOf(Chord(0, ChordQuality.MAJOR), Chord(7, ChordQuality.MAJOR), Chord(9, ChordQuality.MINOR))
        val analyzer = StreamingChordAnalyzer(22_050)
        repeat(12) { analyzer.accept(sineChord(22_050, 1.0 / 3, *notes[it % 3])) }
        val events = analyzer.finish()
        val reference = List(12) { index ->
            ChordEvent(index * 1_000L / 3, (index + 1) * 1_000L / 3, chords[index % 3], 1.0)
        }
        val score = evaluateChords(reference, events, 4_000)

        assertEquals(reference.map(ChordEvent::chord), events.map(ChordEvent::chord))
        assertTrue(score.qualityWcsr >= 0.90, score.toString())
        assertTrue(score.medianBoundaryErrorMillis <= 50.0, score.toString())
    }

    @Test
    fun `a single pitch class cannot establish a chord`() {
        val events = analyzeChords(frames(9, listOf(9)), SongAnalysisMode.CHORDS, 2_000L)

        assertTrue(events.isEmpty(), events.toString())
    }

    @Test
    fun `equal energy added ninth is not reduced to its major subset`() {
        assertRecognized(Chord(0, ChordQuality.ADD_NINTH))
    }

    @Test
    fun `equal energy major seventh is not reduced to its major subset`() {
        assertRecognized(Chord(0, ChordQuality.MAJOR_SEVENTH))
    }

    @Test
    fun `equal energy minor seventh retains its quality`() {
        assertRecognized(Chord(9, ChordQuality.MINOR_SEVENTH))
    }

    @Test
    fun `pure major triad does not gain an unsupported extension`() {
        assertRecognized(Chord(0, ChordQuality.MAJOR))
    }

    private fun assertRecognized(expected: Chord) {
        val events = analyzeChords(
            frames(expected.rootPitchClass, expected.pitchClasses),
            SongAnalysisMode.CHORDS,
            2_000L,
        )
        assertEquals(listOf(expected), events.map(ChordEvent::chord), events.toString())
    }

    private fun frames(root: Int, pitchClasses: Collection<Int>): List<HarmonicFrame> {
        val chroma = FloatArray(12) { if (it in pitchClasses) (1.0 / sqrt(pitchClasses.size.toDouble())).toFloat() else 0f }
        val bass = FloatArray(12) { if (it == root) 1f else 0f }
        return List(20) { index ->
            HarmonicFrame(
                startMillis = index * 100L,
                chroma = chroma,
                bassChroma = bass,
                noteSalience = FloatArray(88),
                tonalStrength = 0.8f,
                onsetStrength = 0f,
            )
        }
    }
}
