package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class QuietDecayAccuracyTest {
    @Test
    fun `a quiet first strike acquires the fundamental rather than its octave below`() {
        for (rate in listOf(44_100, 48_000)) for (seed in listOf(211, 419)) {
            for (shift in listOf(-30.0, 30.0)) for (base in listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)) {
                val frequency = base * 2.0.pow(shift / 1200.0)
                val random = Random(seed)
                val samples = ShortArray(rate * 2) { index ->
                    val phase = 2 * PI * frequency * index / rate + 0.7
                    val amplitude = 0.0001 * minOf(index / (rate * 0.005), 1.0)
                    val tone = amplitude * (sin(phase) + 0.45 * sin(2 * phase + 0.4) + 0.2 * sin(3 * phase - 0.3))
                    ((tone + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
                }
                val readings = measure(samples, 250L..1500L, rate)
                assertTrue(readings.count { it != null } >= readings.size * 0.9, "Quiet acquisition dropout: $readings")
                assertTrue(readings.filterNotNull().all { abs(MusicMath.cents(it.hertz, frequency)) <= 10.0 },
                    "Wrong quiet acquisition at $frequency Hz: $readings")
            }
        }
    }

    @Test
    fun `quiet decaying guitar retains accurate fresh measurements`() {
        val failures = mutableListOf<String>()
        for (seed in listOf(51, 73, 137)) {
            for (hertz in listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)) {
                val readings = measure(decay(hertz, seed))
                val errors = readings.mapNotNull { it?.hertz }.map { abs(MusicMath.cents(it, hertz)) }.sorted()
                val correct = errors.count { it <= 10.0 }
                val result = "seed=$seed hertz=$hertz correct=$correct/${readings.size} fresh=${errors.size} " +
                    "p95=${errors.getOrNull((errors.size * 0.95).toInt())}"
                println("QUIET_REFINEMENT $result")
                if (correct < readings.size * 0.90) failures += result
                if (errors.any { it > 10.0 }) failures += "Inaccurate fresh reading: $result"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `a stopped tone cannot continue through background hiss`() {
        val random = Random(99)
        val samples = ShortArray(RATE * 3) { index ->
            val tone = if (index < RATE / 2) 0.01 * sin(2 * PI * 110.0 * index / RATE) else 0.0
            ((tone + 0.00016 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
        assertTrue(measure(samples, 900L..3000L).all { it == null })
        assertTrue(measure(decay(146.8324, 51) + decay(null, 109), 4001L..8000L).all { it == null })
    }

    @Test
    fun `a new quiet string cannot preserve the preceding note`() {
        for (next in listOf(195.9977, 246.9417)) {
            val random = Random(99)
            val samples = ShortArray(RATE * 3) { index ->
                val frequency = if (index < RATE / 2) 329.6276 else next
                val amplitude = if (index < RATE / 2) 0.01 else 0.0001
                val phase = 2 * PI * frequency * index / RATE
                val tone = amplitude * (sin(phase) + 0.45 * sin(2 * phase))
                ((tone + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
            }
            val readings = measure(samples, 900L..2500L)
            assertTrue(readings.none { it != null && abs(MusicMath.cents(it.hertz, 329.6276)) < 100.0 })
            assertTrue(readings.count { it != null && abs(MusicMath.cents(it.hertz, next)) <= 10.0 } >= readings.size * 0.9)
        }
    }

    @Test
    fun `the same noise without a guitar does not create measurements`() {
        for (seed in listOf(7, 31, 51, 109)) {
            val readings = measure(decay(null, seed), 0L..4000L)
            assertTrue(readings.all { it == null }, "Noise seed=$seed: $readings")
        }
    }

    @Test
    fun `nonperiodic noise level changes and isolated clicks cannot acquire a note`() {
        for (seed in listOf(13, 71, 229, 503)) {
            val random = Random(seed)
            var colored = 0.0
            val samples = ShortArray(RATE * 3) { index ->
                colored = 0.98 * colored + 0.02 * random.nextDouble(-1.0, 1.0)
                val level = if (index < RATE / 2) 0.00016 else 0.004
                val click = if (index == 3000 || index == 57000 || index == 117000) 0.06 else 0.0
                ((level * (colored + 0.3 * random.nextDouble(-1.0, 1.0)) + click) * 32768.0).roundToInt().toShort()
            }
            for (mode in listOf(TunerMode.AUTO, TunerMode.CHROMATIC)) {
                assertTrue(measure(samples, 0L..3000L, mode = mode).all { it == null }, "Noise seed=$seed mode=$mode")
            }
        }
    }

    private fun measure(
        samples: ShortArray,
        times: LongRange = 1250L..3250L,
        rate: Int = RATE,
        mode: TunerMode = TunerMode.AUTO,
    ): List<PitchEstimate?> {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(mode, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val settings = TunerAudioSettings()
        val floor = AdaptiveNoiseFloor()
        val readings = mutableListOf<PitchEstimate?>()
        for (end in 8192..samples.size step 2048) {
            val frame = detector.analyze(samples.copyOfRange(end - 8192, end), rate, range.minHertz, range.maxHertz)
            floor.observe(frame.rms, frame.isDetectorVoiced)
            assertTrue(floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection))
            val reading = tracker.update(frame, settings)
            if (end * 1000L / rate in times) {
                readings += reading
            }
        }
        return readings
    }

    private fun decay(hertz: Double?, seed: Int): ShortArray {
        val random = Random(seed)
        return ShortArray(RATE * 4) { index ->
            val age = index.toDouble() / RATE - 1.0
            val envelope = if (hertz != null && age in 0.0..2.5) {
                0.0007 * minOf(age / 0.008, 1.0) * exp(-age / 0.75)
            } else 0.0
            val phase = 2.0 * PI * (hertz ?: 0.0) * age
            val signal = envelope * (sin(phase) + 0.45 * sin(2 * phase) + 0.2 * sin(3 * phase))
            ((signal + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
    }

    private companion object {
        const val RATE = 48_000
    }
}
