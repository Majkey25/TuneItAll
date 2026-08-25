package com.tuneitall.tuner

import com.tuneitall.tuner.ui.PrimaryDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScreenTest {
    @Test
    fun `back follows the screen hierarchy`() {
        assertNull(parentScreen(AppScreen.Tuner))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.Metronome))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.Chords))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.Library))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.Trainer))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.AutoScroll))
        assertEquals(AppScreen.Library, parentScreen(AppScreen.CustomTuning))
        assertEquals(AppScreen.Tuner, parentScreen(AppScreen.Settings))
        assertEquals(AppScreen.Settings, parentScreen(AppScreen.About))
    }

    @Test
    fun `primary navigation replaces duplicate library with auto scroll`() {
        assertEquals(
            listOf("TUNER", "METRONOME", "CHORDS", "AUTO_SCROLL", "TRAINER"),
            PrimaryDestination.entries.map(Enum<*>::name),
        )
    }
}
