package com.tuneitall.tuner.ui

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class MetronomeGeometryTest {
    @Test
    fun `animated arm aligns with the imported mechanical body`() {
        val geometry = metronomeArmGeometry(Size(1_000f, 1_000f), density = 1f)

        assertEquals(554.4f, geometry.pivot.x, 0.1f)
        assertEquals(635.6f, geometry.pivot.y, 0.1f)
        assertEquals(554.4f, geometry.armEnd.x, 0.1f)
        assertEquals(100f, geometry.armEnd.y, 0.1f)
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
