package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.audio.PitchEstimate
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunerEngineTest {
    private val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
    private val reference = ReferencePitch(440.0)

    @Test
    fun `auto mode selects the closest string and preserves cents direction`() {
        val reading = requireNotNull(
            TunerEngine().update(
                estimate = estimate(111.0),
                mode = TunerMode.AUTO,
                tuning = standard,
                selectedString = 0,
                referencePitch = reference,
                settings = settings,
            ),
        )

        assertEquals(MidiNote(45), reading.target)
        assertTrue(reading.cents > 0.0)
        assertFalse(reading.inTune)
    }

    @Test
    fun `manual mode keeps the selected target`() {
        val reading = requireNotNull(
            TunerEngine().update(estimate(110.0), TunerMode.MANUAL, standard, 0, reference, settings),
        )

        assertEquals(MidiNote(40), reading.target)
        assertEquals(MidiNote(45), reading.detected)
    }

    @Test
    fun `chromatic mode uses the nearest equal temperament note`() {
        val reading = requireNotNull(
            TunerEngine().update(estimate(443.0), TunerMode.CHROMATIC, standard, 0, reference, settings),
        )

        assertEquals(MidiNote(69), reading.target)
        assertEquals(MidiNote(69), reading.detected)
        assertTrue(reading.cents > 0.0)
    }

    @Test
    fun `one cent tolerance accepts one cent but rejects two`() {
        val oneCentSharp = 440.0 * 2.0.pow(1.0 / 1200.0)
        val twoCentsSharp = 440.0 * 2.0.pow(2.0 / 1200.0)

        assertTrue(
            requireNotNull(TunerEngine().update(estimate(oneCentSharp), TunerMode.CHROMATIC, standard, 0, reference, settings.copy(inTuneCents = 1))).inTune,
        )
        assertFalse(
            requireNotNull(TunerEngine().update(estimate(twoCentsSharp), TunerMode.CHROMATIC, standard, 0, reference, settings.copy(inTuneCents = 1))).inTune,
        )
    }

    @Test
    fun `ten cent tolerance accepts ten cents but rejects eleven`() {
        val tenCentsSharp = 440.0 * 2.0.pow(10.0 / 1200.0)
        val elevenCentsSharp = 440.0 * 2.0.pow(11.0 / 1200.0)

        assertTrue(
            requireNotNull(TunerEngine().update(estimate(tenCentsSharp), TunerMode.CHROMATIC, standard, 0, reference, settings.copy(inTuneCents = 10))).inTune,
        )
        assertFalse(
            requireNotNull(TunerEngine().update(estimate(elevenCentsSharp), TunerMode.CHROMATIC, standard, 0, reference, settings.copy(inTuneCents = 10))).inTune,
        )
    }

    @Test
    fun `needle damps alternating pitch jitter`() {
        val engine = TunerEngine()
        val outputs = listOf(0.0, 8.0, -8.0, 8.0, -8.0, 7.0, -7.0).map { cents ->
            val hertz = 440.0 * 2.0.pow(cents / 1200.0)
            requireNotNull(engine.update(estimate(hertz), TunerMode.CHROMATIC, standard, 0, reference, settings)).cents
        }

        assertTrue(outputs.drop(2).all { abs(it) <= 3.0 }, "Unstable cents: $outputs")
    }

    @Test
    fun `default needle stability uses the documented coefficient`() {
        val engine = TunerEngine()
        val input = 440.0 * 2.0.pow(30.0 / 1200.0)

        engine.update(estimate(440.0), TunerMode.CHROMATIC, standard, 0, reference, settings)
        val reading = engine.update(estimate(input), TunerMode.CHROMATIC, standard, 0, reference, settings)

        assertEquals(440.0 * 2.0.pow(30.0 * 0.3775 / 1200.0), reading.hertz, 1e-9)
    }

    @Test
    fun `engine accepts a valid low confidence low RMS estimate`() {
        val reading = TunerEngine().update(
            estimate(440.0, confidence = 0.000001, rms = 0.000001),
            TunerMode.CHROMATIC,
            standard,
            0,
            reference,
            settings,
        )

        assertEquals(440.0, reading.hertz, 1e-9)
    }

    @Test
    fun `smoothing resets when detected note changes`() {
        val engine = TunerEngine()
        engine.update(estimate(440.0), TunerMode.CHROMATIC, standard, 0, reference, settings)
        val withinNote = requireNotNull(
            engine.update(estimate(440.0 * 2.0.pow(30.0 / 1200.0)), TunerMode.CHROMATIC, standard, 0, reference, settings),
        )
        val changedNote = requireNotNull(
            engine.update(estimate(466.16), TunerMode.CHROMATIC, standard, 0, reference, settings),
        )

        assertTrue(withinNote.hertz > 440.0 && withinNote.hertz < 440.0 * 2.0.pow(30.0 / 1200.0))
        assertEquals(466.16, changedNote.hertz, 1e-9)
        assertEquals(MidiNote(70), changedNote.detected)
    }

    @Test
    fun `auto target hysteresis ignores small boundary jitter`() {
        val engine = TunerEngine()
        val e2 = MusicMath.frequency(MidiNote(40), reference)
        val a2 = MusicMath.frequency(MidiNote(45), reference)
        val boundary = sqrt(e2 * a2)
        val below = boundary * 2.0.pow(-20.0 / 1200.0)
        val barelyAbove = boundary * 2.0.pow(2.0 / 1200.0)
        val clearlyAbove = boundary * 2.0.pow(10.0 / 1200.0)

        repeat(3) { engine.update(estimate(below), TunerMode.AUTO, standard, 0, reference, settings) }
        val jitter = repeatReading(engine, barelyAbove)
        assertEquals(MidiNote(40), jitter.target)

        val switched = repeatReading(engine, clearlyAbove)
        assertEquals(MidiNote(45), switched.target)
    }

    @Test
    fun `context change clears smoothing state`() {
        val engine = TunerEngine()
        engine.update(estimate(82.41), TunerMode.MANUAL, standard, 0, reference, settings)
        engine.update(estimate(82.41), TunerMode.MANUAL, standard, 0, reference, settings)

        val reading = requireNotNull(
            engine.update(estimate(110.0), TunerMode.MANUAL, standard, 1, reference, settings),
        )
        assertEquals(110.0, reading.hertz, 1e-9)
        assertEquals(MidiNote(45), reading.target)
    }

    @Test
    fun `invalid state and pitch estimates are rejected`() {
        val engine = TunerEngine()

        assertFailsWith<IllegalArgumentException> {
            engine.update(estimate(440.0), TunerMode.MANUAL, standard, 6, reference, settings)
        }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(Double.NaN, 0.9, 0.1) }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(440.0, 1.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(440.0, 0.9, -0.1) }
    }

    private fun repeatReading(engine: TunerEngine, hertz: Double): TunerReading {
        var reading: TunerReading? = null
        repeat(3) {
            reading = engine.update(estimate(hertz), TunerMode.AUTO, standard, 0, reference, settings)
        }
        return requireNotNull(reading)
    }

    private fun estimate(
        hertz: Double,
        confidence: Double = 0.95,
        rms: Double = 0.20,
    ): PitchEstimate = PitchEstimate(hertz, confidence, rms)

    private val settings = TunerAudioSettings()
}
