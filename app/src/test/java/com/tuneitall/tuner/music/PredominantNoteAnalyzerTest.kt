package com.tuneitall.tuner.music

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredominantNoteAnalyzerTest {
    @Test
    fun `an ambiguous note considers transitions beyond neighboring semitones`() {
        for (direction in listOf(-1, 1)) for (transpose in -3..8) {
            val destination = 60 + transpose
            val first = FloatArray(88).apply {
                this[destination + direction - 21] = 0.30f
                this[destination + 3 * direction - 21] = 0.45f
                this[destination + 36 * direction - 21] = 0.50f
                val norm = sqrt(sumOf { it.toDouble() * it })
                indices.forEach { this[it] = (this[it] / norm).toFloat() }
            }
            val second = FloatArray(88).apply { this[destination - 21] = 1f }
            val frames = listOf(first, second).mapIndexed { index, salience ->
                HarmonicFrame(
                    startMillis = index * 200L,
                    chroma = FloatArray(12),
                    bassChroma = FloatArray(12),
                    noteSalience = salience,
                    tonalStrength = 1f,
                    onsetStrength = 0f,
                )
            }

            val events = analyzeNotes(frames, NoteRange.ANY, 400L)

            assertEquals(listOf(destination + 3 * direction, destination), events.map(NoteEvent::midiNote))
            assertEquals(listOf(0L, 200L), events.map(NoteEvent::startMillis))
        }
    }

    @Test
    fun `note mode follows an annotated A4 C5 E5 melody`() {
        val samples = sine(SAMPLE_RATE, 2, 440.0) +
            sine(SAMPLE_RATE, 2, 523.25) +
            sine(SAMPLE_RATE, 2, 659.25)

        val events = analyze(samples, NoteRange.VIOLIN)

        assertEquals(listOf(69, 72, 76), events.map(NoteEvent::midiNote))
        assertTrue(events.all { it.durationMillis >= 1_000L }, events.toString())
    }

    @Test
    fun `violin mode rejects a bass note and its upper harmonics`() {
        val samples = noisyPowerRiff(SAMPLE_RATE, seconds = 4, rootHertz = 82.41)

        val events = analyze(samples, NoteRange.VIOLIN)

        assertTrue(events.isEmpty(), events.toString())
    }

    @Test
    fun `changing broadband noise produces no notes`() {
        val events = analyze(changingNoise(SAMPLE_RATE, seconds = 4), NoteRange.ANY)

        assertTrue(events.isEmpty(), events.toString())
    }

    @Test
    fun `harmonic stack resolves to its weak fundamental`() {
        val events = analyze(harmonicTone(SAMPLE_RATE, seconds = 4, fundamentalHertz = 82.41), NoteRange.GUITAR)

        val longest = events.maxBy(NoteEvent::durationMillis)
        assertEquals(40, longest.midiNote)
        assertTrue(longest.durationMillis >= 3_000L, events.toString())
    }

    private fun analyze(samples: FloatArray, range: NoteRange): List<NoteEvent> {
        val extractor = StreamingHarmonicFeatureExtractor(SAMPLE_RATE)
        extractor.accept(samples)
        return analyzeNotes(extractor.finish(), range, extractor.durationMillis)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
