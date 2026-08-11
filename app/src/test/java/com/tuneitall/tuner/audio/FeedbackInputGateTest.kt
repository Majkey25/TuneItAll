package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackInputGateTest {
    @Test
    fun `confirmation feedback is ignored until the suppression boundary`() {
        val gate = FeedbackInputGate()

        gate.suppress(nowMillis = 1_000L, durationMillis = 500L)

        assertFalse(gate.accepts(1_000L))
        assertFalse(gate.accepts(1_499L))
        assertTrue(gate.accepts(1_500L))
    }

    @Test
    fun `new feedback extends an active suppression window`() {
        val gate = FeedbackInputGate()
        gate.suppress(nowMillis = 1_000L, durationMillis = 500L)

        gate.suppress(nowMillis = 1_300L, durationMillis = 500L)

        assertFalse(gate.accepts(1_799L))
        assertTrue(gate.accepts(1_800L))
    }
}
