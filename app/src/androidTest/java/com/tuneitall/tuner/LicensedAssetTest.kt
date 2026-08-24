package com.tuneitall.tuner

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensedAssetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun headstockPngsAndMetronomeVectorLoadAtSourceSize() {
        assertPng(R.drawable.headstock_3x3_noun)
        assertPng(R.drawable.headstock_6_inline_noun)
        val metronome = requireNotNull(context.getDrawable(R.drawable.metronome_body_cc0_exact))
        assertTrue(metronome.intrinsicWidth >= 180)
        assertTrue(metronome.intrinsicHeight >= 180)
    }

    private fun assertPng(@DrawableRes resourceId: Int) {
        val bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, resourceId))
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertTrue(bitmap.hasAlpha())
    }
}
