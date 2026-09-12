package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.InTuneConfirmationTracker
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerEngine
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PitchTrackerRetuneTest {
    @Test
    fun quietRetuneDoesNotLatchTheOctaveBelowAfterABriefCandidateGap() {
        for ((initialHertz, seed) in listOf(329.6276 to 81, 146.8324 to 31, 195.9977 to 31)) {
            checkRetune(initialHertz, seed)
        }
    }

    private fun checkRetune(initialHertz: Double, seed: Int) {
        val expected = initialHertz * 2.0.pow(30.0 / 1200.0)
        val samples = quietRetune(initialHertz, expected, seed)
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val settings = TunerAudioSettings()
        val floor = AdaptiveNoiseFloor()
        val engine = TunerEngine()
        val confirmation = InTuneConfirmationTracker()
        val settled = mutableListOf<Double?>()
        for (end in 8192..samples.size step 2048) {
            val frame = detector.analyze(samples.copyOfRange(end - 8192, end), SAMPLE_RATE, range.minHertz, range.maxHertz)
            floor.observe(frame.rms, frame.isDetectorVoiced)
            assertTrue(floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection))
            val tracked = tracker.update(frame, settings)
            val millis = end * 1000L / SAMPLE_RATE
            val reading = tracked?.let { engine.update(it, TunerMode.AUTO, tuning, 0, ReferencePitch(440.0), settings) }
            assertFalse(confirmation.update(reading?.target, reading?.inTune == true, millis, settings.confirmationMillis))
            if (millis in 1000L..2500L) assertFalse(reading?.inTune == true, "$initialHertz Hz: retuned audio was declared in tune")
            if (millis in 1500L..2500L) settled += tracked?.hertz
        }
        val correct = settled.count { it != null && abs(MusicMath.cents(it, expected)) <= 10.0 }
        assertTrue(settled.isNotEmpty() && correct >= settled.size * 0.9,
            "$initialHertz Hz: brief candidate loss must not latch a subharmonic: $correct/${settled.size}, $settled")
    }

    private fun quietRetune(initialHertz: Double, retunedHertz: Double, seed: Int): ShortArray {
        val random = Random(seed)
        var phase = 0.0
        return ShortArray(SAMPLE_RATE * 3) { index ->
            val hertz = if (index < SAMPLE_RATE / 2) initialHertz else retunedHertz
            val amplitude = if (index < SAMPLE_RATE / 2) 0.01 else 0.0001
            phase += 2 * PI * hertz / SAMPLE_RATE
            val signal = amplitude * (sin(phase) + 0.45 * sin(2 * phase))
            ((signal + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
