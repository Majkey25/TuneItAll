package com.tuneitall.tuner.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun `accent text is readable while selected containers keep the brand green`() {
        listOf(TuneItAllLightColors, TuneItAllDarkColors).forEach { colors ->
            listOf(colors.primary, colors.tertiary).forEach { foreground ->
                assertTrue(contrast(foreground, colors.background) >= 4.5f, "Accent text must contrast with its background")
                assertTrue(contrast(foreground, colors.surface) >= 4.5f, "Accent text must contrast with its surface")
            }
            assertTrue(contrast(colors.onPrimary, colors.primary) >= 4.5f)
            assertTrue(contrast(colors.onTertiary, colors.tertiary) >= 4.5f)
            assertEquals(Color(0xFF63D17A), colors.primaryContainer)
            assertEquals(Color(0xFF63D17A), colors.tertiaryContainer)
            assertTrue(contrast(colors.onPrimaryContainer, colors.primaryContainer) >= 4.5f)
            assertTrue(contrast(colors.onTertiaryContainer, colors.tertiaryContainer) >= 4.5f)
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) / (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
