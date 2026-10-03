package com.tuneitall.tuner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BrandIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun launcherAndRoundIconKeepForkCenteredInsideTheMask() {
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round).forEach { id ->
            val drawable = requireNotNull(context.getDrawable(id))
            assertTrue(drawable is AdaptiveIconDrawable)
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 512, 512)
            drawable.draw(Canvas(bitmap))
            val bounds = bounds(bitmap) { pixel ->
                Color.alpha(pixel) > 200 && Color.red(pixel) > 200 && Color.green(pixel) > 200
            }
            assertCentered(bounds, 512)
            assertTrue("Launcher mark height: ${bounds.height()}", bounds.height() in 344..352)
            assertTrue("Launcher mark width: ${bounds.width()}", bounds.width() in 130..138)
            assertEquals(255, Color.alpha(bitmap.getPixel(256, 256)))
            bitmap.recycle()
        }
    }

    @Test
    fun monochromeAndNotificationMarksUseTheirOwnSafeSizes() {
        listOf(R.drawable.ic_launcher_monochrome to (230..235), R.drawable.ic_notification to (460..468))
            .forEach { (id, expectedHeight) ->
                val drawable = requireNotNull(context.getDrawable(id))
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 512, 512)
                drawable.draw(Canvas(bitmap))
                val bounds = bounds(bitmap) { Color.alpha(it) > 128 }
                assertCentered(bounds, 512)
                assertTrue("Mask mark height: ${bounds.height()}", bounds.height() in expectedHeight)
                assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
                bitmap.recycle()
            }
    }

    private fun assertCentered(bounds: Rect, size: Int) {
        assertTrue(abs(bounds.exactCenterX() - size / 2f) <= 1f)
        assertTrue(abs(bounds.exactCenterY() - size / 2f) <= 1f)
    }

    private fun bounds(bitmap: Bitmap, matches: (Int) -> Boolean): Rect {
        val result = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (matches(bitmap.getPixel(x, y))) {
                result.left = minOf(result.left, x)
                result.top = minOf(result.top, y)
                result.right = maxOf(result.right, x + 1)
                result.bottom = maxOf(result.bottom, y + 1)
            }
        }
        assertTrue("Icon mark missing", !result.isEmpty)
        return result
    }
}
