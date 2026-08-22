package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunerReadingRetainerTest {
    private val reading = TunerReading(MidiNote(69), MidiNote(69), 440.0, 0.0, inTune = true)

    @Test
    fun `zero hold clears the display immediately`() {
        val retainer = TunerReadingRetainer()

        assertEquals(reading, retainer.update(reading, nowMillis = 1_000, holdMillis = 0))
        assertNull(retainer.update(null, nowMillis = 1_000, holdMillis = 0))
    }

    @Test
    fun `one thousand millisecond hold clears after the boundary`() {
        val retainer = TunerReadingRetainer()

        assertEquals(reading, retainer.update(reading, nowMillis = 1_000, holdMillis = 1_000))
        assertEquals(reading, retainer.update(null, nowMillis = 2_000, holdMillis = 1_000))
        assertNull(retainer.update(null, nowMillis = 2_001, holdMillis = 1_000))
    }

    @Test
    fun `reset removes retained reading`() {
        val retainer = TunerReadingRetainer()
        retainer.update(reading, nowMillis = 1_000, holdMillis = 1_000)

        retainer.reset()

        assertNull(retainer.update(null, nowMillis = 1_001, holdMillis = 1_000))
    }
}
