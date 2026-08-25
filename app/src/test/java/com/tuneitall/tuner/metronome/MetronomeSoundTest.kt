package com.tuneitall.tuner.metronome

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeSoundTest {
    @Test
    fun `click buffers stay bounded and clean at every supported test rate`() {
        listOf(8_000, 48_000, 192_000).forEach { sampleRate ->
            MetronomeSound.entries.forEach { sound ->
                PulseKind.entries.forEach { kind ->
                    val samples = createClickBuffer(sound, kind, sampleRate)

                    assertEquals(sampleRate * 70 / 1_000, samples.size)
                    assertTrue(samples.size <= 13_440)
                    assertTrue(samples.contentEquals(createClickBuffer(sound, kind, sampleRate)))
                    assertEquals(0, samples.first().toInt())
                    assertEquals(0, samples.last().toInt())
                    assertTrue(samples.maxOf { abs(it.toInt()) } in 500..29_999)
                    assertTrue(abs(samples.average()) < 5.0)
                    assertTrue(rms(samples) > 50.0)
                    assertTrue(samples.takeLast(sampleRate / 1_000).maxOf { abs(it.toInt()) } < 400)
                }
            }
        }
    }

    @Test
    fun `pulse kinds have distinct levels and accents carry more energy`() {
        MetronomeSound.entries.forEach { sound ->
            val normal = createClickBuffer(sound, PulseKind.MAIN, 48_000)
            val accent = createClickBuffer(sound, PulseKind.ACCENT, 48_000)
            val subdivision = createClickBuffer(sound, PulseKind.SUBDIVISION, 48_000)

            assertFalse(normal.contentEquals(accent))
            assertFalse(normal.contentEquals(subdivision))
            assertTrue(energy(accent) > energy(normal))
            assertTrue(energy(subdivision) < energy(normal))
        }
    }

    @Test
    fun `dominant frequencies and subdivision level match the synthesis contract`() {
        mapOf(
            MetronomeSound.DEEP to 650.0,
            MetronomeSound.WOOD to 1_100.0,
            MetronomeSound.CLICK to 1_800.0,
            MetronomeSound.RIM to 2_600.0,
            MetronomeSound.BRIGHT to 3_000.0,
        ).forEach { (sound, mainHertz) ->
            val main = createClickBuffer(sound, PulseKind.MAIN, 48_000)
            val accent = createClickBuffer(sound, PulseKind.ACCENT, 48_000)
            val subdivision = createClickBuffer(sound, PulseKind.SUBDIVISION, 48_000)

            val mainFrequency = dominantFrequency(main, mainHertz, 48_000)
            val accentFrequency = dominantFrequency(accent, mainHertz * 1.25, 48_000)
            val subdivisionFrequency = dominantFrequency(subdivision, mainHertz, 48_000)
            val rmsRatio = rms(subdivision) / rms(main)
            val energyRatio = energy(subdivision) / energy(main)

            assertNear(mainHertz, mainFrequency, 30.0)
            assertNear(mainHertz * 1.25, accentFrequency, 30.0)
            assertNear(mainHertz, subdivisionFrequency, 30.0)
            assertTrue(rmsRatio in 0.54..0.56)
            assertTrue(energyRatio in 0.29..0.31)
        }
    }

    @Test
    fun `raised cosine attack starts quietly and reaches the click body`() {
        MetronomeSound.entries.forEach { sound ->
            val samples = createClickBuffer(sound, PulseKind.MAIN, 48_000)
            val firstHalfPeak = peak(samples, 1, 24)
            val secondHalfPeak = peak(samples, 24, 48)
            val bodyPeak = peak(samples, 48, 96)

            assertTrue(firstHalfPeak < secondHalfPeak)
            assertTrue(abs(samples[1].toInt()) < bodyPeak * 0.10)
        }
    }

    @Test
    fun `post attack windows decay monotonically`() {
        MetronomeSound.entries.forEach { sound ->
            val samples = createClickBuffer(sound, PulseKind.MAIN, 48_000)
            val starts = listOf(48, 528, 1_008, 1_488)
            val rmsWindows = starts.map { start -> rms(samples.copyOfRange(start, start + 240)) }
            val peakWindows = starts.map { start -> peak(samples, start, start + 240) }

            rmsWindows.zipWithNext().forEach { (earlier, later) ->
                assertTrue(later < earlier * 0.65)
            }
            peakWindows.zipWithNext().forEach { (earlier, later) ->
                assertTrue(later < earlier * 0.65)
            }
        }
    }

    @Test
    fun `all click sounds remain below overlap headroom at fastest subdivision`() {
        MetronomeSound.entries.forEach { sound ->
            val accent = createClickBuffer(sound, PulseKind.ACCENT, 48_000)
            val main = createClickBuffer(sound, PulseKind.MAIN, 48_000)
            val subdivision = createClickBuffer(sound, PulseKind.SUBDIVISION, 48_000)

            val accentOverlapPeak = mixedPeak(accent, subdivision, 1_800)
            val mainOverlapPeak = mixedPeak(main, subdivision, 1_800)

            assertTrue(accentOverlapPeak < 30_000)
            assertTrue(mainOverlapPeak < 30_000)
        }
    }

    @Test
    fun `eight kilohertz rim accent filters aliased harmonics`() {
        val samples = createClickBuffer(MetronomeSound.RIM, PulseKind.ACCENT, 8_000)
        val fundamental = spectralAmplitude(samples, 3_250.0, 8_000)

        val secondHarmonicAlias = spectralAmplitude(samples, 1_500.0, 8_000)
        val thirdHarmonicAlias = spectralAmplitude(samples, 1_750.0, 8_000)

        assertTrue(secondHarmonicAlias < fundamental * 0.10)
        assertTrue(thirdHarmonicAlias < fundamental * 0.10)
    }

    @Test
    fun `stop fade changes only requested tail and ends at zero`() {
        val samples = ShortArray(1_000) { 12_000 }
        val unchangedPrefix = samples.copyOfRange(0, 520)

        applyStopFade(samples, 480)

        assertTrue(unchangedPrefix.contentEquals(samples.copyOfRange(0, 520)))
        assertEquals(12_000, samples[520].toInt())
        assertEquals(0, samples.last().toInt())
        for (frame in 521 until samples.size) {
            assertTrue(abs(samples[frame].toInt()) <= abs(samples[frame - 1].toInt()))
        }
    }

    @Test
    fun `one frame stop fade zeroes only final sample`() {
        val samples = shortArrayOf(100, -200, 300)

        applyStopFade(samples, 1)

        assertTrue(samples.copyOfRange(0, 2).contentEquals(shortArrayOf(100, -200)))
        assertEquals(0, samples.last().toInt())
    }

    @Test
    fun `click generation and fade reject unsafe arguments`() {
        listOf(0, 7_999, 192_001, Int.MAX_VALUE).forEach { sampleRate ->
            assertFailsWith<IllegalArgumentException> {
                createClickBuffer(MetronomeSound.WOOD, PulseKind.MAIN, sampleRate)
            }
        }
        assertFailsWith<IllegalArgumentException> { applyStopFade(ShortArray(0), 0) }
        assertFailsWith<IllegalArgumentException> { applyStopFade(shortArrayOf(1, 2), 0) }
        assertFailsWith<IllegalArgumentException> { applyStopFade(shortArrayOf(1, 2), 3) }
    }

    private fun dominantFrequency(samples: ShortArray, expected: Double, sampleRate: Int): Double {
        var dominant = expected - 100.0
        var amplitude = Double.NEGATIVE_INFINITY
        for (hertz in expected.toInt() - 100..expected.toInt() + 100 step 2) {
            val candidate = spectralAmplitude(samples, hertz.toDouble(), sampleRate)
            if (candidate > amplitude) {
                dominant = hertz.toDouble()
                amplitude = candidate
            }
        }
        return dominant
    }

    private fun spectralAmplitude(samples: ShortArray, hertz: Double, sampleRate: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        samples.forEachIndexed { frame, sample ->
            val phase = 2.0 * PI * hertz * frame / sampleRate
            real += sample * cos(phase)
            imaginary -= sample * sin(phase)
        }
        return hypot(real, imaginary) / samples.size
    }

    private fun mixedPeak(first: ShortArray, second: ShortArray, secondOffset: Int): Int {
        val mixed = IntArray(maxOf(first.size, secondOffset + second.size))
        first.forEachIndexed { frame, sample -> mixed[frame] += sample.toInt() }
        second.forEachIndexed { frame, sample -> mixed[frame + secondOffset] += sample.toInt() }
        return mixed.maxOf { abs(it) }
    }

    private fun peak(samples: ShortArray, start: Int, endExclusive: Int): Int =
        samples.copyOfRange(start, endExclusive).maxOf { abs(it.toInt()) }

    private fun energy(samples: ShortArray): Double = samples.sumOf { sample ->
        val value = sample.toDouble()
        value * value
    }

    private fun rms(samples: ShortArray): Double = sqrt(energy(samples) / samples.size)

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(abs(actual - expected) <= tolerance, "expected=$expected actual=$actual tolerance=$tolerance")
    }
}
