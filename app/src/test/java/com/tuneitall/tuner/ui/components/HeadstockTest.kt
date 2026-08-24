package com.tuneitall.tuner.ui.components

import androidx.compose.ui.unit.dp
import com.tuneitall.tuner.model.HeadstockLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadstockTest {
    @Test
    fun `split guitar photo fits between the string controls`() {
        assertEquals(132.dp, SplitHeadstockGeometry.imageWidth)
        assertEquals(228.dp, SplitHeadstockGeometry.imageHeight)
        assertEquals(132.dp, SplitHeadstockGeometry.centerGap)
    }

    @Test
    fun `split guitar rows keep physical standard string mapping`() {
        assertEquals(2 to 3, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(0))
        assertEquals(1 to 4, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(1))
        assertEquals(0 to 5, HeadstockLayout.SPLIT_3_3.stringIndicesAtRow(2))
    }
}
