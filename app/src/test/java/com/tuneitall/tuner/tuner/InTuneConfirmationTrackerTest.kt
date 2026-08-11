package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InTuneConfirmationTrackerTest {
    private val a4 = MidiNote(69)

    @Test
    fun `confirms once after two hundred fifty milliseconds in tune`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_249))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_250))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_000))
    }

    @Test
    fun `rearmer requires five hundred milliseconds outside the green zone`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(a4, inTune = false, nowMillis = 1_600))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_099))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_100))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_100))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_349))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_350))
    }

    @Test
    fun `target change starts a fresh confirmation`() {
        val tracker = confirmedTracker()
        val b4 = MidiNote(71)

        assertFalse(tracker.update(b4, inTune = true, nowMillis = 1_600))
        assertFalse(tracker.isConfirmed)
        assertTrue(tracker.update(b4, inTune = true, nowMillis = 1_850))
    }

    @Test
    fun `missing reading uses the same rearm delay`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_600))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 2_100))
        assertFalse(tracker.isConfirmed)
    }

    @Test
    fun `clock cannot move backwards`() {
        val tracker = InTuneConfirmationTracker()
        tracker.update(a4, inTune = true, nowMillis = 100)

        assertFailsWith<IllegalArgumentException> {
            tracker.update(a4, inTune = true, nowMillis = 99)
        }
    }

    private fun confirmedTracker(): InTuneConfirmationTracker = InTuneConfirmationTracker().also {
        it.update(a4, inTune = true, nowMillis = 1_000)
        assertTrue(it.update(a4, inTune = true, nowMillis = 1_250))
    }
}
