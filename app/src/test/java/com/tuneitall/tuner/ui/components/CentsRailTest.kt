package com.tuneitall.tuner.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class CentsRailTest {
    @Test
    fun `cents position clamps to the physical rail`() {
        assertEquals(0f, normalizedCentsPosition(-60.0))
        assertEquals(0.5f, normalizedCentsPosition(0.0))
        assertEquals(1f, normalizedCentsPosition(60.0))
    }
}
