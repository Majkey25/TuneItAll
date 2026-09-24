package com.tuneitall.tuner.music

import kotlin.math.PI
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
            if (lag >= minimumLag - 1) autocorrelation(onset, lag) else 0.0
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
            val octaveDistance = ln(bpm / PREFERRED_TEMPO_BPM) / ln(2.0)
            val prior = exp(-0.5 * octaveDistance * octaveDistance)
            val tempoBias = sqrt(bpm / PREFERRED_TEMPO_BPM).coerceIn(0.7, 1.3)
            val score = correlation * (0.75 + 0.25 * prior) * tempoBias
            if (score > bestScore) {
                bestLag = lag
                bestCorrelation = correlation
                bestScore = score
            }
        }
        if (bestLag == 0 || bestCorrelation < MIN_CORRELATION) return null
        val left = correlations[bestLag - 1]
        val right = correlations[bestLag + 1]
        val offset = (0.5 * (left - right) / (left - 2.0 * bestCorrelation + right)).coerceIn(-0.5, 0.5)
        val bpm = (framesPerSecond * 60.0 / (bestLag + offset)).roundToInt()
            .coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        // Periodicity strength, not a calibrated probability or a resolution of half/double-time ambiguity.
        return TempoEstimate(bpm, bestCorrelation.coerceIn(0.0, 1.0))
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

private fun autocorrelation(values: DoubleArray, lag: Int): Double {
    var product = 0.0
    var currentEnergy = 0.0
    var delayedEnergy = 0.0
    for (index in lag until values.size) {
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
