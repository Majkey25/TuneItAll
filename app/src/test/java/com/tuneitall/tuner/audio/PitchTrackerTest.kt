package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchTrackerTest {
    private val settings = TunerProfile.BALANCED.settings

    @Test
    fun `one octave candidate cannot replace a stable fundamental`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        val result = tracker.update(frame(880.0, rms = 0.2), settings)

        assertEquals(440.0, requireNotNull(result).hertz, 1.0)
    }

    @Test
    fun `three consistent frames switch to a new note`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        val result = List(3) { tracker.update(frame(493.88, rms = 0.2), settings) }.last()

        assertEquals(493.88, requireNotNull(result).hertz, 1.0)
    }

    @Test
    fun `energy onset changes note within three frames`() {
        val onsetFrames = acquisitionFrames(settings, previousRms = 0.01, currentRms = 0.02)
        val equalRmsFrames = acquisitionFrames(settings, previousRms = 0.01, currentRms = 0.01)

        assertTrue(onsetFrames < equalRmsFrames)
        assertTrue(onsetFrames <= 3)
    }

    @Test
    fun `reset removes prior pitch state`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        tracker.reset()

        assertEquals(880.0, requireNotNull(tracker.update(frame(880.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `unvoiced frames return null and clear old state after three empty frames`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        assertNull(tracker.update(emptyFrame(unvoicedProbability = 1.0), settings))
        repeat(2) { assertNull(tracker.update(emptyFrame(unvoicedProbability = 0.01), settings)) }

        assertEquals(880.0, requireNotNull(tracker.update(frame(880.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `three RMS-gated frames clear old pitch state`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        repeat(3) { assertNull(tracker.update(gatedFrame(rms = 0.001), settings)) }

        assertEquals(880.0, requireNotNull(tracker.update(frame(880.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `response modes acquire new notes at configured speeds`() {
        val fast = acquisitionFrames(
            TunerProfile.BALANCED.settings.copy(response = ResponseMode.FAST),
            currentHertz = 523.25,
        )
        val balanced = acquisitionFrames(
            TunerProfile.BALANCED.settings.copy(response = ResponseMode.BALANCED),
            currentHertz = 523.25,
        )
        val stable = acquisitionFrames(
            TunerProfile.BALANCED.settings.copy(response = ResponseMode.STABLE),
            currentHertz = 523.25,
        )

        assertTrue(fast <= balanced)
        assertTrue(balanced <= stable)
        assertTrue(fast <= 2)
        assertTrue(balanced <= 3)
    }

    private fun acquisitionFrames(
        settings: TunerAudioSettings,
        previousRms: Double = 0.2,
        currentRms: Double = 0.2,
        currentHertz: Double = 493.88,
    ): Int {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = previousRms), settings) }
        return (1..6).first { tracker.update(frame(currentHertz, rms = currentRms), settings)?.hertz == currentHertz }
    }

    private fun frame(hertz: Double, rms: Double): PitchFrame = PitchFrame(
        candidates = listOf(PitchCandidate(hertz = hertz, probability = 0.95, periodicity = 0.95)),
        rms = rms,
        peak = rms,
        unvoicedProbability = 0.05,
    )

    private fun emptyFrame(unvoicedProbability: Double): PitchFrame = PitchFrame(
        candidates = emptyList(),
        rms = 0.0,
        peak = 0.0,
        unvoicedProbability = unvoicedProbability,
    )

    private fun gatedFrame(rms: Double): PitchFrame = PitchFrame(
        candidates = emptyList(),
        rms = rms,
        peak = rms,
        unvoicedProbability = 1.0,
    )
}
