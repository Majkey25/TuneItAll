package com.tuneitall.tuner.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AutoScrollAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var speedLevel = AutoScrollSpeed.DEFAULT_LEVEL
    private val nextBatch = Runnable(::dispatchNextBatch)

    override fun onServiceConnected() {
        instance = this
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
        return dispatchNextBatch()
    }

    fun setSpeed(speed: Int) {
        speedLevel = AutoScrollSpeed.clamp(speed)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(nextBatch)
    }

    fun isRunning(): Boolean = running

    private fun dispatchNextBatch(): Boolean {
        if (!running) return false
        val profile = AutoScrollGestureProfileFactory.create(AutoScrollSettings.defaults, speedLevel)
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * GESTURE_X_FRACTION, height * profile.startYFraction)
            lineTo(width * GESTURE_X_FRACTION, height * profile.endYFraction)
        }
        val builder = GestureDescription.Builder()
        gestureStartTimes(profile, GestureDescription.getMaxStrokeCount()).forEach { startTime ->
            builder.addStroke(GestureDescription.StrokeDescription(path, startTime, profile.gestureDurationMs))
        }
        val accepted = dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (running) handler.post(nextBatch)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    stop()
                }
            },
            null,
        )
        if (!accepted) stop()
        return accepted
    }

    companion object {
        @Volatile
        var instance: AutoScrollAccessibilityService? = null
            private set
    }
}
