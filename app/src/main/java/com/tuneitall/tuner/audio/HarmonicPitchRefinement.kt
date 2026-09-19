package com.tuneitall.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

// Real-valued least squares on the current filtered frame, with a fitted DC offset.
internal fun fitHarmonicFrequency(
    samples: DoubleArray, start: Int, sampleRate: Double, lower: Double, upper: Double,
): Double? {
    val count = samples.size - start
    // ponytail: refine three low partials; model inharmonicity before adding higher ones.
    val harmonics = minOf(3, ((sampleRate / 2.0 - 1e-6) / upper).toInt())
    if (count <= 0 || lower >= upper || harmonics < 1) return null
    var mean = 0.0
    for (index in start until samples.size) mean += samples[index]
    mean /= count
    val steps = ceil((upper - lower) * count * harmonics * 2.0 / sampleRate).toInt().coerceAtLeast(4)
    val spacing = (upper - lower) / steps
    fun score(frequency: Double): Double {
        val normalized = frequency / sampleRate
        return harmonicFitEnergy(count, normalized, harmonics, harmonicProjections(samples, start, mean, normalized, harmonics))
    }
    var best = 0
    var bestScore = Double.NEGATIVE_INFINITY
    for (index in 0..steps) {
        val value = score(lower + index * spacing)
        if (value > bestScore) {
            best = index
            bestScore = value
        }
    }
    if (best == 0 || best == steps || !bestScore.isFinite()) return null
    var left = lower + (best - 1) * spacing
    var right = lower + (best + 1) * spacing
    val ratio = 0.6180339887498949
    var first = right - ratio * (right - left)
    var second = left + ratio * (right - left)
    var firstScore = score(first)
    var secondScore = score(second)
    repeat(16) {
        if (firstScore < secondScore) {
            left = first
            first = second
            firstScore = secondScore
            second = left + ratio * (right - left)
            secondScore = score(second)
        } else {
            right = second
            second = first
            secondScore = firstScore
            first = right - ratio * (right - left)
            firstScore = score(first)
        }
    }
    return (left + right) / 2.0
}

internal fun harmonicCandidateWeights(
    samples: DoubleArray, start: Int, sampleRate: Double, maxFrequency: Double, candidates: List<PitchCandidate>,
): DoubleArray? {
    if (candidates.isEmpty()) return null
    val count = samples.size - start
    var mean = 0.0
    for (index in start until samples.size) mean += samples[index]
    mean /= count
    var energy = 0.0
    for (index in start until samples.size) {
        val centered = samples[index] - mean
        energy += centered * centered
    }
    if (energy <= 0.0) return null
    // Account for the coarse low-pass bandwidth when penalizing fitted model order.
    val effectiveCount = count * 2.0 * minOf(0.4, maxFrequency * 2.5 / sampleRate)
    if (effectiveCount <= 1.0) return null
    val bandwidth = minOf(sampleRate * 0.4, maxFrequency * 2.5)
    val criteria = DoubleArray(candidates.size) { index ->
        val frequency = candidates[index].hertz
        // Include strong upper partials throughout the proposal filter's passband.
        // ponytail: cap order at 16; larger chromatic models need a profiled recursive solver.
        val maximumOrder = minOf(16, (bandwidth / frequency).toInt() + 1,
            ((sampleRate / 2.0 - 1e-6) / frequency).toInt())
        // Each lower-order model uses the same projections. Scan the PCM only once per partial.
        val projections = harmonicProjections(samples, start, mean, frequency / sampleRate, maximumOrder)
        (1..maximumOrder).minOfOrNull { order ->
            val fitted = harmonicFitEnergy(count, frequency / sampleRate, order, projections)
            val residual = (energy - fitted).coerceAtLeast(energy * 1e-12)
            effectiveCount * ln(residual / energy) + 2.0 * order * ln(effectiveCount)
        } ?: Double.POSITIVE_INFINITY
    }
    val best = criteria.min()
    if (!best.isFinite()) return null
    // Keep finite hypotheses without confusing underflow with a continuation-only zero weight.
    val weights = DoubleArray(criteria.size) {
        if (criteria[it].isFinite()) exp(-0.5 * (criteria[it] - best)).coerceAtLeast(java.lang.Double.MIN_NORMAL) else 0.0
    }
    val total = weights.sum()
    weights.indices.forEach { weights[it] /= total }
    return weights
}

private fun harmonicProjections(
    samples: DoubleArray, start: Int, mean: Double, frequency: Double, harmonics: Int,
): DoubleArray {
    val omega = 2.0 * PI * frequency
    val projections = DoubleArray(2 * harmonics)
    for (harmonic in 1..harmonics) {
        val cosineStep = cos(harmonic * omega)
        val sineStep = sin(harmonic * omega)
        var cosine = 1.0
        var sine = 0.0
        var cosineProjection = 0.0
        var sineProjection = 0.0
        for (index in start until samples.size) {
            val value = samples[index] - mean
            cosineProjection += value * cosine
            sineProjection += value * sine
            val nextCosine = cosine * cosineStep - sine * sineStep
            sine = sine * cosineStep + cosine * sineStep
            cosine = nextCosine
        }
        projections[2 * (harmonic - 1)] = cosineProjection
        projections[2 * (harmonic - 1) + 1] = sineProjection
    }
    return projections
}

private fun harmonicFitEnergy(count: Int, frequency: Double, harmonics: Int, projections: DoubleArray): Double {
    val omega = 2.0 * PI * frequency
    val cosineSums = DoubleArray(2 * harmonics + 1)
    val sineSums = DoubleArray(cosineSums.size)
    cosineSums[0] = count.toDouble()
    for (harmonic in 1 until cosineSums.size) {
        val halfAngle = harmonic * omega / 2.0
        val scale = sin(count * halfAngle) / sin(halfAngle)
        cosineSums[harmonic] = scale * cos((count - 1) * halfAngle)
        sineSums[harmonic] = scale * sin((count - 1) * halfAngle)
    }
    fun cosineSum(harmonic: Int) = cosineSums[abs(harmonic)]
    fun sineSum(harmonic: Int) = if (harmonic < 0) -sineSums[-harmonic] else sineSums[harmonic]
    val size = 2 * harmonics
    val matrix = Array(size) { row ->
        val first = row / 2 + 1
        DoubleArray(size + 1) { column ->
            if (column == size) projections[row] else {
                val second = column / 2 + 1
                val product = when {
                    row % 2 == 0 && column % 2 == 0 -> (cosineSum(first - second) + cosineSum(first + second)) / 2.0
                    row % 2 == 1 && column % 2 == 1 -> (cosineSum(first - second) - cosineSum(first + second)) / 2.0
                    row % 2 == 0 -> (sineSum(second + first) + sineSum(second - first)) / 2.0
                    else -> (sineSum(first + second) + sineSum(first - second)) / 2.0
                }
                val firstSum = if (row % 2 == 0) cosineSum(first) else sineSum(first)
                val secondSum = if (column % 2 == 0) cosineSum(second) else sineSum(second)
                product - firstSum * secondSum / count
            }
        }
    }
    for (column in 0 until size) {
        val pivot = (column until size).maxBy { abs(matrix[it][column]) }
        if (abs(matrix[pivot][column]) < count * 1e-9) return Double.NEGATIVE_INFINITY
        val swap = matrix[column]
        matrix[column] = matrix[pivot]
        matrix[pivot] = swap
        for (row in column + 1 until size) {
            val scale = matrix[row][column] / matrix[column][column]
            for (index in column..size) matrix[row][index] -= scale * matrix[column][index]
        }
    }
    val coefficients = DoubleArray(size)
    for (row in size - 1 downTo 0) {
        var value = matrix[row][size]
        for (column in row + 1 until size) value -= matrix[row][column] * coefficients[column]
        coefficients[row] = value / matrix[row][row]
    }
    return (0 until size).sumOf { projections[it] * coefficients[it] }
}
