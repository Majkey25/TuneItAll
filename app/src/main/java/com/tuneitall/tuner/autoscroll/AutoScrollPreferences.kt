package com.tuneitall.tuner.autoscroll

import android.content.Context

class AutoScrollPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var speed: Int
        get() = AutoScrollSpeed.clamp(preferences.getInt(KEY_SPEED, AutoScrollSpeed.DEFAULT_LEVEL))
        set(value) {
            preferences.edit().putInt(KEY_SPEED, AutoScrollSpeed.clamp(value)).apply()
        }

    var disclosureAccepted: Boolean
        get() = preferences.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "auto_scroll_settings"
        const val KEY_SPEED = "speed"
        const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"
    }
}
