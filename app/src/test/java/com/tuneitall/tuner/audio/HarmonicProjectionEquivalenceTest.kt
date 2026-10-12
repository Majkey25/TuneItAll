package com.tuneitall.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HarmonicProjectionEquivalenceTest {
    private val projections = Class.forName("com.tuneitall.tuner.audio.HarmonicPitchRefinementKt")
        .getDeclaredMethod(
            "harmonicProjections", DoubleArray::class.java, Integer.TYPE, java.lang.Double.TYPE,
            java.lang.Double.TYPE, Integer.TYPE,
        ).apply { isAccessible = true }

    @Test
    fun `projections match direct sums for quiet offset short and endpoint frames`() {
        for (count in listOf(0, 1, 2, 7, 64, 1024, 4096, 8192)) {
            for (start in listOf(0, 1, 17)) {
                for (signal in 0..5) {
                    val samples = samples(count, start, signal)
                    var mean = 0.0
                    for (index in start until samples.size) mean += samples[index]
                    if (count > 0) mean /= count
                    var absoluteSum = 0.0
                    for (index in start until samples.size) absoluteSum += abs(samples[index] - mean)
                    val tolerance = 1e-8 * maxOf(java.lang.Double.MIN_NORMAL, absoluteSum)
                    for (harmonics in listOf(1, 3, 16)) {
                        for (partial in listOf(
                            0.0, 1e-12, 1e-8, 1e-5, 27.5 / 48_000.0, 82.4069 / 12_000.0,
                            146.8324 / 24_000.0, 0.0712317, 0.125113, 0.249997, 0.49999,
                            0.499999999999, 0.5,
                        )) {
                            val frequency = partial / harmonics
                            val actual = projections.invoke(null, samples, start, mean, frequency, harmonics) as DoubleArray
                            val expected = directProjections(samples, start, mean, frequency, harmonics)
                            assertEquals(expected.size, actual.size)
                            for (index in expected.indices) {
                                assertEquals(expected[index], actual[index], tolerance,
                                    "count=$count start=$start signal=$signal frequency=$frequency harmonics=$harmonics component=$index")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun directProjections(
        samples: DoubleArray, start: Int, mean: Double, frequency: Double, harmonics: Int,
    ): DoubleArray {
        val result = DoubleArray(2 * harmonics)
        val omega = 2.0 * PI * frequency
        for (harmonic in 1..harmonics) {
            for (index in start until samples.size) {
                val angle = (harmonic * omega) * (index - start)
                val value = samples[index] - mean
                result[2 * (harmonic - 1)] += value * cos(angle)
                result[2 * (harmonic - 1) + 1] += value * sin(angle)
            }
        }
        return result
    }

    private fun samples(count: Int, start: Int, signal: Int): DoubleArray {
        val random = Random(211)
        return DoubleArray(count + start) { offset ->
            val index = offset - start
            if (index < 0) 100_000.0 else when (signal) {
                0 -> 32767.0
                1 -> 12.0 + random.nextDouble(-1.0, 1.0)
                2 -> if (index == count - 1) 1.0 else 0.0
                3 -> 12.0 + 0.0001 * exp(-index / 1400.0) * (sin(index * 0.043 + 0.7) +
                    0.45 * sin(index * 0.086 + 0.4) + 0.2 * sin(index * 0.129 - 0.3))
                4 -> 128.0 + sin(index * 0.0712317 + 0.7) + 0.45 * sin(index * 0.1447311 + 0.4) +
                    0.2 * sin(index * 0.2181733 - 0.3)
                else -> if (index % 2 == 0) 1.0 else -1.0
            }
        }
    }
}
