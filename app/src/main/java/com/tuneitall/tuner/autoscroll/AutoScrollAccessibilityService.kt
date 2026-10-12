package com.tuneitall.tuner.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AutoScrollState(
    val running: Boolean = false,
    val speed: Int? = null,
)

class AutoScrollAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var runGeneration = 0L
    private var speedLevel = AutoScrollSpeed.DEFAULT_LEVEL
    private val nextBatch = Runnable(::dispatchNextBatch)

    override fun onServiceConnected() {
        instance = this
        speedLevel = AutoScrollPreferences(this).speed
        publishState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = stop()

    override fun onDestroy() {
        stop()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun start(speed: Int): Boolean {
        stop()
        speedLevel = AutoScrollSpeed.clamp(speed)
        running = true
        publishState()
        return dispatchNextBatch()
    }

    fun stop() {
        runGeneration++
        running = false
        handler.removeCallbacks(nextBatch)
        publishState()
    }

    fun isRunning(): Boolean = running

    private fun dispatchNextBatch(): Boolean {
        if (!running) return false
        val generation = runGeneration
        val profile = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, speedLevel)
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val startY = height * profile.startYFraction
        val endY = profile.endYPixels(height, touchSlop)
        // Cross both ordinary and paging slop before the long-press deadline.
        val durationMs = profile.durationForTravelMs(startY - endY, touchSlop, ViewConfiguration.getLongPressTimeout())
        val path = Path().apply {
            moveTo(width * GESTURE_X_FRACTION, startY)
            lineTo(width * GESTURE_X_FRACTION, endY)
        }
        val builder = GestureDescription.Builder()
        gestureStartTimes(profile, GestureDescription.getMaxStrokeCount()).forEach { startTime ->
            builder.addStroke(GestureDescription.StrokeDescription(path, startTime, durationMs))
        }
        val accepted = dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (generation == runGeneration && running) {
                        handler.postDelayed(nextBatch, profile.gestureDurationMs - durationMs)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (generation == runGeneration) stop()
                }
            },
            null,
        )
        if (!accepted) stop()
        return accepted
    }

    private fun publishState() {
        if (instance === this) mutableState.value = AutoScrollState(running, speedLevel)
    }

    companion object {
        private val mutableState = MutableStateFlow(AutoScrollState())
        val state: StateFlow<AutoScrollState> = mutableState.asStateFlow()

        fun updateSpeed(speed: Int) {
            val level = AutoScrollSpeed.clamp(speed)
            instance?.speedLevel = level
            mutableState.value = mutableState.value.copy(speed = level)
        }

        @Volatile
        var instance: AutoScrollAccessibilityService? = null
            private set
    }
}
