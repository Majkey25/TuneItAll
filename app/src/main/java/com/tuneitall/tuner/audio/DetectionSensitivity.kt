package com.tuneitall.tuner.audio

@JvmInline
value class DetectionSensitivity(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "Detection sensitivity must be between $MIN_VALUE and $MAX_VALUE" }
    }

    internal val minimumRms: Double
        get() = threshold(LOW_SENSITIVITY_RMS, DEFAULT_RMS, HIGH_SENSITIVITY_RMS)

    internal val minimumConfidence: Double
        get() = threshold(LOW_SENSITIVITY_CONFIDENCE, DEFAULT_CONFIDENCE, HIGH_SENSITIVITY_CONFIDENCE)

    internal fun yinThreshold(defaultThreshold: Double): Double {
        if (value <= DEFAULT_VALUE) return defaultThreshold
        val highSensitivityThreshold = maxOf(defaultThreshold, 1.0 - HIGH_SENSITIVITY_CONFIDENCE)
        return defaultThreshold +
            (highSensitivityThreshold - defaultThreshold) * (value - DEFAULT_VALUE) / (MAX_VALUE - DEFAULT_VALUE)
    }

    private fun threshold(low: Double, normal: Double, high: Double): Double = when {
        value < DEFAULT_VALUE -> low + (normal - low) * value / DEFAULT_VALUE
        value == DEFAULT_VALUE -> normal
        else -> normal + (high - normal) * (value - DEFAULT_VALUE) / (MAX_VALUE - DEFAULT_VALUE)
    }

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 100
        const val DEFAULT_VALUE = 50
        val DEFAULT = DetectionSensitivity(DEFAULT_VALUE)

        private const val LOW_SENSITIVITY_RMS = 0.012
        private const val DEFAULT_RMS = 0.003
        private const val HIGH_SENSITIVITY_RMS = 0.00075
        private const val LOW_SENSITIVITY_CONFIDENCE = 0.92
        private const val DEFAULT_CONFIDENCE = 0.80
        private const val HIGH_SENSITIVITY_CONFIDENCE = 0.65
    }
}
