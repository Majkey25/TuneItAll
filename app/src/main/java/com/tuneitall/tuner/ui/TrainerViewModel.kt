package com.tuneitall.tuner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tuneitall.tuner.storage.TrainerStats
import com.tuneitall.tuner.storage.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrainerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val mutableStats = MutableStateFlow(preferences.trainerStats)
    val stats: StateFlow<TrainerStats> = mutableStats.asStateFlow()

    fun record(correct: Boolean) {
        val current = mutableStats.value
        val base = if (current.attempts == TrainerStats.MAX_ATTEMPTS) {
            TrainerStats(current.correct / 2, current.attempts / 2)
        } else {
            current
        }
        val updated = TrainerStats(base.correct + if (correct) 1 else 0, base.attempts + 1)
        mutableStats.value = updated
        preferences.trainerStats = updated
    }

    fun reset() {
        val empty = TrainerStats()
        mutableStats.value = empty
        preferences.trainerStats = empty
    }
}
