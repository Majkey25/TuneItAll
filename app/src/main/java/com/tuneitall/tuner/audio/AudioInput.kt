package com.tuneitall.tuner.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.annotation.RequiresPermission
import kotlin.math.max

sealed interface AudioInputError {
    data object PermissionMissing : AudioInputError

    data class InitializationFailed(val reason: String) : AudioInputError

    data class ReadFailed(val errorCode: Int) : AudioInputError
}

class AudioInput(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var running = false
    private var session: RecorderSession? = null
    private var worker: Thread? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        windowSize: Int,
        onWindow: (ShortArray, Int) -> Unit,
        onError: (AudioInputError) -> Unit,
    ) {
        require(windowSize in MIN_WINDOW_SIZE..MAX_WINDOW_SIZE) {
            "Window size must be between $MIN_WINDOW_SIZE and $MAX_WINDOW_SIZE"
        }
        synchronized(lock) {
            if (session != null) return
        }
        if (applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError(AudioInputError.PermissionMissing)
            return
        }

        val recorder = try {
            createRecorder(windowSize)
        } catch (error: SecurityException) {
            onError(AudioInputError.PermissionMissing)
            return
        } catch (error: RuntimeException) {
            onError(AudioInputError.InitializationFailed(error.message ?: error.javaClass.simpleName))
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError(AudioInputError.InitializationFailed("AudioRecord did not initialize"))
            return
        }

        val nextSession = RecorderSession(recorder)
        val nextWorker = Thread(
            { recordLoop(nextSession, windowSize, onWindow, onError) },
            THREAD_NAME,
        )
        synchronized(lock) {
            if (session != null) {
                nextSession.release()
                return
            }
            running = true
            session = nextSession
            worker = nextWorker
            nextWorker.start()
        }
    }

    fun stop() {
        val activeSession: RecorderSession?
        val activeWorker: Thread?
        synchronized(lock) {
            running = false
            activeSession = session
            activeWorker = worker
        }
        activeWorker?.interrupt()
        activeSession?.stop()
        try {
            if (activeWorker != null && activeWorker !== Thread.currentThread()) {
                activeWorker.join(STOP_JOIN_TIMEOUT_MS)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        activeSession?.release()
        synchronized(lock) {
            if (session === activeSession) session = null
            if (worker === activeWorker) worker = null
        }
    }

    override fun close() = stop()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createRecorder(windowSize: Int): AudioRecord {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            REQUESTED_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferBytes > 0) { "Device rejected the requested microphone format" }
        val audioManager = applicationContext.getSystemService(AudioManager::class.java)
        val supportsUnprocessed = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
        val source = if (supportsUnprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        return AudioRecord.Builder()
            .setAudioSource(source)
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
        activeSession: RecorderSession,
        windowSize: Int,
        onWindow: (ShortArray, Int) -> Unit,
        onError: (AudioInputError) -> Unit,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val recorder = activeSession.recorder
        val readBuffer = ShortArray(max(windowSize / 2, MIN_READ_SIZE))
        val assembler = AudioWindowAssembler(windowSize, max(1, windowSize / 2)) { window ->
            onWindow(window, recorder.sampleRate)
        }
        try {
            if (!activeSession.start()) {
                if (running) {
                    onError(AudioInputError.InitializationFailed("Microphone did not start recording"))
                }
                return
            }
            while (running && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    assembler.append(readBuffer, count)
                } else if (count < 0 && running) {
                    onError(AudioInputError.ReadFailed(count))
                    return
                }
            }
        } catch (error: SecurityException) {
            if (running) onError(AudioInputError.PermissionMissing)
        } catch (error: IllegalStateException) {
            if (running) {
                onError(AudioInputError.InitializationFailed(error.message ?: "Microphone state error"))
            }
        } finally {
            activeSession.stop()
            activeSession.release()
            synchronized(lock) {
                if (session === activeSession) {
                    running = false
                    session = null
                    worker = null
                }
            }
        }
    }

    private class RecorderSession(val recorder: AudioRecord) {
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
        const val STOP_JOIN_TIMEOUT_MS = 1_000L
        const val THREAD_NAME = "TuneItAll-Audio"
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
