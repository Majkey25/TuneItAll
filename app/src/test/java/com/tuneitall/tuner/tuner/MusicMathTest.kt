package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MusicMathTest {
    @Test
    fun `A4 follows the selected reference pitch`() {
        assertEquals(440.0, MusicMath.frequency(MidiNote(69), ReferencePitch(440.0)), 1e-9)
        assertEquals(444.0, MusicMath.frequency(MidiNote(69), ReferencePitch(444.0)), 1e-9)
    }

    @Test
    fun `frequency and cents use equal temperament`() {
        assertEquals(261.625565, MusicMath.frequency(MidiNote(60), ReferencePitch(440.0)), 1e-6)
        assertEquals(0.0, MusicMath.cents(440.0, 440.0), 1e-9)
        assertEquals(100.0, MusicMath.cents(466.1637615, 440.0), 1e-6)
    }

    @Test
    fun `nearest MIDI note is clamped to the supported range`() {
        assertEquals(MidiNote(69), MusicMath.nearestMidi(440.0, ReferencePitch(440.0)))
        assertEquals(MidiNote(0), MusicMath.nearestMidi(0.001, ReferencePitch(440.0)))
        assertEquals(MidiNote(127), MusicMath.nearestMidi(100_000.0, ReferencePitch(440.0)))
    }

    @Test
    fun `invalid frequencies are rejected`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { hertz ->
            assertFailsWith<IllegalArgumentException> {
                MusicMath.nearestMidi(hertz, ReferencePitch(440.0))
            }
            assertFailsWith<IllegalArgumentException> { MusicMath.cents(hertz, 440.0) }
            assertFailsWith<IllegalArgumentException> { MusicMath.cents(440.0, hertz) }
        }
    }

    @Test
    fun `reference pitch accepts safe inclusive range`() {
        assertEquals(410.0, ReferencePitch(410.0).hertz)
        assertEquals(480.0, ReferencePitch(480.0).hertz)
        listOf(409.9, 480.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { hertz ->
            assertFailsWith<IllegalArgumentException> { ReferencePitch(hertz) }
        }
    }

    @Test
    fun `MIDI note accepts only protocol range`() {
        assertEquals(0, MidiNote(0).value)
        assertEquals(127, MidiNote(127).value)
        assertFailsWith<IllegalArgumentException> { MidiNote(-1) }
        assertFailsWith<IllegalArgumentException> { MidiNote(128) }
    }

    @Test
    fun `preset enforces instrument and headstock string counts`() {
        TuningPreset(
            id = "ukulele-standard",
            name = "Standard C",
            instrument = Instrument.UKULELE,
            notesLowToHigh = listOf(MidiNote(67), MidiNote(60), MidiNote(64), MidiNote(69)),
            layouts = setOf(HeadstockLayout.SPLIT_2_2),
        )

        assertFailsWith<IllegalArgumentException> {
            TuningPreset(
                id = "bad-guitar",
                name = "Bad",
                instrument = Instrument.GUITAR,
                notesLowToHigh = List(5) { MidiNote(40 + it) },
                layouts = setOf(HeadstockLayout.INLINE_6),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TuningPreset(
                id = "bad-layout",
                name = "Bad",
                instrument = Instrument.BASS,
                notesLowToHigh = List(4) { MidiNote(28 + it) },
                layouts = setOf(HeadstockLayout.INLINE_6),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TuningPreset(
                id = "chromatic-preset",
                name = "Bad",
                instrument = Instrument.CHROMATIC,
                notesLowToHigh = listOf(MidiNote(69)),
                layouts = emptySet(),
            )
        }
    }
}
