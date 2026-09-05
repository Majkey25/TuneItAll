package com.tuneitall.tuner.music

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AcousticArrangementTest {
    private val catalog by lazy {
        ChordShapeCatalog.parse(
            guitarJson = File("src/main/res/raw/chords_db_guitar.json").readText(),
            ukuleleJson = File("src/main/res/raw/chords_db_ukulele.json").readText(),
        )
    }

    @Test
    fun `capo optimizer keeps one easy capo for the complete song`() {
        val chords = listOf(
            Chord(6, ChordQuality.MINOR),
            Chord(11, ChordQuality.MAJOR),
            Chord(2, ChordQuality.MAJOR),
        )

        val result = arrangeForStandardE(chords, catalog, ArrangementMode.EXACT)

        assertEquals(2, result.capo)
        assertEquals(
            listOf(
                Chord(4, ChordQuality.MINOR),
                Chord(9, ChordQuality.MAJOR),
                Chord(0, ChordQuality.MAJOR),
            ),
            result.instructions.map(AcousticChordInstruction::shapeChord),
        )
    }

    @Test
    fun `simplified mode preserves root and major minor function`() {
        assertEquals(
            Chord(9, ChordQuality.MINOR),
            simplifyForAcoustic(Chord(9, ChordQuality.MINOR_SEVENTH, bassPitchClass = 0)),
        )
        assertEquals(
            Chord(0, ChordQuality.MAJOR),
            simplifyForAcoustic(Chord(0, ChordQuality.ADD_NINTH)),
        )
    }
}
