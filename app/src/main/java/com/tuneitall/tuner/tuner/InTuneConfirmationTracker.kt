package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote

class InTuneConfirmationTracker {
    private var activeTarget: MidiNote? = null
    private var inTuneSince: Long? = null
    private var outOfTuneSince: Long? = null
    private var confirmed = false
    private var lastUpdateMillis: Long? = null

    val isConfirmed: Boolean
        @Synchronized get() = confirmed

    @Synchronized
    fun update(target: MidiNote?, inTune: Boolean, nowMillis: Long, confirmationMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Confirmation time must not be negative" }
        require(confirmationMillis >= 0L) { "Confirmation duration must not be negative" }
        require(lastUpdateMillis == null || nowMillis >= requireNotNull(lastUpdateMillis)) {
            "Confirmation time must not move backwards"
        }
        lastUpdateMillis = nowMillis

        if (target != null && target != activeTarget) {
            clearConfirmation()
            activeTarget = target
        }

        if (target != null && inTune) {
            outOfTuneSince = null
            if (confirmed) return false
            val startedAt = inTuneSince ?: nowMillis.also { inTuneSince = it }
            if (nowMillis - startedAt >= confirmationMillis) {
                confirmed = true
                return true
            }
            return false
        }

        inTuneSince = null
        if (!confirmed) return false
        val startedAt = outOfTuneSince ?: nowMillis.also { outOfTuneSince = it }
        if (nowMillis - startedAt >= REARM_MILLIS) clearConfirmation()
        return false
    }

    @Synchronized
    fun reset() {
        activeTarget = null
        lastUpdateMillis = null
        clearConfirmation()
    }

    private fun clearConfirmation() {
        inTuneSince = null
        outOfTuneSince = null
        confirmed = false
    }

    private companion object {
        const val REARM_MILLIS = 500L
    }
}
