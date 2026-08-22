package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveNoiseFloorTest {
    @Test
    fun `quiet voiced frame remains above the initial adaptive threshold`() {
        val floor = AdaptiveNoiseFloor()

        assertTrue(floor.accepts(rms = 0.0002, absoluteFloor = 0.00005, noiseRejection = 30))
    }

    @Test
    fun `voiced frames do not train the noise floor`() {
        val floor = AdaptiveNoiseFloor()

        floor.observe(rms = 0.01, voiced = true)

        assertEquals(0.00005, floor.value, 0.0)
    }

    @Test
    fun `noise floor rises slowly and falls quickly`() {
        val floor = AdaptiveNoiseFloor()
        repeat(100) { floor.observe(rms = 0.01, voiced = false) }
        val raised = floor.value
        repeat(10) { floor.observe(rms = 0.001, voiced = false) }

        assertTrue(raised > 0.00005)
        assertTrue(floor.value < raised)
        assertFalse(floor.accepts(rms = 0.004, absoluteFloor = 0.00005, noiseRejection = 30))
    }

    @Test
    fun `reset restores the initial noise floor`() {
        val floor = AdaptiveNoiseFloor()
        repeat(100) { floor.observe(rms = 0.01, voiced = false) }

        floor.reset()

        assertEquals(0.00005, floor.value, 0.0)
    }
}
