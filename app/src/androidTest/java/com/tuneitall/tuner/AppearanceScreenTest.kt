package com.tuneitall.tuner

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.ui.AppearanceScreen
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemeFont
import com.tuneitall.tuner.ui.theme.ThemeLayout
import com.tuneitall.tuner.ui.theme.ThemeMode
import com.tuneitall.tuner.ui.theme.ThemePalette
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.rgb
import com.tuneitall.tuner.ui.theme.resolveDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppearanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetQaPreferences() {
        check(context.packageName.endsWith(".qa"))
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun preferenceUpgradePreservesLegacyModeAndRejectsWrongStorageType() {
        val prefs = UserPreferences(context)
        prefs.themeMode = ThemeMode.DARK
        val settings = AppearanceSettings(lightTheme = ThemePalette.OCEAN, darkTheme = ThemePalette.IRIS, textScale = 115)
        prefs.appearance = settings
        assertEquals(settings, UserPreferences(context).appearance)
        assertEquals(ThemeMode.DARK, UserPreferences(context).themeMode)
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit().putInt("appearance_v1", 7).commit()
        assertEquals(AppearanceSettings(), UserPreferences(context).appearance)
        assertEquals(ThemeMode.DARK, UserPreferences(context).themeMode)
    }

    @Test fun themesApplyImmediatelyPersistAndReturnToSettingsThenTuner() {
        val vm = TunerViewModel(ApplicationProvider.getApplicationContext<Application>())
        var background = -1
        compose.setContent {
            val state by vm.uiState.collectAsState()
            TuneItAllTheme(resolveDarkTheme(state.themeMode, false), state.appearance) {
                val currentBackground = MaterialTheme.colorScheme.background.rgb()
                SideEffect { background = currentBackground }
                TuneItAllApp(state, vm, {}, {}, AppLanguage.SYSTEM, {})
            }
        }
        compose.onNodeWithTag("tuner_settings").performClick()
        compose.onNodeWithTag("settings_section_general").performClick()
        compose.onNodeWithTag("settings_appearance").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_theme_OCEAN_light").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ThemePalette.OCEAN, UserPreferences(context).appearance.lightTheme)
            assertEquals(0xF5F9FC, background)
        }
        compose.onNodeWithTag("appearance_mode_DARK").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0x101010, background) }
        compose.onNodeWithTag("appearance_theme_IRIS_dark").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0x1A1522, background) }
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings_appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithTag("tuning_picker").assertIsDisplayed()
        assertEquals(ThemePalette.OCEAN, UserPreferences(context).appearance.lightTheme)
        assertEquals(ThemePalette.IRIS, UserPreferences(context).appearance.darkTheme)
    }

    @Test fun customHexValidationAndHueOnWhiteAreUsable() {
        var settings by mutableStateOf(AppearanceSettings())
        compose.setContent {
            TuneItAllTheme(false, settings) {
                Surface { AppearanceScreen(settings, ThemeMode.LIGHT, { settings = it }, {}, {}) }
            }
        }
        compose.onNodeWithTag("appearance_edit_custom").performScrollTo().performClick()
        compose.onNodeWithTag("custom_color_TEXT").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement("zz1122")
        compose.onNodeWithTag("appearance_color_apply").assertIsNotEnabled()
        compose.onNodeWithTag("appearance_color_hex").performTextReplacement("#FFFFFF")
        compose.onNodeWithContentDescription(context.getString(R.string.appearance_hue))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(240f) }
        compose.onNodeWithContentDescription(context.getString(R.string.appearance_saturation))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("appearance_color_apply").performClick()
        compose.runOnIdle {
            assertEquals(0x0000FF, settings.customLight.text)
            assertEquals(ThemePalette.CUSTOM, settings.lightTheme)
            assertEquals(ThemePalette.INTONIVA, settings.darkTheme)
        }
    }

    @Test fun largerTextMaterialControlsAndResetKeepBackReachable() {
        var settings by mutableStateOf(AppearanceSettings())
        var mode by mutableStateOf(ThemeMode.DARK)
        var fontScale = 0f
        var back = false
        compose.setContent {
            TuneItAllTheme(resolveDarkTheme(mode, false), settings) {
                val currentFontScale = LocalDensity.current.fontScale
                SideEffect { fontScale = currentFontScale }
                Surface { AppearanceScreen(settings, mode, { settings = it }, { mode = it }, { back = true }) }
            }
        }
        compose.onNodeWithTag("appearance_layout_MATERIAL").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_font_SERIF").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_text_scale").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(130f) }
        compose.runOnIdle {
            assertEquals(ThemeLayout.MATERIAL, settings.layout)
            assertEquals(ThemeFont.SERIF, settings.font)
            assertEquals(130, settings.textScale)
            assertTrue(fontScale >= 1.3f)
        }
        compose.onNodeWithTag("appearance_black").performScrollTo().performClick()
        compose.onNodeWithTag("appearance_reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppearanceSettings(), settings); assertEquals(ThemeMode.SYSTEM, mode) }
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed().performClick()
        assertTrue(back)
    }

    @Test fun invertedCustomSurfaceKeepsTransparentTunerLabelsVisible() {
        val base = AppearanceSettings().customLight.copy(background = 0xFFFFFF, surface = 0, text = 0xFFFFFF, muted = 0xFFFFFF)
        val settings = AppearanceSettings(lightTheme = ThemePalette.CUSTOM, customLight = base)
        UserPreferences(context).appearance = settings
        val vm = TunerViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            val state by vm.uiState.collectAsState()
            TuneItAllTheme(false, settings) { TuneItAllApp(state, vm, {}, {}, AppLanguage.SYSTEM, {}) }
        }
        listOf("signed_cents", "headstock_note_6").forEach { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
            node.assertIsDisplayed()
            val bitmap = node.captureToImage().asAndroidBitmap()
            var darkPixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) < 80 && android.graphics.Color.green(pixel) < 80 && android.graphics.Color.blue(pixel) < 80) darkPixels++
            }
            assertTrue("Transparent label $tag disappeared", darkPixels > 10)
        }
    }
}
