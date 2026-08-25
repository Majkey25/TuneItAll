package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

private val THRESHOLDS = doubleArrayOf(0.05, 0.075, 0.10, 0.125, 0.15, 0.175, 0.20, 0.25, 0.30)
private val THRESHOLD_WEIGHTS = doubleArrayOf(0.02, 0.06, 0.12, 0.18, 0.20, 0.17, 0.12, 0.08, 0.05)
private const val MAX_CANDIDATES = 8
private const val MERGE_CENTS = 15.0

internal fun decimate(samples: ShortArray, output: ShortArray) {
    require(output.isNotEmpty() && samples.size % output.size == 0) { "Decimation sizes must divide evenly" }
    val factor = samples.size / output.size
    require(factor == 2 || factor == 4) { "Decimation factor must be two or four" }
    for (index in output.indices) {
        val input = index * factor
        var sum = 0
        for (offset in 0 until factor) sum += samples[input + offset]
        output[index] = (sum / factor).toShort()
    }
}

data class PitchCandidate(
    val hertz: Double,
    val probability: Double,
    val periodicity: Double,
) {
    init {
        require(hertz.isFinite() && hertz > 0.0) { "Pitch frequency must be positive and finite" }
        require(probability.isFinite() && probability in 0.0..1.0) {
            "Pitch probability must be finite and between 0 and 1"
        }
        require(periodicity.isFinite() && periodicity in 0.0..1.0) {
            "Pitch periodicity must be finite and between 0 and 1"
        }
    }
}

data class PitchFrame(
    val candidates: List<PitchCandidate>,
    val rms: Double,
    val peak: Double,
    val unvoicedProbability: Double,
) {
    init {
        require(candidates.size <= MAX_CANDIDATES) { "Pitch frame has too many candidates" }
        require(rms.isFinite() && rms >= 0.0) { "Pitch RMS must be finite and non-negative" }
        require(peak.isFinite() && peak in 0.0..1.0) { "Pitch peak must be finite and between 0 and 1" }
        require(unvoicedProbability.isFinite() && unvoicedProbability in 0.0..1.0) {
            "Unvoiced probability must be finite and between 0 and 1"
        }
    }
}

internal val PitchFrame.isDetectorVoiced: Boolean
    get() = (candidates.maxOfOrNull(PitchCandidate::probability) ?: 0.0) > unvoicedProbability

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

class YinPitchDetector {
    private var difference = DoubleArray(0)
    private var cumulativeMean = DoubleArray(0)
    private var decimated = ShortArray(0)
    private var recentWindow = ShortArray(0)

    fun analyze(
        samples: ShortArray,
        sampleRate: Int,
        minFrequency: Double,
        maxFrequency: Double,
    ): PitchFrame {
        validateArguments(samples, sampleRate, minFrequency, maxFrequency)
        var analysisSamples = samples
        var analysisSampleRate = sampleRate
        if (samples.size >= LONG_WINDOW_SIZE && samples.size % 4 == 0 && maxFrequency <= LOW_RANGE_MAX_HERTZ) {
            val outputSize = samples.size / 4
            if (decimated.size != outputSize) decimated = ShortArray(outputSize)
            decimate(samples, decimated)
            analysisSamples = decimated
            analysisSampleRate = sampleRate / 4
        } else if (
            samples.size >= LONG_WINDOW_SIZE &&
            samples.size % 2 == 0 &&
            (minFrequency >= HIGH_RANGE_MIN_HERTZ || maxFrequency > WIDE_RANGE_MAX_HERTZ) &&
            maxFrequency <= sampleRate / 4.0
        ) {
            val outputSize = samples.size / 2
            if (decimated.size != outputSize) decimated = ShortArray(outputSize)
            decimate(samples, decimated)
            analysisSamples = decimated
            analysisSampleRate = sampleRate / 2
        } else if (samples.size >= LONG_WINDOW_SIZE) {
            if (recentWindow.size != SHORT_WINDOW_SIZE) recentWindow = ShortArray(SHORT_WINDOW_SIZE)
            samples.copyInto(recentWindow, startIndex = samples.size - SHORT_WINDOW_SIZE)
            analysisSamples = recentWindow
        }

        val tauMin = floor(analysisSampleRate / maxFrequency).toInt().coerceAtLeast(MIN_TAU)
        val tauMax = ceil(analysisSampleRate / minFrequency).toInt()
        require(analysisSamples.size > tauMax * 2) { "Sample frame is too short for the minimum frequency" }

        val rms = calculateRms(analysisSamples)
        val peak = calculatePeak(analysisSamples)
        ensureCapacity(tauMax + 1)
        calculateDifference(analysisSamples, tauMax)
        calculateCumulativeMean(tauMax)

        val candidates = mutableListOf<PitchCandidate>()
        THRESHOLDS.indices.forEach { index ->
            val tau = findThresholdMinimum(tauMin, tauMax, THRESHOLDS[index]) ?: return@forEach
            val refinedTau = parabolicInterpolation(tau, tauMax)
            val hertz = analysisSampleRate / refinedTau
            if (isWithinRange(hertz, minFrequency, maxFrequency)) {
                mergeCandidate(candidates, hertz, 1.0 - cumulativeMean[tau], THRESHOLD_WEIGHTS[index])
            }
        }
        if (candidates.isEmpty()) {
            val tau = findNoTroughMinimum(tauMin, tauMax)
            val periodicity = (1.0 - cumulativeMean[tau]).coerceIn(0.0, 1.0)
            val hertz = analysisSampleRate / parabolicInterpolation(tau, tauMax)
            if (periodicity >= NO_TROUGH_MIN_PERIODICITY && isWithinRange(hertz, minFrequency, maxFrequency)) {
                mergeCandidate(candidates, hertz, periodicity, periodicity)
            }
        }
        val boundedCandidates = candidates.sortedByDescending { it.probability }.take(MAX_CANDIDATES)
        return PitchFrame(
            candidates = boundedCandidates,
            rms = rms,
            peak = peak,
            unvoicedProbability = (1.0 - boundedCandidates.sumOf { it.probability }).coerceIn(0.0, 1.0),
        )
    }

    private fun mergeCandidate(
        candidates: MutableList<PitchCandidate>,
        hertz: Double,
        periodicity: Double,
        weight: Double,
    ) {
        val index = candidates.indexOfFirst { candidate ->
            abs(CENTS_PER_OCTAVE * ln(hertz / candidate.hertz) / ln(2.0)) <= MERGE_CENTS
        }
        if (index < 0) {
            candidates += PitchCandidate(hertz, weight, periodicity.coerceIn(0.0, 1.0))
            return
        }

        val existing = candidates[index]
        candidates[index] = PitchCandidate(
            hertz = if (periodicity > existing.periodicity) hertz else existing.hertz,
            probability = (existing.probability + weight).coerceIn(0.0, 1.0),
            periodicity = maxOf(existing.periodicity, periodicity.coerceIn(0.0, 1.0)),
        )
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

    private fun findNoTroughMinimum(tauMin: Int, tauMax: Int): Int {
        val global = (tauMin..tauMax).minBy(cumulativeMean::get)
        if (tauMax.toDouble() / tauMin <= NARROW_RANGE_RATIO) return global
        val acceptedValue = cumulativeMean[global] + NO_TROUGH_MARGIN
        for (tau in (tauMin + 1) until tauMax) {
            if (
                cumulativeMean[tau] <= acceptedValue &&
                cumulativeMean[tau] <= cumulativeMean[tau - 1] &&
                cumulativeMean[tau] < cumulativeMean[tau + 1]
            ) {
                return tau
            }
        }
        return global
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

    private fun calculatePeak(samples: ShortArray): Double =
        samples.maxOf { abs(it.toDouble()) / PCM_SCALE }

    private fun isWithinRange(hertz: Double, minFrequency: Double, maxFrequency: Double): Boolean {
        val boundaryRatio = 2.0.pow(MAX_BOUNDARY_ERROR_CENTS / CENTS_PER_OCTAVE)
        return hertz >= minFrequency / boundaryRatio && hertz <= maxFrequency * boundaryRatio
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
        const val MIN_SAMPLE_COUNT = 4
        const val LONG_WINDOW_SIZE = 8192
        const val SHORT_WINDOW_SIZE = 4096
        const val LOW_RANGE_MAX_HERTZ = 150.0
        const val HIGH_RANGE_MIN_HERTZ = 150.0
        const val WIDE_RANGE_MAX_HERTZ = 1_000.0
        const val MIN_TAU = 2
        const val PCM_SCALE = 32768.0
        const val NO_TROUGH_MIN_PERIODICITY = 0.25
        const val NO_TROUGH_MARGIN = 0.05
        const val NARROW_RANGE_RATIO = 8.0
        const val INTERPOLATION_EPSILON = 1e-12
        const val MAX_BOUNDARY_ERROR_CENTS = 1.0
        const val CENTS_PER_OCTAVE = 1200.0
    }
}
