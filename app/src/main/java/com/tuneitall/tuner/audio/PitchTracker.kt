package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.math.ln

class PitchTracker {
    private val states = mutableListOf<VoicedState>()
    private var unvoicedScore = 0.0
    private var previousRms = 0.0
    private var missingFrames = 0

    fun update(frame: PitchFrame, settings: TunerAudioSettings): PitchEstimate? {
        if (frame.candidates.isEmpty()) {
            missingFrames = (missingFrames + 1).coerceAtMost(MAX_MISSING_FRAMES)
            if (missingFrames == MAX_MISSING_FRAMES) {
                states.clear()
                unvoicedScore = 0.0
            }
            previousRms = frame.rms
            return null
        }

        missingFrames = 0
        val onset = previousRms > 0.0 && frame.rms >= previousRms * ONSET_RATIO
        val strongestPeriodicity = frame.candidates.maxOf(PitchCandidate::periodicity)
        val candidates = frame.candidates.map { candidate ->
            VoicedState(
                hertz = candidate.hertz,
                confidence = maxOf(candidate.probability, candidate.periodicity),
                score = ln(maxOf(candidate.probability, candidate.periodicity, MIN_PROBABILITY)) + bestPreviousScore(
                    candidate.hertz,
                    settings,
                    onset,
                ),
            )
        }
        val retained = states.filter { state -> candidates.none { samePitch(state.hertz, it.hertz) } }
            .map { it.copy(score = it.score - STALE_STATE_COST) }
        val unvoicedProbability = minOf(frame.unvoicedProbability, 1.0 - strongestPeriodicity)
        val previousUnvoiced = unvoicedScore
        val previousVoiced = states.maxOfOrNull(VoicedState::score) ?: Double.NEGATIVE_INFINITY
        unvoicedScore = ln(unvoicedProbability.coerceAtLeast(MIN_PROBABILITY)) +
            maxOf(previousUnvoiced, previousVoiced - UNVOICED_SWITCH_COST)

        states.clear()
        states += (candidates + retained).sortedByDescending { it.score }.take(MAX_STATES)
        normalize()
        previousRms = frame.rms

        val best = states.maxByOrNull { it.score } ?: return null
        return best.takeIf { it.score > unvoicedScore }?.let { PitchEstimate(it.hertz, it.confidence, frame.rms) }
    }

    fun reset() {
        states.clear()
        unvoicedScore = 0.0
        previousRms = 0.0
        missingFrames = 0
    }

    private fun bestPreviousScore(hertz: Double, settings: TunerAudioSettings, onset: Boolean): Double = maxOf(
        unvoicedScore - VOICED_SWITCH_COST,
        states.maxOfOrNull { state ->
            state.score - transitionCost(state.hertz, hertz, settings, onset) +
                if (samePitch(state.hertz, hertz)) voicedPersistenceBonus(settings) else 0.0
        } ?: Double.NEGATIVE_INFINITY,
    )

    private fun voicedPersistenceBonus(settings: TunerAudioSettings): Double =
        MIN_VOICED_PERSISTENCE_BONUS +
            (MAX_VOICED_PERSISTENCE_BONUS - MIN_VOICED_PERSISTENCE_BONUS) * settings.sensitivity.value / 100.0

    private fun transitionCost(
        previousHertz: Double,
        currentHertz: Double,
        settings: TunerAudioSettings,
        onset: Boolean,
    ): Double {
        val cents = centsDistance(previousHertz, currentHertz)
        val octavePenalty = if (abs(cents - CENTS_PER_OCTAVE) <= OCTAVE_TOLERANCE_CENTS) {
            OCTAVE_PENALTY * settings.harmonicProtection / 100.0
        } else {
            0.0
        }
        val onsetScale = if (onset) ONSET_COST_SCALE else 1.0
        return (cents / CENTS_PER_TRANSITION_COST + octavePenalty) *
            responseMultiplier(settings.response) * onsetScale
    }

    private fun responseMultiplier(response: ResponseMode): Double = when (response) {
        ResponseMode.FAST -> 0.55
        ResponseMode.BALANCED -> 1.0
        ResponseMode.STABLE -> 1.6
    }

    private fun normalize() {
        val best = maxOf(unvoicedScore, states.maxOfOrNull { it.score } ?: Double.NEGATIVE_INFINITY)
        unvoicedScore -= best
        states.replaceAll { it.copy(score = it.score - best) }
    }

    private fun samePitch(first: Double, second: Double): Boolean = centsDistance(first, second) <= SAME_PITCH_CENTS

    private fun centsDistance(first: Double, second: Double): Double =
        abs(CENTS_PER_OCTAVE * ln(first / second) / ln(2.0))

    private data class VoicedState(
        val hertz: Double,
        val confidence: Double,
        val score: Double,
    )

    private companion object {
        const val MAX_STATES = 8
        const val MIN_PROBABILITY = 1e-9
        const val CENTS_PER_OCTAVE = 1_200.0
        const val CENTS_PER_TRANSITION_COST = 400.0
        const val OCTAVE_TOLERANCE_CENTS = 100.0
        const val OCTAVE_PENALTY = 3.0
        const val STALE_STATE_COST = 0.4
        const val VOICED_SWITCH_COST = 0.8
        const val UNVOICED_SWITCH_COST = 0.8
        const val MIN_VOICED_PERSISTENCE_BONUS = 0.4
        const val MAX_VOICED_PERSISTENCE_BONUS = 1.2
        const val ONSET_RATIO = 1.8
        const val ONSET_COST_SCALE = 0.25
        const val SAME_PITCH_CENTS = 15.0
        const val MAX_MISSING_FRAMES = 8
    }
}
