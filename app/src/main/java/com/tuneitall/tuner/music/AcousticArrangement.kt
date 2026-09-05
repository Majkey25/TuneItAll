package com.tuneitall.tuner.music

enum class ArrangementMode {
    EXACT,
    SIMPLIFIED,
}

data class AcousticChordInstruction(
    val soundingChord: Chord,
    val shapeChord: Chord,
    val voicing: ChordVoicing?,
)

data class AcousticArrangement(
    val capo: Int,
    val instructions: List<AcousticChordInstruction>,
) {
    init {
        require(capo in MIN_CAPO..MAX_CAPO)
    }
}

fun arrangeForStandardE(
    chords: List<Chord>,
    catalog: ChordShapeCatalog,
    mode: ArrangementMode,
): AcousticArrangement {
    val candidates = (MIN_CAPO..MAX_CAPO).map { capo ->
        val instructions = chords.map { sounding ->
            val shape = when (mode) {
                ArrangementMode.EXACT -> sounding
                ArrangementMode.SIMPLIFIED -> simplifyForAcoustic(sounding)
            }.transpose(-capo)
            AcousticChordInstruction(sounding, shape, catalog.shape(STANDARD_E_TUNING_ID, shape))
        }
        CapoCandidate(capo, instructions)
    }
    val best = candidates.minWith(
        compareBy<CapoCandidate> { it.missingVoicings }
            .thenBy { it.barres }
            .thenBy { it.baseFrets }
            .thenBy { it.frettedNotes }
            .thenBy { it.movement }
            .thenBy { it.capo },
    )
    return AcousticArrangement(best.capo, best.instructions)
}

fun simplifyForAcoustic(chord: Chord): Chord = when (chord.quality) {
    ChordQuality.MAJOR_SIXTH,
    ChordQuality.DOMINANT_SEVENTH,
    ChordQuality.MAJOR_SEVENTH,
    ChordQuality.ADD_NINTH,
    -> Chord(chord.rootPitchClass, ChordQuality.MAJOR)

    ChordQuality.MINOR_SIXTH,
    ChordQuality.MINOR_SEVENTH,
    ChordQuality.MINOR_ADD_NINTH,
    -> Chord(chord.rootPitchClass, ChordQuality.MINOR)

    else -> chord.copy(bassPitchClass = null)
}

private data class CapoCandidate(
    val capo: Int,
    val instructions: List<AcousticChordInstruction>,
) {
    private val voicings = instructions.mapNotNull(AcousticChordInstruction::voicing)
    val missingVoicings = instructions.size - voicings.size
    val barres = voicings.sumOf { it.barres.size }
    val baseFrets = voicings.sumOf(ChordVoicing::baseFret)
    val frettedNotes = voicings.sumOf { voicing -> voicing.frets.count { it > 0 } }
    val movement = voicings.zipWithNext().sumOf { (first, second) ->
        kotlin.math.abs(first.baseFret - second.baseFret)
    }
}

private const val STANDARD_E_TUNING_ID = "guitar-6-standard"
private const val MIN_CAPO = 0
private const val MAX_CAPO = 8
