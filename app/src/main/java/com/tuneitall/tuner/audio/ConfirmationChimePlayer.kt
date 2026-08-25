package com.tuneitall.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class ConfirmationChimePlayer : AutoCloseable {
    private var track: AudioTrack? = null

    @Synchronized
    fun play() {
        stop()
        val samples = createConfirmationChime()
        val nextTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(CHIME_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (nextTrack.state == AudioTrack.STATE_UNINITIALIZED) {
            nextTrack.release()
            throw IllegalStateException("Confirmation sound output did not initialize")
        }
        val written = nextTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (
            written != samples.size ||
            nextTrack.state != AudioTrack.STATE_INITIALIZED ||
            nextTrack.setNotificationMarkerPosition(samples.lastIndex) != AudioTrack.SUCCESS
        ) {
            nextTrack.release()
            throw IllegalStateException("Confirmation sound buffer could not be prepared")
        }
        nextTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(completedTrack: AudioTrack) = release(completedTrack)

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            },
            Handler(Looper.getMainLooper()),
        )
        track = nextTrack
        try {
            nextTrack.play()
        } catch (error: IllegalStateException) {
            track = null
            nextTrack.release()
            throw error
        }
    }

    @Synchronized
    fun stop() {
        val activeTrack = track ?: return
        track = null
        runCatching { activeTrack.stop() }
        activeTrack.release()
    }

    @Synchronized
    private fun release(completedTrack: AudioTrack) {
        if (track !== completedTrack) return
        track = null
        completedTrack.release()
    }

    override fun close() = stop()
}

internal fun createConfirmationChime(
    sampleRate: Int = CHIME_SAMPLE_RATE,
    durationMillis: Int = CONFIRMATION_CHIME_DURATION_MILLIS,
): ShortArray {
    require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) { "Chime sample rate is outside the supported range" }
    require(durationMillis in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS) {
        "Chime duration is outside the supported range"
    }
    val sampleCount = Math.multiplyExact(sampleRate, durationMillis) / 1_000
    val attackSamples = sampleRate * CHIME_ATTACK_MILLIS / 1_000
    return ShortArray(sampleCount) { index ->
        val seconds = index.toDouble() / sampleRate
        val attack = (index.toDouble() / attackSamples).coerceAtMost(1.0)
        val release = (sampleCount - 1 - index).toDouble() / (sampleCount - 1)
        val envelope = attack * release * release * exp(-CHIME_DECAY * seconds)
        val wave =
            0.70 * sin(2.0 * PI * CHIME_HERTZ * seconds) +
                0.22 * sin(4.01 * PI * CHIME_HERTZ * seconds) +
                0.08 * sin(7.98 * PI * CHIME_HERTZ * seconds)
        (wave * envelope * CHIME_AMPLITUDE * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}

private const val CHIME_SAMPLE_RATE = 48_000
internal const val CONFIRMATION_CHIME_DURATION_MILLIS = 90
private const val CHIME_ATTACK_MILLIS = 6
private const val CHIME_HERTZ = 880.0
private const val CHIME_DECAY = 5.5
private const val CHIME_AMPLITUDE = 0.72
private const val MIN_SAMPLE_RATE = 8_000
private const val MAX_SAMPLE_RATE = 192_000
private const val MIN_DURATION_MILLIS = 50
private const val MAX_DURATION_MILLIS = 2_000
