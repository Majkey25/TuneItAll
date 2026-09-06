package com.tuneitall.tuner.music

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SingleNoteChordTest {
    @Test
    fun `nearby chord does not turn single tone flanks into chords`() {
        val samples = concatenate(
            sineChord(SAMPLE_RATE, 1, 440.0),
            sineChord(SAMPLE_RATE, 1, 293.6648, 349.2282, 440.0),
            sineChord(SAMPLE_RATE, 1, 440.0),
        )
        val events = StreamingChordAnalyzer(SAMPLE_RATE).apply { accept(samples) }.finish()

        assertEquals(Chord(2, ChordQuality.MINOR), chordEventAt(events, 1_500)?.chord, events.toString())
        assertTrue(events.all { it.startMillis >= 800 && it.endMillis <= 2_200 }, events.toString())
    }

    @Test
    fun `single sinusoids do not create chords from virtual subharmonics`() {
        assertNoChords(0)
    }

    @Test
    fun `octave only tones do not create chords from virtual subharmonics`() {
        assertNoChords(0, 12, 24)
    }

    private fun assertNoChords(vararg semitoneOffsets: Int) {
        val failures = buildList {
            for (midi in 45..80) {
                for (gain in listOf(0.001, 0.02, 0.3)) {
                    val frequencies = semitoneOffsets.map { midiToHertz(midi + it) }.toDoubleArray()
                    val samples = sineChord(SAMPLE_RATE, 1, *frequencies)
                    samples.indices.forEach { samples[it] = (samples[it] * gain).toFloat() }
                    val events = StreamingChordAnalyzer(SAMPLE_RATE, SongAnalysisMode.CHORDS).apply {
                        accept(samples)
                    }.finish()
                    if (events.isNotEmpty()) add("MIDI $midi, gain $gain: $events")
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
