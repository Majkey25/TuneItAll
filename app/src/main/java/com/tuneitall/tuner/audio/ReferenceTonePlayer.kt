package com.tuneitall.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.VolumeShaper
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class ReferenceTonePlayer : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val tracks = LinkedHashSet<AudioTrack>()
    private val volumeShapers = mutableMapOf<AudioTrack, VolumeShaper>()
    private var track: AudioTrack? = null
    private var closed = false

    @Synchronized
    fun play(hertz: Double) {
        check(!closed) { "Reference tone player is closed" }
        val samples = createToneBuffer(hertz)
        val nextTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TONE_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        if (nextTrack.state == AudioTrack.STATE_UNINITIALIZED) {
            nextTrack.release()
            throw IllegalStateException("Reference tone output did not initialize")
        }
        val written = nextTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (
            written != samples.size ||
            nextTrack.state != AudioTrack.STATE_INITIALIZED ||
            nextTrack.setNotificationMarkerPosition(samples.lastIndex) != AudioTrack.SUCCESS
        ) {
            nextTrack.release()
            throw IllegalStateException("Reference tone buffer could not be prepared")
        }
        nextTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(completedTrack: AudioTrack) = release(completedTrack)

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            },
            handler,
        )

        val previousTrack = track
        try {
            nextTrack.play()
        } catch (error: IllegalStateException) {
            nextTrack.release()
            throw error
        }
        track = nextTrack
        tracks += nextTrack
        if (previousTrack != null) fadeOutAndRelease(previousTrack)
        while (tracks.size > MAX_ACTIVE_TRACKS) {
            val oldestTrack = tracks.first()
            runCatching { oldestTrack.setVolume(0f) }
            release(oldestTrack)
        }
    }

    @Synchronized
    fun stop() {
        val activeTrack = track ?: return
        track = null
        fadeOutAndRelease(activeTrack)
    }

    @Synchronized
    private fun release(completedTrack: AudioTrack) {
        if (!tracks.remove(completedTrack)) return
        if (track === completedTrack) track = null
        runCatching { volumeShapers.remove(completedTrack)?.close() }
        runCatching { completedTrack.stop() }
        completedTrack.release()
    }

    private fun fadeOutAndRelease(activeTrack: AudioTrack) {
        if (activeTrack !in tracks) return
        val fade = referenceToneSwitchFade()
        val shaper = try {
            activeTrack.createVolumeShaper(
                VolumeShaper.Configuration.Builder()
                    .setDuration(fade.durationMillis)
                    .setCurve(fade.times.toFloatArray(), fade.volumes.toFloatArray())
                    .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                    .build(),
            ).also { it.apply(VolumeShaper.Operation.PLAY) }
        } catch (_: RuntimeException) {
            runCatching { activeTrack.setVolume(0f) }
            release(activeTrack)
            return
        }
        volumeShapers.put(activeTrack, shaper)?.close()
        handler.postDelayed({ release(activeTrack) }, fade.durationMillis)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        track = null
        tracks.toList().forEach(::release)
    }
}

internal data class ToneSwitchFade(
    val durationMillis: Long,
    val times: List<Float>,
    val volumes: List<Float>,
)

internal fun referenceToneSwitchFade(): ToneSwitchFade = TONE_SWITCH_FADE

internal fun createToneBuffer(
    hertz: Double,
    sampleRate: Int = TONE_SAMPLE_RATE,
    durationSeconds: Int = TONE_DURATION_SECONDS,
    fadeMilliseconds: Int = TONE_RELEASE_MILLISECONDS,
): ShortArray {
    require(sampleRate > 0) { "Tone sample rate must be positive" }
    require(durationSeconds > 0) { "Tone duration must be positive" }
    require(fadeMilliseconds > 0) { "Tone fade must be positive" }
    require(hertz.isFinite() && hertz >= MIN_TONE_HERTZ && hertz < sampleRate / 2.0) {
        "Tone frequency must be finite and between $MIN_TONE_HERTZ Hz and Nyquist"
    }
    val sampleCount = Math.multiplyExact(sampleRate, durationSeconds)
    val fadeSamples = sampleRate * fadeMilliseconds / 1_000
    require(fadeSamples > 0 && fadeSamples * 2 < sampleCount) { "Tone fade must fit the sample buffer" }

    val attackSamples = sampleRate * TONE_ATTACK_MILLISECONDS / 1_000
    return ShortArray(sampleCount) { index ->
        val seconds = index.toDouble() / sampleRate
        val attack = (index.toDouble() / attackSamples).coerceAtMost(1.0)
        val release = ((sampleCount - 1 - index).toDouble() / fadeSamples).coerceAtMost(1.0)
        var wave = 0.0
        HARMONIC_AMPLITUDES.forEachIndexed { harmonicIndex, amplitude ->
            val harmonic = harmonicIndex + 1
            if (hertz * harmonic >= sampleRate / 2.0) return@forEachIndexed
            val decay = exp(-seconds * (TONE_DECAY + harmonicIndex * HARMONIC_DECAY))
            wave += amplitude * decay * sin(2.0 * PI * hertz * harmonic * seconds)
        }
        val envelope = attack * release * release
        val value = TONE_AMPLITUDE * envelope * wave / HARMONIC_NORMALIZATION
        (value * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

private const val TONE_SAMPLE_RATE = 48_000
private const val TONE_DURATION_SECONDS = 1
private const val TONE_ATTACK_MILLISECONDS = 5
private const val TONE_RELEASE_MILLISECONDS = 200
private const val MIN_TONE_HERTZ = 20.0
private const val TONE_DECAY = 3.0
private const val HARMONIC_DECAY = 0.45
private const val TONE_AMPLITUDE = 0.92
private val HARMONIC_AMPLITUDES = doubleArrayOf(1.0, 0.55, 0.32, 0.20, 0.13, 0.08)
private const val HARMONIC_NORMALIZATION = 1.70
private const val MAX_ACTIVE_TRACKS = 4
private val TONE_SWITCH_FADE = ToneSwitchFade(
    durationMillis = 45L,
    times = listOf(0f, 1f),
    volumes = listOf(1f, 0f),
)
