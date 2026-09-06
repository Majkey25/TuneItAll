package com.tuneitall.tuner.music

import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SongAnalysisCancellationTest {
    @Test
    fun `feature finalization responds to cancellation after decoding`() {
        var finalizing = false
        var checks = 0
        val extractor = StreamingHarmonicFeatureExtractor(8_000, isCancelled = { finalizing && ++checks > 2 })
        extractor.accept(FloatArray(8_000 * 4) { sin(2 * PI * 220 * it / 8_000).toFloat() })
        finalizing = true

        assertFailsWith<CancellationException> { extractor.finish() }
        assertEquals(3, checks)
    }

    @Test
    fun `chord note and tempo decoding cancel inside their analysis loops`() {
        val frames = List(30) { index ->
            HarmonicFrame(
                startMillis = index * 93L,
                chroma = FloatArray(12) { if (it in setOf(0, 4, 7)) 0.6f else 0f },
                bassChroma = FloatArray(12) { if (it == 0) 1f else 0f },
                noteSalience = FloatArray(88) { if (it == 39) 1f else 0f },
                tonalStrength = 0.7f,
                onsetStrength = 0f,
            )
        }
        var checks = 0
        assertFailsWith<CancellationException> {
            analyzeChords(frames, SongAnalysisMode.CHORDS, 2_790) { ++checks > 2 }
        }
        assertEquals(3, checks)
        checks = 0
        assertFailsWith<CancellationException> { analyzeNotes(frames, NoteRange.ANY, 2_790) { ++checks > 2 } }
        assertEquals(3, checks)

        var finalizing = false
        checks = 0
        val tempo = StreamingTempoAnalyzer(8_000, isCancelled = { finalizing && ++checks > 2 })
        tempo.accept(FloatArray(8_000 * 6) { if (it % 4_000 < 80) 0.8f else 0f })
        finalizing = true
        assertFailsWith<CancellationException> { tempo.finish() }
        assertEquals(3, checks)
    }
}
