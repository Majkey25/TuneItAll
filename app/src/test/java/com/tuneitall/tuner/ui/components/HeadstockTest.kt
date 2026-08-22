package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadstockTest {
    @Test
    fun `split guitar geometry keeps generic headstock proportions`() {
        assertEquals(88.dp, SplitHeadstockGeometry.bodyWidth)
        assertEquals(48.dp, SplitHeadstockGeometry.nutWidth)
        assertEquals(44.dp, SplitHeadstockGeometry.neckWidth)
        assertEquals(120.dp, SplitHeadstockGeometry.centerGap)
        assertEquals(0.06f, SplitHeadstockGeometry.fillAlpha)
        assertTrue(SplitHeadstockGeometry.bodyLength.value / SplitHeadstockGeometry.bodyWidth.value in 2.35f..2.45f)
        assertEquals(listOf(36.dp, 29.dp, 22.dp), SplitHeadstockGeometry.postOffsets)
        assertEquals(3.5.dp, SplitHeadstockGeometry.postRadius)
        assertEquals(8.dp, SplitHeadstockGeometry.keyWidth)
        assertEquals(14.dp, SplitHeadstockGeometry.keyHeight)
        assertEquals(listOf(-18.dp, -10.8.dp, -3.6.dp, 3.6.dp, 10.8.dp, 18.dp), SplitHeadstockGeometry.nutSlots)
    }

    @Test
    fun `split guitar posts stay inside the wood outline`() {
        SplitHeadstockGeometry.postCenters.zip(SplitHeadstockGeometry.postOffsets).forEach { (y, offset) ->
            assertTrue(offset + SplitHeadstockGeometry.postRadius <= SplitHeadstockGeometry.bodyHalfWidthAt(y))
        }
    }

    @Test
    fun `split guitar rows keep physical standard string mapping`() {
        assertEquals(2 to 3, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(0))
        assertEquals(1 to 4, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(1))
        assertEquals(0 to 5, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(2))
    }
}
