package com.tuneitall.tuner

import com.tuneitall.tuner.ui.theme.AppearanceCodec
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemeFont
import com.tuneitall.tuner.ui.theme.ThemeLayout
import com.tuneitall.tuner.ui.theme.ThemePalette
import com.tuneitall.tuner.ui.theme.appearanceColorScheme
import com.tuneitall.tuner.ui.theme.colorContrast
import com.tuneitall.tuner.ui.theme.parseHexColor
import com.tuneitall.tuner.ui.theme.inTuneRgb
import com.tuneitall.tuner.ui.theme.usesLightSystemBarIcons
import com.tuneitall.tuner.ui.theme.rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun customSettingsRoundTripAndKeepLightAndDarkIndependent() {
        val settings = AppearanceSettings(lightTheme = ThemePalette.OCEAN, darkTheme = ThemePalette.CUSTOM,
            customDark = AppearanceSettings().customDark.copy(accent = 0xBB77FF), layout = ThemeLayout.CUSTOM,
            cornerRadius = 17, font = ThemeFont.SERIF, textScale = 123, highContrast = true, blackBackground = true)
        assertEquals(settings, AppearanceCodec.decode(AppearanceCodec.encode(settings)))
    }

    @Test
    fun malformedAndOversizedPreferencesFallBackWithoutBreakingLaunch() {
        val defaults = AppearanceSettings()
        assertEquals(defaults, AppearanceCodec.decode("not json"))
        assertEquals(defaults, AppearanceCodec.decode(" ".repeat(4097)))
        assertEquals(defaults, AppearanceCodec.decode("""{"light":"missing","corners":40,"textScale":99.2,"font":true,"black":"true","customLight":{"accent":-1}}"""))
    }

    @Test
    fun legacyPaletteAndModeDefaultsArePreserved() {
        val settings = AppearanceCodec.decode("{}")
        assertEquals(AppearanceSettings(), settings)
        assertEquals(0xFAF9F6, appearanceColorScheme(settings, false).background.rgb())
        assertEquals(0x101010, appearanceColorScheme(settings, true).background.rgb())
        assertEquals(0x63D17A, appearanceColorScheme(settings, false).primaryContainer.rgb())
    }

    @Test
    fun everyPresetAndExtremeCustomPaletteKeepsTextReadable() {
        for (palette in ThemePalette.entries) for (dark in listOf(false, true)) {
            val settings = AppearanceSettings(lightTheme = palette, darkTheme = palette)
            assertReadable(settings, dark)
            assertReadable(settings.copy(highContrast = true, blackBackground = true), dark)
        }
        for (background in listOf(0, 0xFFFFFF, 0x777777, 0x63D17A)) {
            val custom = AppearanceSettings().customLight.copy(background = background, surface = 0xFFFFFF - background,
                text = background, muted = background, accent = background, outline = background)
            val settings = AppearanceSettings(lightTheme = ThemePalette.CUSTOM, darkTheme = ThemePalette.CUSTOM,
                customLight = custom, customDark = custom)
            assertReadable(settings, false)
            assertReadable(settings.copy(highContrast = true), false)
            assertReadable(settings.copy(blackBackground = true), true)
        }
    }

    @Test
    fun colourInputRejectsAlphaPartialAndInvalidDigits() {
        assertEquals(0xABCDEF, parseHexColor("#aBcDeF"))
        assertEquals(0x166534, parseHexColor("166534"))
        listOf("fff", "FF166534", "GG6634", "-12345", "", "0x166534").forEach { assertNull(parseHexColor(it)) }
    }

    @Test
    fun inTuneFeedbackStaysGreenAndReadableOnMiddleGreyAndOtherBackgrounds() {
        for (grey in 0..255) {
            val background = grey * 0x010101
            val green = inTuneRgb(background)
            assertTrue(colorContrast(green, background) >= 3.0)
            assertTrue((green shr 8 and 255) > (green shr 16 and 255))
        }
        assertTrue(colorContrast(inTuneRgb(0x6A6A6A), 0x6A6A6A) >= 3.0)
    }

    @Test
    fun systemBarIconsFollowActualBackgroundInsteadOfMode() {
        assertTrue(usesLightSystemBarIcons(0))
        assertTrue(!usesLightSystemBarIcons(0xFFFFFF))
        val palette = AppearanceSettings().customLight.copy(background = 0)
        val scheme = appearanceColorScheme(AppearanceSettings(lightTheme = ThemePalette.CUSTOM, customLight = palette), false)
        assertTrue(usesLightSystemBarIcons(scheme.background.rgb()))
    }

    private fun assertReadable(settings: AppearanceSettings, dark: Boolean) {
        val scheme = appearanceColorScheme(settings, dark)
        listOf(scheme.onBackground to scheme.background, scheme.onSurface to scheme.surface,
            scheme.onSurfaceVariant to scheme.surface, scheme.primary to scheme.background,
            scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
            scheme.error to scheme.surface).forEach { (foreground, background) ->
            assertTrue("Contrast failed for ${settings.palette(dark)}, dark=$dark", colorContrast(foreground.rgb(), background.rgb()) >= 4.5)
        }
        assertTrue(colorContrast(scheme.outline.rgb(), scheme.background.rgb()) >= 3.0)
    }
}
