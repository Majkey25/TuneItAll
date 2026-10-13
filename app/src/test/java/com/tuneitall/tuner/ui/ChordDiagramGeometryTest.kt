package com.tuneitall.tuner.ui

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.music.ChordVoicing
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

    @Test
    fun `G minor add ninth barre excludes the separate bass finger`() {
        val voicing = ChordVoicing(
            frets = listOf(3, 1, 0, 2, 3, 3),
            fingers = listOf(3, 1, 0, 2, 4, 4),
            barres = listOf(3),
        )

        assertEquals(listOf(4..5), chordBarreSpans(voicing, 3))
    }

    @Test
    fun `F major barre spans all six strings`() {
        val voicing = ChordVoicing(
            frets = listOf(1, 3, 3, 2, 1, 1),
            fingers = listOf(1, 3, 4, 2, 1, 1),
            barres = listOf(1),
        )

        assertEquals(listOf(0..5), chordBarreSpans(voicing, 1))
    }

    @Test
    fun `unknown fingers do not create a barre span`() {
        val voicing = ChordVoicing(
            frets = listOf(1, 3, 3, 2, 1, 1),
            barres = listOf(1),
        )

        assertEquals(emptyList<IntRange>(), chordBarreSpans(voicing, 1))
    }
}
