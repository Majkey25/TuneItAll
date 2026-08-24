package com.tuneitall.tuner.autoscroll

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AutoScrollPermissionState {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AutoScrollAccessibilityService::class.java).flattenToString()
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
