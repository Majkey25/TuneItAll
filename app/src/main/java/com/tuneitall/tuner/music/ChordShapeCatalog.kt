package com.tuneitall.tuner.music

import android.content.res.Resources
import com.tuneitall.tuner.R
import org.json.JSONArray
import org.json.JSONObject

class ChordShapeCatalog private constructor(
    private val shapes: Map<String, Map<Chord, ChordVoicing>>,
) {
    fun shape(tuningId: String, chord: Chord): ChordVoicing? = shapes[tuningId]?.get(chord)

    fun supports(tuningId: String): Boolean = tuningId in shapes

    companion object {
        fun parse(guitarJson: String, ukuleleJson: String): ChordShapeCatalog = ChordShapeCatalog(
            mapOf(
                GUITAR_STANDARD_ID to parseInstrument(guitarJson, GUITAR_KEYS, 6),
                UKULELE_STANDARD_ID to parseInstrument(ukuleleJson, UKULELE_KEYS, 4),
            ),
        )

        fun fromResources(resources: Resources): ChordShapeCatalog = parse(
            guitarJson = resources.readRawText(R.raw.chords_db_guitar),
            ukuleleJson = resources.readRawText(R.raw.chords_db_ukulele),
        )
    }
}

private fun parseInstrument(json: String, sourceKeys: List<String>, stringCount: Int): Map<Chord, ChordVoicing> {
    require(sourceKeys.size == 12)
    val sourceChords = JSONObject(json).getJSONObject("chords")
    return buildMap {
        sourceKeys.forEachIndexed { rootPitchClass, sourceKey ->
            val entries = sourceChords.getJSONArray(sourceKey)
            instructionalChordQualities.forEach { quality ->
                val source = entries.findChord(SUFFIXES.getValue(quality))
                put(Chord(rootPitchClass, quality), source.firstVoicing(stringCount))
            }
        }
    }
}

private fun JSONArray.findChord(suffix: String): JSONObject {
    for (index in 0 until length()) {
        val chord = getJSONObject(index)
        if (chord.getString("suffix") == suffix) return chord
    }
    error("Chord suffix '$suffix' is missing from the bundled catalog")
}

private fun JSONObject.firstVoicing(stringCount: Int): ChordVoicing {
    val position = getJSONArray("positions").getJSONObject(0)
    val baseFret = position.optInt("baseFret", 1)
    val relativeFrets = position.getJSONArray("frets").toIntList(stringCount)
    return ChordVoicing(
        frets = relativeFrets.map { fret -> fret.toAbsoluteFret(baseFret) },
        fingers = position.getJSONArray("fingers").toIntList(stringCount),
        barres = position.getJSONArray("barres").toIntList().map { it.toAbsoluteFret(baseFret) },
        baseFret = baseFret,
    )
}

private fun JSONArray.toIntList(expectedSize: Int? = null): List<Int> =
    List(length(), ::getInt).also { values ->
        if (expectedSize != null) require(values.size == expectedSize)
    }

private fun Int.toAbsoluteFret(baseFret: Int): Int = if (this <= 0) this else baseFret + this - 1

private fun Resources.readRawText(resourceId: Int): String =
    openRawResource(resourceId).bufferedReader(Charsets.UTF_8).use { it.readText() }

private val SUFFIXES = mapOf(
    ChordQuality.MAJOR to "major",
    ChordQuality.MINOR to "minor",
    ChordQuality.SUSPENDED_SECOND to "sus2",
    ChordQuality.DOMINANT_SEVENTH to "7",
)
private val GUITAR_KEYS = listOf("C", "Csharp", "D", "Eb", "E", "F", "Fsharp", "G", "Ab", "A", "Bb", "B")
private val UKULELE_KEYS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private const val GUITAR_STANDARD_ID = "guitar-6-standard"
private const val UKULELE_STANDARD_ID = "ukulele-standard"
