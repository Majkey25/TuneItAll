package com.tuneitall.tuner.tuner

class TunerReadingRetainer {
    private var lastReading: TunerReading? = null
    private var lastReadingMillis: Long? = null

    @Synchronized
    fun update(reading: TunerReading?, nowMillis: Long): TunerReading? {
        require(nowMillis >= 0L) { "Reading time must not be negative" }
        if (reading != null) {
            lastReading = reading
            lastReadingMillis = nowMillis
            return reading
        }
        val lastSeen = lastReadingMillis ?: return null
        if (nowMillis - lastSeen <= HOLD_MILLIS) return lastReading
        reset()
        return null
    }

    @Synchronized
    fun reset() {
        lastReading = null
        lastReadingMillis = null
    }

    private companion object {
        const val HOLD_MILLIS = 250L
    }
}
