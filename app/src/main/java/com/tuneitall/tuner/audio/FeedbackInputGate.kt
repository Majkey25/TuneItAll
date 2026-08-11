package com.tuneitall.tuner.audio

class FeedbackInputGate {
    @Volatile
    private var suppressedUntilMillis = 0L

    fun accepts(nowMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Feedback time must not be negative" }
        return nowMillis >= suppressedUntilMillis
    }

    @Synchronized
    fun suppress(nowMillis: Long, durationMillis: Long) {
        require(nowMillis >= 0L) { "Feedback time must not be negative" }
        require(durationMillis > 0L) { "Feedback suppression must be positive" }
        suppressedUntilMillis = maxOf(suppressedUntilMillis, Math.addExact(nowMillis, durationMillis))
    }

    @Synchronized
    fun reset() {
        suppressedUntilMillis = 0L
    }
}
