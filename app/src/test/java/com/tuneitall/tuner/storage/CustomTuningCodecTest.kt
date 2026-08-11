package com.tuneitall.tuner.storage

import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.Instrument
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.TuningPreset
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CustomTuningCodecTest {
    @Test
    fun `custom tuning round trips through one JSON array`() {
        val preset = preset("custom-low", "My Low Tuning", 40)

        val encoded = CustomTuningCodec.encode(listOf(preset))
        val root = JSONArray(encoded)
        val item = root.getJSONObject(0)

        assertIs<JSONArray>(item.get("notes"))
        assertEquals("INLINE_6", item.getString("layout"))
        assertEquals(listOf(preset), CustomTuningCodec.decode(encoded))
    }

    @Test
    fun `corrupt and invalid MIDI items do not discard valid siblings`() {
        val validFirst = objectJson("custom-one", "One", 40)
        val invalidMidi = objectJson("custom-bad-midi", "Bad MIDI", 40)
            .put("notes", JSONArray(listOf(-1, 45, 50, 55, 59, 64)))
        val corrupt = JSONObject().put("id", "missing-fields")
        val validLast = objectJson("custom-two", "Two", 41)
        val encoded = JSONArray(listOf(validFirst, invalidMidi, corrupt, validLast)).toString()

        assertEquals(
            listOf("custom-one", "custom-two"),
            CustomTuningCodec.decode(encoded).map(TuningPreset::id),
        )
        assertEquals(emptyList(), CustomTuningCodec.decode("not-json"))
    }

    @Test
    fun `duplicate IDs keep only the first decoded item and cannot be encoded`() {
        val encoded = JSONArray(
            listOf(
                objectJson("custom-same", "First", 40),
                objectJson("custom-same", "Second", 41),
            ),
        ).toString()

        assertEquals(listOf("First"), CustomTuningCodec.decode(encoded).map(TuningPreset::name))
        assertFailsWith<IllegalArgumentException> {
            CustomTuningCodec.encode(listOf(preset("custom-same", "First", 40), preset("custom-same", "Second", 41)))
        }
    }

    @Test
    fun `custom tuning count is bounded to one hundred`() {
        val presets = (0..100).map { index -> preset("custom-$index", "Custom $index", 40) }
        val encoded = JSONArray((0..100).map { index -> objectJson("custom-$index", "Custom $index", 40) }).toString()

        assertFailsWith<IllegalArgumentException> { CustomTuningCodec.encode(presets) }
        assertEquals(100, CustomTuningCodec.decode(encoded).size)
    }

    private fun preset(id: String, name: String, firstMidi: Int): TuningPreset = TuningPreset(
        id = id,
        name = name,
        instrument = Instrument.GUITAR,
        notesLowToHigh = listOf(firstMidi, 45, 50, 55, 59, 64).map(::MidiNote),
        layouts = setOf(HeadstockLayout.INLINE_6),
    )

    private fun objectJson(id: String, name: String, firstMidi: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("instrument", "GUITAR")
        .put("notes", JSONArray(listOf(firstMidi, 45, 50, 55, 59, 64)))
        .put("layout", "INLINE_6")
}
