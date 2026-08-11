package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfirmationChimeTest {
    @Test
    fun `chime buffer has useful level and silent boundaries`() {
        val samples = createConfirmationChime(sampleRate = 48_000, durationMillis = 420)

        assertEquals(20_160, samples.size)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.maxOf { abs(it.toInt()) } > 1_000)
    }

    @Test
    fun `chime rejects invalid format`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            createConfirmationChime(sampleRate = 0, durationMillis = 420)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            createConfirmationChime(sampleRate = 48_000, durationMillis = 0)
        }
    }
}
