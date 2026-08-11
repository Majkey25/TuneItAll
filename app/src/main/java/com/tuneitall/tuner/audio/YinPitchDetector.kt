package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

data class PitchEstimate(
    val hertz: Double,
    val confidence: Double,
    val rms: Double,
) {
    init {
        require(hertz.isFinite() && hertz > 0.0) { "Pitch frequency must be positive and finite" }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "Pitch confidence must be finite and between 0 and 1"
        }
        require(rms.isFinite() && rms >= 0.0) { "Pitch RMS must be finite and non-negative" }
    }
}

class YinPitchDetector(
    private val threshold: Double = DEFAULT_THRESHOLD,
) {
    private var difference = DoubleArray(0)
    private var cumulativeMean = DoubleArray(0)

    init {
        require(threshold > 0.0 && threshold < 1.0) { "YIN threshold must be between 0 and 1" }
    }

    fun detect(
        samples: ShortArray,
        sampleRate: Int,
        minFrequency: Double,
        maxFrequency: Double,
        sensitivity: DetectionSensitivity = DetectionSensitivity.DEFAULT,
    ): PitchEstimate? {
        validateArguments(samples, sampleRate, minFrequency, maxFrequency)

        val tauMin = floor(sampleRate / maxFrequency).toInt().coerceAtLeast(MIN_TAU)
        val tauMax = ceil(sampleRate / minFrequency).toInt()
        require(samples.size > tauMax * 2) { "Sample frame is too short for the minimum frequency" }

        val rms = calculateRms(samples)
        if (rms < sensitivity.minimumRms) return null

        ensureCapacity(tauMax + 1)
        calculateDifference(samples, tauMax)
        calculateCumulativeMean(tauMax)

        var candidate = findThresholdMinimum(tauMin, tauMax, sensitivity.yinThreshold(threshold)) ?: return null
        candidate = correctStrongHarmonic(candidate, tauMax)
        val confidence = (1.0 - cumulativeMean[candidate]).coerceIn(0.0, 1.0)
        if (confidence < sensitivity.minimumConfidence) return null

        val refinedTau = parabolicInterpolation(candidate, tauMax)
        val hertz = sampleRate / refinedTau
        val boundaryRatio = 2.0.pow(MAX_BOUNDARY_ERROR_CENTS / CENTS_PER_OCTAVE)
        if (hertz < minFrequency / boundaryRatio || hertz > maxFrequency * boundaryRatio) return null
        return PitchEstimate(hertz, confidence, rms)
    }

    private fun calculateDifference(samples: ShortArray, tauMax: Int) {
        difference[0] = 0.0
        val analysisLength = samples.size - tauMax
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (index in 0 until analysisLength) {
                val delta = samples[index].toDouble() - samples[index + tau].toDouble()
                sum += delta * delta
            }
            difference[tau] = sum
        }
    }

    private fun calculateCumulativeMean(tauMax: Int) {
        cumulativeMean[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..tauMax) {
            runningSum += difference[tau]
            cumulativeMean[tau] = if (runningSum == 0.0) {
                1.0
            } else {
                difference[tau] * tau / runningSum
            }
        }
    }

    private fun findThresholdMinimum(tauMin: Int, tauMax: Int, activeThreshold: Double): Int? {
        var tau = tauMin
        while (tau <= tauMax) {
            if (cumulativeMean[tau] < activeThreshold) {
                while (tau < tauMax && cumulativeMean[tau + 1] < cumulativeMean[tau]) tau++
                return tau
            }
            tau++
        }
        return null
    }

    private fun correctStrongHarmonic(candidate: Int, tauMax: Int): Int {
        val doubled = candidate * 2
        if (doubled > tauMax) return candidate

        var best = doubled
        val from = (doubled - 2).coerceAtLeast(candidate + 1)
        val to = (doubled + 2).coerceAtMost(tauMax)
        for (tau in from..to) {
            if (cumulativeMean[tau] < cumulativeMean[best]) best = tau
        }
        return if (cumulativeMean[best] + HARMONIC_MARGIN < cumulativeMean[candidate]) best else candidate
    }

    private fun parabolicInterpolation(tau: Int, tauMax: Int): Double {
        if (tau <= 1 || tau >= tauMax) return tau.toDouble()
        val previous = difference[tau - 1]
        val current = difference[tau]
        val next = difference[tau + 1]
        val denominator = previous - 2.0 * current + next
        if (abs(denominator) < INTERPOLATION_EPSILON) return tau.toDouble()
        val offset = (0.5 * (previous - next) / denominator).coerceIn(-1.0, 1.0)
        return tau + offset
    }

    private fun calculateRms(samples: ShortArray): Double {
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / PCM_SCALE
            sum += normalized * normalized
        }
        return sqrt(sum / samples.size)
    }

    private fun ensureCapacity(size: Int) {
        if (difference.size >= size) return
        difference = DoubleArray(size)
        cumulativeMean = DoubleArray(size)
    }

    private fun validateArguments(
        samples: ShortArray,
        sampleRate: Int,
        minFrequency: Double,
        maxFrequency: Double,
    ) {
        require(samples.size >= MIN_SAMPLE_COUNT) { "At least $MIN_SAMPLE_COUNT samples are required" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(minFrequency.isFinite() && minFrequency > 0.0) {
            "Minimum frequency must be positive and finite"
        }
        require(maxFrequency.isFinite() && maxFrequency > minFrequency) {
            "Maximum frequency must be finite and greater than minimum frequency"
        }
        require(maxFrequency <= sampleRate / 2.0) { "Maximum frequency must not exceed Nyquist" }
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 0.15
        const val MIN_SAMPLE_COUNT = 4
        const val MIN_TAU = 2
        const val PCM_SCALE = 32768.0
        const val HARMONIC_MARGIN = 0.05
        const val INTERPOLATION_EPSILON = 1e-12
        const val MAX_BOUNDARY_ERROR_CENTS = 1.0
        const val CENTS_PER_OCTAVE = 1200.0
    }
}
