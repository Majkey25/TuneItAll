package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.audio.PitchEstimate
import com.tuneitall.tuner.audio.TunerAudioSettings
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
    private val needleSmoother = NeedleSmoother()
    private var context: EngineContext? = null
    private var currentTarget: MidiNote? = null
    private var smoothedDetected: MidiNote? = null

    fun update(
        estimate: PitchEstimate,
        mode: TunerMode,
        tuning: TuningPreset,
        selectedString: Int,
        referencePitch: ReferencePitch,
        settings: TunerAudioSettings,
    ): TunerReading {
        require(selectedString in tuning.notesLowToHigh.indices) { "Selected string is outside the tuning" }
        resetIfContextChanged(EngineContext(mode, tuning, selectedString, referencePitch, settings))

        val detected = MusicMath.nearestMidi(estimate.hertz, referencePitch)
        if (detected != smoothedDetected) {
            needleSmoother.reset()
            smoothedDetected = detected
        }
        val hertz = needleSmoother.add(estimate.hertz, settings.needleStability)
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
            inTune = abs(cents) <= settings.inTuneCents,
        )
    }

    fun reset() {
        context = null
        currentTarget = null
        smoothedDetected = null
        needleSmoother.reset()
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
        reset()
        context = next
    }

    private data class EngineContext(
        val mode: TunerMode,
        val tuning: TuningPreset,
        val selectedString: Int,
        val referencePitch: ReferencePitch,
        val settings: TunerAudioSettings,
    )

    private class NeedleSmoother {
        private var smoothedLogHertz: Double? = null

        fun add(value: Double, needleStability: Int): Double {
            val next = log2(value)
            val current = smoothedLogHertz ?: return value.also { smoothedLogHertz = next }
            val factor = 0.15 + (100 - needleStability) * 0.0065
            val smoothed = current + (next - current) * factor
            smoothedLogHertz = smoothed
            return 2.0.pow(smoothed)
        }

        fun reset() {
            smoothedLogHertz = null
        }
    }

    private companion object {
        const val TARGET_HYSTERESIS_CENTS = 8.0
    }
}
