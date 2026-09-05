package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChordEvaluationTest {
    @Test
    fun `exact timeline scores one on every metric`() {
        val timeline = listOf(
            event(0, 333, 0, ChordQuality.MAJOR),
            event(333, 666, 7, ChordQuality.MAJOR),
            event(666, 1_000, 9, ChordQuality.MINOR),
        )

        val result = evaluateChords(timeline, timeline, 1_000)

        assertEquals(1.0, result.rootWcsr, 0.0)
        assertEquals(1.0, result.majorMinorWcsr, 0.0)
        assertEquals(1.0, result.qualityWcsr, 0.0)
        assertEquals(1.0, result.segmentationScore, 0.0)
        assertEquals(0.0, result.medianBoundaryErrorMillis, 0.0)
        assertEquals(1.0, result.coverage, 0.0)
    }

    @Test
    fun `boundary and quality errors remain separate`() {
        val reference = listOf(
            event(0, 500, 0, ChordQuality.MAJOR),
            event(500, 1_000, 9, ChordQuality.MINOR),
        )
        val estimate = listOf(
            event(0, 450, 0, ChordQuality.MAJOR),
            event(450, 1_000, 9, ChordQuality.MAJOR),
        )

        val result = evaluateChords(reference, estimate, 1_000)

        assertEquals(0.95, result.rootWcsr, 1e-9)
        assertEquals(0.45, result.majorMinorWcsr, 1e-9)
        assertEquals(0.45, result.qualityWcsr, 1e-9)
        assertEquals(0.95, result.segmentationScore, 1e-9)
        assertEquals(50.0, result.medianBoundaryErrorMillis, 0.0)
        assertEquals(1.0, result.coverage, 0.0)
    }

    @Test
    fun `unlabelled estimate time lowers coverage without inventing matches`() {
        val reference = listOf(event(0, 1_000, 0, ChordQuality.MAJOR))
        val estimate = listOf(event(200, 700, 0, ChordQuality.MAJOR))

        val result = evaluateChords(reference, estimate, 1_000)

        assertEquals(0.5, result.rootWcsr, 0.0)
        assertEquals(0.5, result.coverage, 0.0)
    }

    @Test
    fun `evaluation rejects invalid timelines`() {
        val overlap = listOf(
            event(0, 600, 0, ChordQuality.MAJOR),
            event(500, 1_000, 7, ChordQuality.MAJOR),
        )

        assertFailsWith<IllegalArgumentException> { evaluateChords(overlap, emptyList(), 1_000) }
        assertFailsWith<IllegalArgumentException> { evaluateChords(emptyList(), emptyList(), 0) }
    }

    @Test
    fun `quality score is independent of inversion bass`() {
        val reference = listOf(ChordEvent(0, 1_000, Chord(0, ChordQuality.MAJOR, 4), 1.0))
        val estimate = listOf(ChordEvent(0, 1_000, Chord(0, ChordQuality.MAJOR, 7), 1.0))

        assertEquals(1.0, evaluateChords(reference, estimate, 1_000).qualityWcsr, 0.0)
    }

    @Test
    fun `major minor score excludes unsupported reference qualities`() {
        val reference = listOf(
            event(0, 500, 0, ChordQuality.MAJOR),
            event(500, 1_000, 5, ChordQuality.SUSPENDED_SECOND),
        )
        val estimate = listOf(event(0, 500, 0, ChordQuality.MAJOR))

        assertEquals(1.0, evaluateChords(reference, estimate, 1_000).majorMinorWcsr, 0.0)
    }

    private fun event(start: Long, end: Long, root: Int, quality: ChordQuality) =
        ChordEvent(start, end, Chord(root, quality), confidence = 1.0)
}
