package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.TuningCatalog
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordShapeCatalogTest {
    private val catalog: ChordShapeCatalog by lazy {
        ChordShapeCatalog.parse(
            guitarJson = File("src/main/res/raw/chords_db_guitar.json").readText(),
            ukuleleJson = File("src/main/res/raw/chords_db_ukulele.json").readText(),
        )
    }

    @Test
    fun `standard guitar uses canonical open and barre shapes`() {
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), shape(0, ChordQuality.MAJOR).frets)
        assertEquals(listOf(3, 2, 0, 0, 0, 3), shape(7, ChordQuality.MAJOR).frets)
        assertEquals(listOf(-1, -1, 0, 2, 3, 2), shape(2, ChordQuality.MAJOR).frets)
        assertEquals(listOf(-1, 0, 2, 2, 1, 0), shape(9, ChordQuality.MINOR).frets)
        assertEquals(listOf(1, 3, 3, 2, 1, 1), shape(5, ChordQuality.MAJOR).frets)
        assertEquals(listOf(-1, 2, 1, 2, 0, 2), shape(11, ChordQuality.DOMINANT_SEVENTH).frets)
    }

    @Test
    fun `bundled catalog exposes reviewed common quality shapes`() {
        listOf(
            Chord(0, ChordQuality.MAJOR_SEVENTH),
            Chord(9, ChordQuality.MINOR_SEVENTH),
            Chord(11, ChordQuality.HALF_DIMINISHED_SEVENTH),
            Chord(0, ChordQuality.SUSPENDED_FOURTH),
            Chord(0, ChordQuality.ADD_NINTH),
        ).forEach { chord ->
            requireNotNull(catalog.shape("guitar-6-standard", chord))
        }
    }

    @Test
    fun `relative source frets become absolute and keep fingering details`() {
        val abMinor = shape(8, ChordQuality.MINOR)

        assertEquals(listOf(4, 6, 6, 4, 4, 4), abMinor.frets)
        assertEquals(listOf(1, 3, 4, 1, 1, 1), abMinor.fingers)
        assertEquals(listOf(4), abMinor.barres)
        assertEquals(4, abMinor.baseFret)
    }

    @Test
    fun `standard ukulele uses upstream shapes and unsupported tunings stay empty`() {
        val cMajor = requireNotNull(catalog.shape("ukulele-standard", Chord(0, ChordQuality.MAJOR)))

        assertEquals(listOf(0, 0, 0, 3), cMajor.frets)
        assertNull(catalog.shape("guitar-6-drop-d", Chord(0, ChordQuality.MAJOR)))
        assertNull(catalog.shape("ukulele-low-g", Chord(0, ChordQuality.MAJOR)))
    }

    @Test
    fun `bundled catalog files keep their reviewed upstream hashes`() {
        assertEquals(GUITAR_SHA256, sha256(File("src/main/res/raw/chords_db_guitar.json")))
        assertEquals(UKULELE_SHA256, sha256(File("src/main/res/raw/chords_db_ukulele.json")))
    }

    @Test
    fun `every exposed shape contains exactly the requested chord tones`() {
        listOf("guitar-6-standard", "ukulele-standard").forEach { tuningId ->
            val tuning = requireNotNull(TuningCatalog.byId(tuningId))
            repeat(12) { root ->
                instructionalChordQualities.forEach { quality ->
                    val chord = Chord(root, quality)
                    val voicing = catalog.shape(tuningId, chord) ?: return@forEach
                    val soundedPitchClasses = voicing.frets.mapIndexedNotNull { index, fret ->
                        fret.takeIf { it >= 0 }?.let { (tuning.notesLowToHigh[index].value + it) % 12 }
                    }.toSet()
                    val requiredPitchClasses = essentialIntervals.getValue(quality)
                        .mapTo(mutableSetOf()) { (root + it) % 12 }

                    assertTrue(soundedPitchClasses.all(chord.pitchClasses::contains), "$tuningId $chord $voicing")
                    assertTrue(soundedPitchClasses.containsAll(requiredPitchClasses), "$tuningId $chord $voicing")
                    assertTrue(
                        voicing.frets.zip(voicing.fingers).all { (fret, finger) ->
                            (fret <= 0 && finger == 0) || (fret > 0 && (finger > 0 || fret in voicing.barres))
                        },
                        "$tuningId $chord $voicing",
                    )
                }
            }
        }
    }

    private fun shape(root: Int, quality: ChordQuality): ChordVoicing =
        requireNotNull(catalog.shape("guitar-6-standard", Chord(root, quality)))

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val essentialIntervals = mapOf(
            ChordQuality.MAJOR to setOf(0, 4),
            ChordQuality.MINOR to setOf(0, 3),
            ChordQuality.SUSPENDED_SECOND to setOf(0, 2, 7),
            ChordQuality.SUSPENDED_FOURTH to setOf(0, 5, 7),
            ChordQuality.DIMINISHED to setOf(0, 3, 6),
            ChordQuality.AUGMENTED to setOf(0, 4, 8),
            ChordQuality.MAJOR_SIXTH to setOf(0, 4, 9),
            ChordQuality.MINOR_SIXTH to setOf(0, 3, 9),
            ChordQuality.DOMINANT_SEVENTH to setOf(0, 4, 10),
            ChordQuality.MAJOR_SEVENTH to setOf(0, 4, 11),
            ChordQuality.MINOR_SEVENTH to setOf(0, 3, 10),
            ChordQuality.HALF_DIMINISHED_SEVENTH to setOf(0, 3, 6, 10),
            ChordQuality.ADD_NINTH to setOf(0, 2, 4),
            ChordQuality.MINOR_ADD_NINTH to setOf(0, 2, 3),
        )
        const val GUITAR_SHA256 = "cfe439962b2f444d2c341b1f0261403b4c3a3416e321147286fc608922699974"
        const val UKULELE_SHA256 = "233b7018ec35785a8bfa985bad90f4745cee04614c0fd1d5b819cff7406ec601"
    }
}
