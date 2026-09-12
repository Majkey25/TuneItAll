package com.tuneitall.tuner.audio

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchTrackerGapRecoveryTest {
    private val settings = TunerAudioSettings()

    @Test
    fun `fresh continuation recovers after three missing fundamental candidates`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(PitchCandidate(110.0, 0.95, 0.95)), settings) }
        repeat(3) {
            assertNull(tracker.update(frame(PitchCandidate(220.0, 0.05, 0.70)), settings))
        }

        val recovered = tracker.update(frame(
            PitchCandidate(110.2, 0.0, 0.70),
            PitchCandidate(220.0, 0.05, 0.72),
        ), settings)

        assertEquals(110.2, requireNotNull(recovered).hertz, 0.0)
    }

    @Test
    fun `continuation expires after eight nonempty frames without a fresh reading`() {
        for (gap in listOf(7, 8, 16)) {
            val tracker = PitchTracker()
            repeat(5) { tracker.update(frame(PitchCandidate(110.0, 0.95, 0.95)), settings) }
            repeat(gap) { index ->
                val distractor = 220.0 * 2.0.pow((index % 8) / 3.0)
                assertNull(tracker.update(frame(PitchCandidate(distractor, 0.01, 0.40)), settings))
            }
            val recovered = tracker.update(frame(PitchCandidate(110.2, 0.0, 0.70)), settings)
            if (gap == 7) assertEquals(110.2, requireNotNull(recovered).hertz, 0.0)
            else assertNull(recovered, "Expired continuation must not resurrect after $gap frames")
        }
    }

    @Test
    fun `an unvoiced winner clears continuation before the gap limit`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(PitchCandidate(110.0, 0.95, 0.95)), settings) }
        repeat(3) { assertNull(tracker.update(frame(PitchCandidate(220.0, 0.01, 0.01)), settings)) }
        assertNull(tracker.update(frame(PitchCandidate(110.2, 0.0, 0.70)), settings))
    }

    private fun frame(vararg candidates: PitchCandidate) = PitchFrame(
        candidates.toList(), 0.0001, 0.0002, (1.0 - candidates.sumOf { it.probability }).coerceIn(0.0, 1.0),
    )
}
