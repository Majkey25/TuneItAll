package com.tuneitall.tuner.audio

import kotlin.math.pow
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

        assertNull(result, "A retained pitch is not a fresh measurement")
        assertEquals(440.0, requireNotNull(tracker.update(frame(440.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `short confirmation chime cannot replace a stable guitar string`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(82.41, rms = 0.0002), settings) }

        val result = tracker.update(frame(880.0, rms = 0.1), settings)

        assertNull(result, "Chime must not renew the guitar measurement")
        assertEquals(82.41, requireNotNull(tracker.update(frame(82.41, rms = 0.0002), settings)).hertz, 0.01)
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
    fun `universal tracking acquires a weak but periodic tone`() {
        val tracker = PitchTracker()
        val weakTone = PitchFrame(
            candidates = listOf(PitchCandidate(82.41, probability = 0.13, periodicity = 0.78)),
            rms = 0.0001,
            peak = 0.0002,
            unvoicedProbability = 0.87,
        )

        val result = List(3) { tracker.update(weakTone, settings) }.last()

        assertEquals(82.41, requireNotNull(result).hertz, 0.01)
    }

    @Test
    fun `universal tracking acquires persistent low clarity string buzz`() {
        val tracker = PitchTracker()
        val stringBuzz = PitchFrame(
            candidates = listOf(PitchCandidate(82.41, probability = 0.05, periodicity = 0.40)),
            rms = 0.0001,
            peak = 0.0003,
            unvoicedProbability = 0.95,
        )

        val result = List(3) { tracker.update(stringBuzz, settings) }.last()

        assertEquals(82.41, requireNotNull(result).hertz, 0.01)
    }

    @Test
    fun `zero probability alternatives cannot acquire a new pitch`() {
        val tracker = PitchTracker()
        val alternative = PitchFrame(listOf(PitchCandidate(110.0, 0.0, 0.99)), 0.0001, 0.0002, 1.0)

        repeat(12) { assertNull(tracker.update(alternative, settings)) }
        repeat(5) { tracker.update(frame(220.0, rms = 0.0002), settings) }
        assertNull(tracker.update(alternative, settings), "An octave alternative cannot introduce a new note")
    }

    @Test
    fun `current continuation evidence follows tuning movement without returning the old pitch`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
        val alternative = PitchFrame(listOf(PitchCandidate(112.0, 0.0, 0.70)), 0.0001, 0.0002, 1.0)

        val results = List(3) { tracker.update(alternative, settings) }

        assertEquals(112.0, requireNotNull(results.last()).hertz, 0.0)
        assertTrue(results.all { it == null || it.hertz == 112.0 })
    }

    @Test
    fun `continuation evidence cannot resurrect an obsolete pitch through the unvoiced path`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
        repeat(30) { tracker.update(frame(220.0, rms = 0.0002), settings) }
        val obsolete = PitchFrame(
            listOf(PitchCandidate(110.0, 0.0, 0.99), PitchCandidate(440.0, 0.01, 0.01)),
            0.0001,
            0.0002,
            0.99,
        )

        repeat(5) {
            assertTrue(tracker.update(obsolete, settings)?.hertz != 110.0)
        }
    }

    @Test
    fun `rejected continuation cannot outlast competing observations and become fresh again`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
        repeat(7) { tracker.update(frame(220.0, rms = 0.0002), settings) }
        tracker.update(
            PitchFrame(listOf(PitchCandidate(220.0, 0.97, 0.97)), 0.0002, 0.0002, 0.03),
            settings,
        )
        val mixture = PitchFrame(
            listOf(
                PitchCandidate(110.0, 0.0, 0.999),
                PitchCandidate(220.0, 0.000001, 0.000001),
                PitchCandidate(880.0, 0.02, 0.999),
            ),
            0.0002,
            0.0002,
            0.979999,
        )

        val results = List(12) { tracker.update(mixture, settings)?.hertz }

        assertTrue(results.none { it == 110.0 }, "Rejected historical tone must stay rejected: $results")
    }

    @Test
    fun `continuation survives seven empty frames but cannot acquire after eight`() {
        for (missingFrames in listOf(7, 8)) {
            val tracker = PitchTracker()
            repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
            repeat(missingFrames) { assertNull(tracker.update(emptyFrame(1.0), settings)) }
            val alternative = PitchFrame(listOf(PitchCandidate(110.0, 0.0, 0.70)), 0.0001, 0.0002, 1.0)

            val results = List(3) { tracker.update(alternative, settings)?.hertz }

            if (missingFrames == 7) {
                assertEquals(110.0, results.last())
            } else {
                assertTrue(results.all { it == null }, "A cleared state cannot seed continuation: $results")
            }
        }
    }

    @Test
    fun `zero probability continuation is limited to a semitone in either direction`() {
        for (cents in listOf(-100.001, -99.999, 99.999, 100.001)) {
            val tracker = PitchTracker()
            repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
            val hertz = 110.0 * 2.0.pow(cents / 1_200.0)
            val alternative = PitchFrame(listOf(PitchCandidate(hertz, 0.0, 0.70)), 0.0001, 0.0002, 1.0)

            val results = List(3) { tracker.update(alternative, settings)?.hertz }

            if (cents in -100.0..100.0) {
                assertEquals(hertz, results.last(), "Current evidence within one semitone must follow retuning")
            } else {
                assertTrue(results.all { it == null }, "Out-of-range continuation ($cents cents): $results")
            }
        }
    }

    @Test
    fun `a newly acquired octave becomes the continuation reference`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(110.0, rms = 0.0002), settings) }
        val acquired = List(5) { tracker.update(frame(220.0, rms = 0.0002), settings) }
        assertEquals(220.0, requireNotNull(acquired.last()).hertz, 0.0)

        val continuation = PitchFrame(listOf(PitchCandidate(220.0, 0.0, 0.70)), 0.0001, 0.0002, 1.0)
        repeat(3) { assertEquals(220.0, requireNotNull(tracker.update(continuation, settings)).hertz, 0.0) }
    }

    @Test
    fun `reset removes prior pitch state`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        tracker.reset()

        assertEquals(880.0, requireNotNull(tracker.update(frame(880.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `unvoiced frames return null and clear old state after eight empty frames`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        assertNull(tracker.update(emptyFrame(unvoicedProbability = 1.0), settings))
        repeat(7) { assertNull(tracker.update(emptyFrame(unvoicedProbability = 0.01), settings)) }

        assertEquals(880.0, requireNotNull(tracker.update(frame(880.0, rms = 0.2), settings)).hertz, 1.0)
    }

    @Test
    fun `short dropouts retain a decaying guitar pitch but a long dropout releases it`() {
        val retained = PitchTracker()
        repeat(5) { retained.update(frame(82.41, rms = 0.0002), settings) }
        repeat(7) { assertNull(retained.update(emptyFrame(unvoicedProbability = 1.0), settings)) }

        assertNull(retained.update(frame(880.0, rms = 0.0001), settings))
        assertEquals(82.41, requireNotNull(retained.update(frame(82.41, rms = 0.0001), settings)).hertz, 0.01)

        val released = PitchTracker()
        repeat(5) { released.update(frame(82.41, rms = 0.0002), settings) }
        repeat(8) { assertNull(released.update(emptyFrame(unvoicedProbability = 1.0), settings)) }

        assertEquals(880.0, requireNotNull(released.update(frame(880.0, rms = 0.0001), settings)).hertz, 0.01)
    }

    @Test
    fun `eight RMS-gated frames clear old pitch state`() {
        val tracker = PitchTracker()
        repeat(5) { tracker.update(frame(440.0, rms = 0.2), settings) }

        repeat(8) { assertNull(tracker.update(gatedFrame(rms = 0.001), settings)) }

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
