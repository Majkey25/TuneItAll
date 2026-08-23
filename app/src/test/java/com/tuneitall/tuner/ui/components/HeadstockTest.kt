package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadstockTest {
    @Test
    fun `split guitar geometry keeps generic headstock proportions`() {
        assertEquals(104.dp, SplitHeadstockGeometry.bodyWidth)
        assertEquals(52.dp, SplitHeadstockGeometry.nutWidth)
        assertEquals(48.dp, SplitHeadstockGeometry.neckWidth)
        assertEquals(132.dp, SplitHeadstockGeometry.centerGap)
        assertTrue(SplitHeadstockGeometry.bodyLength.value / SplitHeadstockGeometry.bodyWidth.value in 2.0f..2.1f)
        assertEquals(listOf(42.dp, 34.dp, 22.dp), SplitHeadstockGeometry.postOffsets)
        assertEquals(4.5.dp, SplitHeadstockGeometry.postRadius)
        assertEquals(12.dp, SplitHeadstockGeometry.keyWidth)
        assertEquals(9.dp, SplitHeadstockGeometry.keyHeight)
        assertEquals(listOf(-20.dp, -12.dp, -4.dp, 4.dp, 12.dp, 20.dp), SplitHeadstockGeometry.nutSlots)
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
