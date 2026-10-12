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
    private var generation = 0L
    private var stopCompletion: (() -> Unit)? = null

    fun play(hertz: Double, isCurrent: () -> Boolean = { true }) =
        prepareAndPlay({ createToneBuffer(hertz) }, isCurrent)

    fun playChord(hertz: DoubleArray, isCurrent: () -> Boolean = { true }) =
        prepareAndPlay({ createChordToneBuffer(hertz) }, isCurrent)

    fun repeatNotes(firstHertz: Double, secondHertz: Double, isCurrent: () -> Boolean = { true }) =
        prepareAndPlay({ createComparisonToneBuffer(firstHertz, secondHertz) }, isCurrent, repeat = true)

    @Synchronized
    fun invalidatePending() {
        generation++
    }

    private fun prepareAndPlay(samples: () -> ShortArray, isCurrent: () -> Boolean, repeat: Boolean = false) {
        if (!isCurrent()) return
        val requestGeneration = synchronized(this) {
            check(!closed) { "Reference tone player is closed" }
            ++generation
        }
        val rendered = samples()
        if (!isCurrentRequest(requestGeneration, isCurrent)) return
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
            .setBufferSizeInBytes(rendered.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        var published = false
        try {
            if (nextTrack.state == AudioTrack.STATE_UNINITIALIZED) {
                throw IllegalStateException("Reference tone output did not initialize")
            }
            if (!isCurrentRequest(requestGeneration, isCurrent)) return
            val written = nextTrack.write(rendered, 0, rendered.size, AudioTrack.WRITE_BLOCKING)
            if (
                written != rendered.size ||
                nextTrack.state != AudioTrack.STATE_INITIALIZED ||
                (if (repeat) nextTrack.setLoopPoints(0, rendered.size, -1)
                else nextTrack.setNotificationMarkerPosition(rendered.lastIndex)) != AudioTrack.SUCCESS
            ) {
                throw IllegalStateException("Reference tone buffer could not be prepared")
            }
            nextTrack.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(completedTrack: AudioTrack) {
                        if (!repeat) release(completedTrack)
                    }

                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
                handler,
            )
            val previousTrack = synchronized(this) {
                if (closed || requestGeneration != generation || !isCurrent()) return
                nextTrack.play()
                val previous = track
                track = nextTrack
                tracks += nextTrack
                stopCompletion = null
                published = true
                previous
            }
            if (previousTrack != null) fadeOutAndRelease(previousTrack)
            while (true) {
                val oldestTrack = synchronized(this) { tracks.firstOrNull().takeIf { tracks.size > MAX_ACTIVE_TRACKS } }
                    ?: break
                runCatching { oldestTrack.setVolume(0f) }
                release(oldestTrack)
            }
        } finally {
            if (!published) nextTrack.release()
        }
    }

    private fun isCurrentRequest(requestGeneration: Long, isCurrent: () -> Boolean): Boolean =
        isCurrent() && synchronized(this) { !closed && requestGeneration == generation }

    fun stop(onStopped: () -> Unit = {}) {
        val activeTrack: AudioTrack?
        val alreadyStopped: Boolean
        synchronized(this) {
            generation++
            activeTrack = track
            track = null
            alreadyStopped = tracks.isEmpty()
            stopCompletion = onStopped.takeUnless { alreadyStopped }
        }
        if (activeTrack != null) fadeOutAndRelease(activeTrack)
        if (alreadyStopped) onStopped()
    }

    private fun release(completedTrack: AudioTrack) {
        val released = synchronized(this) {
            if (!tracks.remove(completedTrack)) return
            if (track === completedTrack) track = null
            val completion = if (tracks.isEmpty()) stopCompletion.also { stopCompletion = null } else null
            Pair(volumeShapers.remove(completedTrack), completion)
        }
        runCatching { released.first?.close() }
        runCatching { completedTrack.stop() }
        completedTrack.release()
        released.second?.invoke()
    }

    @Synchronized
    private fun fadeOutAndRelease(activeTrack: AudioTrack) {
        if (activeTrack !in tracks || activeTrack in volumeShapers) return
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

    override fun close() {
        val activeTracks = synchronized(this) {
            if (closed) return
            closed = true
            generation++
            handler.removeCallbacksAndMessages(null)
            track = null
            stopCompletion = null
            tracks.toList()
        }
        activeTracks.forEach(::release)
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
    val lowToneBlend = ((LOW_TONE_EQ_CEILING_HERTZ - hertz) / LOW_TONE_EQ_RANGE_HERTZ)
        .coerceIn(0.0, 1.0)
    val toneNormalization = HARMONIC_NORMALIZATION + LOW_TONE_NORMALIZATION_LIFT * lowToneBlend
    return ShortArray(sampleCount) { index ->
        val seconds = index.toDouble() / sampleRate
        val attack = (index.toDouble() / attackSamples).coerceAtMost(1.0)
        val release = ((sampleCount - 1 - index).toDouble() / fadeSamples).coerceAtMost(1.0)
        var wave = 0.0
        HARMONIC_AMPLITUDES.forEachIndexed { harmonicIndex, amplitude ->
            val harmonic = harmonicIndex + 1
            if (hertz * harmonic >= sampleRate / 2.0) return@forEachIndexed
            val equalizedAmplitude = amplitude +
                (LOW_TONE_HARMONIC_AMPLITUDES[harmonicIndex] - amplitude) * lowToneBlend
            val harmonicDecay = HARMONIC_DECAY * (1.0 - lowToneBlend)
            val decay = exp(-seconds * (TONE_DECAY + harmonicIndex * harmonicDecay))
            wave += equalizedAmplitude * decay * sin(2.0 * PI * hertz * harmonic * seconds)
        }
        val envelope = attack * release * release
        val value = TONE_AMPLITUDE * envelope * wave / toneNormalization
        (value * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

internal fun createChordToneBuffer(hertz: DoubleArray): ShortArray {
    require(hertz.size in 2..9) { "A chord tone needs between two and nine notes" }
    require(hertz.distinct().size == hertz.size) { "Chord tone frequencies must be unique" }
    val tones = hertz.map(::createToneBuffer)
    val mixed = IntArray(tones.first().size)
    tones.forEach { tone -> tone.indices.forEach { mixed[it] += tone[it].toInt() } }
    val peak = mixed.maxOf { kotlin.math.abs(it) }
    val scale = if (peak > CHORD_MAX_PEAK) CHORD_MAX_PEAK.toDouble() / peak else 1.0
    return ShortArray(mixed.size) { index ->
        (mixed[index] * scale).roundToInt().coerceIn(-CHORD_MAX_PEAK, CHORD_MAX_PEAK).toShort()
    }
}

internal fun createComparisonToneBuffer(firstHertz: Double, secondHertz: Double): ShortArray {
    val first = createToneBuffer(firstHertz)
    val second = createToneBuffer(secondHertz)
    val gap = TONE_SAMPLE_RATE * 150 / 1_000
    return ShortArray(first.size + second.size + gap * 2).also { samples ->
        first.copyInto(samples)
        second.copyInto(samples, first.size + gap)
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
private const val CHORD_MAX_PEAK = 29_000
private const val LOW_TONE_EQ_CEILING_HERTZ = 330.0
private const val LOW_TONE_EQ_RANGE_HERTZ = 250.0
private const val LOW_TONE_NORMALIZATION_LIFT = 0.41
private val LOW_TONE_HARMONIC_AMPLITUDES = doubleArrayOf(0.05, 1.00, 0.80, 0.38, 0.13, 0.06)
private val TONE_SWITCH_FADE = ToneSwitchFade(
    durationMillis = 45L,
    times = listOf(0f, 1f),
    volumes = listOf(1f, 0f),
)
