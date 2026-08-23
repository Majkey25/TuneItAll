package com.tuneitall.tuner.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomeGeometryTest {
    @Test
    fun `mechanical body layers stay visibly filled without becoming accent colored`() {
        assertEquals(0.06f, MECHANICAL_BODY_FILL_ALPHA)
        assertEquals(0.10f, MECHANICAL_SIDE_PLANE_ALPHA)
    }

    @Test
    fun `classic mechanical silhouette uses tapered body plate plinth and horizontal scale`() {
        val geometry = mechanicalMetronomeGeometry(Size(1_000f, 1_000f), density = 1f)

        assertEquals(
            listOf(
                Offset(430f, 90f),
                Offset(570f, 90f),
                Offset(680f, 770f),
                Offset(320f, 770f),
            ),
            geometry.body,
        )
        assertEquals(Rect(190f, 890f, 810f, 898f), geometry.plinth)
        assertEquals(4, geometry.sidePlane.size)
        assertEquals(
            listOf(
                Offset(460f, 150f),
                Offset(540f, 150f),
                Offset(570f, 650f),
                Offset(430f, 650f),
            ),
            geometry.scalePlate,
        )
        assertEquals(7, geometry.scaleTicks.size)
        geometry.scaleTicks.forEachIndexed { index, tick ->
            assertEquals(tick.start.y, tick.end.y)
            assertEquals(if (index % 2 == 0) 12f else 8f, tick.end.x - tick.start.x)
        }
        assertEquals(Rect(455f, 45f, 545f, 105f), geometry.topCap)
        assertEquals(Rect(488f, 18f, 512f, 52f), geometry.topBead)
        assertEquals(4, geometry.baseFront.size)
        assertEquals(Offset(500f, 720f), geometry.pivot)
        assertEquals(Offset(500f, 120f), geometry.armEnd)
        assertEquals(Size(32f, 16f), geometry.weight.size)
        assertEquals(3f, geometry.weightSlot.width)
        assertEquals(
            listOf(
                MechanicalLayer.BODY,
                MechanicalLayer.SIDE_PLANE,
                MechanicalLayer.SCALE_PLATE,
                MechanicalLayer.SCALE_TICKS,
                MechanicalLayer.TOP_CAP,
                MechanicalLayer.BASE_FRONT,
                MechanicalLayer.PLINTH,
                MechanicalLayer.FEET,
                MechanicalLayer.WINDING_KEY,
                MechanicalLayer.ARM,
                MechanicalLayer.WEIGHT,
                MechanicalLayer.HUB,
            ),
            geometry.layers,
        )
    }

    @Test
    fun `audio phase only rotates arm across exact endpoints`() {
        val geometry = mechanicalMetronomeGeometry(Size(360f, 188f), density = 1f)

        assertEquals(-24f, metronomeArmDegrees(-1.0))
        assertEquals(0f, metronomeArmDegrees(0.0))
        assertEquals(24f, metronomeArmDegrees(1.0))
        assertTrue(geometry.scaleTicks.all { it.start.y == it.end.y })
    }
}
