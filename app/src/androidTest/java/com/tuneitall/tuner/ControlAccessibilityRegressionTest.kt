package com.tuneitall.tuner

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.ui.AppearanceColorDialog
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeUiState
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemeLayout
import com.tuneitall.tuner.ui.theme.ThemePalette
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.colorContrast
import com.tuneitall.tuner.ui.theme.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ControlAccessibilityRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun metronomeStepGlyphsFitTheirButtonsAtLargeText() {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 2.6f),
            ) {
                TuneItAllTheme(darkTheme = false) {
                    Box(Modifier.requiredSize(360.dp, 720.dp)) {
                        MetronomeScreen(
                            state = MetronomeUiState(), phaseProvider = { 0.0 }, onBpmChange = {},
                            onTap = {}, onStart = {}, onStop = {}, onOpenSettings = {},
                        )
                    }
                }
            }
        }

        listOf("−" to "metronome_bpm_decrease", "+" to "metronome_bpm_increase").forEach { (glyph, tag) ->
            val results = mutableListOf<TextLayoutResult>()
            val glyphNode = compose.onNode(hasText(glyph) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            glyphNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                assertTrue(getResults(results))
            }
            val layout = results.single()
            val diagnostic = "$glyph size=${layout.size}, hasVisualOverflow=${layout.hasVisualOverflow}, " +
                "heightOverflow=${layout.didOverflowHeight}, constraints=${layout.layoutInput.constraints}, " +
                "paragraphWidth=${layout.multiParagraph.width}, " +
                "lineLeft=${layout.getLineLeft(0)}, lineRight=${layout.getLineRight(0)}"
            assertTrue("$glyph has no drawing width", layout.size.width > 0)
            assertTrue("$glyph has no drawing height", layout.size.height > 0)
            assertEquals(diagnostic, 1, layout.lineCount)
            assertFalse(diagnostic, layout.didOverflowHeight)
            // The semantics action rebuilds paragraph width from max constraints, not the measured glyph width.
            assertTrue(diagnostic, layout.getLineLeft(0) >= 0f && layout.getLineRight(0) <= layout.size.width)
            val node = glyphNode.assertIsDisplayed().fetchSemanticsNode()
            val bounds = node.boundsInRoot
            val button = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$glyph extends outside $button: $bounds", bounds.left >= button.left &&
                bounds.top >= button.top && bounds.right <= button.right && bounds.bottom <= button.bottom)
            assertEquals("$glyph is horizontally clipped", node.size.width.toFloat(), bounds.width, 1f)
            assertEquals("$glyph is vertically clipped", node.size.height.toFloat(), bounds.height, 1f)
        }
    }

    @Test
    fun languageDialogCanScrollToLastChoiceInLandscape() {
        val originalOrientation = compose.activity.requestedOrientation
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(5_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            var selected: AppLanguage? = null
            compose.setContent {
                TuneItAllTheme(darkTheme = false) {
                    SettingsScreen(
                        state = TunerUiState(
                            mode = TunerMode.AUTO,
                            tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard")),
                            selectedString = 0, headstockLayout = HeadstockLayout.SPLIT_3_3,
                            referencePitch = ReferencePitch(440.0), notation = NoteNotation.SHARPS,
                            favoriteIds = emptySet(), customTunings = emptyList(), reading = null,
                            microphoneGranted = false, microphonePermanentlyDenied = false,
                            listening = false, referenceTonePlaying = false, error = null,
                        ),
                        onAppLanguageChanged = { selected = it },
                        onThemeModeChanged = {}, onReferencePitchChanged = {}, onNotationChanged = {},
                        onLayoutChanged = {}, onAudioSettingsChanged = {}, onOpenAbout = {},
                        onSupport = {}, onBack = {},
                    )
                }
            }
            compose.onNodeWithText(context.getString(R.string.language_system)).performScrollTo().performClick()
            compose.onNodeWithText("Español").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(AppLanguage.SPANISH, selected) }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }

    @Test
    fun colorPickerThumbsContrastWithAnInvertedCustomSurface() {
        val appearance = AppearanceSettings(
            lightTheme = ThemePalette.CUSTOM,
            layout = ThemeLayout.CLASSIC,
            customLight = AppearanceSettings().customLight.copy(
                background = 0xFFFFFF, surface = 0x000000, accent = 0x000000, text = 0xFFFFFF,
            ),
        )
        compose.setContent {
            TuneItAllTheme(darkTheme = false, appearance = appearance) {
                AppearanceColorDialog("Color", initial = 0x00FF00, onDismiss = {}, onSave = {})
            }
        }

        listOf(R.string.appearance_hue to 180f, R.string.appearance_saturation to 0.5f,
            R.string.appearance_brightness to 0.5f).forEach { (resource, midpoint) ->
            val slider = compose.onNodeWithContentDescription(context.getString(resource))
                .performScrollTo().assertIsDisplayed()
            slider.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(midpoint)) }
            val pixels = slider.captureToImage().toPixelMap()
            // The 44 dp thumb extends above the 16 dp track. Sample only the thumb.
            val thumbY = pixels.height / 2 - with(compose.density) { 16.dp.roundToPx() }
            assertTrue("Thumb sample is outside the slider", thumbY in 0 until pixels.height)
            val contrast = colorContrast(pixels[pixels.width / 2, thumbY].rgb(), 0x000000)
            assertTrue("${context.getString(resource)} thumb contrast: $contrast", contrast >= 3.0)
        }
    }
}
