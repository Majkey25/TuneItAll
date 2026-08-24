package com.tuneitall.tuner

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoAssetTest {
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun instrumentPhotosDecodeAndMetronomeHasTransparentCorner() {
        assertPhoto(R.drawable.headstock_photo_cc0, transparentCorner = false)
        assertPhoto(R.drawable.metronome_nikko_body, transparentCorner = true)
    }

    private fun assertPhoto(@DrawableRes resourceId: Int, transparentCorner: Boolean) {
        val bitmap = requireNotNull(BitmapFactory.decodeResource(resources, resourceId))
        assertTrue(bitmap.width >= 512)
        assertTrue(bitmap.height >= 512)
        if (transparentCorner) {
            assertTrue(bitmap.hasAlpha())
            assertTrue(Color.alpha(bitmap.getPixel(0, 0)) <= 1)
        }
    }
}
