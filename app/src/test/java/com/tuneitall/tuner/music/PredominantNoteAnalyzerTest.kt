package com.tuneitall.tuner.music

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredominantNoteAnalyzerTest {
    @Test
    fun `note changes follow the audio timing across sample rates`() {
        for (sampleRate in listOf(44_100, 48_000, 96_000)) for (range in listOf(NoteRange.ANY, NoteRange.VIOLIN)) {
            val samples = sine(sampleRate, 1, 440.0) + sine(sampleRate, 1, 523.25) + sine(sampleRate, 1, 659.25)

            val events = analyze(samples, range, sampleRate)

            assertEquals(listOf(69, 72, 76), events.map(NoteEvent::midiNote), "$sampleRate: $events")
            assertEquals(0L, events.first().startMillis)
            assertEquals(3_000L, events.last().endMillis)
            events.drop(1).forEachIndexed { index, event ->
                assertTrue(abs(event.startMillis - (index + 1) * 1_000L) <= 50L, "$sampleRate: $events")
                assertEquals(events[index].endMillis, event.startMillis)
            }
        }
    }

    @Test
    fun `violin melody survives unrelated bass accompaniment`() {
        for (transpose in listOf(-2, 0, 3)) for (bassWeight in listOf(0.25f, 0.5f)) {
            val ratio = 2.0.pow(transpose / 12.0)
            val bass = sine(SAMPLE_RATE, 2, 82.40689 * ratio)
            val melody = sine(SAMPLE_RATE, 2, 440.0 * ratio)
            val samples = FloatArray(bass.size) { (bassWeight * bass[it] + melody[it]) / (bassWeight + 1f) }

            val events = analyze(samples, NoteRange.VIOLIN)

            assertEquals(listOf(69 + transpose), events.map(NoteEvent::midiNote), "$transpose/$bassWeight: $events")
            assertEquals(2_000L, events.single().durationMillis)
        }
    }

    @Test
    fun `violin mode retains missing fundamentals through its high register`() {
        for (midi in listOf(69, 91, 95)) {
            val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val events = analyze(harmonicTone(SAMPLE_RATE, 2, frequency), NoteRange.VIOLIN)

            assertEquals(listOf(midi), events.map(NoteEvent::midiNote))
            assertEquals(2_000L, events.single().durationMillis)
        }
    }

    @Test
    fun `high missing fundamental melody survives unrelated weak bass`() {
        val midi = 91
        val melody = harmonicTone(SAMPLE_RATE, 2, 440.0 * 2.0.pow((midi - 69) / 12.0))
        val bass = sine(SAMPLE_RATE, 2, midiToHertz(50))
        val samples = FloatArray(melody.size) { melody[it] + 0.1f * bass[it] }

        val events = analyze(samples, NoteRange.VIOLIN)

        assertEquals(listOf(midi), events.map(NoteEvent::midiNote), events.toString())
    }

    @Test
    fun `violin mode rejects transposed bass with a missing fundamental`() {
        for (midi in listOf(35, 40, 47, 52)) {
            val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val events = analyze(harmonicTone(SAMPLE_RATE, 2, frequency), NoteRange.VIOLIN)

            assertTrue(events.isEmpty(), "$midi: $events")
        }
    }

    @Test
    fun `note and silence boundaries lie between overlapping window centers`() {
        val frames = listOf(69, 69, 72, 72, null, null, 76, 76).mapIndexed { index, midi ->
            HarmonicFrame(
                startMillis = index * 200L,
                chroma = FloatArray(12),
                bassChroma = FloatArray(12),
                noteSalience = FloatArray(88).apply { if (midi != null) this[midi - 21] = 1f },
                tonalStrength = if (midi == null) 0f else 1f,
                onsetStrength = 0f,
            )
        }

        val events = analyzeNotes(frames, NoteRange.ANY, 1_600L)

        assertEquals(listOf(69, 72, 76), events.map(NoteEvent::midiNote))
        assertEquals(listOf(0L, 500L, 1_300L), events.map(NoteEvent::startMillis))
        assertEquals(listOf(500L, 900L, 1_600L), events.map(NoteEvent::endMillis))
        val truncated = analyzeNotes(frames.take(3), NoteRange.ANY, 450L)
        assertEquals(listOf(NoteEvent(0L, 450L, 69, 1.0)), truncated)
    }

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

            val events = analyzeNotes(frames, NoteRange.ANY, 500L)

            assertEquals(listOf(destination + 3 * direction, destination), events.map(NoteEvent::midiNote))
            assertEquals(listOf(0L, 300L), events.map(NoteEvent::startMillis))
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
        for (midi in 28..54 step 2) {
            val transposed = analyze(noisyPowerRiff(SAMPLE_RATE, 2, midiToHertz(midi)), NoteRange.VIOLIN)
            assertTrue(transposed.isEmpty(), "MIDI $midi: $transposed")
        }
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

    private fun analyze(samples: FloatArray, range: NoteRange, sampleRate: Int = SAMPLE_RATE): List<NoteEvent> {
        val extractor = StreamingHarmonicFeatureExtractor(sampleRate)
        extractor.accept(samples)
        return analyzeNotes(extractor.finish(), range, extractor.durationMillis)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
