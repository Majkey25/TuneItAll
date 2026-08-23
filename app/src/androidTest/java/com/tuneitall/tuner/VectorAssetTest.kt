package com.tuneitall.tuner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caverock.androidsvg.SVG
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VectorAssetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledInstrumentSvgAssetsParse() {
        val headstock = SVG.getFromResource(context, R.raw.guitar_head_commons)
        val metronome = SVG.getFromResource(context, R.raw.metronome_body_cc0)

        assertTrue(headstock.documentWidth > 0f)
        assertTrue(headstock.documentHeight > 0f)
        assertTrue(metronome.documentWidth > 0f)
        assertTrue(metronome.documentHeight > 0f)
    }
}
