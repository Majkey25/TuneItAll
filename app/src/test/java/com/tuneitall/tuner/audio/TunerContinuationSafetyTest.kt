package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.InTuneConfirmationTracker
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerEngine
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.TunerReading
import com.tuneitall.tuner.tuner.TunerReadingRetainer
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunerContinuationSafetyTest {
    @Test
    fun `a brief tuned tone followed by hiss cannot renew readings or finish confirmation`() {
        val random = Random(99)
        val samples = ShortArray(RATE * 3) { index ->
            val tone = if (index < RATE / 2) 0.01 * sin(2 * PI * 110.0 * index / RATE) else 0.0
            ((tone + 0.00016 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
        val observations = observe(samples)
        assertTrue(observations.none { it.triggered })
        assertTrue(observations.filter { it.millis >= 900L }.all { it.estimate == null })
        assertNull(observations.last().display)
        assertFalse(observations.last().confirmed)
    }

    @Test
    fun `a quiet new string cannot finish confirmation of the older tuned E4`() {
        for (successor in listOf(195.9977, 246.9417)) {
            val random = Random(99)
            val samples = ShortArray(RATE * 3) { index ->
                val hertz = if (index < RATE / 2) 329.6276 else successor
                val amplitude = if (index < RATE / 2) 0.01 else 0.0001
                val phase = 2 * PI * hertz * index / RATE
                val tone = amplitude * (sin(phase) + 0.45 * sin(2 * phase))
                ((tone + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
            }
            val observations = observe(samples)
            assertTrue(observations.filter { it.millis >= 900L }.none {
                it.estimate != null && abs(MusicMath.cents(it.estimate.hertz, 329.6276)) < 100.0
            }, "$successor Hz renewed the older E4")
            assertTrue(observations.none { it.triggered && it.reading?.target?.value == 64 })
        }
    }

    private fun observe(samples: ShortArray): List<Observation> {
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val floor = AdaptiveNoiseFloor()
        val engine = TunerEngine()
        val retainer = TunerReadingRetainer()
        val confirmation = InTuneConfirmationTracker()
        val settings = TunerAudioSettings()
        val reference = ReferencePitch(440.0)
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, reference)
        return (8192..samples.size step 2048).map { end ->
            val frame = detector.analyze(samples.copyOfRange(end - 8192, end), RATE, range.minHertz, range.maxHertz)
            floor.observe(frame.rms, frame.isDetectorVoiced)
            val accepted = floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection)
            val estimate = tracker.update(if (accepted) frame else PitchFrame(emptyList(), frame.rms, frame.peak, 1.0), settings)
            val reading = estimate?.let { engine.update(it, TunerMode.AUTO, tuning, 0, reference, settings) }
            val millis = end * 1000L / RATE
            val display = retainer.update(reading, millis, settings.readingHoldMillis)
            val triggered = confirmation.update(reading?.target, reading?.inTune == true, millis, settings.confirmationMillis)
            Observation(millis, estimate, reading, display, triggered, confirmation.isConfirmed)
        }
    }

    private data class Observation(
        val millis: Long,
        val estimate: PitchEstimate?,
        val reading: TunerReading?,
        val display: TunerReading?,
        val triggered: Boolean,
        val confirmed: Boolean,
    )

    private companion object {
        const val RATE = 48_000
    }
}
