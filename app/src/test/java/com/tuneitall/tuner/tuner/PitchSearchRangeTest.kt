package com.tuneitall.tuner.tuner

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
}
