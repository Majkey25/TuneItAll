package com.tuneitall.tuner.autoscroll

object AutoScrollSpeed {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 30
    const val DEFAULT_LEVEL = 15

    fun clamp(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    fun stepUp(level: Int): Int = clamp(level + 1)

    fun stepDown(level: Int): Int = clamp(level - 1)

    internal fun intervalFactor(level: Int): Float = factor(0.50f, 0.19f, level)

    internal fun distanceFactor(level: Int): Float = factor(0.10f, 0.70f, level)

    internal fun durationFactor(level: Int): Float = factor(2.16f, 1.00f, level)

    private fun factor(start: Float, end: Float, level: Int): Float {
        val progress = (clamp(level) - MIN_LEVEL).toFloat() / (MAX_LEVEL - MIN_LEVEL)
        return start + ((end - start) * progress)
    }
}

data class AutoScrollSettings(
    val distancePercent: Int = 9,
    val intervalMs: Int = 90,
    val gestureDurationMs: Int = 900,
) {
    init {
        require(distancePercent in 6..18) { "Distance must be between 6 and 18 percent" }
        require(intervalMs in 40..260) { "Interval must be between 40 and 260 milliseconds" }
        require(gestureDurationMs in 420..1_400) { "Duration must be between 420 and 1400 milliseconds" }
    }

    companion object {
        val defaults = AutoScrollSettings()
    }
}

data class AutoScrollGestureProfile(
    val intervalMs: Long,
    val gestureDurationMs: Long,
    val startYFraction: Float,
    val endYFraction: Float,
)

object AutoScrollGestureProfileFactory {
    fun create(settings: AutoScrollSettings, speedLevel: Int): AutoScrollGestureProfile {
        val intervalMs = (settings.intervalMs * AutoScrollSpeed.intervalFactor(speedLevel))
            .toLong()
            .coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        val gestureDurationMs = (settings.gestureDurationMs * AutoScrollSpeed.durationFactor(speedLevel))
            .toLong()
            .coerceIn(MIN_GESTURE_DURATION_MS, MAX_GESTURE_DURATION_MS)
        val distanceFraction = (settings.distancePercent / 100f * AutoScrollSpeed.distanceFactor(speedLevel))
            .coerceIn(MIN_DISTANCE_FRACTION, MAX_DISTANCE_FRACTION)

        return AutoScrollGestureProfile(
            intervalMs = intervalMs,
            gestureDurationMs = gestureDurationMs,
            startYFraction = START_Y_FRACTION,
            endYFraction = (START_Y_FRACTION - distanceFraction).coerceAtLeast(MIN_END_Y_FRACTION),
        )
    }
}

fun gestureStartTimes(profile: AutoScrollGestureProfile, platformMaxStrokeCount: Int): List<Long> {
    val stepMs = profile.gestureDurationMs + profile.intervalMs
    val durationLimit = ((MAX_GESTURE_BATCH_MS + profile.intervalMs) / stepMs).toInt().coerceAtLeast(1)
    val count = minOf(platformMaxStrokeCount.coerceAtLeast(1), GESTURES_PER_BATCH, durationLimit)
    return List(count) { index -> index * stepMs }
}

internal const val GESTURE_X_FRACTION = 0.5f
private const val START_Y_FRACTION = 0.72f
private const val MIN_END_Y_FRACTION = 0.28f
private const val MIN_DISTANCE_FRACTION = 0.009f
private const val MAX_DISTANCE_FRACTION = 0.16f
private const val MIN_INTERVAL_MS = 4L
private const val MAX_INTERVAL_MS = 50L
private const val MIN_GESTURE_DURATION_MS = 300L
private const val MAX_GESTURE_DURATION_MS = 2_000L
private const val MAX_GESTURE_BATCH_MS = 2_000L
private const val GESTURES_PER_BATCH = 8
