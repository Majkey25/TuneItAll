package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
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
