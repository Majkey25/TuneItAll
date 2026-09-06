package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultCaptureAccuracyTest {
    @Test
    fun `quiet low E is measured within the standard tuning tolerance`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val frequency = MusicMath.frequency(tuning.notesLowToHigh.first(), ReferencePitch(440.0))
        for (buzz in listOf(false, true)) {
            val result = measure(tuning, pluck(frequency, seed = 42, buzz = buzz), frequency, 0.25, 1.6)
            assertTrue(result.correctRatio >= 0.95, "Lost low E with buzz=$buzz: $result")
            assertTrue(requireNotNull(result.p95ErrorCents) <= 3.0, "Unstable low E with buzz=$buzz: $result")
        }
    }

    @Test
    fun `default auto acquires quiet electric strings without a manual frequency hint`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        for (note in tuning.notesLowToHigh) {
            val frequency = MusicMath.frequency(note, ReferencePitch(ReferencePitch.DEFAULT_HERTZ))
            val result = measure(tuning, pluck(frequency, seed = note.value), frequency, 0.25, 1.25)
            assertTrue(result.correctRatio >= 0.85, "Quiet ${note.value}: $result")
            assertTrue(result.octaveErrors == 0, "Quiet ${note.value}: $result")
        }
    }

    @Test
    fun `default auto follows a decaying electric string with fret collision buzz`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val frequency = MusicMath.frequency(tuning.notesLowToHigh.first(), ReferencePitch(ReferencePitch.DEFAULT_HERTZ))
        val result = measure(tuning, pluck(frequency, seed = 42, buzz = true), frequency, 0.25, 1.6)
        assertTrue(result.correctRatio >= 0.85, "Buzzing E2: $result")
        assertTrue(result.octaveErrors == 0, "Buzzing E2: $result")
    }

    @Test
    fun `default five string bass auto retains low B after the attack`() {
        val tuning = requireNotNull(TuningCatalog.byId("bass-5-standard"))
        val frequency = MusicMath.frequency(tuning.notesLowToHigh.first(), ReferencePitch(ReferencePitch.DEFAULT_HERTZ))
        val result = measure(tuning, pluck(frequency, seed = 23), frequency, 0.35, 1.5)
        assertTrue(result.correctRatio >= 0.85, "Bass B0: $result")
        assertTrue(result.octaveErrors == 0, "Bass B0: $result")
        assertTrue(requireNotNull(result.p95ErrorCents) <= 3.5, "Low-pass refinement degraded bass B0: $result")
    }

    @Test
    fun `default auto retains useful readings near the microphone noise floor`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val results = tuning.notesLowToHigh.map { note ->
            val frequency = MusicMath.frequency(note, ReferencePitch(ReferencePitch.DEFAULT_HERTZ))
            measure(tuning, pluck(frequency, note.value, amplitude = 0.00010), frequency, 0.25, 1.25)
        }
        val frames = results.sumOf { it.frames }
        assertTrue(results.sumOf { it.voiced }.toDouble() / frames >= 0.78, "Near-noise coverage: $results")
        assertTrue(results.sumOf { it.correct }.toDouble() / frames >= 0.68, "Near-noise accuracy: $results")
        assertTrue(results.sumOf { it.octaveErrors }.toDouble() / frames <= 0.04, "Near-noise octaves: $results")
    }

    @Test
    fun `default auto and chromatic track new notes and tuning movement promptly`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        for (mode in listOf(TunerMode.AUTO, TunerMode.CHROMATIC)) {
            val step = tone { time -> if (time < 1.0) 82.406889 else 110.0 }
            val firstNew = observations(step, tuning, mode).first { (time, estimate) ->
                time >= 1.0 && estimate != null && abs(MusicMath.cents(estimate.hertz, 110.0)) < 5.0
            }
            assertTrue(firstNew.first < 1.3, "$mode note switch took ${(firstNew.first - 1.0) * 1_000.0} ms")
            val glideFrequency = { time: Double ->
                110.0 * 2.0.pow((60.0 * (time - 0.7).coerceIn(0.0, 1.0) - 30.0) / 1_200.0)
            }
            val glide = observations(tone(glideFrequency), tuning, mode).filter { it.first in 1.0..1.6 }
            assertTrue(glide.all { (_, estimate) -> estimate != null }, "$mode lost the tuning glide")
            assertTrue(glide.all { (time, estimate) ->
                abs(MusicMath.cents(requireNotNull(estimate).hertz, glideFrequency(time))) < 10.0
            }, "$mode tuning glide lags by more than ten cents")
            println("TEMPORAL $mode stepMillis=${(firstNew.first - 1.0) * 1_000.0}")
        }
    }

    @Test
    fun `default auto does not invent stable notes in microphone hiss`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        for (seed in 1..4) {
            val random = Random(seed)
            val samples = ShortArray((DURATION_SECONDS * SAMPLE_RATE).toInt()) {
                pcm(0.00016 * random.nextDouble(-1.0, 1.0))
            }
            val result = measure(tuning, samples, null, 0.25, 2.0)
            assertTrue(result.voicedRatio <= 0.03, "Noise seed $seed: $result")
        }
    }

    private fun measure(
        tuning: TuningPreset,
        samples: ShortArray,
        expectedHertz: Double?,
        fromSeconds: Double,
        untilSeconds: Double,
    ): Accuracy {
        var considered = 0
        var voiced = 0
        var correct = 0
        var octaveErrors = 0
        val errors = mutableListOf<Double>()
        for ((seconds, estimate) in observations(samples, tuning)) {
            if (seconds !in fromSeconds..untilSeconds) continue
            considered++
            if (estimate == null) continue
            voiced++
            if (expectedHertz != null) {
                val error = abs(MusicMath.cents(estimate.hertz, expectedHertz))
                errors += error
                if (error <= 10.0) correct++
                if (abs(error - 1_200.0) < 100.0) octaveErrors++
            }
        }
        return Accuracy(
            considered,
            voiced,
            correct,
            octaveErrors,
            errors.sorted().let { if (it.isEmpty()) null else it[(it.size * 0.95).toInt().coerceAtMost(it.lastIndex)] },
        ).also { println("AUTO ${tuning.id} expected=$expectedHertz $it") }
    }

    private fun observations(
        samples: ShortArray,
        tuning: TuningPreset,
        mode: TunerMode = TunerMode.AUTO,
    ): List<Pair<Double, PitchEstimate?>> {
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val noiseFloor = AdaptiveNoiseFloor()
        val settings = TunerAudioSettings()
        val range = pitchSearchRange(mode, tuning, 0, ReferencePitch(ReferencePitch.DEFAULT_HERTZ))
        val window = ShortArray(WINDOW_SIZE)
        val runtimes = mutableListOf<Long>()
        return (WINDOW_SIZE..samples.size step HOP_SIZE).map { end ->
            samples.copyInto(window, startIndex = end - WINDOW_SIZE, endIndex = end)
            lateinit var frame: PitchFrame
            runtimes += measureNanoTime { frame = detector.analyze(window, SAMPLE_RATE, range.minHertz, range.maxHertz) }
            noiseFloor.observe(frame.rms, frame.isDetectorVoiced)
            val accepted = noiseFloor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection)
            end.toDouble() / SAMPLE_RATE to tracker.update(
                if (accepted) frame else PitchFrame(emptyList(), frame.rms, frame.peak, 1.0),
                settings,
            )
        }.also {
            val timed = runtimes.drop(10).sorted()
            if (timed.isNotEmpty()) println("RUNTIME $mode ${tuning.id} p95Millis=${timed[(timed.size * 0.95).toInt()] / 1e6}")
        }
    }

    private fun tone(frequency: (Double) -> Double): ShortArray {
        var phase = 0.0
        return ShortArray(SAMPLE_RATE * 2) { index ->
            phase += 2.0 * PI * frequency(index.toDouble() / SAMPLE_RATE) / SAMPLE_RATE
            pcm(0.07 * sin(phase) + 0.025 * sin(phase * 2.0) + 0.01 * sin(phase * 3.0))
        }
    }

    private fun pluck(frequency: Double, seed: Int, buzz: Boolean = false, amplitude: Double = 0.00035): ShortArray {
        val random = Random(seed)
        var phase = 0.0
        return ShortArray((DURATION_SECONDS * SAMPLE_RATE).toInt()) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val attackSharpness = 12.0 * exp(-time / 0.035)
            phase += 2.0 * PI * frequency * 2.0.pow(attackSharpness / 1_200.0) / SAMPLE_RATE
            val envelope = amplitude * (1.0 - exp(-time / 0.004)) * exp(-time / 1.1)
            var tone = 0.0
            for (harmonic in 1..8) {
                val harmonicAmplitude = if (harmonic == 1) 0.35 else 1.0 / harmonic
                val inharmonicRatio = sqrt((1.0 + 0.00008 * harmonic * harmonic) / 1.00008)
                tone += harmonicAmplitude * sin(phase * harmonic * inharmonicRatio) * exp(-time * harmonic * 0.08)
            }
            val collision = if (buzz) {
                val contact = ((sin(phase) - 0.88) / 0.12).coerceAtLeast(0.0)
                contact * 2.0 * sin(2.0 * PI * 3_100.0 * time)
            } else {
                0.0
            }
            val hiss = 0.00016 * random.nextDouble(-1.0, 1.0)
            pcm(envelope * (tone + collision) + hiss)
        }
    }

    private fun pcm(value: Double): Short = (value * Short.MAX_VALUE).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private data class Accuracy(
        val frames: Int,
        val voiced: Int,
        val correct: Int,
        val octaveErrors: Int,
        val p95ErrorCents: Double?,
    ) {
        val correctRatio: Double get() = correct.toDouble() / frames
        val voicedRatio: Double get() = voiced.toDouble() / frames
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 8_192
        const val HOP_SIZE = 2_048
        const val DURATION_SECONDS = 2.4
    }
}
