package com.tuneitall.tuner.audio

@JvmInline
value class DetectionSensitivity(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "Detection sensitivity must be between $MIN_VALUE and $MAX_VALUE" }
    }

    internal val minimumRms: Double
        get() = threshold(LOW_SENSITIVITY_RMS, BALANCED_RMS, HIGH_SENSITIVITY_RMS)

    internal val adaptiveNoiseScale: Double
        get() = 1.0 - (MAX_NOISE_REDUCTION * value / MAX_VALUE)

    private fun threshold(low: Double, normal: Double, high: Double): Double = when {
        value < BALANCED_VALUE -> low + (normal - low) * value / BALANCED_VALUE
        value == BALANCED_VALUE -> normal
        else -> normal + (high - normal) * (value - BALANCED_VALUE) / (MAX_VALUE - BALANCED_VALUE)
    }

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 100
        const val DEFAULT_VALUE = 100
        val DEFAULT = DetectionSensitivity(DEFAULT_VALUE)

        private const val BALANCED_VALUE = 50
        private const val LOW_SENSITIVITY_RMS = 0.012
        private const val BALANCED_RMS = 0.003
        private const val HIGH_SENSITIVITY_RMS = 0.00006
        private const val MAX_NOISE_REDUCTION = 1.0
    }
}
