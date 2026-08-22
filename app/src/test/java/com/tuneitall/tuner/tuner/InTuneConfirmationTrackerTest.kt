package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InTuneConfirmationTrackerTest {
    private val a4 = MidiNote(69)

    @Test
    fun `one hundred millisecond confirmation confirms at the boundary`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 100))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_099, confirmationMillis = 100))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_100, confirmationMillis = 100))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_000, confirmationMillis = 100))
    }

    @Test
    fun `one thousand millisecond confirmation confirms at the boundary`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 1_000))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_999, confirmationMillis = 1_000))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_000, confirmationMillis = 1_000))
    }

    @Test
    fun `rearmer requires five hundred milliseconds outside the green zone`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(a4, inTune = false, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_099, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_349, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_350, confirmationMillis = 250))
    }

    @Test
    fun `target change starts a fresh confirmation`() {
        val tracker = confirmedTracker()
        val b4 = MidiNote(71)

        assertFalse(tracker.update(b4, inTune = true, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertTrue(tracker.update(b4, inTune = true, nowMillis = 1_850, confirmationMillis = 250))
    }

    @Test
    fun `missing reading uses the same rearm delay`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_600, confirmationMillis = 250))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
    }

    @Test
    fun `retained in-tune display cannot confirm without a raw reading`() {
        val tracker = InTuneConfirmationTracker()
        val retainer = TunerReadingRetainer()
        val reading = TunerReading(a4, a4, 440.0, 0.0, inTune = true)

        assertEquals(reading, retainer.update(reading, nowMillis = 1_000, holdMillis = 1_000))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 100))
        assertEquals(reading, retainer.update(null, nowMillis = 1_100, holdMillis = 1_000))

        assertFalse(tracker.update(null, inTune = false, nowMillis = 1_100, confirmationMillis = 100))
        assertFalse(tracker.isConfirmed)
    }

    @Test
    fun `clock cannot move backwards`() {
        val tracker = InTuneConfirmationTracker()
        tracker.update(a4, inTune = true, nowMillis = 100, confirmationMillis = 100)

        assertFailsWith<IllegalArgumentException> {
            tracker.update(a4, inTune = true, nowMillis = 99, confirmationMillis = 100)
        }
    }

    private fun confirmedTracker(): InTuneConfirmationTracker = InTuneConfirmationTracker().also {
        it.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 250)
        assertTrue(it.update(a4, inTune = true, nowMillis = 1_250, confirmationMillis = 250))
    }
}
