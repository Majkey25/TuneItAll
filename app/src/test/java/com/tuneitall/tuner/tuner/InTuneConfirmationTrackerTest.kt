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
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `one thousand millisecond confirmation confirms at the boundary`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 1_000))
        for (nowMillis in 1_100L..1_900L step 100L) {
            assertFalse(tracker.update(a4, inTune = true, nowMillis = nowMillis, confirmationMillis = 1_000))
        }
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_999, confirmationMillis = 1_000))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_000, confirmationMillis = 1_000))
    }

    @Test
    fun `a callback-free gap requires a fresh continuous confirmation interval`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 1_000))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 5_000, confirmationMillis = 1_000))
        assertFalse(tracker.isConfirmed)
        for (nowMillis in 5_100L..5_900L step 100L) {
            assertFalse(tracker.update(a4, inTune = true, nowMillis = nowMillis, confirmationMillis = 1_000))
        }
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 5_999, confirmationMillis = 1_000))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 6_000, confirmationMillis = 1_000))
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `rearmer requires five hundred milliseconds outside the green zone`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(a4, inTune = false, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_099, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_200, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_300, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_349, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_350, confirmationMillis = 250))
    }

    @Test
    fun `target change starts a fresh confirmation`() {
        val tracker = confirmedTracker()
        val b4 = MidiNote(71)

        assertFalse(tracker.update(b4, inTune = true, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(b4, inTune = true, nowMillis = 1_700, confirmationMillis = 250))
        assertFalse(tracker.update(b4, inTune = true, nowMillis = 1_800, confirmationMillis = 250))
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
    fun `return after missing readings rearms only after five hundred milliseconds`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_099, confirmationMillis = 250))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_600, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_700, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_800, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_849, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_850, confirmationMillis = 250))
    }

    @Test
    fun `return after an out-of-tune interval rearms only after five hundred milliseconds`() {
        val tracker = confirmedTracker()

        assertFalse(tracker.update(a4, inTune = false, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_099, confirmationMillis = 250))
        assertTrue(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = false, nowMillis = 2_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_600, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_700, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_800, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 2_849, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 2_850, confirmationMillis = 250))
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
    fun `brief missing frame preserves accumulated in-tune time`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 250))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_100, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_250, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_399, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_400, confirmationMillis = 250))
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `brief dropout preserves voiced time without counting missing time`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 0, confirmationMillis = 150))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 10, confirmationMillis = 150))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 150, confirmationMillis = 150))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 289, confirmationMillis = 150))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 290, confirmationMillis = 150))
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `long missing interval restarts pending confirmation`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 250))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_100, confirmationMillis = 250))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_301, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_302, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_402, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_502, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_552, confirmationMillis = 250))
    }

    @Test
    fun `a fresh reading after a long missing interval restarts pending confirmation`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 250))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_500, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_600, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_700, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_749, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_750, confirmationMillis = 250))
    }

    @Test
    fun `a fresh reading at the dropout boundary preserves pending confirmation`() {
        val tracker = InTuneConfirmationTracker()

        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_000, confirmationMillis = 250))
        assertFalse(tracker.update(target = null, inTune = false, nowMillis = 1_100, confirmationMillis = 250))
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_300, confirmationMillis = 250))
        assertFalse(tracker.isConfirmed)
        assertFalse(tracker.update(a4, inTune = true, nowMillis = 1_449, confirmationMillis = 250))
        assertTrue(tracker.update(a4, inTune = true, nowMillis = 1_450, confirmationMillis = 250))
        assertTrue(tracker.isConfirmed)
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
        assertFalse(it.update(a4, inTune = true, nowMillis = 1_100, confirmationMillis = 250))
        assertFalse(it.update(a4, inTune = true, nowMillis = 1_200, confirmationMillis = 250))
        assertTrue(it.update(a4, inTune = true, nowMillis = 1_250, confirmationMillis = 250))
    }
}
