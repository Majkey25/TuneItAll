package com.tuneitall.tuner.audio

class AdaptiveNoiseFloor {
    var value = INITIAL_VALUE
        private set

    fun observe(rms: Double, voiced: Boolean) {
        require(rms.isFinite() && rms >= 0.0) { "RMS must be finite and non-negative" }
        if (voiced) return

        val rate = if (rms > value) RISE_RATE else FALL_RATE
        value += (rms - value) * rate
    }

    fun accepts(rms: Double, absoluteFloor: Double, noiseRejection: Int): Boolean {
        require(rms.isFinite() && rms >= 0.0) { "RMS must be finite and non-negative" }
        require(absoluteFloor.isFinite() && absoluteFloor >= 0.0) {
            "Absolute floor must be finite and non-negative"
        }
        require(noiseRejection in 0..100) { "Noise rejection must be between 0 and 100" }
        return rms >= maxOf(absoluteFloor, value * (1.0 + noiseRejection * NOISE_RATIO_STEP))
    }

    fun reset() {
        value = INITIAL_VALUE
    }

    private companion object {
        const val INITIAL_VALUE = 0.00005
        const val RISE_RATE = 0.005
        const val FALL_RATE = 0.10
        const val NOISE_RATIO_STEP = 0.05
    }
}
