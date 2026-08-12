package com.tuneitall.tuner.audio

import com.tuneitall.tuner.tuner.MusicMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()

    @Test
    fun `detects the supported chromatic and instrument range within one cent`() {
        listOf(30.87, 34.65, 41.20, 46.25, 82.41, 110.0, 440.0, 4186.01).forEach { expected ->
            val sampleCount = if (expected < 50.0) 8192 else 4096
            val estimate = assertNotNull(
                detector.detect(
                    samples = sine(expected, sampleCount),
                    sampleRate = SAMPLE_RATE,
                    minFrequency = 27.5,
                    maxFrequency = 4186.01,
                ),
                "No estimate for $expected Hz",
            )

            val error = abs(MusicMath.cents(estimate.hertz, expected))
            assertTrue(error <= 1.0, "$expected Hz error was $error cents")
            assertTrue(estimate.confidence in 0.0..1.0)
            assertTrue(estimate.rms > 0.1)
        }
    }

    @Test
    fun `strong second harmonic does not cause an octave jump`() {
        val expected = 110.0
        val samples = signal(
            frequency = expected,
            sampleCount = 4096,
            harmonics = listOf(1 to 0.25, 2 to 0.70, 3 to 0.10),
        )

        val estimate = assertNotNull(detector.detect(samples, SAMPLE_RATE, 27.5, 4186.01))
        val error = abs(MusicMath.cents(estimate.hertz, expected))
        assertTrue(error <= 1.0, "Harmonic signal error was $error cents")
    }

    @Test
    fun `default sensitivity detects a very quiet guitar tone`() {
        val expected = 110.0
        val samples = signal(
            frequency = expected,
            sampleCount = 4096,
            harmonics = listOf(1 to 0.00030, 2 to 0.00012, 3 to 0.00006),
        )

        val estimate = assertNotNull(detector.detect(samples, SAMPLE_RATE, 70.0, 420.0))
        val error = abs(MusicMath.cents(estimate.hertz, expected))
        assertTrue(error <= 2.0, "Quiet signal error was $error cents")
    }

    @Test
    fun `silence and deterministic noise have no pitch`() {
        assertNull(detector.detect(ShortArray(4096), SAMPLE_RATE, 27.5, 4186.01))

        val random = Random(7)
        val noise = ShortArray(4096) { random.nextInt(-10_000, 10_001).toShort() }
        assertNull(detector.detect(noise, SAMPLE_RATE, 27.5, 4186.01))
    }

    @Test
    fun `sensitivity does not turn deterministic noise into a pitch`() {
        val random = Random(19)
        val noisyTone = ShortArray(4096) { index ->
            val seconds = index.toDouble() / SAMPLE_RATE
            val sample = 0.25 * sin(2.0 * PI * 110.0 * seconds) + random.nextDouble(-0.15, 0.15)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }

        assertNull(
            detector.detect(
                noisyTone,
                SAMPLE_RATE,
                27.5,
                4186.01,
                DetectionSensitivity(50),
            ),
        )
        assertNull(
            detector.detect(noisyTone, SAMPLE_RATE, 27.5, 4186.01, DetectionSensitivity(100)),
        )
    }

    @Test
    fun `invalid detector arguments are rejected`() {
        val samples = sine(110.0, 4096)

        assertFailsWith<IllegalArgumentException> { detector.detect(ShortArray(1), SAMPLE_RATE, 27.5, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.detect(samples, 0, 27.5, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.detect(samples, SAMPLE_RATE, 0.0, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.detect(samples, SAMPLE_RATE, 440.0, 110.0) }
        assertFailsWith<IllegalArgumentException> { detector.detect(samples, SAMPLE_RATE, 27.5, 24_001.0) }
        assertFailsWith<IllegalArgumentException> { YinPitchDetector(threshold = 0.0) }
        assertFailsWith<IllegalArgumentException> { YinPitchDetector(threshold = 1.0) }
    }

    @Test
    fun `reused detector processes one hundred guitar frames promptly`() {
        val frame = signal(
            frequency = 82.41,
            sampleCount = 4096,
            harmonics = listOf(1 to 0.70, 2 to 0.20, 3 to 0.10),
        )

        detector.detect(frame, SAMPLE_RATE, 70.0, 1000.0)
        val elapsed = measureTimeMillis {
            repeat(100) {
                assertNotNull(detector.detect(frame, SAMPLE_RATE, 70.0, 1000.0))
            }
        }

        assertTrue(elapsed < 10_000, "100 frames took $elapsed ms")
    }

    private fun sine(frequency: Double, sampleCount: Int): ShortArray =
        signal(frequency, sampleCount, listOf(1 to 0.80))

    private fun signal(
        frequency: Double,
        sampleCount: Int,
        harmonics: List<Pair<Int, Double>>,
    ): ShortArray = ShortArray(sampleCount) { index ->
        val seconds = index.toDouble() / SAMPLE_RATE
        val sample = harmonics.sumOf { (multiple, amplitude) ->
            amplitude * sin(2.0 * PI * frequency * multiple * seconds)
        }.coerceIn(-1.0, 1.0)
        (sample * Short.MAX_VALUE).toInt().toShort()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
