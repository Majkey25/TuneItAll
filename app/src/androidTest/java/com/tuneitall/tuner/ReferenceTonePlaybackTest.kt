package com.tuneitall.tuner

import android.os.SystemClock
import com.tuneitall.tuner.audio.ConfirmationChimePlayer
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.music.midiToHertz
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTonePlaybackTest {
    @Test
    fun stopDuringPreparationReturnsBeforeThePendingRequestAndPreventsPlayback() {
        val player = ReferenceTonePlayer()
        val preparing = CountDownLatch(1)
        val continuePreparation = CountDownLatch(1)
        val checks = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                player.play(440.0) {
                    if (checks.incrementAndGet() == 2) {
                        preparing.countDown()
                        assertTrue(continuePreparation.await(3, TimeUnit.SECONDS))
                    }
                    true
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            worker.start()
            assertTrue(preparing.await(3, TimeUnit.SECONDS))
            val stopped = CountDownLatch(1)
            val stopper = Thread { player.stop { stopped.countDown() } }
            stopper.start()
            assertTrue("Stop waited for preparation", stopped.await(500, TimeUnit.MILLISECONDS))
            continuePreparation.countDown()
            worker.join(3_000)
            stopper.join(1_000)
            assertTrue("Preparation worker did not finish", !worker.isAlive)
            assertNull(failure.get())
            val stillStopped = AtomicBoolean(false)
            player.stop { stillStopped.set(true) }
            assertTrue("Stopped request published a track", stillStopped.get())
        } finally {
            continuePreparation.countDown()
            worker.join(3_000)
            player.close()
        }
    }

    @Test
    fun cancelledCallerEnteringAfterStopCannotStartAnyReferenceMode() {
        val player = ReferenceTonePlayer()
        try {
            player.stop()
            player.play(440.0) { false }
            player.playChord(doubleArrayOf(261.63, 329.63, 392.0)) { false }
            player.repeatNotes(261.63, 277.18) { false }
            val stopped = AtomicBoolean(false)
            player.stop { stopped.set(true) }
            assertTrue("Cancelled caller published a track", stopped.get())
        } finally {
            player.close()
        }
    }

    @Test
    fun closeDuringPreparationCannotPublishThePendingTrack() {
        val player = ReferenceTonePlayer()
        val preparing = CountDownLatch(1)
        val continuePreparation = CountDownLatch(1)
        val checks = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                player.play(440.0) {
                    if (checks.incrementAndGet() == 2) {
                        preparing.countDown()
                        assertTrue(continuePreparation.await(3, TimeUnit.SECONDS))
                    }
                    true
                }
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            worker.start()
            assertTrue(preparing.await(3, TimeUnit.SECONDS))
            player.close()
            continuePreparation.countDown()
            worker.join(3_000)
            assertTrue("Preparation worker did not finish", !worker.isAlive)
            assertNull(failure.get())
            val stopped = AtomicBoolean(false)
            player.stop { stopped.set(true) }
            assertTrue("Closed request published a track", stopped.get())
        } finally {
            continuePreparation.countDown()
            worker.join(3_000)
            player.close()
        }
    }

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

    @Test
    fun chordAndSingleNoteTrainerAudioComplete() {
        val player = ReferenceTonePlayer()
        try {
            player.playChord(doubleArrayOf(130.8128, 164.8138, 195.9977, 261.6256, 329.6276))
            SystemClock.sleep(1_100)
            player.play(midiToHertz(60))
            SystemClock.sleep(1_100)
        } finally {
            player.close()
        }
    }
}
