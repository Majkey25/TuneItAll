package com.tuneitall.tuner.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TuningCatalogTest {
    @Test
    fun `catalog identifiers and note sequences are unique`() {
        val presets = TuningCatalog.presets

        assertTrue(presets.isNotEmpty())
        assertEquals(presets.size, presets.map(TuningPreset::id).distinct().size)
        assertEquals(
            presets.size,
            presets
                .map { preset ->
                    Triple(
                        preset.instrument,
                        preset.notesLowToHigh.size,
                        preset.notesLowToHigh.map(MidiNote::value),
                    )
                }
                .distinct()
                .size,
        )
        presets.forEach { preset ->
            assertTrue(preset.id.isNotBlank())
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.layouts.all { it.stringCount == preset.notesLowToHigh.size })
        }
    }

    @Test
    fun `catalog includes standard instrument and extended guitar presets`() {
        val expected = mapOf(
            "guitar-6-standard" to "E2 A2 D3 G3 B3 E4",
            "guitar-7-standard" to "B1 E2 A2 D3 G3 B3 E4",
            "guitar-8-standard" to "F#1 B1 E2 A2 D3 G3 B3 E4",
            "guitar-9-standard" to "C#1 F#1 B1 E2 A2 D3 G3 B3 E4",
            "bass-4-standard" to "E1 A1 D2 G2",
            "ukulele-standard" to "G4 C4 E4 A4",
        )

        expected.forEach { (id, spec) ->
            assertEquals(notes(spec), assertNotNull(TuningCatalog.byId(id)).notesLowToHigh)
        }
        assertFalse(HeadstockLayout.entries.any { it.name == "INLINE_6" })
        TuningCatalog.presets.filter { it.notesLowToHigh.size == 6 }.forEach { preset ->
            assertEquals(setOf(HeadstockLayout.SPLIT_3_3), preset.layouts)
        }
    }

    @Test
    fun `six string drop presets cover every semitone from D through F`() {
        val expected = mapOf(
            "guitar-6-drop-d" to "D2 A2 D3 G3 B3 E4",
            "guitar-6-drop-c-sharp" to "C#2 G#2 C#3 F#3 A#3 D#4",
            "guitar-6-drop-c" to "C2 G2 C3 F3 A3 D4",
            "guitar-6-drop-b" to "B1 F#2 B2 E3 G#3 C#4",
            "guitar-6-drop-b-flat" to "Bb1 F2 Bb2 Eb3 G3 C4",
            "guitar-6-drop-a" to "A1 E2 A2 D3 F#3 B3",
            "guitar-6-drop-a-flat" to "Ab1 Eb2 Ab2 Db3 F3 Bb3",
            "guitar-6-drop-g" to "G1 D2 G2 C3 E3 A3",
            "guitar-6-drop-g-flat" to "Gb1 Db2 Gb2 B2 Eb3 Ab3",
            "guitar-6-drop-f" to "F1 C2 F2 Bb2 D3 G3",
        )

        expected.forEach { (id, spec) ->
            assertEquals(notes(spec), assertNotNull(TuningCatalog.byId(id)).notesLowToHigh)
        }
    }

    @Test
    fun `catalog includes verified open and modal tunings`() {
        val expected = mapOf(
            "guitar-6-dadgad" to "D2 A2 D3 G3 A3 D4",
            "guitar-6-open-d" to "D2 A2 D3 F#3 A3 D4",
            "guitar-6-open-e" to "E2 B2 E3 G#3 B3 E4",
            "guitar-6-open-g" to "D2 G2 D3 G3 B3 D4",
            "guitar-6-open-a" to "E2 A2 E3 A3 C#4 E4",
        )

        expected.forEach { (id, spec) ->
            assertEquals(notes(spec), assertNotNull(TuningCatalog.byId(id)).notesLowToHigh)
        }
    }

    @Test
    fun `scientific note parser handles accidentals and rejects malformed input`() {
        assertEquals(listOf(30, 30, 58, 58), notes("F#1 Gb1 A#3 Bb3").map(MidiNote::value))
        assertFailsWith<IllegalArgumentException> { notes("") }
        assertFailsWith<IllegalArgumentException> { notes("H2") }
        assertFailsWith<IllegalArgumentException> { notes("C10") }
    }
}
