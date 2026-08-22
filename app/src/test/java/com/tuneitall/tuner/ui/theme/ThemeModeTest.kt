package com.tuneitall.tuner.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `system theme follows Android`() {
        assertTrue(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `explicit theme ignores Android`() {
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, systemDark = false))
    }
}
