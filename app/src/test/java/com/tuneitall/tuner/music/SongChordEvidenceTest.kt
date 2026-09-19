package com.tuneitall.tuner.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongChordEvidenceTest {
    @Test
    fun `inversion duration uses the same window boundaries as the chord`() {
        for (inversionFirst in listOf(false, true)) {
            val frames = List(4) { index ->
                val bass = if ((index < 2) == inversionFirst) 4 else 0
                HarmonicFrame(
                    startMillis = index * 100L,
                    chroma = FloatArray(12) { if (it in setOf(0, 4, 7)) 0.6f else 0f },
                    bassChroma = FloatArray(12) { if (it == bass) 1f else 0f },
                    noteSalience = FloatArray(88),
                    tonalStrength = 0.8f,
                    onsetStrength = 0f,
                )
            }
            val events = analyzeChords(frames, SongAnalysisMode.CHORDS, 450L)
            assertEquals(listOf(Chord(0, ChordQuality.MAJOR, if (inversionFirst) 4 else null)),
                events.map { it.chord }, events.toString())
        }
    }

    @Test
    fun `a single pitch class is not a chord even with octave doubling`() {
        for (midi in listOf(48, 60, 69, 76)) for (gain in listOf(0.01f, 0.3f)) {
            for (octaves in listOf(false, true)) {
                val frequencies = if (octaves) doubleArrayOf(midiToHertz(midi), midiToHertz(midi + 12))
                    else doubleArrayOf(midiToHertz(midi))
                val samples = sineChord(22_050, 1, *frequencies).map { it * gain }.toFloatArray()
                val analyzer = StreamingChordAnalyzer(22_050)
                analyzer.accept(samples)
                assertTrue(analyzer.finish().isEmpty(), "Invented chord for MIDI $midi, gain=$gain, octaves=$octaves")
            }
        }
    }

    @Test
    fun `a real chord does not leak across neighboring single note passages`() {
        val analyzer = StreamingChordAnalyzer(22_050)
        analyzer.accept(concatenate(
            sineChord(22_050, 1, 440.0),
            sineChord(22_050, 1, 293.6648, 349.2282, 440.0),
            sineChord(22_050, 1, 440.0),
        ))
        val events = analyzer.finish()
        assertEquals(listOf(Chord(2, ChordQuality.MINOR)), events.map { it.chord })
        assertTrue(events.single().startMillis in 800L..1200L, events.toString())
        assertTrue(events.single().endMillis in 1800L..2200L, events.toString())
    }
}
