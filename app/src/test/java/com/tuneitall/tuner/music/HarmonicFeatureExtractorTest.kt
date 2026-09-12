package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HarmonicFeatureExtractorTest {
    @Test
    fun `extractor keeps a detuned A peak in A chroma`() {
        val frames = extract(sine(SAMPLE_RATE, seconds = 3, hertz = 445.0))
        val frame = frames[frames.size / 2]

        assertEquals(9, frame.chroma.indices.maxBy(frame.chroma::get))
        assertTrue(frame.tonalStrength > 0.5f)
    }

    @Test
    fun `two second aggregation suppresses isolated percussion`() {
        val frames = extract(noisyPowerRiff(SAMPLE_RATE, seconds = 5, rootHertz = 82.41))
        val stableFrames = frames.drop(12).dropLast(12)

        assertTrue(stableFrames.isNotEmpty())
        assertTrue(stableFrames.all { it.chroma[4] > it.chroma[2] })
    }

    @Test
    fun `silence has no tonal salience`() {
        val frames = extract(FloatArray(SAMPLE_RATE * 3))

        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.tonalStrength == 0f })
        assertTrue(frames.all { frame -> frame.chroma.all { it == 0f } })
    }

    @Test
    fun `silence after music does not normalize rounding residue into new notes`() {
        for (signal in listOf(
            sineChord(SAMPLE_RATE, 2, 130.81, 164.81, 196.0),
            noisyPowerRiff(SAMPLE_RATE, seconds = 2, rootHertz = 82.41),
        )) {
            val frames = extract(signal.copyOf(signal.size + SAMPLE_RATE * 8))
            assertTrue(frames.any { it.startMillis < 1_000L && it.tonalStrength > 0f })
            val silence = frames.filter { it.startMillis >= 3_000L }
            assertTrue(silence.isNotEmpty())
            val leaked = silence.filter { frame ->
                listOf(frame.chroma, frame.contextChroma, frame.noteSalience, frame.bassChroma, frame.observedChroma)
                    .any { values -> values.any { it != 0f } }
            }
            assertTrue(leaked.isEmpty(), leaked.take(3).joinToString { frame ->
                "${frame.startMillis}: chord=${frame.chroma.contentToString()} context=${frame.contextChroma.contentToString()}"
            })
        }
    }

    @Test
    fun `predominant note timeline ends when music is followed by silence`() {
        val signal = sineChord(SAMPLE_RATE, 2, 130.81, 164.81, 196.0)
        val frames = extract(signal.copyOf(SAMPLE_RATE * 10))
        val notes = analyzeNotes(frames, NoteRange.ANY, 10_000L)

        assertTrue(notes.any { it.startMillis < 2_000L }, "The initial music must produce notes")
        assertTrue(notes.all { it.endMillis <= 3_000L }, "Invented notes in silence: $notes")
    }

    @Test
    fun `repeated mono matches mono features for even and odd channel counts`() {
        val mono = sineChord(SAMPLE_RATE, 3, 130.81, 164.81, 196.0)

        val expected = extract(mono)
        for (channels in listOf(2, 3, 8)) {
            assertFramesClose(expected, extract(interleave(*Array(channels) { mono }), channelCount = channels))
        }
    }

    @Test
    fun `stereo features ignore channel order and polarity`() {
        val left = sine(SAMPLE_RATE, 3, 261.63)
        val right = sine(SAMPLE_RATE, 3, 329.63)
        val expected = extract(interleave(left, right), channelCount = 2)
        val swapped = extract(interleave(right, left), channelCount = 2)
        val inverted = extract(interleave(left, FloatArray(right.size) { -right[it] }), channelCount = 2)

        assertFramesClose(expected, swapped)
        assertFramesClose(expected, inverted)
        val third = sine(SAMPLE_RATE, 3, 392.0)
        assertFramesClose(
            extract(interleave(left, right, third), channelCount = 3),
            extract(interleave(third, left, FloatArray(right.size) { -right[it] }), channelCount = 3),
        )
    }

    @Test
    fun `anti phase upper chord keeps observed pitch evidence`() {
        val left = FloatArray(SAMPLE_RATE * 4)
        val right = FloatArray(left.size)
        val downmix = FloatArray(left.size)
        for (frame in left.indices) {
            val bass = 0.3 * sin(2.0 * PI * 130.81 * frame / SAMPLE_RATE)
            val upper = 0.16 * (
                sin(2.0 * PI * 261.63 * frame / SAMPLE_RATE) +
                    sin(2.0 * PI * 329.63 * frame / SAMPLE_RATE) +
                    sin(2.0 * PI * 392.0 * frame / SAMPLE_RATE)
                )
            left[frame] = (bass + upper).toFloat()
            right[frame] = (bass - upper).toFloat()
            downmix[frame] = ((left[frame] + right[frame]) / 2f)
        }

        val stereoFrames = extract(interleave(left, right), channelCount = 2)
        val stereoEvidence = stereoFrames[stereoFrames.size / 2].observedChroma
        val monoEvidence = extract(downmix)[stereoFrames.size / 2].observedChroma
        val chord = analyzeChords(stereoFrames, SongAnalysisMode.CHORDS, 4_000L)
            .maxBy(ChordEvent::durationMillis).chord

        assertTrue(downmix.sumOf { it.toDouble() * it } / downmix.size > 0.01)
        assertTrue(stereoEvidence[4] > monoEvidence[4] + 0.1f, stereoEvidence.contentToString())
        assertTrue(stereoEvidence[7] > monoEvidence[7] + 0.1f, stereoEvidence.contentToString())
        assertEquals(0, chord.rootPitchClass)
        assertEquals(ChordQuality.MAJOR, chord.quality)
    }

    @Test
    fun `stereo chunking duration and eof match whole input`() {
        val mono = sineChord(SAMPLE_RATE, 2, 220.0, 261.63, 329.63).copyOf(SAMPLE_RATE + 4_321)
        val stereo = interleave(mono, FloatArray(mono.size) { -mono[it] })
        val whole = StreamingHarmonicFeatureExtractor(SAMPLE_RATE, channelCount = 2).apply { accept(stereo) }
        val chunked = StreamingHarmonicFeatureExtractor(SAMPLE_RATE, channelCount = 2)
        acceptInChunks(chunked, stereo, chunkFrames = 773)

        assertEquals(mono.size * 1_000L / SAMPLE_RATE, whole.durationMillis)
        assertEquals(whole.durationMillis, chunked.durationMillis)
        assertFramesClose(whole.finish(), chunked.finish())
    }

    @Test
    fun `stereo input validates channel count complete frames and frame duration`() {
        assertFailsWith<IllegalArgumentException> { StreamingHarmonicFeatureExtractor(SAMPLE_RATE, channelCount = 0) }
        assertFailsWith<IllegalArgumentException> { StreamingHarmonicFeatureExtractor(SAMPLE_RATE, channelCount = 9) }
        val analyzer = StreamingHarmonicFeatureExtractor(SAMPLE_RATE, maxDurationSeconds = 1, channelCount = 2)
        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(3)) }
        assertFailsWith<IllegalArgumentException> { analyzer.accept(floatArrayOf(0f, Float.NaN)) }
        analyzer.accept(FloatArray(SAMPLE_RATE * 2))
        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(2)) }
    }

    private fun extract(samples: FloatArray, channelCount: Int = 1): List<HarmonicFrame> =
        StreamingHarmonicFeatureExtractor(SAMPLE_RATE, channelCount = channelCount).apply { accept(samples) }.finish()

    private fun interleave(vararg channels: FloatArray): FloatArray {
        require(channels.isNotEmpty() && channels.all { it.size == channels[0].size })
        return FloatArray(channels[0].size * channels.size) { index ->
            channels[index % channels.size][index / channels.size]
        }
    }

    private fun acceptInChunks(
        extractor: StreamingHarmonicFeatureExtractor,
        samples: FloatArray,
        chunkFrames: Int,
    ) {
        var offset = 0
        val chunkSamples = chunkFrames * 2
        while (offset < samples.size) {
            val end = minOf(offset + chunkSamples, samples.size)
            extractor.accept(samples.copyOfRange(offset, end))
            offset = end
        }
    }

    private fun assertFramesClose(expected: List<HarmonicFrame>, actual: List<HarmonicFrame>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) ->
            assertEquals(left.startMillis, right.startMillis)
            assertEquals(left.tonalStrength, right.tonalStrength, 1e-5f)
            assertEquals(left.onsetStrength, right.onsetStrength, 1e-5f)
            assertEquals(left.spectralFlatness, right.spectralFlatness, 1e-5f)
            listOf(
                left.chroma to right.chroma,
                left.contextChroma to right.contextChroma,
                left.bassChroma to right.bassChroma,
                left.noteSalience to right.noteSalience,
                left.observedChroma to right.observedChroma,
            ).forEach { (leftValues, rightValues) ->
                leftValues.indices.forEach { index ->
                    assertEquals(leftValues[index], rightValues[index], 1e-5f)
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
