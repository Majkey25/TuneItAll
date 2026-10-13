package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchSearchRangeTest {
    private val standard = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
    private val reference = ReferencePitch(440.0)

    @Test
    fun `automatic guitar detection excludes unrelated harmonics`() {
        val range = pitchSearchRange(TunerMode.AUTO, standard, selectedString = 0, reference)

        assertTrue(range.minHertz in 60.0..75.0)
        assertTrue(range.maxHertz in 380.0..430.0)
    }

    @Test
    fun `manual detection stays near the selected string`() {
        val range = pitchSearchRange(TunerMode.MANUAL, standard, selectedString = 0, reference)

        assertTrue(range.minHertz in 55.0..70.0)
        assertTrue(range.maxHertz in 105.0..120.0)
    }

    @Test
    fun `chromatic detection keeps the complete supported range`() {
        assertEquals(PitchSearchRange(27.0, 4_300.0), pitchSearchRange(TunerMode.CHROMATIC, standard, 0, reference))
    }

    @Test
    fun `accepted low custom notes fall back to a nonempty supported capture range`() {
        val tuning = standard.copy(id = "custom-low", notesLowToHigh = List(6) { MidiNote(12) })

        for (mode in listOf(TunerMode.AUTO, TunerMode.MANUAL)) {
            assertEquals(PitchSearchRange(27.0, 4_300.0), pitchSearchRange(mode, tuning, 0, reference))
        }
    }

    @Test
    fun `accepted high custom notes fall back to a nonempty supported capture range`() {
        val tuning = standard.copy(id = "custom-high", notesLowToHigh = List(6) { MidiNote(127) })

        for (mode in listOf(TunerMode.AUTO, TunerMode.MANUAL)) {
            assertEquals(PitchSearchRange(27.0, 4_300.0), pitchSearchRange(mode, tuning, 0, reference))
        }
    }

    @Test
    fun `chromatic capture bounds follow reference pitch calibration`() {
        for ((a4, expectedMin, expectedMax) in listOf(
            Triple(410.0, 25.15909090909091, 4_006.818181818182),
            Triple(480.0, 29.454545454545453, 4_690.909090909091),
        )) {
            val range = pitchSearchRange(TunerMode.CHROMATIC, standard, 0, ReferencePitch(a4))

            assertEquals(expectedMin, range.minHertz, 1e-9)
            assertEquals(expectedMax, range.maxHertz, 1e-9)
        }
    }

    @Test
    fun `drop A bass includes its calibrated A0 target in every mode`() {
        val tuning = requireNotNull(TuningCatalog.byId("bass-5-drop-a"))

        for (mode in TunerMode.entries) {
            val range = pitchSearchRange(mode, tuning, 0, ReferencePitch(410.0))

            assertTrue(25.625 in range.minHertz..range.maxHertz, "$mode excludes calibrated A0: $range")
        }
    }
}
