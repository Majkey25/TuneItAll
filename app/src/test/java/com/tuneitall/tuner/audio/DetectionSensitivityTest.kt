package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DetectionSensitivityTest {
    @Test
    fun `value must stay between zero and one hundred`() {
        assertFailsWith<IllegalArgumentException> { DetectionSensitivity(-1) }
        assertFailsWith<IllegalArgumentException> { DetectionSensitivity(101) }
    }

    @Test
    fun `default sensitivity is tuned for quiet instruments`() {
        val sensitivity = DetectionSensitivity.DEFAULT

        assertEquals(100, sensitivity.value)
        assertTrue(sensitivity.minimumRms <= 0.0002)
        assertTrue(sensitivity.minimumConfidence >= 0.70)
    }

    @Test
    fun `higher sensitivity accepts quieter lower confidence signals`() {
        val low = DetectionSensitivity(0)
        val normal = DetectionSensitivity(50)
        val high = DetectionSensitivity(100)

        assertTrue(low.minimumRms > normal.minimumRms)
        assertTrue(normal.minimumRms > high.minimumRms)
        assertTrue(low.minimumConfidence > normal.minimumConfidence)
        assertTrue(normal.minimumConfidence > high.minimumConfidence)
        assertTrue(high.minimumRms > 0.0)
        assertTrue(high.minimumConfidence in 0.0..1.0)
    }
}
