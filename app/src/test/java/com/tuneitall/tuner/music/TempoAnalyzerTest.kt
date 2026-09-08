package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TempoAnalyzerTest {
    @Test
    fun `streaming analyzer recognizes common song tempos`() {
        listOf(60, 90, 120, 180).forEach { bpm ->
            val analyzer = StreamingTempoAnalyzer(SAMPLE_RATE)
            val audio = clickTrack(bpm, seconds = 12)
            audio.asList().chunked(777).forEach { chunk -> analyzer.accept(chunk.toFloatArray()) }

            val estimate = assertNotNull(analyzer.finish(), "bpm=$bpm")

            assertTrue(kotlin.math.abs(estimate.bpm - bpm) <= 2, "expected=$bpm actual=${estimate.bpm}")
            assertTrue(estimate.confidence in 0.0..1.0)
        }
    }

    @Test
    fun `streaming analyzer tolerates missing beats and quiet background tone`() {
        val analyzer = StreamingTempoAnalyzer(SAMPLE_RATE)
        analyzer.accept(clickTrack(bpm = 105, seconds = 16, skipEvery = 4, backgroundAmplitude = 0.015f))

        val estimate = assertNotNull(analyzer.finish())

        assertTrue(kotlin.math.abs(estimate.bpm - 105) <= 2, estimate.toString())
    }

    @Test
    fun `streaming analyzer rejects silence and too little audio`() {
        assertEquals(null, StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(FloatArray(SAMPLE_RATE * 8)) }.finish())
        assertEquals(null, StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(clickTrack(120, seconds = 2)) }.finish())
    }

    @Test
    fun `dual mono matches mono tempo`() {
        val mono = clickTrack(105, seconds = 12)
        val monoEstimate = StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(mono) }.finish()
        val stereoEstimate = StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2).apply {
            accept(interleave(mono, mono))
        }.finish()

        assertEquals(monoEstimate, stereoEstimate)
    }

    @Test
    fun `stereo tempo preserves anti phase pulse energy`() {
        val left = clickTrack(120, seconds = 12)
        val right = FloatArray(left.size) { -left[it] }
        val expected = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2).apply {
            accept(interleave(left, right))
        }.finish())
        val swapped = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2).apply {
            accept(interleave(right, left))
        }.finish())

        assertTrue(kotlin.math.abs(expected.bpm - 120) <= 2, expected.toString())
        assertEquals(expected, swapped)
    }

    @Test
    fun `stereo duration chunking and eof use sample frames`() {
        val mono = clickTrack(120, seconds = 5).copyOf(SAMPLE_RATE * 4 + 37)
        val stereo = interleave(mono, FloatArray(mono.size) { -mono[it] })
        val whole = StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2).apply { accept(stereo) }
        val chunked = StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2)
        acceptInChunks(chunked, stereo, chunkFrames = 389)

        assertEquals(whole.finish(), chunked.finish())
        assertEquals(
            null,
            StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 2).apply {
                val short = clickTrack(120, seconds = 2)
                accept(interleave(short, short))
            }.finish(),
        )
    }

    @Test
    fun `stereo input validates channel count complete frames and frame duration`() {
        assertFailsWith<IllegalArgumentException> { StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 0) }
        assertFailsWith<IllegalArgumentException> { StreamingTempoAnalyzer(SAMPLE_RATE, channelCount = 9) }
        val analyzer = StreamingTempoAnalyzer(SAMPLE_RATE, maxDurationSeconds = 1, channelCount = 2)
        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(3)) }
        assertFailsWith<IllegalArgumentException> { analyzer.accept(floatArrayOf(0f, Float.NaN)) }
        analyzer.accept(FloatArray(SAMPLE_RATE * 2))
        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(2)) }
    }

    private fun interleave(vararg channels: FloatArray): FloatArray {
        require(channels.isNotEmpty() && channels.all { it.size == channels[0].size })
        return FloatArray(channels[0].size * channels.size) { index ->
            channels[index % channels.size][index / channels.size]
        }
    }

    private fun acceptInChunks(analyzer: StreamingTempoAnalyzer, samples: FloatArray, chunkFrames: Int) {
        var offset = 0
        val chunkSamples = chunkFrames * 2
        while (offset < samples.size) {
            val end = minOf(offset + chunkSamples, samples.size)
            analyzer.accept(samples.copyOfRange(offset, end))
            offset = end
        }
    }

    private fun clickTrack(
        bpm: Int,
        seconds: Int,
        skipEvery: Int = 0,
        backgroundAmplitude: Float = 0f,
    ): FloatArray {
        val interval = SAMPLE_RATE * 60 / bpm
        return FloatArray(SAMPLE_RATE * seconds) { frame ->
            val beat = frame / interval
            val withinBeat = frame % interval
            val click = if ((skipEvery == 0 || beat % skipEvery != skipEvery - 1) && withinBeat < CLICK_FRAMES) {
                val decay = 1f - withinBeat.toFloat() / CLICK_FRAMES
                (0.8 * decay * sin(2.0 * PI * 1_000.0 * frame / SAMPLE_RATE)).toFloat()
            } else {
                0f
            }
            click + backgroundAmplitude * sin(2.0 * PI * 220.0 * frame / SAMPLE_RATE).toFloat()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val CLICK_FRAMES = 160
    }
}
