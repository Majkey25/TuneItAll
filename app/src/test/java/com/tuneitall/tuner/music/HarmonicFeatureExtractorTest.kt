package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun extract(samples: FloatArray): List<HarmonicFrame> =
        StreamingHarmonicFeatureExtractor(SAMPLE_RATE).apply { accept(samples) }.finish()

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
