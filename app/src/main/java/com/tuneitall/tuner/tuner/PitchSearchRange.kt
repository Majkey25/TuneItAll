package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningPreset
import kotlin.math.pow

internal data class PitchSearchRange(
    val minHertz: Double,
    val maxHertz: Double,
) {
    init {
        require(minHertz.isFinite() && minHertz > 0.0) { "Minimum frequency must be positive and finite" }
        require(maxHertz.isFinite() && maxHertz > minHertz) {
            "Maximum frequency must be finite and greater than minimum frequency"
        }
    }
}

internal fun pitchSearchRange(
    mode: TunerMode,
    tuning: TuningPreset,
    selectedString: Int,
    referencePitch: ReferencePitch,
): PitchSearchRange {
    require(selectedString in tuning.notesLowToHigh.indices) { "Selected string is outside the tuning" }
    if (mode == TunerMode.CHROMATIC) return PitchSearchRange(MIN_FREQUENCY, MAX_FREQUENCY)

    val notes = if (mode == TunerMode.MANUAL) {
        listOf(tuning.notesLowToHigh[selectedString])
    } else {
        tuning.notesLowToHigh
    }
    val margin = 2.0.pow(
        (if (mode == TunerMode.MANUAL) MANUAL_MARGIN_SEMITONES else AUTO_MARGIN_SEMITONES) /
            SEMITONES_PER_OCTAVE,
    )
    return PitchSearchRange(
        minHertz = (MusicMath.frequency(notes.minBy { it.value }, referencePitch) / margin).coerceAtLeast(MIN_FREQUENCY),
        maxHertz = (MusicMath.frequency(notes.maxBy { it.value }, referencePitch) * margin).coerceAtMost(MAX_FREQUENCY),
    )
}

private const val MIN_FREQUENCY = 27.0
private const val MAX_FREQUENCY = 4_300.0
private const val AUTO_MARGIN_SEMITONES = 3.0
private const val MANUAL_MARGIN_SEMITONES = 5.0
private const val SEMITONES_PER_OCTAVE = 12.0
