package com.tuneitall.tuner.music

import android.content.res.Resources
import com.tuneitall.tuner.R
import org.json.JSONArray
import org.json.JSONObject

class ChordShapeCatalog private constructor(
    private val shapes: Map<String, Map<Chord, ChordVoicing>>,
) {
    fun shape(tuningId: String, chord: Chord): ChordVoicing? =
        if (chord.bassPitchClass == null) shapes[tuningId]?.get(chord) else null

    fun supports(tuningId: String): Boolean = tuningId in shapes

    companion object {
        fun parse(guitarJson: String, ukuleleJson: String): ChordShapeCatalog = ChordShapeCatalog(
            mapOf(
                GUITAR_STANDARD_ID to parseInstrument(guitarJson, GUITAR_KEYS, GUITAR_OPEN_PITCH_CLASSES),
                UKULELE_STANDARD_ID to parseInstrument(ukuleleJson, UKULELE_KEYS, UKULELE_OPEN_PITCH_CLASSES),
            ),
        )

        fun fromResources(resources: Resources): ChordShapeCatalog = parse(
            guitarJson = resources.readRawText(R.raw.chords_db_guitar),
            ukuleleJson = resources.readRawText(R.raw.chords_db_ukulele),
        )
    }
}

private fun parseInstrument(json: String, sourceKeys: List<String>, openPitchClasses: List<Int>): Map<Chord, ChordVoicing> {
    require(sourceKeys.size == 12)
    val sourceChords = JSONObject(json).getJSONObject("chords")
    return buildMap {
        sourceKeys.forEachIndexed { rootPitchClass, sourceKey ->
            val entries = sourceChords.getJSONArray(sourceKey)
            instructionalChordQualities.forEach { quality ->
                val source = entries.findChord(SUFFIXES.getValue(quality))
                val chord = Chord(rootPitchClass, quality)
                val voicing = source.firstVoicing(openPitchClasses.size)
                if (voicing.matches(chord, openPitchClasses)) put(chord, voicing)
            }
        }
    }
}

private fun ChordVoicing.matches(chord: Chord, openPitchClasses: List<Int>): Boolean {
    val sounded = frets.mapIndexedNotNull { index, fret ->
        fret.takeIf { it >= 0 }?.let { (openPitchClasses[index] + it) % 12 }
    }.toSet()
    val essential = chord.quality.essentialIntervals.mapTo(mutableSetOf()) { (chord.rootPitchClass + it) % 12 }
    val fingeringIsValid = frets.zip(fingers).all { (fret, finger) ->
        (fret <= 0 && finger == 0) || (fret > 0 && (finger > 0 || fret in barres))
    }
    return sounded.all(chord.pitchClasses::contains) && sounded.containsAll(essential) && fingeringIsValid
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
    ChordQuality.SUSPENDED_FOURTH to "sus4",
    ChordQuality.DIMINISHED to "dim",
    ChordQuality.AUGMENTED to "aug",
    ChordQuality.MAJOR_SIXTH to "6",
    ChordQuality.MINOR_SIXTH to "m6",
    ChordQuality.DOMINANT_SEVENTH to "7",
    ChordQuality.MAJOR_SEVENTH to "maj7",
    ChordQuality.MINOR_SEVENTH to "m7",
    ChordQuality.HALF_DIMINISHED_SEVENTH to "m7b5",
    ChordQuality.ADD_NINTH to "add9",
    ChordQuality.MINOR_ADD_NINTH to "madd9",
)
private val GUITAR_KEYS = listOf("C", "Csharp", "D", "Eb", "E", "F", "Fsharp", "G", "Ab", "A", "Bb", "B")
private val UKULELE_KEYS = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val GUITAR_OPEN_PITCH_CLASSES = listOf(4, 9, 2, 7, 11, 4)
private val UKULELE_OPEN_PITCH_CLASSES = listOf(7, 0, 4, 9)
private const val GUITAR_STANDARD_ID = "guitar-6-standard"
private const val UKULELE_STANDARD_ID = "ukulele-standard"
