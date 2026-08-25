package com.tuneitall.tuner.audio

import com.tuneitall.tuner.tuner.MusicMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YinPitchDetectorTest {
    private val detector = YinPitchDetector()

    @Test
    fun `analysis retains fundamental and harmonic interpretations`() {
        val frame = detector.analyze(
            signal(110.0, 4096, listOf(1 to 0.20, 2 to 0.70, 3 to 0.10)),
            SAMPLE_RATE,
            70.0,
            1_000.0,
        )

        assertTrue(frame.candidates.size in 1..8)
        assertTrue(frame.candidates.any { abs(MusicMath.cents(it.hertz, 110.0)) <= 2.0 })
        assertTrue(frame.candidates.any { abs(MusicMath.cents(it.hertz, 220.0)) <= 2.0 })
        assertTrue(
            frame.candidates.any { fundamental ->
                abs(MusicMath.cents(fundamental.hertz, 110.0)) <= 2.0 &&
                    frame.candidates.any { harmonic ->
                        abs(MusicMath.cents(harmonic.hertz, 220.0)) <= 2.0 &&
                            abs(MusicMath.cents(fundamental.hertz, harmonic.hertz)) > 15.0
                    }
            },
        )
        assertTrue(frame.candidates.all { it.probability in 0.0..1.0 && it.periodicity in 0.0..1.0 })
    }

    @Test
    fun `silence returns a bounded unvoiced frame`() {
        val frame = detector.analyze(ShortArray(4096), SAMPLE_RATE, 70.0, 1_000.0)

        assertTrue(frame.candidates.isEmpty())
        assertEquals(1.0, frame.unvoicedProbability, 0.0)
    }

    @Test
    fun `dominant unvoiced probability is not voiced`() {
        val frame = PitchFrame(
            candidates = listOf(PitchCandidate(440.0, probability = 0.20, periodicity = 0.90)),
            rms = 0.1,
            peak = 0.2,
            unvoicedProbability = 0.80,
        )

        assertFalse(frame.isDetectorVoiced)
    }

    @Test
    fun `dominant candidate probability is voiced`() {
        val frame = PitchFrame(
            candidates = listOf(PitchCandidate(440.0, probability = 0.80, periodicity = 0.90)),
            rms = 0.1,
            peak = 0.2,
            unvoicedProbability = 0.20,
        )

        assertTrue(frame.isDetectorVoiced)
    }

    @Test
    fun `analysis contains supported chromatic and instrument pitches within one cent`() {
        listOf(30.87, 34.65, 41.20, 46.25, 82.41, 110.0, 440.0, 4186.01).forEach { expected ->
            val sampleCount = if (expected < 50.0) 8192 else 4096
            val frame = detector.analyze(sine(expected, sampleCount), SAMPLE_RATE, 27.5, 4186.01)
            val candidate = assertNotNull(
                frame.candidates.minByOrNull { abs(MusicMath.cents(it.hertz, expected)) },
                "No candidate for $expected Hz",
            )

            val error = abs(MusicMath.cents(candidate.hertz, expected))
            assertTrue(error <= 1.0, "$expected Hz error was $error cents")
            assertTrue(candidate.probability in 0.0..1.0)
            assertTrue(frame.rms > 0.1)
        }
    }

    @Test
    fun `analysis retains a very quiet guitar tone`() {
        val expected = 110.0
        val samples = signal(
            frequency = expected,
            sampleCount = 4096,
            harmonics = listOf(1 to 0.00030, 2 to 0.00012, 3 to 0.00006),
        )

        val candidate = assertNotNull(
            detector.analyze(samples, SAMPLE_RATE, 70.0, 420.0).candidates
                .minByOrNull { abs(MusicMath.cents(it.hertz, expected)) },
        )
        val error = abs(MusicMath.cents(candidate.hertz, expected))
        assertTrue(error <= 2.0, "Quiet signal error was $error cents")
    }

    @Test
    fun `analysis retains a near silent low electric guitar tone`() {
        val expected = 82.41
        val samples = signal(
            frequency = expected,
            sampleCount = 4096,
            harmonics = listOf(1 to 0.00016, 2 to 0.00006, 3 to 0.00003),
        )
        val frame = detector.analyze(samples, SAMPLE_RATE, 70.0, 420.0)
        val candidate = assertNotNull(
            frame.candidates.minByOrNull { abs(MusicMath.cents(it.hertz, expected)) },
        )

        assertTrue(abs(MusicMath.cents(candidate.hertz, expected)) <= 3.0)
        assertTrue(frame.rms < 0.0002)
    }

    @Test
    fun `analysis retains the fundamental under strong string buzz`() {
        val expected = 82.41
        val samples = noisyString(expected, seed = 7)
        val frame = detector.analyze(samples, SAMPLE_RATE, 70.0, 420.0)

        val candidate = assertNotNull(
            frame.candidates.minByOrNull { abs(MusicMath.cents(it.hertz, expected)) },
        )

        val error = abs(MusicMath.cents(candidate.hertz, expected))
        assertTrue(error <= 15.0, "Buzz candidate ${candidate.hertz} Hz was $error cents from E2")
        assertTrue(frame.isDetectorVoiced, "A valid buzzing-string candidate must not train the noise floor: $frame")
    }

    @Test
    fun `default universal pipeline acquires quiet buzzing guitar bass and ukulele`() {
        listOf(
            Triple(41.20, 34.0, 120.0),
            Triple(82.41, 69.0, 420.0),
            Triple(261.63, 190.0, 500.0),
            Triple(329.63, 190.0, 500.0),
        ).forEachIndexed { pitchIndex, (expected, minHertz, maxHertz) ->
            val tracker = PitchTracker()
            val rawCandidates = mutableListOf<Double>()
            var estimate: PitchEstimate? = null

            repeat(3) { frameIndex ->
                val frame = detector.analyze(
                    noisyString(expected, seed = 20 + pitchIndex * 10 + frameIndex, sampleCount = 8192),
                    SAMPLE_RATE,
                    minHertz,
                    maxHertz,
                )
                frame.candidates.minByOrNull { abs(MusicMath.cents(it.hertz, expected)) }
                    ?.let { rawCandidates += it.hertz }
                estimate = tracker.update(frame, TunerProfile.BALANCED.settings)
            }

            val detected = assertNotNull(estimate, "No universal estimate for $expected Hz")
            val error = abs(MusicMath.cents(detected.hertz, expected))
            assertTrue(error <= 20.0, "$expected Hz universal error was $error cents from $rawCandidates")
        }
    }

    @Test
    fun `analysis rejects very quiet broadband noise`() {
        val random = Random(11)
        val samples = ShortArray(4096) {
            (0.00025 * random.nextDouble(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        val frame = detector.analyze(samples, SAMPLE_RATE, 70.0, 420.0)

        assertTrue(frame.candidates.isEmpty())
        assertEquals(1.0, frame.unvoicedProbability, 0.0)
    }

    @Test
    fun `invalid detector arguments are rejected`() {
        val samples = sine(110.0, 4096)

        assertFailsWith<IllegalArgumentException> { detector.analyze(ShortArray(1), SAMPLE_RATE, 27.5, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.analyze(samples, 0, 27.5, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.analyze(samples, SAMPLE_RATE, 0.0, 4186.01) }
        assertFailsWith<IllegalArgumentException> { detector.analyze(samples, SAMPLE_RATE, 440.0, 110.0) }
        assertFailsWith<IllegalArgumentException> { detector.analyze(samples, SAMPLE_RATE, 27.5, 24_001.0) }
    }

    @Test
    fun `reused detector processes one hundred guitar frames promptly`() {
        val frame = signal(
            frequency = 82.41,
            sampleCount = 8192,
            harmonics = listOf(1 to 0.70, 2 to 0.20, 3 to 0.10),
        )

        detector.analyze(frame, SAMPLE_RATE, 27.0, 4_300.0)
        val elapsed = measureTimeMillis {
            repeat(100) {
                assertTrue(detector.analyze(frame, SAMPLE_RATE, 27.0, 4_300.0).candidates.isNotEmpty())
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

    private fun noisyString(frequency: Double, seed: Int, sampleCount: Int = 4096): ShortArray {
        val random = Random(seed)
        return ShortArray(sampleCount) { index ->
            val seconds = index.toDouble() / SAMPLE_RATE
            val periodic = 0.00020 * sin(2.0 * PI * frequency * seconds) +
                0.00012 * sin(4.0 * PI * frequency * seconds) +
                0.00008 * sin(6.0 * PI * frequency * seconds)
            val buzz = 0.00025 * random.nextDouble(-1.0, 1.0)
            ((periodic + buzz).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
