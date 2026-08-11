package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioPipelineTest {
    @Test
    fun `window assembler emits overlapping windows at a bounded hop`() {
        val snapshots = mutableListOf<List<Short>>()
        var reusedBuffer: ShortArray? = null
        val assembler = AudioWindowAssembler(windowSize = 4, hopSize = 2) { window ->
            if (reusedBuffer == null) reusedBuffer = window else assertSame(reusedBuffer, window)
            snapshots += window.toList()
        }

        assembler.append(shortArrayOf(1, 2, 3), 3)
        assertTrue(snapshots.isEmpty())
        assembler.append(shortArrayOf(4), 1)
        assembler.append(shortArrayOf(5, 6, 7, 8, 9), 5)

        assertEquals(
            listOf(
                listOf<Short>(1, 2, 3, 4),
                listOf<Short>(3, 4, 5, 6),
                listOf<Short>(5, 6, 7, 8),
            ),
            snapshots,
        )
    }

    @Test
    fun `window assembler validates sizes and append count`() {
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(1, 1) {} }
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(4, 0) {} }
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(4, 5) {} }

        val assembler = AudioWindowAssembler(4, 2) {}
        assertFailsWith<IllegalArgumentException> { assembler.append(shortArrayOf(1), 2) }
    }

    @Test
    fun `reference tone buffer has one second duration and faded edges`() {
        val samples = createToneBuffer(hertz = 1_000.0)

        assertEquals(48_000, samples.size)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.maxOf { abs(it.toInt()) } in 20_000..Short.MAX_VALUE.toInt())
        assertTrue(abs(samples[12].toInt()) < abs(samples[468].toInt()))
        assertTrue(abs(samples[samples.lastIndex - 12].toInt()) < abs(samples[samples.lastIndex - 468].toInt()))
    }

    @Test
    fun `low reference tone includes harmonics audible on a phone speaker`() {
        val samples = createToneBuffer(hertz = 82.0)
        val fundamental = amplitudeAt(samples, 82.0)

        assertTrue(amplitudeAt(samples, 164.0) > fundamental * 0.20)
        assertTrue(amplitudeAt(samples, 246.0) > fundamental * 0.10)
    }

    @Test
    fun `reference tone omits harmonics above Nyquist`() {
        val samples = createToneBuffer(hertz = 10_000.0)
        val fundamental = amplitudeAt(samples, 10_000.0)

        assertTrue(amplitudeAt(samples, 18_000.0) < fundamental * 0.05)
    }

    @Test
    fun `reference tone reaches a quiet tail before playback ends`() {
        val samples = createToneBuffer(hertz = 82.0)
        val finalFiftyMilliseconds = samples.takeLast(2_400)

        assertTrue(finalFiftyMilliseconds.maxOf { abs(it.toInt()) } < 1_500)
    }

    @Test
    fun `reference tone switching uses a short fade to silence`() {
        val fade = referenceToneSwitchFade()

        assertTrue(fade.durationMillis in 30L..80L)
        assertEquals(listOf(0f, 1f), fade.times)
        assertEquals(listOf(1f, 0f), fade.volumes)
    }

    @Test
    fun `reference tone rejects unsafe frequencies`() {
        listOf(0.0, -1.0, Double.NaN, 24_000.0).forEach { hertz ->
            assertFailsWith<IllegalArgumentException> { createToneBuffer(hertz) }
        }
    }

    private fun amplitudeAt(samples: ShortArray, hertz: Double, sampleRate: Int = 48_000): Double {
        var real = 0.0
        var imaginary = 0.0
        samples.forEachIndexed { index, sample ->
            val phase = 2.0 * PI * hertz * index / sampleRate
            real += sample * cos(phase)
            imaginary -= sample * sin(phase)
        }
        return hypot(real, imaginary) / samples.size
    }
}
