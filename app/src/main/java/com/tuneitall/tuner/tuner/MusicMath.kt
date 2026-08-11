package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object MusicMath {
    fun frequency(note: MidiNote, referencePitch: ReferencePitch): Double =
        referencePitch.hertz * 2.0.pow((note.value - A4_MIDI) / SEMITONES_PER_OCTAVE)

    fun nearestMidi(hertz: Double, referencePitch: ReferencePitch): MidiNote {
        requirePositiveFinite(hertz, "Frequency")
        val note = A4_MIDI + SEMITONES_PER_OCTAVE * (log2(hertz) - log2(referencePitch.hertz))
        return MidiNote(note.roundToInt().coerceIn(MidiNote.MIN_VALUE, MidiNote.MAX_VALUE))
    }

    fun cents(hertz: Double, targetHertz: Double): Double {
        requirePositiveFinite(hertz, "Frequency")
        requirePositiveFinite(targetHertz, "Target frequency")
        return CENTS_PER_OCTAVE * (log2(hertz) - log2(targetHertz))
    }

    private fun requirePositiveFinite(value: Double, name: String) {
        require(value.isFinite() && value > 0.0) { "$name must be positive and finite" }
    }

    private const val A4_MIDI = 69
    private const val SEMITONES_PER_OCTAVE = 12.0
    private const val CENTS_PER_OCTAVE = 1200.0
}
