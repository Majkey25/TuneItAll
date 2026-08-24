package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadstockTest {
    @Test
    fun `split guitar image fills the space between the string controls`() {
        assertEquals(196.dp, SplitHeadstockGeometry.imageSize)
        assertEquals(196.dp, SplitHeadstockGeometry.centerGap)
    }

    @Test
    fun `split guitar rows keep physical standard string mapping`() {
        assertEquals(2 to 3, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(0))
        assertEquals(1 to 4, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(1))
        assertEquals(0 to 5, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(2))
    }

    @Test
    fun `inline six puts string controls on the left`() {
        assertEquals(244.dp, InlineSixGeometry.imageSize)
        assertEquals(244.dp, InlineSixGeometry.centerGap)
        assertEquals(5 to null, HeadstockLayout.INLINE_6.stringIndicesAtRow(0))
        assertEquals(0 to null, HeadstockLayout.INLINE_6.stringIndicesAtRow(5))
    }
}
