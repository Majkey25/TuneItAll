package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.PitchEstimate
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
import kotlin.test.assertNull
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
            ),
        )

        assertEquals(MidiNote(45), reading.target)
        assertTrue(reading.cents > 0.0)
        assertFalse(reading.inTune)
    }

    @Test
    fun `manual mode keeps the selected target`() {
        val reading = requireNotNull(
            TunerEngine().update(estimate(110.0), TunerMode.MANUAL, standard, 0, reference),
        )

        assertEquals(MidiNote(40), reading.target)
        assertEquals(MidiNote(45), reading.detected)
    }

    @Test
    fun `chromatic mode uses the nearest equal temperament note`() {
        val reading = requireNotNull(
            TunerEngine().update(estimate(443.0), TunerMode.CHROMATIC, standard, 0, reference),
        )

        assertEquals(MidiNote(69), reading.target)
        assertEquals(MidiNote(69), reading.detected)
        assertTrue(reading.cents > 0.0)
    }

    @Test
    fun `three cents or less is in tune`() {
        val oneCentSharp = 440.0 * 2.0.pow(1.0 / 1200.0)
        val reading = requireNotNull(
            TunerEngine().update(estimate(oneCentSharp), TunerMode.CHROMATIC, standard, 0, reference),
        )

        assertTrue(reading.inTune)
        assertEquals(1.0, reading.cents, 1e-6)
    }

    @Test
    fun `green range accepts three cents but rejects four`() {
        val engine = TunerEngine()
        val threeCentsSharp = 440.0 * 2.0.pow(3.0 / 1200.0)
        val fourCentsSharp = 440.0 * 2.0.pow(4.0 / 1200.0)

        repeat(8) { engine.update(estimate(threeCentsSharp), TunerMode.CHROMATIC, standard, 0, reference) }
        assertTrue(requireNotNull(engine.update(estimate(threeCentsSharp), TunerMode.CHROMATIC, standard, 0, reference)).inTune)
        repeat(12) { engine.update(estimate(fourCentsSharp), TunerMode.CHROMATIC, standard, 0, reference) }
        assertFalse(requireNotNull(engine.update(estimate(fourCentsSharp), TunerMode.CHROMATIC, standard, 0, reference)).inTune)
    }

    @Test
    fun `configured confidence and RMS gates are applied before smoothing`() {
        val engine = TunerEngine()
        val balanced = DetectionSensitivity(50)

        assertNull(
            engine.update(
                estimate(440.0, confidence = 0.79),
                TunerMode.CHROMATIC,
                standard,
                0,
                reference,
                balanced,
            ),
        )
        assertNull(
            engine.update(estimate(440.0, rms = 0.002), TunerMode.CHROMATIC, standard, 0, reference, balanced),
        )
        val reading = requireNotNull(
            engine.update(estimate(441.0), TunerMode.CHROMATIC, standard, 0, reference, balanced),
        )
        assertEquals(441.0, reading.hertz, 1e-9)
    }

    @Test
    fun `high sensitivity accepts a quiet estimate rejected by balanced sensitivity`() {
        val quiet = estimate(440.0, confidence = 0.76, rms = 0.0015)

        assertNull(
            TunerEngine().update(
                quiet,
                TunerMode.CHROMATIC,
                standard,
                0,
                reference,
                DetectionSensitivity(50),
            ),
        )
        assertTrue(
            TunerEngine().update(
                quiet,
                TunerMode.CHROMATIC,
                standard,
                0,
                reference,
                DetectionSensitivity(100),
            ) != null,
        )
    }

    @Test
    fun `three value median removes a single outlier`() {
        val engine = TunerEngine()

        engine.update(estimate(440.0), TunerMode.CHROMATIC, standard, 0, reference)
        engine.update(estimate(880.0), TunerMode.CHROMATIC, standard, 0, reference)
        val reading = requireNotNull(
            engine.update(estimate(441.0), TunerMode.CHROMATIC, standard, 0, reference),
        )

        assertTrue(reading.hertz in 440.0..440.5)
        assertEquals(MidiNote(69), reading.target)
    }

    @Test
    fun `inconsistent harmonic glitches do not move a stable reading`() {
        val engine = TunerEngine()
        repeat(5) { engine.update(estimate(440.0), TunerMode.CHROMATIC, standard, 0, reference) }

        engine.update(estimate(880.0), TunerMode.CHROMATIC, standard, 0, reference)
        val reading = requireNotNull(
            engine.update(estimate(660.0), TunerMode.CHROMATIC, standard, 0, reference),
        )

        assertEquals(440.0, reading.hertz, 0.5)
        assertEquals(MidiNote(69), reading.target)
    }

    @Test
    fun `needle damps alternating pitch jitter`() {
        val engine = TunerEngine()
        val outputs = listOf(0.0, 8.0, -8.0, 8.0, -8.0, 7.0, -7.0).map { cents ->
            val hertz = 440.0 * 2.0.pow(cents / 1200.0)
            requireNotNull(engine.update(estimate(hertz), TunerMode.CHROMATIC, standard, 0, reference)).cents
        }

        assertTrue(outputs.drop(2).all { abs(it) <= 3.0 }, "Unstable cents: $outputs")
    }

    @Test
    fun `new string needs three consistent frames before switching`() {
        val engine = TunerEngine()
        repeat(5) { engine.update(estimate(440.0), TunerMode.CHROMATIC, standard, 0, reference) }

        val first = requireNotNull(engine.update(estimate(329.63), TunerMode.CHROMATIC, standard, 0, reference))
        val second = requireNotNull(engine.update(estimate(329.63), TunerMode.CHROMATIC, standard, 0, reference))
        val third = requireNotNull(engine.update(estimate(329.63), TunerMode.CHROMATIC, standard, 0, reference))

        assertEquals(MidiNote(69), first.target)
        assertEquals(MidiNote(69), second.target)
        assertEquals(MidiNote(64), third.target)
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

        repeat(3) { engine.update(estimate(below), TunerMode.AUTO, standard, 0, reference) }
        val jitter = repeatReading(engine, barelyAbove)
        assertEquals(MidiNote(40), jitter.target)

        val switched = repeatReading(engine, clearlyAbove)
        assertEquals(MidiNote(45), switched.target)
    }

    @Test
    fun `context change clears smoothing state`() {
        val engine = TunerEngine()
        engine.update(estimate(82.41), TunerMode.MANUAL, standard, 0, reference)
        engine.update(estimate(82.41), TunerMode.MANUAL, standard, 0, reference)

        val reading = requireNotNull(
            engine.update(estimate(110.0), TunerMode.MANUAL, standard, 1, reference),
        )
        assertEquals(110.0, reading.hertz, 1e-9)
        assertEquals(MidiNote(45), reading.target)
    }

    @Test
    fun `invalid state and pitch estimates are rejected`() {
        val engine = TunerEngine()

        assertFailsWith<IllegalArgumentException> {
            engine.update(estimate(440.0), TunerMode.MANUAL, standard, 6, reference)
        }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(Double.NaN, 0.9, 0.1) }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(440.0, 1.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { PitchEstimate(440.0, 0.9, -0.1) }
    }

    private fun repeatReading(engine: TunerEngine, hertz: Double): TunerReading {
        var reading: TunerReading? = null
        repeat(3) {
            reading = engine.update(estimate(hertz), TunerMode.AUTO, standard, 0, reference)
        }
        return requireNotNull(reading)
    }

    private fun estimate(
        hertz: Double,
        confidence: Double = 0.95,
        rms: Double = 0.20,
    ): PitchEstimate = PitchEstimate(hertz, confidence, rms)
}
