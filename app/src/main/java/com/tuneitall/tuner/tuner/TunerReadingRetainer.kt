package com.tuneitall.tuner.tuner

class TunerReadingRetainer {
    private var lastReading: TunerReading? = null
    private var lastReadingMillis: Long? = null

    @Synchronized
    fun update(reading: TunerReading?, nowMillis: Long, holdMillis: Long): TunerReading? {
        require(nowMillis >= 0L) { "Reading time must not be negative" }
        require(holdMillis >= 0L) { "Reading hold duration must not be negative" }
        if (reading != null) {
            lastReading = reading
            lastReadingMillis = nowMillis
            return reading
        }
        val lastSeen = lastReadingMillis ?: return null
        if (holdMillis > 0L && nowMillis - lastSeen <= holdMillis) return lastReading
        reset()
        return null
    }

    @Synchronized
    fun reset() {
        lastReading = null
        lastReadingMillis = null
    }
}
