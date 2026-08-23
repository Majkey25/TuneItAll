package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadstockTest {
    @Test
    fun `split guitar geometry fits the imported headstock vector`() {
        assertEquals(120.dp, SplitHeadstockGeometry.bodyWidth)
        assertEquals(52.dp, SplitHeadstockGeometry.nutWidth)
        assertEquals(132.dp, SplitHeadstockGeometry.centerGap)
        assertEquals(228.dp, SplitHeadstockGeometry.vectorHeight)
        assertEquals(4.dp, SplitHeadstockGeometry.vectorTop)
        assertEquals(listOf(52.dp, 120.dp, 188.dp), SplitHeadstockGeometry.postCenters)
        assertEquals(listOf(34.dp, 34.dp, 34.dp), SplitHeadstockGeometry.postOffsets)
        assertEquals(listOf(-20.dp, -12.dp, -4.dp, 4.dp, 12.dp, 20.dp), SplitHeadstockGeometry.nutSlots)
    }

    @Test
    fun `split guitar posts stay inside the imported vector bounds`() {
        assertTrue(SplitHeadstockGeometry.postOffsets.all { it + SplitHeadstockGeometry.selectedPostRadius < 60.dp })
    }

    @Test
    fun `split guitar rows keep physical standard string mapping`() {
        assertEquals(2 to 3, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(0))
        assertEquals(1 to 4, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(1))
        assertEquals(0 to 5, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(2))
    }
}
