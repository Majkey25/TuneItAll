package com.tuneitall.tuner.autoscroll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoScrollCoreTest {
    @Test
    fun `speed clamps and steps inside the supported range`() {
        assertEquals(1, AutoScrollSpeed.clamp(-1))
        assertEquals(30, AutoScrollSpeed.clamp(31))
        assertEquals(1, AutoScrollSpeed.stepDown(1))
        assertEquals(16, AutoScrollSpeed.stepUp(15))
        assertEquals(30, AutoScrollSpeed.stepUp(30))
    }

    @Test
    fun `faster levels increase distance and reduce delay`() {
        val slow = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, 1)
        val normal = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, 15)
        val fast = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, 30)

        assertTrue(slow.startYFraction - slow.endYFraction < normal.startYFraction - normal.endYFraction)
        assertTrue(normal.startYFraction - normal.endYFraction < fast.startYFraction - fast.endYFraction)
        assertTrue(slow.intervalMs > normal.intervalMs)
        assertTrue(normal.intervalMs > fast.intervalMs)
        assertTrue(slow.gestureDurationMs > normal.gestureDurationMs)
        assertTrue(normal.gestureDurationMs > fast.gestureDurationMs)
    }

    @Test
    fun `gesture profiles remain inside safe screen and timing bounds`() {
        listOf(1, 15, 30).forEach { level ->
            val profile = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, level)

            assertTrue(profile.intervalMs in 4L..50L)
            assertTrue(profile.gestureDurationMs in 300L..2_000L)
            assertTrue(profile.endYFraction in 0.28f..<profile.startYFraction)
            assertEquals(0.72f, profile.startYFraction)
        }
    }

    @Test
    fun `gesture batches fit the platform stroke and duration limits`() {
        val profile = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, 30)
        val starts = gestureStartTimes(profile, platformMaxStrokeCount = 10)

        assertEquals(listOf(0L, 917L), starts)
        assertTrue(starts.size <= 8)
        assertTrue(starts.last() + profile.gestureDurationMs <= 2_000L)
        assertEquals(listOf(0L), gestureStartTimes(profile, platformMaxStrokeCount = 1))
    }
}
