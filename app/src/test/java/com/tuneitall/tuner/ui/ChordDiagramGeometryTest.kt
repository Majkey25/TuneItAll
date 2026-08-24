package com.tuneitall.tuner.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordDiagramGeometryTest {
    @Test
    fun `chord diagram is narrow and portrait shaped everywhere`() {
        assertEquals(232.dp, CHORD_DIAGRAM_WIDTH)
        assertEquals(300.dp, CHORD_DIAGRAM_HEIGHT)
        assertTrue(CHORD_DIAGRAM_HEIGHT > CHORD_DIAGRAM_WIDTH)
    }
}
