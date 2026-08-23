package com.tuneitall.tuner.audio

import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MetronomePlayerTest {
    @Test
    fun `click mixing saturates PCM16 and clips to the target buffer`() {
        val saturated = shortArrayOf(32_000, -32_000, 100)
        mixClick(saturated, shortArrayOf(10_000, -10_000, -500), targetOffset = 0, volume = 100)
        assertContentEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, -400), saturated)

        val clipped = ShortArray(2)
        mixClick(clipped, shortArrayOf(100, 200, 300), targetOffset = -1, volume = 50)
        assertContentEquals(shortArrayOf(100, 150), clipped)
    }

    @Test
    fun `stream stop fades immediately for ten milliseconds then stays silent`() {
        val buffer = ShortArray(1_024) { 12_000 }

        applyStreamStopFade(buffer, fadeFrames = 480)

        assertEquals(12_000, buffer.first().toInt())
        assertEquals(0, buffer[479].toInt())
        assertTrue(buffer.copyOfRange(480, buffer.size).all { it == 0.toShort() })
    }

    @Test
    fun `repeated start keeps one output session and releases it once`() {
        val output = FakeOutput(writeDelayMillis = 1L)
        val creations = AtomicInteger()
        val player = testPlayer(output, onCreate = { creations.incrementAndGet() })

        player.start(MetronomeSettings())
        player.start(MetronomeSettings(bpm = Bpm(137)))
        assertEquals(MetronomeStopResult.STOPPED, player.stop())

        assertEquals(1, creations.get())
        assertEquals(1, output.playCount.get())
        assertEquals(1, output.stopCount.get())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `startup timeout retains live stopping worker and rejects restart`() {
        val priorityEntered = CountDownLatch(1)
        val allowPriority = CountDownLatch(1)
        val output = FakeOutput()
        val player = testPlayer(
            output,
            startupTimeoutMillis = 20L,
            stopTimeoutMillis = 200L,
            fadeDrainTimeoutMillis = 10L,
            setPriority = {
                priorityEntered.countDown()
                allowPriority.await()
            },
        )

        val startedAt = System.nanoTime()
        assertFailsWith<IllegalStateException> { player.start(MetronomeSettings()) }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue(elapsedMillis < 100L, "elapsedMillis=$elapsedMillis")
        assertTrue(priorityEntered.await(1, TimeUnit.SECONDS))
        assertTrue(player.status.running)
        assertTrue(player.status.stopping)
        assertFailsWith<IllegalStateException> { player.start(MetronomeSettings()) }

        allowPriority.countDown()
        assertTrue(waitUntil { !player.status.running })
        assertNotNull(player.status.failure)
        assertEquals(MetronomeStopResult.FAILED, player.stop())
        assertEquals(0, output.playCount.get())
        assertEquals(0, output.stopCount.get())
        assertEquals(0, output.releaseCount.get())
    }

    @Test
    fun `concurrent stop is not blocked by startup wait`() {
        val priorityEntered = CountDownLatch(1)
        val allowPriority = CountDownLatch(1)
        val player = testPlayer(
            FakeOutput(),
            startupTimeoutMillis = 500L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
            setPriority = {
                priorityEntered.countDown()
                allowPriority.await()
            },
        )
        val startFailure = AtomicReference<Throwable?>()
        val starter = Thread {
            try {
                player.start(MetronomeSettings())
            } catch (error: Throwable) {
                startFailure.set(error)
            }
        }
        starter.start()
        assertTrue(priorityEntered.await(1, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        assertEquals(MetronomeStopResult.STOPPING, player.stop())
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue(elapsedMillis < 150L, "elapsedMillis=$elapsedMillis")

        allowPriority.countDown()
        starter.join(1_000L)
        assertFalse(starter.isAlive)
        assertEquals(MetronomeStopResult.STOPPED, player.stop())
    }

    @Test
    fun `stop winning pre create claim prevents output creation`() {
        val atPreCreate = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val output = FakeOutput()
        val creations = AtomicInteger()
        val player = testPlayer(
            output,
            startupTimeoutMillis = 500L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
            onCreate = { creations.incrementAndGet() },
            beforeCreate = {
                atPreCreate.countDown()
                allowCreate.await()
            },
        )
        val startFailure = AtomicReference<Throwable?>()
        val starter = Thread {
            try {
                player.start(MetronomeSettings())
            } catch (error: Throwable) {
                startFailure.set(error)
            }
        }
        starter.start()
        assertTrue(atPreCreate.await(1, TimeUnit.SECONDS))

        assertEquals(MetronomeStopResult.STOPPING, player.stop())
        allowCreate.countDown()
        starter.join(1_000L)

        assertFalse(starter.isAlive)
        assertNotNull(startFailure.get())
        assertEquals(0, creations.get())
        assertEquals(0, output.playCount.get())
        assertEquals(0, output.releaseCount.get())
    }

    @Test
    fun `cancellation winning pre session claim prevents worker reservation`() {
        val atSessionClaim = CountDownLatch(1)
        val allowSessionClaim = CountDownLatch(1)
        val cancelled = AtomicBoolean()
        val output = FakeOutput()
        val creations = AtomicInteger()
        val player = testPlayer(
            output,
            onCreate = { creations.incrementAndGet() },
            beforeSessionClaim = {
                atSessionClaim.countDown()
                allowSessionClaim.await()
            },
        )
        val startFailure = AtomicReference<Throwable?>()
        val starter = Thread {
            try {
                player.start(MetronomeSettings()) { cancelled.get() }
            } catch (error: Throwable) {
                startFailure.set(error)
            }
        }

        starter.start()
        assertTrue(atSessionClaim.await(1, TimeUnit.SECONDS))
        cancelled.set(true)
        allowSessionClaim.countDown()
        starter.join(1_000L)

        assertFalse(starter.isAlive)
        assertNotNull(startFailure.get())
        assertFalse(player.status.running)
        assertEquals(0, creations.get())
        assertEquals(0, output.playCount.get())
    }

    @Test
    fun `stop winning pre play guard prevents native play`() {
        val atPrePlay = CountDownLatch(1)
        val allowPlay = CountDownLatch(1)
        val output = FakeOutput()
        val creations = AtomicInteger()
        val player = testPlayer(
            output,
            startupTimeoutMillis = 500L,
            stopTimeoutMillis = 20L,
            fadeDrainTimeoutMillis = 10L,
            onCreate = { creations.incrementAndGet() },
            beforePlay = {
                atPrePlay.countDown()
                allowPlay.await()
            },
        )
        val startFailure = AtomicReference<Throwable?>()
        val starter = Thread {
            try {
                player.start(MetronomeSettings())
            } catch (error: Throwable) {
                startFailure.set(error)
            }
        }
        starter.start()
        assertTrue(atPrePlay.await(1, TimeUnit.SECONDS))

        assertEquals(MetronomeStopResult.STOPPING, player.stop())
        allowPlay.countDown()
        starter.join(1_000L)

        assertFalse(starter.isAlive)
        assertNotNull(startFailure.get())
        assertEquals(1, creations.get())
        assertEquals(0, output.playCount.get())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `stop timeout retains ownership until blocked write exits`() {
        val output = FakeOutput(blockWriteCall = 2)
        val player = testPlayer(output, stopTimeoutMillis = 20L, fadeDrainTimeoutMillis = 10L)
        player.start(MetronomeSettings())
        assertTrue(output.writeBlocked.await(1, TimeUnit.SECONDS))

        assertEquals(MetronomeStopResult.STOPPING, player.stop())
        assertTrue(player.status.running)
        assertTrue(player.status.stopping)
        assertFailsWith<IllegalStateException> { player.start(MetronomeSettings()) }

        output.allowWrite.countDown()
        assertTrue(output.released.await(1, TimeUnit.SECONDS))
        assertEquals(MetronomeStopResult.STOPPED, player.stop())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `request stop returns immediately and worker retains ownership until exit`() {
        val output = FakeOutput(blockWriteCall = 2)
        val player = testPlayer(output, stopTimeoutMillis = 500L, fadeDrainTimeoutMillis = 10L)
        player.start(MetronomeSettings())
        assertTrue(output.writeBlocked.await(1, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        player.requestStop()
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertTrue(elapsedMillis < 150L, "elapsedMillis=$elapsedMillis")
        assertTrue(player.status.running)
        assertTrue(player.status.stopping)
        assertEquals(0, output.releaseCount.get())

        output.allowWrite.countDown()
        assertTrue(output.released.await(1, TimeUnit.SECONDS))
        assertTrue(waitUntil { !player.status.running })
        assertFalse(player.status.running)
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `final fade reaches playback head before stop and release`() {
        val output = FakeOutput(blockWriteCall = 2, holdPlaybackWriteCall = 3)
        val player = testPlayer(output, stopTimeoutMillis = 500L, fadeDrainTimeoutMillis = 400L)
        player.start(MetronomeSettings())
        assertTrue(output.writeBlocked.await(1, TimeUnit.SECONDS))
        val result = AtomicReference<MetronomeStopResult>()
        val stopper = Thread { result.set(player.stop()) }

        stopper.start()
        assertTrue(waitUntil { player.status.stopping })
        output.allowWrite.countDown()
        assertTrue(output.fadeWritten.await(1, TimeUnit.SECONDS))
        assertEquals(0, output.stopCount.get())
        assertEquals(0, output.releaseCount.get())
        assertTrue(output.heldWriteWasFade)
        assertTrue(stopper.isAlive)

        output.renderFade()
        stopper.join(1_000L)
        assertFalse(stopper.isAlive)
        assertEquals(MetronomeStopResult.STOPPED, result.get(), player.status.failure)
        assertEquals(1, output.stopCount.get())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `fade drain timeout is reported after one release`() {
        val output = FakeOutput(blockWriteCall = 2, holdPlaybackWriteCall = 3)
        val player = testPlayer(output, stopTimeoutMillis = 100L, fadeDrainTimeoutMillis = 20L)
        player.start(MetronomeSettings())
        assertTrue(output.writeBlocked.await(1, TimeUnit.SECONDS))
        val result = AtomicReference<MetronomeStopResult>()
        val stopper = Thread { result.set(player.stop()) }

        stopper.start()
        assertTrue(waitUntil { player.status.stopping })
        output.allowWrite.countDown()
        stopper.join(1_000L)

        assertEquals(MetronomeStopResult.FAILED, result.get())
        assertTrue(player.status.failure?.contains("fade") == true)
        assertEquals(1, output.stopCount.get())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `worker failure is reported and releases output once`() {
        val output = FakeOutput(blockWriteCall = 2, failWriteCall = 2)
        val player = testPlayer(output)
        player.start(MetronomeSettings())

        assertTrue(output.writeBlocked.await(1, TimeUnit.SECONDS))
        output.allowWrite.countDown()
        assertTrue(output.released.await(1, TimeUnit.SECONDS))
        assertNotNull(player.status.failure)
        assertEquals(MetronomeStopResult.FAILED, player.stop())
        assertEquals(1, output.releaseCount.get())
    }

    @Test
    fun `stream update starts at next main beat`() {
        val stream = MetronomeStream(MetronomeSettings(bpm = Bpm(120), subdivision = 1))
        val rendered = ShortArray(40_960)
        val buffer = ShortArray(1_024)

        stream.render(buffer).also { buffer.copyInto(rendered, it.toInt()) }
        stream.update(MetronomeSettings(bpm = Bpm(120), subdivision = 2))
        repeat(39) {
            stream.render(buffer).also { start -> buffer.copyInto(rendered, start.toInt()) }
        }

        assertTrue(rendered.copyOfRange(12_000, 12_500).all { it == 0.toShort() })
        assertTrue(rendered.copyOfRange(24_000, 24_500).any { it != 0.toShort() })
        assertTrue(rendered.copyOfRange(36_000, 36_500).any { it != 0.toShort() })
    }

    @Test
    fun `stream phase reaches alternating endpoints on audible main beats`() {
        val stream = MetronomeStream(MetronomeSettings(bpm = Bpm(400)))
        val buffer = ShortArray(1_024)

        repeat(8) { stream.render(buffer) }

        assertEquals(-1.0, stream.phaseAt(0), 0.0)
        assertEquals(1.0, stream.phaseAt(7_200), 0.0)
    }

    @Test
    fun `playback frame counter unwraps once under concurrent reads`() {
        val counter = PlaybackFrameCounter()
        assertEquals(0xffff_fffeL, counter.update(-2))
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val first = Thread {
            ready.countDown()
            go.await()
            counter.update(1)
        }
        val second = Thread {
            ready.countDown()
            go.await()
            counter.update(2)
        }
        first.start()
        second.start()
        assertTrue(ready.await(1, TimeUnit.SECONDS))
        go.countDown()
        first.join()
        second.join()

        assertEquals(0x1_0000_0003L, counter.update(3))
    }

    private fun testPlayer(
        output: FakeOutput,
        startupTimeoutMillis: Long = 200L,
        stopTimeoutMillis: Long = 200L,
        fadeDrainTimeoutMillis: Long = 150L,
        setPriority: () -> Unit = {},
        onCreate: () -> Unit = {},
        beforeCreate: () -> Unit = {},
        beforePlay: () -> Unit = {},
        beforeSessionClaim: () -> Unit = {},
    ) = MetronomePlayer(
        outputFactory = {
            onCreate()
            output
        },
        setAudioThreadPriority = setPriority,
        startupTimeoutMillis = startupTimeoutMillis,
        stopTimeoutMillis = stopTimeoutMillis,
        fadeDrainTimeoutMillis = fadeDrainTimeoutMillis,
        beforeCreate = beforeCreate,
        beforePlay = beforePlay,
        beforeSessionClaim = beforeSessionClaim,
    )

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        repeat(100) {
            if (predicate()) return true
            Thread.sleep(5L)
        }
        return predicate()
    }

    private class FakeOutput(
        private val blockWriteCall: Int? = null,
        private val failWriteCall: Int? = null,
        private val holdPlaybackWriteCall: Int? = null,
        private val writeDelayMillis: Long = 0L,
    ) : MetronomeOutput {
        private val framePosition = AtomicLong()
        private val framesWritten = AtomicLong()
        private val writeCount = AtomicInteger()
        private val fadeEndFrame = AtomicLong(-1L)
        val playCount = AtomicInteger()
        val stopCount = AtomicInteger()
        val releaseCount = AtomicInteger()
        val writeBlocked = CountDownLatch(1)
        val allowWrite = CountDownLatch(1)
        val fadeWritten = CountDownLatch(1)
        val released = CountDownLatch(1)
        @Volatile
        var heldWriteWasFade = false
            private set

        override val playbackHeadPosition: Int
            get() = framePosition.get().toInt()

        override fun write(buffer: ShortArray, offset: Int, size: Int): Int {
            val call = writeCount.incrementAndGet()
            if (call == blockWriteCall) {
                writeBlocked.countDown()
                allowWrite.await()
            }
            if (call == failWriteCall) throw IllegalStateException("forced write failure")
            if (writeDelayMillis > 0L) Thread.sleep(writeDelayMillis)
            val copy = buffer.copyOfRange(offset, offset + size)
            val startFrame = framesWritten.getAndAdd(size.toLong())
            if (call == holdPlaybackWriteCall) {
                heldWriteWasFade = size >= 480 &&
                    copy[479] == 0.toShort() &&
                    copy.copyOfRange(480, size).all { it == 0.toShort() }
                fadeEndFrame.set(startFrame + 480L)
                fadeWritten.countDown()
            } else {
                framePosition.set(startFrame + size)
            }
            return size
        }

        override fun play() {
            playCount.incrementAndGet()
        }

        override fun stop() {
            stopCount.incrementAndGet()
        }

        override fun release() {
            releaseCount.incrementAndGet()
            released.countDown()
        }

        fun renderFade() {
            framePosition.set(fadeEndFrame.get())
        }
    }
}
