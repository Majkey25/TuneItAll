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
    fun `default preserves accepted detector gates`() {
        val sensitivity = DetectionSensitivity.DEFAULT

        assertEquals(50, sensitivity.value)
        assertEquals(0.003, sensitivity.minimumRms, 0.0)
        assertEquals(0.80, sensitivity.minimumConfidence, 0.0)
        assertEquals(0.15, sensitivity.yinThreshold(0.15), 0.0)
    }

    @Test
    fun `higher sensitivity accepts quieter lower confidence signals`() {
        val low = DetectionSensitivity(0)
        val normal = DetectionSensitivity.DEFAULT
        val high = DetectionSensitivity(100)

        assertTrue(low.minimumRms > normal.minimumRms)
        assertTrue(normal.minimumRms > high.minimumRms)
        assertTrue(low.minimumConfidence > normal.minimumConfidence)
        assertTrue(normal.minimumConfidence > high.minimumConfidence)
        assertTrue(high.minimumRms > 0.0)
        assertTrue(high.minimumConfidence in 0.0..1.0)
        assertTrue(high.yinThreshold(0.15) > normal.yinThreshold(0.15))
    }
}
