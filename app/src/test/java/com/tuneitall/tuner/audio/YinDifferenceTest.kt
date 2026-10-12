package com.tuneitall.tuner.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class YinDifferenceTest {
    @Test
    fun `FFT retains the fixed reference at every lag across quiet signals and buffer resizing`() {
        val calculator = YinDifference()
        val random = Random(31415)
        for (count in listOf(63, 4096, 2048, 4097, 128, 8192, 63)) {
            val start = count / 8
            val tauMax = (count - start - 1) / 2
            val signals = listOf(
                DoubleArray(count),
                DoubleArray(count) { 32767.0 },
                DoubleArray(count) { random.nextDouble(-32768.0, 32767.0) },
                DoubleArray(count) { 32767.0 + 0.001 * sin(2.0 * PI * it / 17.0) },
                DoubleArray(count) { 1e-10 * sin(2.0 * PI * it / 13.0) },
                DoubleArray(count).also { it[it.lastIndex] = 1.0 },
            )
            for (samples in signals) {
                val expected = DoubleArray(tauMax + 1) { tau ->
                    var sum = 0.0
                    for (index in start until count - tauMax) {
                        val delta = samples[index] - samples[index + tau]
                        sum += delta * delta
                    }
                    sum
                }
                val actual = DoubleArray(tauMax + 1)
                calculator.calculate(samples, tauMax, start, actual)
                val tolerance = expected.max() * 1e-10
                for (tau in actual.indices) {
                    assertEquals(expected[tau], actual[tau], tolerance, "count=$count start=$start tau=$tau")
                }
            }
        }
    }
}
