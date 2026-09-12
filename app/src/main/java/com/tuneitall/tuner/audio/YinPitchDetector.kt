package com.tuneitall.tuner.audio

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.sin

private val THRESHOLDS = doubleArrayOf(0.05, 0.075, 0.10, 0.125, 0.15, 0.175, 0.20, 0.25, 0.30)
private val THRESHOLD_WEIGHTS = doubleArrayOf(0.02, 0.06, 0.12, 0.18, 0.20, 0.17, 0.12, 0.08, 0.05)
private const val MAX_CANDIDATES = 8
private const val MERGE_CENTS = 15.0
private val CHIME_FREQUENCIES = doubleArrayOf(
    CONFIRMATION_CHIME_HERTZ, CONFIRMATION_CHIME_SECOND_HERTZ, CONFIRMATION_CHIME_THIRD_HERTZ,
)

internal fun decimate(
    samples: ShortArray,
    output: DoubleArray,
    sampleRate: Int,
    maxFrequency: Double,
    rejectConfirmation: Boolean = false,
): Int {
    require(output.isNotEmpty() && samples.size % output.size == 0) { "Decimation sizes must divide evenly" }
    val factor = samples.size / output.size
    require(factor == 1 || factor == 2 || factor == 4)
    require(sampleRate > 0 && maxFrequency.isFinite() && maxFrequency > 0.0)
    val cutoff = minOf(0.4 / factor, maxFrequency * 2.5 / sampleRate)
    val first = PitchLowPass(cutoff, 0.5411961001, samples.first().toDouble())
    val second = PitchLowPass(cutoff, 1.3065629649, samples.first().toDouble())
    val notches = if (rejectConfirmation) {
        Array(CHIME_FREQUENCIES.size * 2) {
            ChimeNotch(CHIME_FREQUENCIES[it / 2], sampleRate, samples.first().toDouble())
        }
    } else emptyArray()
    samples.forEachIndexed { index, sample ->
        var filtered = sample.toDouble()
        notches.forEach { notch -> filtered = notch.process(filtered) }
        val value = second.process(first.process(filtered))
        if (index % factor == factor - 1) output[index / factor] = value
    }
    return ceil(4.0 / (cutoff * factor)).toInt()
}

private class ChimeNotch(frequency: Double, sampleRate: Int, initial: Double) {
    // RBJ notch, Q=1: https://www.w3.org/TR/audio-eq-cookbook/#notch
    private val omega = 2.0 * PI * frequency / sampleRate
    private val alpha = sin(omega) / 2.0
    private val b0 = 1.0 / (1.0 + alpha)
    private val b1 = -2.0 * cos(omega) / (1.0 + alpha)
    private val a2 = (1.0 - alpha) / (1.0 + alpha)
    private var x1 = initial
    private var x2 = initial
    private var y1 = initial
    private var y2 = initial

    fun process(sample: Double): Double {
        val output = b0 * (sample + x2) + b1 * (x1 - y1) - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = output
        return output
    }
}

private class PitchLowPass(cutoff: Double, q: Double, initial: Double) {
    private val cosine = cos(2.0 * PI * cutoff)
    private val alpha = sin(2.0 * PI * cutoff) / (2.0 * q)
    private val b0 = (1.0 - cosine) / (2.0 * (1.0 + alpha))
    private val b1 = 2.0 * b0
    private val a1 = -2.0 * cosine / (1.0 + alpha)
    private val a2 = (1.0 - alpha) / (1.0 + alpha)
    private var x1 = initial
    private var x2 = initial
    private var y1 = initial
    private var y2 = initial

    fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
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
    private var decimated = DoubleArray(0)
    private var refinementSamples = DoubleArray(0)

    fun analyze(
        samples: ShortArray,
        sampleRate: Int,
        minFrequency: Double,
        maxFrequency: Double,
        rejectConfirmation: Boolean = false,
    ): PitchFrame {
        validateArguments(samples, sampleRate, minFrequency, maxFrequency)
        require(!rejectConfirmation || (maxFrequency < CONFIRMATION_CHIME_HERTZ / 2.0 && sampleRate >= 8_000)) {
            "Confirmation rejection is only safe below the chime band"
        }
        val filterChime = rejectConfirmation && containsConfirmationTone(samples, sampleRate)
        val factor = when {
            samples.size >= LONG_WINDOW_SIZE && samples.size % 4 == 0 && maxFrequency <= LOW_RANGE_MAX_HERTZ -> 4
            samples.size >= LONG_WINDOW_SIZE && samples.size % 2 == 0 && maxFrequency <= sampleRate / 4.0 -> 2
            else -> 1
        }
        val outputSize = samples.size / factor
        if (decimated.size != outputSize) decimated = DoubleArray(outputSize)
        val analysisStart = decimate(samples, decimated, sampleRate, maxFrequency, filterChime)
            .coerceAtMost(outputSize / 8)
        val analysisSamples = decimated
        val analysisSampleRate = sampleRate.toDouble() / factor

        val tauMin = floor(analysisSampleRate / maxFrequency).toInt().coerceAtLeast(MIN_TAU)
        val tauMax = ceil(analysisSampleRate / minFrequency).toInt()
        require(tauMax in 1..((analysisSamples.size - analysisStart - 1) / 2)) {
            "Sample frame is too short for the minimum frequency"
        }

        val rms = calculateRms(analysisSamples, analysisStart)
        val peak = calculatePeak(analysisSamples)
        ensureCapacity(tauMax + 1)
        calculateDifference(analysisSamples, tauMax, analysisStart)
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
        if (candidates.isNotEmpty() && candidates.sumOf(PitchCandidate::probability) < 1.0) {
            val minimum = (tauMin..tauMax).minOf(cumulativeMean::get)
            val periodicity = (1.0 - minimum).coerceIn(0.0, 1.0)
            if (periodicity >= NO_TROUGH_MIN_PERIODICITY) {
                // Zero-probability alternatives can continue an observed pitch, never start a new one.
                val troughs = (tauMin..tauMax).filter { tau ->
                    cumulativeMean[tau] <= minimum + NO_TROUGH_MARGIN &&
                        (tau == tauMin || cumulativeMean[tau] <= cumulativeMean[tau - 1]) &&
                        (tau == tauMax || cumulativeMean[tau] < cumulativeMean[tau + 1])
                }
                for (tau in troughs) {
                    val hertz = analysisSampleRate / parabolicInterpolation(tau, tauMax)
                    if (isWithinRange(hertz, minFrequency, maxFrequency)) {
                        mergeCandidate(candidates, hertz, 1.0 - cumulativeMean[tau], 0.0)
                    }
                }
            }
        }
        val boundedCandidates = candidates.sortedByDescending { it.probability }.take(MAX_CANDIDATES)
        val clearestPeriodicity = boundedCandidates.maxOfOrNull(PitchCandidate::periodicity) ?: 0.0
        val refinedCandidates = boundedCandidates.map { candidate ->
            if (candidate.periodicity + NO_TROUGH_MARGIN < clearestPeriodicity) candidate else candidate.copy(
                hertz = refineFrequency(
                    samples, sampleRate, factor, candidate.hertz, minFrequency, maxFrequency, filterChime,
                ),
            )
        }
        return PitchFrame(
            candidates = refinedCandidates,
            rms = rms,
            peak = peak,
            unvoicedProbability = (1.0 - boundedCandidates.sumOf { it.probability }).coerceIn(0.0, 1.0),
        )
    }

    private fun containsConfirmationTone(samples: ShortArray, sampleRate: Int): Boolean {
        val blockSize = sampleRate / 50
        for (frequency in CHIME_FREQUENCIES) {
            val coefficient = 2.0 * cos(2.0 * PI * frequency / sampleRate)
            for (start in samples.indices step blockSize) {
                val end = minOf(samples.size, start + blockSize)
                var previous = 0.0
                var beforePrevious = 0.0
                var energy = 0.0
                for (index in start until end) {
                    val value = samples[index].toDouble()
                    val current = value + coefficient * previous - beforePrevious
                    beforePrevious = previous
                    previous = current
                    energy += value * value
                }
                val toneEnergy = previous * previous + beforePrevious * beforePrevious -
                    coefficient * previous * beforePrevious
                if (energy > 0.0 && 2.0 * toneEnergy > MIN_CHIME_ENERGY_RATIO * (end - start) * energy) return true
            }
        }
        return false
    }

    private fun refineFrequency(
        samples: ShortArray,
        sampleRate: Int,
        factor: Int,
        hertz: Double,
        minFrequency: Double,
        maxFrequency: Double,
        rejectConfirmation: Boolean,
    ): Double {
        val lower = maxOf(minFrequency, hertz / REFINEMENT_RATIO)
        val upper = minOf(maxFrequency, hertz * REFINEMENT_RATIO)
        val rate = sampleRate.toDouble() / factor
        val firstLag = floor(rate / upper).toInt().coerceAtLeast(MIN_TAU)
        val lastLag = ceil(rate / lower).toInt()
        val outputSize = samples.size / factor
        if (refinementSamples.size != outputSize) refinementSamples = DoubleArray(outputSize)
        val start = decimate(samples, refinementSamples, sampleRate, upper, rejectConfirmation)
        // Do not trade low-bass precision for an unsettled narrow filter or too few complete periods.
        if (start > outputSize / 8 || lastLag + 1 > (outputSize - start - 1) / 2) return hertz
        ensureCapacity(lastLag + 2)
        calculateDifference(refinementSamples, lastLag + 1, start, firstLag - 1)
        val lag = (firstLag..lastLag).minBy(difference::get)
        if (lag == firstLag || lag == lastLag) return hertz
        val refined = rate / parabolicInterpolation(lag, lastLag + 1)
        return if (refined in lower..upper) refined else hertz
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

    private fun calculateDifference(samples: DoubleArray, tauMax: Int, analysisStart: Int, tauStart: Int = 1) {
        difference[0] = 0.0
        val analysisLength = samples.size - tauMax
        for (tau in tauStart..tauMax) {
            var sum = 0.0
            for (index in analysisStart until analysisLength) {
                val delta = samples[index] - samples[index + tau]
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

    private fun calculateRms(samples: DoubleArray, start: Int): Double {
        var sum = 0.0
        for (index in start until samples.size) {
            val normalized = samples[index] / PCM_SCALE
            sum += normalized * normalized
        }
        return sqrt(sum / (samples.size - start))
    }

    private fun calculatePeak(samples: DoubleArray): Double =
        samples.maxOf { abs(it) / PCM_SCALE }.coerceAtMost(1.0)

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
        // Only during our playback window: bypass rejection when the chime is absent or negligible.
        const val MIN_CHIME_ENERGY_RATIO = 0.05
        const val LONG_WINDOW_SIZE = 8192
        const val LOW_RANGE_MAX_HERTZ = 150.0
        val REFINEMENT_RATIO = 2.0.pow(1.0 / 12.0)
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
