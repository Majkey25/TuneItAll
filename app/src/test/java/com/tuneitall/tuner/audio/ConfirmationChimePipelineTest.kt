package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfirmationChimePipelineTest {
    @Test
    fun `generated confirmation chime does not become a guitar measurement`() {
        checkChime(createConfirmationChime(sampleRate = SAMPLE_RATE))
    }

    @Test
    fun `five string bass stays accurate during confirmation feedback`() {
        checkChime(createConfirmationChime(sampleRate = SAMPLE_RATE), "bass-5-standard")
    }

    @Test
    fun `chime upper partials remain rejected when the room attenuates its fundamental`() {
        val chime = createConfirmationChime(sampleRate = SAMPLE_RATE)
        // A fixed FIR notch models a channel zero at 880 Hz; upper partials remain audible.
        val coefficient = 2.0 * cos(2 * PI * 880.0 / SAMPLE_RATE)
        val colored = DoubleArray(chime.size) { index ->
            chime[index].toDouble() - coefficient * (chime.getOrNull(index - 1)?.toDouble() ?: 0.0) +
                (chime.getOrNull(index - 2)?.toDouble() ?: 0.0)
        }
        val gain = chime.maxOf { abs(it.toInt()) } / colored.maxOf(::abs)
        checkChime(ShortArray(chime.size) { (colored[it] * gain).roundToInt().toShort() })
    }

    private fun checkChime(chime: ShortArray, tuningId: String = "guitar-6-standard") {
        val tuning = requireNotNull(TuningCatalog.byId(tuningId))
        val reference = ReferencePitch(440.0)
        val failures = mutableListOf<String>()
        for (note in tuning.notesLowToHigh) {
            val hertz = MusicMath.frequency(note, reference)
            for (chimeGain in listOf(0.0, 0.01, 0.1)) {
                val random = Random(note.value)
                val samples = ShortArray(2 * SAMPLE_RATE) { index ->
                    val phase = 2.0 * PI * hertz * index / SAMPLE_RATE
                    val guitar = 0.00035 * (sin(phase) + 0.4 * sin(2.0 * phase) + 0.2 * sin(3.0 * phase))
                    val direct = chime.getOrNull(index - SAMPLE_RATE - SAMPLE_RATE / 50)?.toDouble() ?: 0.0
                    val echo = chime.getOrNull(index - SAMPLE_RATE - SAMPLE_RATE * 3 / 50)?.toDouble() ?: 0.0
                    val feedback = (direct + 0.4 * echo) * chimeGain
                    ((guitar + 0.00016 * random.nextDouble(-1.0, 1.0)) * Short.MAX_VALUE + feedback).roundToInt().toShort()
                }
                val observations = measure(samples, tuningId = tuningId)
                assertTrue(observations.filter { it.first in 0.7..1.0 }.all { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, hertz)) < 3.0
                }, "Quiet control must be acquired before feedback: ${note.value}/$chimeGain")
                val wrong = observations.filter { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, hertz)) > 10.0
                }
                val during = observations.filter { it.first in 1.0..1.3 }
                assertEquals(7, during.size)
                assertTrue(during.all { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, hertz)) <= 3.0
                }, "Filtering must retain accurate fresh observations: ${note.value}/$chimeGain: $during")
                println("CHIME ${note.value}/$chimeGain fresh=${during.count { it.second != null }}/${during.size} maxCents=${during.mapNotNull { it.second?.hertz }.maxOfOrNull { abs(MusicMath.cents(it, hertz)) }}")
                if (wrong.isNotEmpty()) failures += "${note.value}/$chimeGain: $wrong"
                assertTrue(observations.filter { it.first in 1.4..1.8 }.all { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, hertz)) < 3.0
                }, "Fresh guitar must resume after feedback: ${note.value}/$chimeGain")
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `feedback rejection still follows tuning movement and a new string`() {
        for (initial in listOf(82.4069, 110.0, 195.9977, 329.6276)) {
            for (next in listOf(initial * 2.0.pow(30.0 / 1_200.0), 146.8324)) {
                val random = Random(33)
                val chime = createConfirmationChime(sampleRate = SAMPLE_RATE)
                var phase = 0.0
                val samples = ShortArray(2 * SAMPLE_RATE) { index ->
                    phase += 2.0 * PI * (if (index < SAMPLE_RATE) initial else next) / SAMPLE_RATE
                    val guitar = 0.00035 * (sin(phase) + 0.4 * sin(phase * 2.0))
                    val feedback = (chime.getOrNull(index - SAMPLE_RATE)?.toDouble() ?: 0.0) * 0.1
                    ((guitar + 0.00016 * random.nextDouble(-1.0, 1.0)) * Short.MAX_VALUE + feedback)
                        .roundToInt().toShort()
                }
                val observations = measure(samples)
                assertTrue(observations.filter { it.first in 0.7..1.0 }.all { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, initial)) <= 3.0
                })
                val moved = observations.filter { it.first in 1.3..1.8 }
                assertTrue(moved.isNotEmpty() && moved.all { (_, estimate) ->
                    estimate != null && abs(MusicMath.cents(estimate.hertz, next)) <= 3.0
                }, "Feedback blocked fresh movement $initial -> $next: $moved")
            }
        }
    }

    @Test
    fun `quiet decay without speaker feedback is not worsened by the temporary filter`() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        for (note in tuning.notesLowToHigh) {
            val hertz = MusicMath.frequency(note, ReferencePitch(440.0))
            val samples = DefaultCaptureAccuracyTest().pluck(hertz, note.value, amplitude = 0.00010)
            val baseline = measure(samples, filterActive = false).filter { it.first in 1.0..1.4 }
            val filtered = measure(samples).filter { it.first in 1.0..1.4 }
            fun correct(values: List<Pair<Double, PitchEstimate?>>) = values.count { (_, estimate) ->
                estimate != null && abs(MusicMath.cents(estimate.hertz, hertz)) <= 10.0
            }
            assertTrue(correct(filtered) >= correct(baseline), "Quiet ${note.value}: $baseline -> $filtered")
        }
    }

    @Test
    fun `an instrument partial near the chime frequency does not lose its quiet fundamental`() {
        for (hertz in listOf(110.0, 146.6667, 220.0, 293.3333)) {
            val random = Random(17)
            val samples = ShortArray(2 * SAMPLE_RATE) { index ->
                val phase = 2 * PI * hertz * index / SAMPLE_RATE
                val amplitude = if (index < SAMPLE_RATE / 2) 0.01 else 0.00010
                val guitar = amplitude * (0.3 * sin(phase) + 0.5 * sin(2 * phase) +
                    0.2 * sin(3 * phase) + sin(2 * PI * 880.0 * index / SAMPLE_RATE))
                ((guitar + 0.00016 * random.nextDouble(-1.0, 1.0)) * Short.MAX_VALUE).roundToInt().toShort()
            }
            val baseline = measure(samples, filterActive = false).filter { it.first in 1.0..1.4 }
            val filtered = measure(samples).filter { it.first in 1.0..1.4 }
            val baselineCorrect = baseline.count { it.second?.let { estimate ->
                abs(MusicMath.cents(estimate.hertz, hertz)) <= 10.0
            } == true }
            val filteredCorrect = filtered.count { it.second?.let { estimate ->
                abs(MusicMath.cents(estimate.hertz, hertz)) <= 10.0
            } == true }
            assertTrue(baselineCorrect > 0, "Control did not acquire $hertz: $baseline")
            assertTrue(filteredCorrect >= baselineCorrect, "Lost instrument at $hertz: $baseline -> $filtered")
        }
    }

    @Test
    fun `chime and hiss cannot renew a stopped guitar measurement`() {
        val chime = createConfirmationChime(sampleRate = SAMPLE_RATE)
        val random = Random(42)
        val samples = ShortArray(2 * SAMPLE_RATE) { index ->
            val guitar = if (index < SAMPLE_RATE) 0.00035 * sin(2 * PI * 110.0 * index / SAMPLE_RATE) else 0.0
            val feedback = (chime.getOrNull(index - SAMPLE_RATE)?.toDouble() ?: 0.0) * 0.1
            ((guitar + 0.00016 * random.nextDouble(-1.0, 1.0)) * Short.MAX_VALUE + feedback).roundToInt().toShort()
        }
        val observations = measure(samples)
        assertTrue(observations.filter { it.first in 0.7..1.0 }.all { it.second != null })
        val stopped = observations.filter { it.first in 1.2..1.8 }
        assertTrue(stopped.isNotEmpty() && stopped.all { it.second == null }, "Renewed stale guitar: $stopped")
    }

    @Test
    fun `high note capture cannot accidentally enable low range feedback rejection`() {
        val detector = YinPitchDetector()
        assertFailsWith<IllegalArgumentException> {
            detector.analyze(ShortArray(WINDOW_SIZE), SAMPLE_RATE, 27.0, 4_300.0, rejectConfirmation = true)
        }
        assertFailsWith<IllegalArgumentException> {
            detector.analyze(ShortArray(WINDOW_SIZE), SAMPLE_RATE, 69.0, 440.0, rejectConfirmation = true)
        }
    }

    private fun measure(
        samples: ShortArray,
        filterActive: Boolean = true,
        tuningId: String = "guitar-6-standard",
    ): List<Pair<Double, PitchEstimate?>> {
        val tuning = requireNotNull(TuningCatalog.byId(tuningId))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val floor = AdaptiveNoiseFloor()
        val settings = TunerAudioSettings()
        val feedbackGate = FeedbackInputGate()
        return (WINDOW_SIZE..samples.size step HOP_SIZE).map { end ->
            val nowMillis = end * 1_000L / SAMPLE_RATE
            if (filterActive && end >= SAMPLE_RATE && end - HOP_SIZE < SAMPLE_RATE) {
                feedbackGate.suppress(1_000L, 400L)
            }
            val frame = detector.analyze(
                samples.copyOfRange(end - WINDOW_SIZE, end), SAMPLE_RATE, range.minHertz, range.maxHertz,
                rejectConfirmation = !feedbackGate.accepts(nowMillis),
            )
            floor.observe(frame.rms, frame.isDetectorVoiced)
            val estimate = tracker.update(
                if (floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection)) frame
                else PitchFrame(emptyList(), frame.rms, frame.peak, 1.0),
                settings,
            )
            end.toDouble() / SAMPLE_RATE to estimate
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 8_192
        const val HOP_SIZE = 2_048
    }
}
