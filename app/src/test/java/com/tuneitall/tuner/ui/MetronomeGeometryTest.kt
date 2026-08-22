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
        val geometry = mechanicalMetronomeGeometry(Size(1_000f, 1_000f), density = 1f, phase = 0.0)

        assertEquals(
            listOf(
                Offset(390f, 70f),
                Offset(610f, 70f),
                Offset(780f, 910f),
                Offset(220f, 910f),
            ),
            geometry.body,
        )
        assertEquals(Rect(190f, 890f, 810f, 900f), geometry.plinth)
        assertEquals(4, geometry.sidePlane.size)
        assertEquals(
            listOf(
                Offset(440f, 130f),
                Offset(560f, 130f),
                Offset(590f, 680f),
                Offset(410f, 680f),
            ),
            geometry.scalePlate,
        )
        assertEquals(7, geometry.scaleTicks.size)
        geometry.scaleTicks.forEachIndexed { index, tick ->
            assertEquals(tick.start.y, tick.end.y)
            assertEquals(if (index % 2 == 0) 12f else 8f, tick.end.x - tick.start.x)
        }
        assertEquals(Offset(500f, 780f), geometry.pivot)
        assertEquals(Offset(500f, 130f), geometry.armEnd)
        assertEquals(Size(32f, 16f), geometry.weight.size)
        assertEquals(3f, geometry.weightSlot.width)
        assertEquals(0f, geometry.armDegrees)
        assertEquals(
            listOf(
                MechanicalLayer.BODY,
                MechanicalLayer.SIDE_PLANE,
                MechanicalLayer.SCALE_PLATE,
                MechanicalLayer.SCALE_TICKS,
                MechanicalLayer.PLINTH,
                MechanicalLayer.ARM,
                MechanicalLayer.WEIGHT,
                MechanicalLayer.HUB,
            ),
            geometry.layers,
        )
    }

    @Test
    fun `audio phase only rotates arm across exact endpoints`() {
        val left = mechanicalMetronomeGeometry(Size(360f, 188f), density = 1f, phase = -1.0)
        val center = mechanicalMetronomeGeometry(Size(360f, 188f), density = 1f, phase = 0.0)
        val right = mechanicalMetronomeGeometry(Size(360f, 188f), density = 1f, phase = 1.0)

        assertEquals(-24f, left.armDegrees)
        assertEquals(0f, center.armDegrees)
        assertEquals(24f, right.armDegrees)
        assertEquals(left.body, center.body)
        assertEquals(center.body, right.body)
        assertEquals(left.scalePlate, right.scalePlate)
        assertTrue(left.scaleTicks.all { it.start.y == it.end.y })
    }
}
