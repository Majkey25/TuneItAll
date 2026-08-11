package com.tuneitall.tuner

import android.os.SystemClock
import com.tuneitall.tuner.audio.ConfirmationChimePlayer
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import org.junit.Test

class ReferenceTonePlaybackTest {
    @Test
    fun generatedReferenceAndConfirmationAudioInitialize() {
        val referencePlayer = ReferenceTonePlayer()
        val confirmationPlayer = ConfirmationChimePlayer()
        try {
            referencePlayer.play(440.0)
            referencePlayer.stop()
            confirmationPlayer.play()
            SystemClock.sleep(500)
        } finally {
            referencePlayer.close()
            confirmationPlayer.close()
        }
    }

    @Test
    fun lowMidAndHighGuitarReferenceTonesComplete() {
        val player = ReferenceTonePlayer()
        try {
            listOf(82.4069, 110.0, 329.6276).forEach { hertz ->
                player.play(hertz)
                SystemClock.sleep(1_100)
            }
        } finally {
            player.close()
        }
    }

    @Test
    fun rapidReferenceToneSwitchesComplete() {
        val player = ReferenceTonePlayer()
        try {
            listOf(82.4069, 110.0, 146.8324, 329.6276).forEach { hertz ->
                player.play(hertz)
                SystemClock.sleep(120)
            }
            SystemClock.sleep(1_100)
        } finally {
            player.close()
        }
    }
}
