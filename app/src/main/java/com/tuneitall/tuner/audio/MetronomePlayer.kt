package com.tuneitall.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.tuneitall.tuner.metronome.MetronomeSchedule
import com.tuneitall.tuner.metronome.MetronomeSettings
import com.tuneitall.tuner.metronome.MetronomeSound
import com.tuneitall.tuner.metronome.PulseKind
import com.tuneitall.tuner.metronome.applyStopFade
import com.tuneitall.tuner.metronome.createClickBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

enum class MetronomeStopResult {
    STOPPED,
    STOPPING,
    FAILED,
}

internal data class MetronomePlayerStatus(
    val running: Boolean,
    val stopping: Boolean,
    val failure: String?,
)

internal interface MetronomeOutput {
    val playbackHeadPosition: Int

    fun write(buffer: ShortArray, offset: Int, size: Int): Int

    fun play()

    fun stop()

    fun release()
}

class MetronomePlayer internal constructor(
    private val outputFactory: () -> MetronomeOutput = ::createAudioOutput,
    private val setAudioThreadPriority: () -> Unit = {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
    },
    private val startupTimeoutMillis: Long = START_TIMEOUT_MILLIS,
    private val stopTimeoutMillis: Long = STOP_JOIN_TIMEOUT_MILLIS,
    private val fadeDrainTimeoutMillis: Long = FADE_DRAIN_TIMEOUT_MILLIS,
    private val beforeCreate: () -> Unit = {},
    private val beforePlay: () -> Unit = {},
    private val beforeSessionClaim: () -> Unit = {},
) : AutoCloseable {
    private var session: PlaybackSession? = null
    private var lastFailure: String? = null

    init {
        require(startupTimeoutMillis > 0L)
        require(stopTimeoutMillis > 0L)
        require(fadeDrainTimeoutMillis > 0L && fadeDrainTimeoutMillis < stopTimeoutMillis)
    }

    internal val status: MetronomePlayerStatus
        @Synchronized get() {
            val active = session
            return MetronomePlayerStatus(
                running = active?.isAlive == true,
                stopping = active?.stopping == true,
                failure = active?.failure?.message ?: lastFailure,
            )
        }

    val running: Boolean
        get() = status.running

    fun start(settings: MetronomeSettings) = start(settings) { false }

    internal fun start(settings: MetronomeSettings, isCancelled: () -> Boolean) {
        beforeSessionClaim()
        val next = synchronized(this) {
            check(!isCancelled()) { "Metronome playback start was cancelled" }
            session?.let { active ->
                if (active.isAlive) {
                    check(active.acceptsUpdates) { "Previous metronome playback is still stopping" }
                    active.update(settings)
                    return
                }
                lastFailure = active.failure?.message
                session = null
            }

            PlaybackSession(
                initialSettings = settings,
                outputFactory = outputFactory,
                setAudioThreadPriority = setAudioThreadPriority,
                startupTimeoutMillis = startupTimeoutMillis,
                stopTimeoutMillis = stopTimeoutMillis,
                fadeDrainTimeoutMillis = fadeDrainTimeoutMillis,
                beforeCreate = beforeCreate,
                beforePlay = beforePlay,
            ).also {
                session = it
                lastFailure = null
                it.begin()
            }
        }
        try {
            next.awaitStarted()
        } catch (error: RuntimeException) {
            next.requestStop()
            synchronized(this) {
                if (!next.isAlive && session === next) {
                    lastFailure = next.failure?.message
                    session = null
                }
            }
            throw error
        }
    }

    @Synchronized
    fun update(settings: MetronomeSettings) {
        val active = session ?: return
        if (!active.isAlive) {
            lastFailure = active.failure?.message
            session = null
            return
        }
        if (active.acceptsUpdates) active.update(settings)
    }

    fun stop(): MetronomeStopResult {
        val active = synchronized(this) {
            session ?: return if (lastFailure == null) {
                MetronomeStopResult.STOPPED
            } else {
                MetronomeStopResult.FAILED
            }
        }
        active.requestStop()
        val result = active.awaitStop()
        synchronized(this) {
            if (!active.isAlive && session === active) {
                lastFailure = active.failure?.message
                session = null
            }
        }
        return result
    }

    @Synchronized
    fun requestStop() = session?.requestStop() ?: Unit

    @Synchronized
    fun phase(): Double = session?.takeIf { it.isAlive }?.phase() ?: 0.0

    override fun close() {
        stop()
    }
}

internal fun mixClick(
    target: ShortArray,
    click: ShortArray,
    targetOffset: Int,
    volume: Int,
) {
    require(volume in 0..100)
    val clickStart = max(0, -targetOffset)
    val targetStart = max(0, targetOffset)
    val count = min(click.size - clickStart, target.size - targetStart)
    for (index in 0 until max(0, count)) {
        val scaled = click[clickStart + index].toInt() * volume / 100
        target[targetStart + index] = (target[targetStart + index].toInt() + scaled)
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}

internal fun applyStreamStopFade(buffer: ShortArray, fadeFrames: Int) {
    require(fadeFrames in 1..buffer.size)
    val fade = buffer.copyOf(fadeFrames)
    applyStopFade(fade, fadeFrames)
    fade.copyInto(buffer)
    buffer.fill(0, fadeFrames)
}

internal class PlaybackFrameCounter {
    private var lastRaw = 0L
    private var wraps = 0L

    @Synchronized
    fun update(rawPosition: Int): Long {
        val raw = Integer.toUnsignedLong(rawPosition)
        if (raw < lastRaw) {
            if (lastRaw - raw > PLAYBACK_WRAP_THRESHOLD) {
                wraps++
            } else {
                return lastRaw + (wraps shl 32)
            }
        }
        lastRaw = raw
        return raw + (wraps shl 32)
    }
}

internal class MetronomeStream(initialSettings: MetronomeSettings) {
    private val schedule = MetronomeSchedule(initialSettings, SAMPLE_RATE)
    private val clickBuffers = Array(MetronomeSound.entries.size) { sound ->
        Array(PulseKind.entries.size) { kind ->
            createClickBuffer(MetronomeSound.entries[sound], PulseKind.entries[kind], SAMPLE_RATE)
        }
    }
    private val activeClicks = ArrayList<ActiveClick>(4)
    private var settings = initialSettings
    private var scheduledSettings: MetronomeSettings? = null
    private var nextBufferFrame = 0L

    fun update(settings: MetronomeSettings) {
        schedule.update(settings)
        scheduledSettings = settings
    }

    fun render(buffer: ShortArray): Long {
        require(buffer.size == BUFFER_FRAMES)
        val startFrame = nextBufferFrame
        buffer.fill(0)
        activeClicks.removeAll { it.frame + it.samples.size <= startFrame }
        activeClicks.forEach { click ->
            mixClick(buffer, click.samples, (click.frame - startFrame).toInt(), click.volume)
        }
        schedule.pulsesForBuffer(startFrame, buffer.size).forEach { pulse ->
            if (pulse.kind != PulseKind.SUBDIVISION) {
                scheduledSettings?.let {
                    settings = it
                    scheduledSettings = null
                }
            }
            val click = ActiveClick(
                frame = pulse.frame,
                samples = clickBuffers[settings.sound.ordinal][pulse.kind.ordinal],
                volume = settings.volume,
            )
            activeClicks += click
            mixClick(buffer, click.samples, (click.frame - startFrame).toInt(), click.volume)
        }
        nextBufferFrame += buffer.size
        return startFrame
    }

    fun phaseAt(frame: Long): Double = schedule.phaseAt(frame)

    private data class ActiveClick(
        val frame: Long,
        val samples: ShortArray,
        val volume: Int,
    )
}

private class PlaybackSession(
    initialSettings: MetronomeSettings,
    private val outputFactory: () -> MetronomeOutput,
    private val setAudioThreadPriority: () -> Unit,
    private val startupTimeoutMillis: Long,
    private val stopTimeoutMillis: Long,
    private val fadeDrainTimeoutMillis: Long,
    private val beforeCreate: () -> Unit,
    private val beforePlay: () -> Unit,
) {
    private val started = CountDownLatch(1)
    private val streamLock = Any()
    private val stateLock = Any()
    private val stream = MetronomeStream(initialSettings)
    private val playbackFrames = PlaybackFrameCounter()
    private val lifecycle = PlaybackLifecycle()
    private val worker = Thread(::playbackLoop, THREAD_NAME)
    private var pendingSettings: MetronomeSettings? = null

    @Volatile
    private var output: MetronomeOutput? = null

    @Volatile
    var failure: RuntimeException? = null
        private set

    @Volatile
    private var playStarted = false

    val stopping: Boolean
        get() = lifecycle.stopping

    val acceptsUpdates: Boolean
        get() = lifecycle.acceptsUpdates

    val isAlive: Boolean
        get() = worker.isAlive

    fun begin() {
        worker.start()
    }

    fun awaitStarted() {
        val ready = try {
            started.await(startupTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!ready) {
            requestStop()
            val error = IllegalStateException("Metronome output did not start within $startupTimeoutMillis ms")
            failure = error
            throw error
        }
        failure?.let { throw it }
        check(playStarted) { "Metronome playback stopped during startup" }
    }

    fun update(settings: MetronomeSettings) = synchronized(stateLock) {
        if (!stopping) pendingSettings = settings
    }

    fun requestStop() {
        lifecycle.requestStop()
    }

    fun awaitStop(): MetronomeStopResult {
        if (worker !== Thread.currentThread()) {
            try {
                worker.join(stopTimeoutMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return when {
            worker.isAlive -> MetronomeStopResult.STOPPING
            failure != null -> MetronomeStopResult.FAILED
            else -> MetronomeStopResult.STOPPED
        }
    }

    fun phase(): Double = synchronized(streamLock) {
        val activeOutput = output ?: return@synchronized 0.0
        return@synchronized try {
            stream.phaseAt(playbackFrame(activeOutput))
        } catch (_: IllegalArgumentException) {
            0.0
        }
    }

    private fun playbackLoop() {
        var activeOutput: MetronomeOutput? = null
        try {
            setAudioThreadPriority()
            beforeCreate()
            if (!lifecycle.claimCreate()) return
            activeOutput = outputFactory()
            output = activeOutput
            if (!lifecycle.outputReady()) return

            val buffer = ShortArray(BUFFER_FRAMES)
            fillBuffer(buffer)
            writeFully(activeOutput, buffer)
            beforePlay()
            if (!lifecycle.play(activeOutput)) return
            playStarted = true
            started.countDown()

            var fadeStartFrame: Long? = null
            while (!stopping) {
                val startFrame = fillBuffer(buffer)
                if (stopping) {
                    applyStreamStopFade(buffer, STOP_FADE_FRAMES)
                    fadeStartFrame = startFrame
                }
                writeFully(activeOutput, buffer)
            }
            if (fadeStartFrame == null) {
                fadeStartFrame = fillBuffer(buffer)
                applyStreamStopFade(buffer, STOP_FADE_FRAMES)
                writeFully(activeOutput, buffer)
            }
            awaitPlaybackFrame(activeOutput, fadeStartFrame + STOP_FADE_FRAMES)
        } catch (error: RuntimeException) {
            failure = error
            started.countDown()
        } finally {
            lifecycle.terminate()
            output = null
            if (activeOutput != null) {
                try {
                    activeOutput.stop()
                } catch (error: RuntimeException) {
                    if (failure == null) failure = error
                }
                try {
                    activeOutput.release()
                } catch (error: RuntimeException) {
                    if (failure == null) failure = error
                }
            }
            started.countDown()
        }
    }

    private fun fillBuffer(buffer: ShortArray): Long = synchronized(streamLock) {
        synchronized(stateLock) {
            pendingSettings?.let {
                stream.update(it)
                pendingSettings = null
            }
        }
        stream.render(buffer)
    }

    private fun awaitPlaybackFrame(activeOutput: MetronomeOutput, targetFrame: Long) {
        val deadline = System.nanoTime() + fadeDrainTimeoutMillis * NANOS_PER_MILLISECOND
        while (playbackFrame(activeOutput) < targetFrame) {
            if (System.nanoTime() >= deadline) {
                throw IllegalStateException("Metronome fade did not reach playback before timeout")
            }
            try {
                Thread.sleep(1L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Metronome fade wait was interrupted")
            }
        }
    }

    private fun playbackFrame(activeOutput: MetronomeOutput): Long {
        return playbackFrames.update(activeOutput.playbackHeadPosition)
    }
}

private class PlaybackLifecycle {
    private val stage = AtomicReference(PlaybackStage.STARTING)
    private val playLock = Any()

    val stopping: Boolean
        get() = stage.get() == PlaybackStage.STOPPING

    val acceptsUpdates: Boolean
        get() = stage.get() == PlaybackStage.PLAYING

    fun claimCreate(): Boolean = stage.compareAndSet(PlaybackStage.STARTING, PlaybackStage.CREATING)

    fun outputReady(): Boolean = stage.compareAndSet(PlaybackStage.CREATING, PlaybackStage.READY)

    fun play(output: MetronomeOutput): Boolean = synchronized(playLock) {
        if (!stage.compareAndSet(PlaybackStage.READY, PlaybackStage.PLAYING)) return@synchronized false
        output.play()
        true
    }

    fun requestStop() = synchronized(playLock) {
        while (true) {
            val current = stage.get()
            if (current == PlaybackStage.STOPPING || current == PlaybackStage.TERMINATED) return@synchronized
            if (stage.compareAndSet(current, PlaybackStage.STOPPING)) return@synchronized
        }
    }

    fun terminate() {
        stage.set(PlaybackStage.TERMINATED)
    }

    private enum class PlaybackStage {
        STARTING,
        CREATING,
        READY,
        PLAYING,
        STOPPING,
        TERMINATED,
    }
}

private fun createAudioOutput(): MetronomeOutput {
    val minBufferBytes = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    require(minBufferBytes > 0) { "Device rejected the metronome output format" }
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(max(minBufferBytes, BUFFER_FRAMES * Short.SIZE_BYTES * 4))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
        track.release()
        throw IllegalStateException("Metronome output did not initialize")
    }
    return AudioTrackOutput(track)
}

private class AudioTrackOutput(private val track: AudioTrack) : MetronomeOutput {
    override val playbackHeadPosition: Int
        get() = track.playbackHeadPosition

    override fun write(buffer: ShortArray, offset: Int, size: Int): Int =
        track.write(buffer, offset, size, AudioTrack.WRITE_BLOCKING)

    override fun play() = track.play()

    override fun stop() = track.stop()

    override fun release() = track.release()
}

private fun writeFully(output: MetronomeOutput, buffer: ShortArray) {
    var offset = 0
    while (offset < buffer.size) {
        val written = output.write(buffer, offset, buffer.size - offset)
        check(written > 0) { "Metronome write failed: $written" }
        offset += written
    }
}

private const val SAMPLE_RATE = 48_000
private const val BUFFER_FRAMES = 1_024
private const val STOP_FADE_FRAMES = 480
private const val START_TIMEOUT_MILLIS = 1_000L
private const val STOP_JOIN_TIMEOUT_MILLIS = 1_000L
private const val FADE_DRAIN_TIMEOUT_MILLIS = 750L
private const val PLAYBACK_WRAP_THRESHOLD = 0x8000_0000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val THREAD_NAME = "TuneItAll-Metronome"
