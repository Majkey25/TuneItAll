package com.tuneitall.tuner

import androidx.annotation.DrawableRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VectorAssetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun instrumentVectorsInflateAtUsefulSize() {
        assertVector(R.drawable.headstock_3x3_vector)
        assertVector(R.drawable.headstock_6_inline_vector)
        assertVector(R.drawable.metronome_body_cc0)
    }

    private fun assertVector(@DrawableRes resourceId: Int) {
        val drawable = requireNotNull(context.getDrawable(resourceId))
        assertTrue(drawable.intrinsicWidth >= 120)
        assertTrue(drawable.intrinsicHeight >= 180)
    }
}
