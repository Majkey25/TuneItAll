package com.tuneitall.tuner.tuner

import com.tuneitall.tuner.model.MidiNote

class InTuneConfirmationTracker {
    private var activeTarget: MidiNote? = null
    private var inTuneSince: Long? = null
    private var missingSince: Long? = null
    private var outOfTuneSince: Long? = null
    private var confirmed = false
    private var lastUpdateMillis: Long? = null

    val isConfirmed: Boolean
        @Synchronized get() = confirmed

    @Synchronized
    fun update(target: MidiNote?, inTune: Boolean, nowMillis: Long, confirmationMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Confirmation time must not be negative" }
        require(confirmationMillis >= 0L) { "Confirmation duration must not be negative" }
        val previousUpdateMillis = lastUpdateMillis
        require(previousUpdateMillis == null || nowMillis >= previousUpdateMillis) {
            "Confirmation time must not move backwards"
        }
        if (!confirmed && previousUpdateMillis != null && nowMillis - previousUpdateMillis > MAX_DROPOUT_MILLIS) {
            inTuneSince = null
        }
        lastUpdateMillis = nowMillis

        if (target != null && target != activeTarget) {
            clearConfirmation()
            activeTarget = target
        }

        if (target == null) {
            if (!confirmed) {
                val startedAt = missingSince ?: nowMillis.also { missingSince = it }
                if (nowMillis - startedAt > MAX_DROPOUT_MILLIS) inTuneSince = null
                return false
            }
            val startedAt = outOfTuneSince ?: nowMillis.also { outOfTuneSince = it }
            if (nowMillis - startedAt >= REARM_MILLIS) clearConfirmation()
            return false
        }

        val missingStartedAt = missingSince
        if (missingStartedAt != null) {
            val missingMillis = nowMillis - missingStartedAt
            inTuneSince = if (missingMillis > MAX_DROPOUT_MILLIS) null else inTuneSince?.plus(missingMillis)
        }
        missingSince = null

        if (inTune) {
            val outOfTuneStartedAt = outOfTuneSince
            if (outOfTuneStartedAt != null && nowMillis - outOfTuneStartedAt >= REARM_MILLIS) clearConfirmation()
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
        missingSince = null
        outOfTuneSince = null
        confirmed = false
    }

    private companion object {
        const val MAX_DROPOUT_MILLIS = 200L
        const val REARM_MILLIS = 500L
    }
}
