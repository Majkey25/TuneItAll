package com.tuneitall.tuner.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import androidx.annotation.RequiresPermission
import kotlin.math.max

sealed interface AudioInputError {
    data object PermissionMissing : AudioInputError

    data class InitializationFailed(val reason: String) : AudioInputError

    data class ReadFailed(val errorCode: Int) : AudioInputError
}

internal fun usesInstrumentCaptureEffects(audioSource: Int): Boolean = audioSource == MediaRecorder.AudioSource.MIC

class AudioInput(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var running = false
    private var session: RecorderSession? = null
    private val workerOwnership = AudioWorkerOwnership()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        windowSize: Int,
        source: AudioInputSource,
        onWindow: (ShortArray, Int) -> Unit,
        onStarted: (AudioInputCapabilities) -> Unit,
        onError: (AudioInputError) -> Unit,
    ) {
        require(windowSize in MIN_WINDOW_SIZE..MAX_WINDOW_SIZE) {
            "Window size must be between $MIN_WINDOW_SIZE and $MAX_WINDOW_SIZE"
        }
        if (applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError(AudioInputError.PermissionMissing)
            return
        }

        val nextWorker = Thread(
            { recordLoop(windowSize, source, onWindow, onStarted, onError) },
            THREAD_NAME,
        )
        val accepted = try {
            workerOwnership.start(nextWorker) { running = true }
        } catch (error: RuntimeException) {
            running = false
            onError(AudioInputError.InitializationFailed(error.message ?: error.javaClass.simpleName))
            return
        }
        if (!accepted) {
            onError(AudioInputError.InitializationFailed("Previous microphone worker is still stopping"))
        }
    }

    fun stop() {
        val activeSession: RecorderSession?
        synchronized(lock) {
            running = false
            activeSession = session
        }
        activeSession?.release()
        if (workerOwnership.stopAndJoin(STOP_JOIN_TIMEOUT_MS)) {
            synchronized(lock) {
                if (session === activeSession) session = null
            }
        }
    }

    override fun close() = stop()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createRecorder(windowSize: Int, audioSource: Int): AudioRecord {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            REQUESTED_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferBytes > 0) { "Device rejected the requested microphone format" }
        return AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(REQUESTED_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBufferBytes, windowSize * Short.SIZE_BYTES))
            .build()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordLoop(
        windowSize: Int,
        source: AudioInputSource,
        onWindow: (ShortArray, Int) -> Unit,
        onStarted: (AudioInputCapabilities) -> Unit,
        onError: (AudioInputError) -> Unit,
    ) {
        fun reportPriorityFailure(error: RuntimeException) {
            if (running) {
                onError(
                    AudioInputError.InitializationFailed(
                        "Could not set audio thread priority: ${error.message ?: error.javaClass.simpleName}",
                    ),
                )
            }
        }

        try {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (error: SecurityException) {
                reportPriorityFailure(error)
                return
            } catch (error: IllegalArgumentException) {
                reportPriorityFailure(error)
                return
            }
            val rawAdvertised = supportsRawInput()
            var rawFailed = source == AudioInputSource.RAW && !rawAdvertised
            var lastFailure = "AudioRecord did not initialize"
            for (audioSource in audioSourceAttempts(source, rawAdvertised)) {
                if (!running) return
                val activeSession = try {
                    val recorder = createRecorder(windowSize, audioSource)
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        recorder.release()
                        if (audioSource == MediaRecorder.AudioSource.UNPROCESSED) rawFailed = true
                        continue
                    }
                    RecorderSession(recorder, createInstrumentCaptureEffects(recorder, audioSource))
                } catch (_: SecurityException) {
                    if (running) onError(AudioInputError.PermissionMissing)
                    return
                } catch (error: RuntimeException) {
                    lastFailure = error.message ?: error.javaClass.simpleName
                    if (audioSource == MediaRecorder.AudioSource.UNPROCESSED) rawFailed = true
                    continue
                }

                if (!activateSession(activeSession)) {
                    activeSession.release()
                    return
                }
                try {
                    val started = try {
                        activeSession.start()
                    } catch (_: SecurityException) {
                        if (running) onError(AudioInputError.PermissionMissing)
                        return
                    } catch (error: RuntimeException) {
                        lastFailure = error.message ?: error.javaClass.simpleName
                        false
                    }
                    if (!started) {
                        lastFailure = "Microphone did not start recording"
                        if (audioSource == MediaRecorder.AudioSource.UNPROCESSED) rawFailed = true
                        continue
                    }

                    onStarted(
                        AudioInputCapabilities(
                            rawSupported = rawAdvertised && !rawFailed,
                            activeSource = audioInputSource(audioSource),
                        ),
                    )
                    readWindows(activeSession, windowSize, onWindow, onError)
                    return
                } finally {
                    releaseSession(activeSession)
                }
            }
            if (running) onError(AudioInputError.InitializationFailed(lastFailure))
        } finally {
            try {
                synchronized(lock) { session }?.let(::releaseSession)
            } finally {
                workerOwnership.release(Thread.currentThread()) { running = false }
            }
        }
    }

    private fun supportsRawInput(): Boolean = applicationContext.getSystemService(AudioManager::class.java)
        ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        ?.equals("true", ignoreCase = true) == true

    private fun createInstrumentCaptureEffects(recorder: AudioRecord, audioSource: Int): List<AudioEffect> {
        if (!usesInstrumentCaptureEffects(audioSource)) return emptyList()
        val sessionId = recorder.audioSessionId
        return listOfNotNull(
            createEffect(AutomaticGainControl.isAvailable(), true) { AutomaticGainControl.create(sessionId) },
            createEffect(NoiseSuppressor.isAvailable(), false) { NoiseSuppressor.create(sessionId) },
            createEffect(AcousticEchoCanceler.isAvailable(), false) { AcousticEchoCanceler.create(sessionId) },
        )
    }

    private fun createEffect(available: Boolean, enabled: Boolean, create: () -> AudioEffect?): AudioEffect? {
        if (!available) return null
        val effect = runCatching(create).getOrNull() ?: return null
        runCatching { effect.enabled = enabled }
        return effect
    }

    private fun activateSession(nextSession: RecorderSession): Boolean = synchronized(lock) {
        if (!running || session != null) return@synchronized false
        session = nextSession
        true
    }

    private fun releaseSession(activeSession: RecorderSession) {
        activeSession.release()
        synchronized(lock) {
            if (session === activeSession) session = null
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun readWindows(
        activeSession: RecorderSession,
        windowSize: Int,
        onWindow: (ShortArray, Int) -> Unit,
        onError: (AudioInputError) -> Unit,
    ) {
        val recorder = activeSession.recorder
        val readBuffer = ShortArray(max(windowSize / 2, MIN_READ_SIZE))
        val assembler = AudioWindowAssembler(windowSize, minOf(DEFAULT_HOP_SIZE, max(1, windowSize / 2))) { window ->
            onWindow(window, recorder.sampleRate)
        }
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    assembler.append(readBuffer, count)
                } else if (count < 0 && running) {
                    onError(AudioInputError.ReadFailed(count))
                    return
                }
            }
        } catch (_: SecurityException) {
            if (running) onError(AudioInputError.PermissionMissing)
        } catch (error: IllegalStateException) {
            if (running) {
                onError(AudioInputError.InitializationFailed(error.message ?: "Microphone state error"))
            }
        }
    }

    private class RecorderSession(val recorder: AudioRecord, private val effects: List<AudioEffect>) {
        private val lock = Any()
        private var started = false
        private var stopRequested = false
        private var released = false

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun start(): Boolean = synchronized(lock) {
            if (stopRequested || released) return@synchronized false
            recorder.startRecording()
            started = recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            started
        }

        fun stop() {
            synchronized(lock) {
                stopRequested = true
                stopRecorder()
            }
        }

        fun release() {
            synchronized(lock) {
                if (released) return
                stopRequested = true
                stopRecorder()
                effects.forEach { effect -> runCatching(effect::release) }
                recorder.release()
                released = true
            }
        }

        private fun stopRecorder() {
            if (!started) return
            runCatching { recorder.stop() }
            started = false
        }
    }

    private companion object {
        const val REQUESTED_SAMPLE_RATE = 48_000
        const val MIN_WINDOW_SIZE = 256
        const val MAX_WINDOW_SIZE = 32_768
        const val MIN_READ_SIZE = 256
        const val DEFAULT_HOP_SIZE = 2_048
        const val STOP_JOIN_TIMEOUT_MS = 1_000L
        const val THREAD_NAME = "TuneItAll-Audio"
    }
}

internal class AudioWorkerOwnership {
    private val lock = Any()
    private var worker: Thread? = null
    private var stopping = false

    val available: Boolean
        get() = synchronized(lock) { worker == null && !stopping }

    fun start(nextWorker: Thread, onClaim: () -> Unit = {}): Boolean = synchronized(lock) {
        if (worker != null || stopping) return@synchronized false
        worker = nextWorker
        onClaim()
        try {
            nextWorker.start()
        } catch (error: RuntimeException) {
            worker = null
            throw error
        }
        true
    }

    fun stopAndJoin(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0L) { "Worker join timeout must be positive" }
        val activeWorker = synchronized(lock) {
            stopping = true
            worker
        }
        if (activeWorker != null) {
            activeWorker.interrupt()
            try {
                if (activeWorker !== Thread.currentThread()) activeWorker.join(timeoutMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return synchronized(lock) {
            if (worker === activeWorker && activeWorker?.isAlive == false) worker = null
            val stopped = worker == null
            stopping = false
            stopped
        }
    }

    fun release(completedWorker: Thread, onRelease: () -> Unit = {}): Boolean = synchronized(lock) {
        if (worker !== completedWorker) return@synchronized false
        onRelease()
        worker = null
        true
    }
}

internal class AudioWindowAssembler(
    windowSize: Int,
    private val hopSize: Int,
    private val onWindow: (ShortArray) -> Unit,
) {
    private val ring = ShortArray(windowSize)
    private val window = ShortArray(windowSize)
    private var writeIndex = 0
    private var buffered = 0
    private var samplesSinceWindow = 0

    init {
        require(windowSize >= 2) { "Window size must be at least 2" }
        require(hopSize in 1..windowSize) { "Hop size must be between 1 and window size" }
    }

    fun append(samples: ShortArray, count: Int) {
        require(count in 0..samples.size) { "Count must fit the sample buffer" }
        for (index in 0 until count) {
            ring[writeIndex] = samples[index]
            writeIndex = (writeIndex + 1) % ring.size
            if (buffered < ring.size) {
                buffered++
                if (buffered == ring.size) emitWindow()
            } else {
                samplesSinceWindow++
                if (samplesSinceWindow >= hopSize) emitWindow()
            }
        }
    }

    private fun emitWindow() {
        val firstPart = ring.size - writeIndex
        ring.copyInto(window, destinationOffset = 0, startIndex = writeIndex, endIndex = ring.size)
        ring.copyInto(window, destinationOffset = firstPart, startIndex = 0, endIndex = writeIndex)
        samplesSinceWindow = 0
        onWindow(window)
    }
}
