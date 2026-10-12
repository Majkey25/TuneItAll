package com.tuneitall.tuner.audio

import com.tuneitall.tuner.music.fft

internal class YinDifference {
    private var real = DoubleArray(0)
    private var imaginary = DoubleArray(0)

    fun calculate(samples: DoubleArray, tauMax: Int, start: Int, difference: DoubleArray) {
        val count = samples.size - start
        val length = count - tauMax
        // The fixed reference ends before tauMax, so these lags cannot wrap around the FFT.
        val size = Integer.highestOneBit(count - 1) shl 1
        if (real.size != size) {
            real = DoubleArray(size)
            imaginary = DoubleArray(size)
        } else {
            real.fill(0.0)
            imaginary.fill(0.0)
        }
        // A common offset preserves every delta without subtracting large DC energies later.
        val offset = samples[start]
        var referenceEnergy = 0.0
        for (index in 0 until count) {
            val value = samples[start + index] - offset
            imaginary[index] = value
            if (index < length) {
                real[index] = value
                referenceEnergy += value * value
            }
        }
        fft(real, imaginary)
        // Recover both real spectra from the packed FFT; conjugation makes the next FFT inverse.
        for (index in 0..size / 2) {
            val mirror = (size - index) % size
            val firstReal = (real[index] + real[mirror]) / 2.0
            val firstImaginary = (imaginary[index] - imaginary[mirror]) / 2.0
            val secondReal = (imaginary[index] + imaginary[mirror]) / 2.0
            val secondImaginary = (real[mirror] - real[index]) / 2.0
            val productReal = firstReal * secondReal + firstImaginary * secondImaginary
            val productImaginary = firstImaginary * secondReal - firstReal * secondImaginary
            real[index] = productReal
            imaginary[index] = productImaginary
            real[mirror] = productReal
            imaginary[mirror] = -productImaginary
        }
        fft(real, imaginary)
        var shiftedEnergy = referenceEnergy
        difference[0] = 0.0
        for (tau in 1..tauMax) {
            val departing = samples[start + tau - 1] - offset
            val entering = samples[start + length + tau - 1] - offset
            shiftedEnergy += entering * entering - departing * departing
            difference[tau] = (referenceEnergy + shiftedEnergy - 2.0 * real[tau] / size).coerceAtLeast(0.0)
        }
    }
}
