package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TempoAnalyzerTest {
    @Test
    fun `equal loudness instrument changes retain a strong regular beat`() {
        for (rate in listOf(8_000, 44_100, 48_000)) {
            val audio = FloatArray(rate * 16) { frame ->
                val frequency = if (frame / (rate / 2) % 2 == 0) 100.0 else 2_000.0
                (0.5 * sin(2.0 * PI * frequency * frame / rate)).toFloat()
            }
            val estimate = assertNotNull(StreamingTempoAnalyzer(rate).apply { accept(audio) }.finish())

            assertTrue(kotlin.math.abs(estimate.bpm - 120) <= 2, "rate=$rate $estimate")
            assertTrue(estimate.confidence > 0.7, "rate=$rate $estimate")
        }
    }

    @Test
    fun `aperiodic noise does not claim a reliable beat`() {
        val random = Random(42)
        val audio = FloatArray(SAMPLE_RATE * 24) { random.nextFloat() * 0.5f - 0.25f }
        val estimate = StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(audio) }.finish()

        assertTrue(estimate == null || estimate.confidence < 0.2, estimate.toString())
    }

    @Test
    fun `steady rising and fading carrier tones are not mistaken for beat subdivisions`() {
        for (rate in listOf(8_000, 44_100, 48_000)) {
            for (frequency in listOf(110.0, 200.5, 220.0, 440.0, 443.0)) {
                for (envelope in listOf("steady", "fade-in", "fade-out")) {
                    val audio = FloatArray(rate * 12) { frame ->
                        val position = frame.toDouble() / (rate * 12)
                        val gain = when (envelope) {
                            "fade-in" -> position
                            "fade-out" -> 1.0 - position
                            else -> 1.0
                        }
                        (0.5 * gain * sin(2.0 * PI * frequency * frame / rate)).toFloat()
                    }
                    val estimate = StreamingTempoAnalyzer(rate).apply { accept(audio) }.finish()
                    assertEquals(null, estimate, "rate=$rate frequency=$frequency envelope=$envelope")
                }
            }
        }
    }

    @Test
    fun `competing unrelated pulse rates have weaker evidence than a clear pulse`() {
        val slow = clickTrack(90, seconds = 24)
        val fast = clickTrack(140, seconds = 24)
        val mixed = FloatArray(slow.size) { (slow[it] + fast[it]) * 0.5f }
        val clear = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(slow) }.finish())
        val ambiguous = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(mixed) }.finish())

        assertTrue(clear.confidence > 0.7, clear.toString())
        assertTrue(ambiguous.confidence < clear.confidence - 0.2, "$clear / $ambiguous")
    }

    @Test
    fun `kick and snare keep their beat with weaker matching under timing variation`() {
        val clear = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(drumPattern()) }.finish())
        assertEquals(120, clear.bpm)
        assertTrue(clear.confidence > 0.8, clear.toString())

        for (audio in listOf(drumPattern(jitterSeconds = 0.02), drumPattern(tempoDrift = 8.0))) {
            val varied = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(audio) }.finish())
            assertTrue(varied.bpm in 116..124, varied.toString())
            assertTrue(varied.confidence < clear.confidence - 0.1, "$clear / $varied")
        }
    }

    @Test
    fun `syncopation weakens rhythm match without doubling the beat`() {
        val estimate = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply {
            accept(drumPattern(syncopated = true))
        }.finish())

        assertTrue(kotlin.math.abs(estimate.bpm - 120) <= 2, estimate.toString())
        assertTrue(estimate.confidence < 0.8, estimate.toString())
    }

    @Test
    fun `sixteenth note hi hats do not suppress the underlying drum beat`() {
        val audio = drumPattern()
        val random = Random(8)
        for (start in audio.indices step (SAMPLE_RATE / 8)) {
            for (offset in 0 until 120) {
                audio[start + offset] += (0.12 * (1.0 - offset / 120.0) * (random.nextDouble() * 2 - 1)).toFloat()
            }
        }
        val estimate = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(audio) }.finish())

        assertEquals(120, estimate.bpm)
        assertTrue(estimate.confidence > 0.7, estimate.toString())
    }

    @Test
    fun `strong half time accents are not forced into the faster subdivision`() {
        val quarterNotes = clickTrack(120, seconds = 16)
        val accented = FloatArray(quarterNotes.size) { frame ->
            quarterNotes[frame] * if (frame / 4_000 % 2 == 0) 1f else 0.02f
        }
        val full = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(quarterNotes) }.finish())
        val half = assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply { accept(accented) }.finish())

        assertEquals(120, full.bpm)
        assertEquals(60, half.bpm)
        // Both are regular rhythms. A high match cannot resolve the intended musical meter.
        assertTrue(full.confidence > 0.8 && half.confidence > 0.8, "$full / $half")
    }

    @Test
    fun `streaming analyzer recognizes common song tempos`() {
        listOf(40, 60, 90, 120, 180, 240).forEach { bpm ->
            val analyzer = StreamingTempoAnalyzer(SAMPLE_RATE)
            val audio = clickTrack(bpm, seconds = 12)
            audio.asList().chunked(777).forEach { chunk -> analyzer.accept(chunk.toFloatArray()) }

            val estimate = assertNotNull(analyzer.finish(), "bpm=$bpm")

            assertTrue(kotlin.math.abs(estimate.bpm - bpm) <= 2, "expected=$bpm actual=${estimate.bpm}")
            assertTrue(estimate.confidence in 0.0..1.0)
        }
    }

    @Test
    fun `off grid tempos are not quantized to whole onset frames`() {
        val estimates = listOf(137, 173, 211, 235, 238).associateWith { bpm ->
            assertNotNull(StreamingTempoAnalyzer(SAMPLE_RATE).apply {
                accept(clickTrack(bpm, seconds = 16))
            }.finish())
        }

        assertTrue(
            estimates.all { (expected, estimate) -> kotlin.math.abs(estimate.bpm - expected) <= 1 },
            estimates.toString(),
        )
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

    private fun drumPattern(
        jitterSeconds: Double = 0.0,
        tempoDrift: Double = 0.0,
        syncopated: Boolean = false,
    ): FloatArray {
        val random = Random(7)
        val audio = FloatArray(SAMPLE_RATE * 24)
        var beatTime = 0.0
        repeat(48) { beat ->
            val delay = if (syncopated && beat % 4 == 2) 0.25 else 0.0
            val start = ((beatTime + delay + jitterSeconds * sin(beat.toDouble())) * SAMPLE_RATE).toInt()
            for (offset in 0 until SAMPLE_RATE / 10) {
                if (start + offset !in audio.indices) break
                val time = offset.toDouble() / SAMPLE_RATE
                val pulse = if (beat % 2 == 0) sin(2.0 * PI * 80.0 * time) else random.nextDouble() * 2.0 - 1.0
                audio[start + offset] += (0.5 * exp(-35.0 * time) * pulse).toFloat()
            }
            beatTime += 60.0 / (120.0 + tempoDrift * (beat / 47.0 - 0.5))
        }
        return audio
    }

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val CLICK_FRAMES = 160
    }
}
