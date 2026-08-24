package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadstockTest {
    @Test
    fun `split guitar vector fits between the string controls`() {
        assertEquals(132.dp, SplitHeadstockGeometry.vectorWidth)
        assertEquals(228.dp, SplitHeadstockGeometry.vectorHeight)
        assertEquals(132.dp, SplitHeadstockGeometry.centerGap)
    }

    @Test
    fun `split guitar rows keep physical standard string mapping`() {
        assertEquals(2 to 3, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(0))
        assertEquals(1 to 4, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(1))
        assertEquals(0 to 5, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(2))
    }

    @Test
    fun `inline six puts string controls opposite the tuning machines`() {
        assertEquals(104.dp, InlineSixGeometry.vectorWidth)
        assertEquals(304.dp, InlineSixGeometry.vectorHeight)
        assertEquals(null to 5, HeadstockLayout.INLINE_6.stringIndicesAtRow(0))
        assertEquals(null to 0, HeadstockLayout.INLINE_6.stringIndicesAtRow(5))
    }
}
