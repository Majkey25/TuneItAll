package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredominantNoteAnalyzerTest {
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
