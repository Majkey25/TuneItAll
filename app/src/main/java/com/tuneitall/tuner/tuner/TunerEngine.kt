package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.PitchEstimate
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningPreset
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

enum class TunerMode {
    AUTO,
    MANUAL,
    CHROMATIC,
}

data class TunerReading(
    val detected: MidiNote,
    val target: MidiNote,
    val hertz: Double,
    val cents: Double,
    val inTune: Boolean,
)

class TunerEngine {
    private val pitchFilter = StablePitchFilter()
    private var context: EngineContext? = null
    private var currentTarget: MidiNote? = null

    fun update(
        estimate: PitchEstimate,
        mode: TunerMode,
        tuning: TuningPreset,
        selectedString: Int,
        referencePitch: ReferencePitch,
        sensitivity: DetectionSensitivity = DetectionSensitivity.DEFAULT,
    ): TunerReading? {
        require(selectedString in tuning.notesLowToHigh.indices) { "Selected string is outside the tuning" }
        resetIfContextChanged(EngineContext(mode, tuning, selectedString, referencePitch, sensitivity))
        if (estimate.confidence < sensitivity.minimumConfidence || estimate.rms < sensitivity.minimumRms) return null

        val hertz = pitchFilter.add(estimate.hertz)
        val detected = MusicMath.nearestMidi(hertz, referencePitch)
        val target = when (mode) {
            TunerMode.AUTO -> applyHysteresis(closestTuningNote(hertz, tuning, referencePitch), hertz, referencePitch)
            TunerMode.MANUAL -> tuning.notesLowToHigh[selectedString].also { currentTarget = it }
            TunerMode.CHROMATIC -> applyHysteresis(detected, hertz, referencePitch)
        }
        val cents = MusicMath.cents(hertz, MusicMath.frequency(target, referencePitch))
        return TunerReading(
            detected = detected,
            target = target,
            hertz = hertz,
            cents = cents,
            inTune = abs(cents) <= IN_TUNE_CENTS,
        )
    }

    private fun closestTuningNote(
        hertz: Double,
        tuning: TuningPreset,
        referencePitch: ReferencePitch,
    ): MidiNote {
        var closest = tuning.notesLowToHigh.first()
        var closestDistance = distanceInCents(hertz, closest, referencePitch)
        for (index in 1 until tuning.notesLowToHigh.size) {
            val note = tuning.notesLowToHigh[index]
            val distance = distanceInCents(hertz, note, referencePitch)
            if (distance < closestDistance) {
                closest = note
                closestDistance = distance
            }
        }
        return closest
    }

    private fun applyHysteresis(
        candidate: MidiNote,
        hertz: Double,
        referencePitch: ReferencePitch,
    ): MidiNote {
        val previous = currentTarget
        if (previous == null || previous == candidate) {
            currentTarget = candidate
            return candidate
        }

        val candidateDistance = distanceInCents(hertz, candidate, referencePitch)
        val previousDistance = distanceInCents(hertz, previous, referencePitch)
        if (candidateDistance + TARGET_HYSTERESIS_CENTS < previousDistance) currentTarget = candidate
        return requireNotNull(currentTarget)
    }

    private fun distanceInCents(
        hertz: Double,
        note: MidiNote,
        referencePitch: ReferencePitch,
    ): Double = abs(MusicMath.cents(hertz, MusicMath.frequency(note, referencePitch)))

    private fun resetIfContextChanged(next: EngineContext) {
        if (context == next) return
        context = next
        pitchFilter.reset()
        currentTarget = null
    }

    private data class EngineContext(
        val mode: TunerMode,
        val tuning: TuningPreset,
        val selectedString: Int,
        val referencePitch: ReferencePitch,
        val sensitivity: DetectionSensitivity,
    )

    private class StablePitchFilter {
        private var smoothedLogHertz: Double? = null
        private var pendingLogHertz: Double? = null
        private var pendingFrames = 0

        fun add(value: Double): Double {
            val next = log2(value)
            val current = smoothedLogHertz ?: return value.also { smoothedLogHertz = next }
            if (centsBetween(next, current) > SWITCH_THRESHOLD_CENTS) {
                val pending = pendingLogHertz
                if (pending != null && centsBetween(next, pending) <= SWITCH_MATCH_CENTS) {
                    pendingFrames++
                } else {
                    pendingLogHertz = next
                    pendingFrames = 1
                }
                if (pendingFrames < SWITCH_FRAMES) return 2.0.pow(current)
                smoothedLogHertz = next
                clearPending()
                return value
            }

            clearPending()
            val smoothed = current + (next - current) * SMOOTHING_FACTOR
            smoothedLogHertz = smoothed
            return 2.0.pow(smoothed)
        }

        fun reset() {
            smoothedLogHertz = null
            clearPending()
        }

        private fun clearPending() {
            pendingLogHertz = null
            pendingFrames = 0
        }

        private fun centsBetween(first: Double, second: Double): Double = abs(first - second) * CENTS_PER_OCTAVE

        private companion object {
            const val SMOOTHING_FACTOR = 0.30
            const val SWITCH_THRESHOLD_CENTS = 80.0
            const val SWITCH_MATCH_CENTS = 50.0
            const val SWITCH_FRAMES = 3
            const val CENTS_PER_OCTAVE = 1_200.0
        }
    }

    private companion object {
        const val IN_TUNE_CENTS = 3.0
        const val TARGET_HYSTERESIS_CENTS = 8.0
    }
}
