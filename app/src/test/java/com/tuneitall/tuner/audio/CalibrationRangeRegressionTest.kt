package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalibrationRangeRegressionTest {
    @Test
    fun `drop A bass acquires A0 at the lowest allowed reference pitch`() {
        for (mode in TunerMode.entries) {
            assertTracks(25.625, ReferencePitch(410.0), mode)
        }
    }

    @Test
    fun `chromatic detection acquires C8 at the highest allowed reference pitch`() {
        assertTracks(4_566.555321610449, ReferencePitch(480.0), TunerMode.CHROMATIC)
    }

    private fun assertTracks(hertz: Double, referencePitch: ReferencePitch, mode: TunerMode) {
        val sampleRate = 48_000
        val samples = ShortArray(8192) { index ->
            (0.2 * sin(2.0 * PI * hertz * index / sampleRate) * 32768.0).roundToInt().toShort()
        }
        val tuning = requireNotNull(TuningCatalog.byId("bass-5-drop-a"))
        val range = pitchSearchRange(mode, tuning, 0, referencePitch)
        val frame = YinPitchDetector().analyze(samples, sampleRate, range.minHertz, range.maxHertz)
        val estimate = assertNotNull(PitchTracker().update(frame, TunerAudioSettings()), "$mode did not acquire $hertz Hz")

        assertTrue(abs(MusicMath.cents(estimate.hertz, hertz)) <= 5.0,
            "$mode acquired ${estimate.hertz} Hz instead of $hertz Hz")
    }
}
