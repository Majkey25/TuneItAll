package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class PitchTrackerRetuneTest {
    @Test
    fun quietRetuneDoesNotLatchTheOctaveBelowAfterABriefCandidateGap() {
        val samples = quietRetune()
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val settings = TunerAudioSettings()
        val settled = mutableListOf<Double?>()
        for (end in 8192..samples.size step 2048) {
            val frame = detector.analyze(samples.copyOfRange(end - 8192, end), SAMPLE_RATE, range.minHertz, range.maxHertz)
            val tracked = tracker.update(frame, settings)
            if (end * 1000L / SAMPLE_RATE in 1500L..2500L) settled += tracked?.hertz
        }
        val correct = settled.count { it != null && abs(MusicMath.cents(it, RETUNED_HERTZ)) <= 10.0 }
        assertTrue(settled.isNotEmpty() && correct >= settled.size * 0.9,
            "Brief candidate loss must not latch a subharmonic: $correct/${settled.size}, $settled")
    }

    private fun quietRetune(): ShortArray {
        val random = Random(81)
        var phase = 0.0
        return ShortArray(SAMPLE_RATE * 3) { index ->
            val hertz = if (index < SAMPLE_RATE / 2) 329.6276 else RETUNED_HERTZ
            val amplitude = if (index < SAMPLE_RATE / 2) 0.01 else 0.0001
            phase += 2 * PI * hertz / SAMPLE_RATE
            val signal = amplitude * (sin(phase) + 0.45 * sin(2 * phase))
            ((signal + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        val RETUNED_HERTZ = 329.6276 * 2.0.pow(30.0 / 1200.0)
    }
}
