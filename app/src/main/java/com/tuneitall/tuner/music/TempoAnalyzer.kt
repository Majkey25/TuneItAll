package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TempoEstimate(val bpm: Int, val confidence: Double) {
    init {
        require(bpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM)
        require(confidence in 0.0..1.0)
    }
}

class StreamingTempoAnalyzer internal constructor(
    private val sampleRate: Int,
    maxDurationSeconds: Int = MAX_TEMPO_ANALYSIS_SECONDS,
    private val isCancelled: () -> Boolean = { false },
    private val channelCount: Int = 1,
) {
    init {
        require(sampleRate in 8_000..192_000)
        require(maxDurationSeconds in 1..MAX_TEMPO_ANALYSIS_SECONDS)
        require(channelCount in 1..8)
    }

    private val frameSize: Int
    private val onsetStrengths = mutableListOf<Double>()
    private val maxFrames: Long
    // Keep bass, midrange and treble attacks when total loudness barely changes.
    private val lowPassCoefficients = doubleArrayOf(200.0, 800.0, 2_400.0).map {
        1.0 - exp(-2.0 * PI * it / sampleRate)
    }.toDoubleArray()
    private val lowPassStates = Array(channelCount) { DoubleArray(lowPassCoefficients.size) }
    private val sampleEnergies = DoubleArray(lowPassCoefficients.size + 1)
    private val energySmoothing = 1.0 - exp(-2.0 * PI * ENVELOPE_CUTOFF_HZ / sampleRate)
    private val smoothedEnergies = Array(sampleEnergies.size) { DoubleArray(4) }
    private val previousLevels = DoubleArray(sampleEnergies.size)
    private var frameFill = 0
    private var totalFrames = 0L

    init {
        frameSize = (sampleRate / ONSET_FRAMES_PER_SECOND).coerceAtLeast(1)
        maxFrames = Math.multiplyExact(sampleRate.toLong(), maxDurationSeconds.toLong())
    }

    fun accept(samples: FloatArray) {
        checkAnalysisCancellation(isCancelled)
        require(samples.size % channelCount == 0) { "Audio input must contain complete sample frames" }
        require(samples.all(Float::isFinite))
        val sampleFrames = samples.size / channelCount
        require(totalFrames + sampleFrames <= maxFrames) { "Song analysis exceeds the duration limit" }
        totalFrames += sampleFrames
        repeat(sampleFrames) { frame ->
            if (frame % 1024 == 0) checkAnalysisCancellation(isCancelled)
            val offset = frame * channelCount
            sampleEnergies.fill(0.0)
            for (channel in 0 until channelCount) {
                val sample = samples[offset + channel].toDouble()
                val states = lowPassStates[channel]
                var previousBand = 0.0
                for (band in states.indices) {
                    states[band] += lowPassCoefficients[band] * (sample - states[band])
                    val value = states[band] - previousBand
                    sampleEnergies[band] += value * value
                    previousBand = states[band]
                }
                val high = sample - previousBand
                sampleEnergies[sampleEnergies.lastIndex] += high * high
            }
            // Filter power before downsampling; otherwise carrier ripple aliases into the beat range.
            for (band in smoothedEnergies.indices) {
                var energy = sampleEnergies[band] / channelCount
                for (stage in smoothedEnergies[band].indices) {
                    smoothedEnergies[band][stage] += energySmoothing * (energy - smoothedEnergies[band][stage])
                    energy = smoothedEnergies[band][stage]
                }
            }
            frameFill++
            if (frameFill == frameSize) closeFrame()
        }
    }

    fun finish(): TempoEstimate? {
        checkAnalysisCancellation(isCancelled)
        if (totalFrames < sampleRate * MIN_ANALYSIS_SECONDS.toLong()) return null
        if (frameFill > 0) closeFrame()
        val onset = normalizedOnsetEnvelope()
        if (onset.sumOf { it * it } / onset.size < MIN_ONSET_ENERGY) return null

        val framesPerSecond = sampleRate.toDouble() / frameSize
        val minimumLag = (framesPerSecond * 60.0 / MAX_TEMPO_BPM).roundToInt().coerceAtLeast(1)
        val maximumLag = (framesPerSecond * 60.0 / MIN_TEMPO_BPM).roundToInt().coerceAtMost(onset.lastIndex)
        val correlations = DoubleArray(maximumLag + 2) { lag ->
            checkAnalysisCancellation(isCancelled)
            if (lag >= minimumLag - 1) autocorrelation(onset, lag, isCancelled) else 0.0
        }
        var bestLag = 0
        var bestCorrelation = 0.0
        var bestScore = 0.0
        for (lag in minimumLag..maximumLag) {
            checkAnalysisCancellation(isCancelled)
            val correlation = correlations[lag]
            // A fade has a broad decaying correlation, not a recurring beat peak.
            if (correlation <= correlations[lag - 1] || correlation < correlations[lag + 1]) continue
            val bpm = framesPerSecond * 60.0 / lag
            val score = correlation * tempoWeight(bpm)
            if (score > bestScore) {
                bestLag = lag
                bestCorrelation = correlation
                bestScore = score
            }
        }
        if (bestLag == 0 || bestCorrelation < MIN_CORRELATION) return null
        // A single matching attack pair is not enough to establish a recurring beat.
        val minimumAttack = onset.sumOf { it.coerceAtLeast(0.0) } / onset.size
        fun isAttackPeak(index: Int): Boolean = onset[index] >= minimumAttack &&
            onset[index] > onset[index - 1] && onset[index] >= onset[index + 1]
        // Allow 20 ms onset jitter at each end of a matching interval.
        val lagTolerance = (framesPerSecond * 0.04).roundToInt().coerceAtLeast(1)
        var pairs = 0
        var lastAttack = -minimumLag
        for (index in 1 until onset.lastIndex) {
            if (index % 1024 == 0) checkAnalysisCancellation(isCancelled)
            if (index - lastAttack >= minimumLag && isAttackPeak(index)) {
                lastAttack = index
                val first = (index - bestLag - lagTolerance).coerceAtLeast(1)
                val last = (index - bestLag + lagTolerance).coerceAtMost(index - 1)
                if ((first..last).any(::isAttackPeak) && ++pairs == MIN_RECURRING_PAIRS) break
            }
        }
        if (pairs < MIN_RECURRING_PAIRS) return null
        val left = correlations[bestLag - 1]
        val right = correlations[bestLag + 1]
        val offset = (0.5 * (left - right) / (left - 2.0 * bestCorrelation + right)).coerceIn(-0.5, 0.5)
        val bpm = (framesPerSecond * 60.0 / (bestLag + offset)).roundToInt()
            .coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        val refinedBpm = if (bestCorrelation < STRONG_PERIODICITY) {
            subdivisionGridTempo(onset, correlations, bpm, framesPerSecond)
        } else bpm
        // Dominant periodicity strength, not a calibrated probability or a resolution of musical meter.
        return TempoEstimate(refinedBpm, bestCorrelation.coerceIn(0.0, 1.0))
    }

    private fun subdivisionGridTempo(
        onset: DoubleArray,
        correlations: DoubleArray,
        bpm: Int,
        framesPerSecond: Double,
    ): Int {
        // A weak peak can follow dotted accents rather than the recurring beat subdivision grid.
        val gridCorrelations = DoubleArray(minOf(onset.lastIndex, (correlations.size - 2) * GRID_BEATS) + 2) { lag ->
            checkAnalysisCancellation(isCancelled)
            if (lag in correlations.indices && correlations[lag] != 0.0) correlations[lag]
            else if (lag > 0) autocorrelation(onset, lag, isCancelled) else 0.0
        }
        fun gridScore(tempo: Double): Double {
            val period = framesPerSecond * 60.0 / tempo
            var score = 0.0
            for (step in 1..GRID_BEATS * 2) {
                val position = period * step / 2
                val lag = position.toInt()
                if (lag + 1 < gridCorrelations.size) {
                    score += gridCorrelations[lag] + (position - lag) *
                        (gridCorrelations[lag + 1] - gridCorrelations[lag])
                }
            }
            return score * tempoWeight(tempo)
        }
        val originalScore = gridScore(bpm.toDouble())
        var bestTempo = bpm.toDouble()
        var bestScore = originalScore
        for (tenths in MIN_TEMPO_BPM * GRID_STEPS_PER_BPM..MAX_TEMPO_BPM * GRID_STEPS_PER_BPM) {
            if (tenths % 1024 == 0) checkAnalysisCancellation(isCancelled)
            val candidate = tenths.toDouble() / GRID_STEPS_PER_BPM
            val score = gridScore(candidate)
            if (score > bestScore) {
                bestScore = score
                bestTempo = candidate
            }
        }
        val octaves = ln(bestTempo / bpm) / ln(2.0)
        // A subdivision grid alone cannot decide between half/double-time interpretations.
        if (abs(octaves - octaves.roundToInt()) < OCTAVE_MATCH_TOLERANCE ||
            bestScore <= originalScore.coerceAtLeast(0.0) * MIN_GRID_SCORE_GAIN
        ) return bpm
        return bestTempo.roundToInt().coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
    }

    private fun closeFrame() {
        var onset = 0.0
        for (band in smoothedEnergies.indices) {
            val level = ln1p(100.0 * sqrt(smoothedEnergies[band].last()))
            onset += (level - previousLevels[band]).coerceAtLeast(0.0)
            previousLevels[band] = level
        }
        onsetStrengths += onset
        frameFill = 0
    }

    private fun normalizedOnsetEnvelope(): DoubleArray {
        val prefix = DoubleArray(onsetStrengths.size + 1)
        onsetStrengths.forEachIndexed { index, value -> prefix[index + 1] = prefix[index] + value }
        val radius = ONSET_FRAMES_PER_SECOND / 2
        val normalized = DoubleArray(onsetStrengths.size) { index ->
            val start = (index - radius).coerceAtLeast(0)
            val end = (index + radius + 1).coerceAtMost(onsetStrengths.size)
            val localMean = (prefix[end] - prefix[start]) / (end - start)
            (onsetStrengths[index] - localMean).coerceAtLeast(0.0)
        }
        // Positive onset values have a nonzero noise floor; remove it before correlating.
        val mean = normalized.average()
        return DoubleArray(normalized.size) { index ->
            normalized[index] * 0.5 +
                normalized.getOrElse(index - 1) { 0.0 } * 0.25 +
                normalized.getOrElse(index + 1) { 0.0 } * 0.25 - mean
        }
    }
}

private fun tempoWeight(bpm: Double): Double {
    val octaveDistance = ln(bpm / PREFERRED_TEMPO_BPM) / ln(2.0)
    val prior = exp(-0.5 * octaveDistance * octaveDistance)
    val tempoBias = sqrt(bpm / PREFERRED_TEMPO_BPM).coerceIn(0.7, 1.3)
    return (0.75 + 0.25 * prior) * tempoBias
}

private fun autocorrelation(values: DoubleArray, lag: Int, isCancelled: () -> Boolean): Double {
    var product = 0.0
    var currentEnergy = 0.0
    var delayedEnergy = 0.0
    for (index in lag until values.size) {
        if (index % 1024 == 0) checkAnalysisCancellation(isCancelled)
        val current = values[index]
        val delayed = values[index - lag]
        product += current * delayed
        currentEnergy += current * current
        delayedEnergy += delayed * delayed
    }
    val denominator = sqrt(currentEnergy * delayedEnergy)
    return if (denominator > 0.0) product / denominator else 0.0
}

private const val ONSET_FRAMES_PER_SECOND = 100
private const val ENVELOPE_CUTOFF_HZ = 10.0
private const val MIN_ANALYSIS_SECONDS = 4
private const val MAX_TEMPO_ANALYSIS_SECONDS = 30 * 60
private const val MIN_TEMPO_BPM = 40
private const val MAX_TEMPO_BPM = 240
private const val PREFERRED_TEMPO_BPM = 120.0
private const val MIN_ONSET_ENERGY = 1e-8
private const val MIN_CORRELATION = 0.08
private const val MIN_RECURRING_PAIRS = 2
private const val STRONG_PERIODICITY = 0.5
private const val GRID_BEATS = 4
private const val GRID_STEPS_PER_BPM = 10
private const val MIN_GRID_SCORE_GAIN = 1.5
private const val OCTAVE_MATCH_TOLERANCE = 0.06
