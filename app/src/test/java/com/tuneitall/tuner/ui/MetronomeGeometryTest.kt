package com.tuneitall.tuner.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class MetronomeGeometryTest {
    @Test
    fun `animated arm aligns with the imported mechanical body`() {
        val geometry = metronomeArmGeometry(Size(1_000f, 1_000f), density = 1f)

        assertEquals(Offset(556f, 620f), geometry.pivot)
        assertEquals(Offset(556f, 100f), geometry.armEnd)
        assertEquals(Size(30f, 16f), geometry.weight.size)
        assertEquals(3f, geometry.weightSlot.width)
    }

    @Test
    fun `audio phase rotates only the arm across exact endpoints`() {
        assertEquals(-24f, metronomeArmDegrees(-1.0))
        assertEquals(0f, metronomeArmDegrees(0.0))
        assertEquals(24f, metronomeArmDegrees(1.0))
    }
}
