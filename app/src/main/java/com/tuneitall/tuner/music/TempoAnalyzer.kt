package com.tuneitall.tuner.music

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
) {
    private val frameSize: Int
    private val onsetStrengths = mutableListOf<Double>()
    private val maxSamples: Long
    private var frameEnergy = 0.0
    private var frameFill = 0
    private var previousLevel = 0.0
    private var totalSamples = 0L

    init {
        require(sampleRate in 8_000..192_000)
        require(maxDurationSeconds in 1..MAX_TEMPO_ANALYSIS_SECONDS)
        frameSize = (sampleRate / ONSET_FRAMES_PER_SECOND).coerceAtLeast(1)
        maxSamples = Math.multiplyExact(sampleRate.toLong(), maxDurationSeconds.toLong())
    }

    fun accept(samples: FloatArray) {
        require(samples.all(Float::isFinite))
        require(totalSamples + samples.size <= maxSamples) { "Song analysis exceeds the duration limit" }
        totalSamples += samples.size
        samples.forEach { sample ->
            frameEnergy += sample * sample
            frameFill++
            if (frameFill == frameSize) closeFrame()
        }
    }

    fun finish(): TempoEstimate? {
        if (totalSamples < sampleRate * MIN_ANALYSIS_SECONDS.toLong()) return null
        if (frameFill > 0) closeFrame()
        val onset = normalizedOnsetEnvelope()
        if (onset.sumOf { it * it } < MIN_ONSET_ENERGY) return null

        val framesPerSecond = sampleRate.toDouble() / frameSize
        val minimumLag = (framesPerSecond * 60.0 / MAX_TEMPO_BPM).roundToInt().coerceAtLeast(1)
        val maximumLag = (framesPerSecond * 60.0 / MIN_TEMPO_BPM).roundToInt().coerceAtMost(onset.lastIndex)
        var bestLag = 0
        var bestCorrelation = 0.0
        var bestScore = 0.0
        for (lag in minimumLag..maximumLag) {
            val correlation = autocorrelation(onset, lag)
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
        val bpm = (framesPerSecond * 60.0 / bestLag).roundToInt().coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM)
        return TempoEstimate(bpm, bestCorrelation.coerceIn(0.0, 1.0))
    }

    private fun closeFrame() {
        val level = ln1p(100.0 * sqrt(frameEnergy / frameFill))
        onsetStrengths += (level - previousLevel).coerceAtLeast(0.0)
        previousLevel = level
        frameEnergy = 0.0
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
        return DoubleArray(normalized.size) { index ->
            normalized[index] * 0.5 +
                normalized.getOrElse(index - 1) { 0.0 } * 0.25 +
                normalized.getOrElse(index + 1) { 0.0 } * 0.25
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
private const val MIN_ANALYSIS_SECONDS = 4
private const val MAX_TEMPO_ANALYSIS_SECONDS = 30 * 60
private const val MIN_TEMPO_BPM = 40
private const val MAX_TEMPO_BPM = 240
private const val PREFERRED_TEMPO_BPM = 120.0
private const val MIN_ONSET_ENERGY = 1e-8
private const val MIN_CORRELATION = 0.08
