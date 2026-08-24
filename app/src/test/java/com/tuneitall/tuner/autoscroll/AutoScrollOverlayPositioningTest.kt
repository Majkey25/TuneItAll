package com.tuneitall.tuner.autoscroll

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoScrollOverlayPositioningTest {
    @Test
    fun `panel remains inside screen margins`() {
        assertEquals(12, AutoScrollOverlayPositioning.clampX(-40, 1_080, 240, 12))
        assertEquals(828, AutoScrollOverlayPositioning.clampX(1_000, 1_080, 240, 12))
        assertEquals(12, AutoScrollOverlayPositioning.clampY(-40, 1_920, 180, 12))
        assertEquals(1_728, AutoScrollOverlayPositioning.clampY(2_200, 1_920, 180, 12))
    }

    @Test
    fun `bubble snaps to nearest edge`() {
        assertEquals(0, AutoScrollOverlayPositioning.snapBubbleToEdge(100, 1_080, 52))
        assertEquals(1_028, AutoScrollOverlayPositioning.snapBubbleToEdge(700, 1_080, 52))
    }
}
