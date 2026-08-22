package com.tuneitall.tuner

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tuneitall.tuner.audio.MetronomePlayer
import com.tuneitall.tuner.audio.MetronomeOutput
import com.tuneitall.tuner.ui.MetronomeViewModel
import com.tuneitall.tuner.ui.TunerViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetronomePlaybackTest {
    @Test
    fun rapidSettingsChangesCompleteInOnePlaybackSession() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MetronomeViewModel(application)

        viewModel.setAccentEvery(2)
        viewModel.start()
        assertTrue(viewModel.uiState.value.playing)

        viewModel.setBpm(137)
        viewModel.setAccentEvery(5)
        viewModel.setMuted(true)
        viewModel.setMuted(false)
        Thread.sleep(600L)
        val stopStarted = SystemClock.elapsedRealtime()
        viewModel.stop()

        assertTrue(SystemClock.elapsedRealtime() - stopStarted <= 1_000L)
        assertEquals(137, viewModel.uiState.value.settings.bpm.value)
        assertEquals(5, viewModel.uiState.value.settings.accentEvery)
        assertFalse(viewModel.uiState.value.muted)
        assertFalse(viewModel.uiState.value.playing)
        assertFalse(viewModel.uiState.value.stopping)
        Thread.sleep(50L)
        assertEquals(null, viewModel.uiState.value.error)
        viewModel.onStop()
    }

    @Test
    fun concurrentSettersDoNotLoseIndependentUpdates() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MetronomeViewModel(application)

        repeat(200) {
            viewModel.setBpm(120)
            viewModel.setVolume(80)
            val ready = CountDownLatch(2)
            val go = CountDownLatch(1)
            val bpm = Thread {
                ready.countDown()
                go.await()
                viewModel.setBpm(137)
            }
            val volume = Thread {
                ready.countDown()
                go.await()
                viewModel.setVolume(33)
            }
            bpm.start()
            volume.start()
            assertTrue(ready.await(1, TimeUnit.SECONDS))
            go.countDown()
            bpm.join()
            volume.join()
            assertEquals(137, viewModel.uiState.value.settings.bpm.value)
            assertEquals(33, viewModel.uiState.value.settings.volume)
        }
    }

    @Test
    fun startupTimeoutMonitorClearsStoppingAfterWorkerExit() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val priorityEntered = CountDownLatch(1)
        val allowPriority = CountDownLatch(1)
        val player = MetronomePlayer(
            outputFactory = { error("stale worker created output") },
            setAudioThreadPriority = {
                priorityEntered.countDown()
                allowPriority.await()
            },
            startupTimeoutMillis = 20L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
        )
        val viewModel = MetronomeViewModel(application, player)

        viewModel.start()
        assertTrue(priorityEntered.await(1, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.value.stopping)
        allowPriority.countDown()

        repeat(100) {
            if (!viewModel.uiState.value.stopping) return@repeat
            Thread.sleep(10L)
        }
        assertFalse(viewModel.uiState.value.stopping)
        assertFalse(viewModel.uiState.value.playing)
    }

    @Test
    fun viewModelLockDoesNotBlockSettersOrConcurrentStopDuringStart() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val priorityEntered = CountDownLatch(1)
        val allowPriority = CountDownLatch(1)
        val player = MetronomePlayer(
            outputFactory = { ImmediateOutput() },
            setAudioThreadPriority = {
                priorityEntered.countDown()
                allowPriority.await()
            },
            startupTimeoutMillis = 500L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
        )
        val viewModel = MetronomeViewModel(application, player)
        val starter = Thread(viewModel::start)
        val setterDone = CountDownLatch(1)
        val stopperDone = CountDownLatch(1)

        starter.start()
        assertTrue(priorityEntered.await(1, TimeUnit.SECONDS))
        Thread {
            viewModel.setVolume(33)
            setterDone.countDown()
        }.start()
        Thread {
            viewModel.stop()
            stopperDone.countDown()
        }.start()

        try {
            assertTrue(setterDone.await(150L, TimeUnit.MILLISECONDS))
            assertTrue(stopperDone.await(150L, TimeUnit.MILLISECONDS))
            assertEquals(33, viewModel.uiState.value.settings.volume)
        } finally {
            allowPriority.countDown()
            starter.join(1_000L)
        }
        assertFalse(starter.isAlive)
        assertFalse(viewModel.uiState.value.playing)
    }

    @Test
    fun stopBeforePlayerStartPreventsStaleStartAndOrphanOutput() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val beforeStart = CountDownLatch(1)
        val allowStart = CountDownLatch(1)
        val creations = AtomicInteger()
        val output = ImmediateOutput()
        val player = MetronomePlayer(
            outputFactory = {
                creations.incrementAndGet()
                output
            },
            setAudioThreadPriority = {},
            startupTimeoutMillis = 200L,
            stopTimeoutMillis = 50L,
            fadeDrainTimeoutMillis = 20L,
            beforeSessionClaim = {
                beforeStart.countDown()
                allowStart.await()
            },
        )
        val viewModel = MetronomeViewModel(application, player)
        val starter = Thread(viewModel::start)

        starter.start()
        assertTrue(beforeStart.await(1, TimeUnit.SECONDS))
        viewModel.stop()
        viewModel.start()
        assertEquals(0, creations.get())

        allowStart.countDown()
        starter.join(1_000L)
        assertFalse(starter.isAlive)
        assertFalse(player.status.running)
        assertFalse(viewModel.uiState.value.playing)
        assertEquals(0, creations.get())
        assertEquals(0, output.playCount.get())
        assertEquals(0, output.releaseCount.get())
    }

    @Test
    fun asyncStartThenImmediateStopCancelsBeforePlayerSessionClaim() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val dispatcherEntered = CountDownLatch(1)
        val releaseDispatcher = CountDownLatch(1)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val creations = AtomicInteger()
        val output = ImmediateOutput()
        val player = MetronomePlayer(
            outputFactory = {
                creations.incrementAndGet()
                output
            },
            setAudioThreadPriority = {},
        )
        dispatcher.executor.execute {
            dispatcherEntered.countDown()
            releaseDispatcher.await()
        }
        val viewModel = MetronomeViewModel(application, player, dispatcher)

        try {
            assertTrue(dispatcherEntered.await(1, TimeUnit.SECONDS))
            viewModel.startAsync()
            assertTrue(viewModel.uiState.value.starting)

            viewModel.stopAsync()
            assertFalse(viewModel.uiState.value.starting)
            assertFalse(viewModel.uiState.value.playing)
            assertTrue(viewModel.uiState.value.stopping)
            assertEquals(0, creations.get())

            releaseDispatcher.countDown()
            val deadline = SystemClock.elapsedRealtime() + 1_000L
            while (
                (viewModel.uiState.value.starting ||
                    viewModel.uiState.value.playing ||
                    viewModel.uiState.value.stopping) &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                Thread.sleep(10L)
            }

            assertFalse(viewModel.uiState.value.starting)
            assertFalse(viewModel.uiState.value.playing)
            assertFalse(viewModel.uiState.value.stopping)
            assertEquals(0, creations.get())
            assertEquals(0, output.playCount.get())
            assertEquals(0, output.releaseCount.get())
        } finally {
            releaseDispatcher.countDown()
            dispatcher.close()
        }
    }

    @Test
    fun stopMonitorWaitsForLateWorkerExitAndAllowsRestart() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val priorityEntered = CountDownLatch(1)
        val allowPriority = CountDownLatch(1)
        val priorityCalls = AtomicInteger()
        val creations = AtomicInteger()
        val player = MetronomePlayer(
            outputFactory = {
                creations.incrementAndGet()
                ImmediateOutput()
            },
            setAudioThreadPriority = {
                if (priorityCalls.incrementAndGet() == 1) {
                    priorityEntered.countDown()
                    allowPriority.await()
                }
            },
            startupTimeoutMillis = 20L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
        )
        val viewModel = MetronomeViewModel(application, player)

        viewModel.start()
        assertTrue(priorityEntered.await(1, TimeUnit.SECONDS))
        assertTrue(viewModel.uiState.value.stopping)
        Thread.sleep(2_000L)
        allowPriority.countDown()

        repeat(100) {
            if (!viewModel.uiState.value.stopping) return@repeat
            Thread.sleep(10L)
        }
        assertFalse(viewModel.uiState.value.stopping)
        assertEquals(0, creations.get())

        viewModel.start()
        assertTrue(viewModel.uiState.value.playing)
        assertEquals(1, creations.get())
        viewModel.stop()
        assertFalse(viewModel.uiState.value.playing)
    }

    @Test
    fun leavingTunerClosesMicrophoneOwnership() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TunerViewModel(application)

        viewModel.onPermissionResult(granted = true, permanentlyDenied = false)
        viewModel.onStart()
        assertTrue(viewModel.uiState.value.listening)

        viewModel.setTunerActive(false)

        assertFalse(viewModel.uiState.value.listening)
        viewModel.onStop()
    }

    private class ImmediateOutput : MetronomeOutput {
        private val frame = AtomicLong()
        val playCount = AtomicInteger()
        val releaseCount = AtomicInteger()

        override val playbackHeadPosition: Int
            get() = frame.get().toInt()

        override fun write(buffer: ShortArray, offset: Int, size: Int): Int {
            frame.addAndGet(size.toLong())
            Thread.sleep(1L)
            return size
        }

        override fun play() {
            playCount.incrementAndGet()
        }

        override fun stop() = Unit

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }
}
