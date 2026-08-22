package com.tuneitall.tuner.metronome

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_SAMPLE_RATE = 8_000
private const val MAX_SAMPLE_RATE = 192_000
private const val CLICK_DURATION_MILLIS = 70
private const val MAX_PEAK = 29_000.0

fun createClickBuffer(sound: MetronomeSound, kind: PulseKind, sampleRate: Int): ShortArray {
    require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE)
    val frameCount = sampleRate * CLICK_DURATION_MILLIS / 1_000
    val attackFrames = (sampleRate + 999) / 1_000
    val baseHertz = when (sound) {
        MetronomeSound.WOOD -> 1_100.0
        MetronomeSound.CLICK -> 1_800.0
        MetronomeSound.RIM -> 2_600.0
    }
    val partials = when (sound) {
        MetronomeSound.WOOD -> doubleArrayOf(0.74, 0.20, 0.06)
        MetronomeSound.CLICK -> doubleArrayOf(0.60, 0.30, 0.10)
        MetronomeSound.RIM -> doubleArrayOf(0.55, 0.30, 0.15)
    }
    val frequency = baseHertz * if (kind == PulseKind.ACCENT) 1.25 else 1.0
    val amplitude = when (kind) {
        PulseKind.MAIN -> 16_000.0
        PulseKind.ACCENT -> 20_000.0
        PulseKind.SUBDIVISION -> 8_800.0
    }
    val values = DoubleArray(frameCount)
    val envelopes = DoubleArray(frameCount)
    var signalTotal = 0.0
    var envelopeTotal = 0.0

    for (frame in 1 until frameCount - 1) {
        val envelope = envelope(frame, attackFrames, frameCount)
        var waveform = 0.0
        partials.forEachIndexed { index, partial ->
            val partialHertz = frequency * (index + 1)
            if (partialHertz < sampleRate / 2.0) {
                waveform += partial * sin(2.0 * PI * partialHertz * frame / sampleRate)
            }
        }
        envelopes[frame] = envelope
        values[frame] = amplitude * envelope * waveform
        signalTotal += values[frame]
        envelopeTotal += envelope
    }

    val dc = signalTotal / envelopeTotal
    var peak = 0.0
    for (frame in 1 until frameCount - 1) {
        values[frame] -= dc * envelopes[frame]
        peak = maxOf(peak, kotlin.math.abs(values[frame]))
    }
    val scale = if (peak > MAX_PEAK) MAX_PEAK / peak else 1.0
    return ShortArray(frameCount) { frame ->
        when (frame) {
            0, frameCount - 1 -> 0
            else -> (values[frame] * scale).roundToInt().coerceIn(-29_000, 29_000).toShort()
        }
    }
}

fun applyStopFade(buffer: ShortArray, fadeFrames: Int) {
    require(buffer.isNotEmpty())
    require(fadeFrames in 1..buffer.size)
    if (fadeFrames == 1) {
        buffer[buffer.lastIndex] = 0
        return
    }
    val firstFadeFrame = buffer.size - fadeFrames
    for (frame in firstFadeFrame..buffer.lastIndex) {
        val progress = (frame - firstFadeFrame).toDouble() / (fadeFrames - 1)
        val gain = 0.5 * (1.0 + cos(PI * progress))
        buffer[frame] = (buffer[frame] * gain).roundToInt().toShort()
    }
}

private fun envelope(frame: Int, attackFrames: Int, frameCount: Int): Double =
    if (frame < attackFrames) {
        0.5 * (1.0 - cos(PI * frame / attackFrames))
    } else {
        exp(-6.907755278982137 * (frame - attackFrames).toDouble() / (frameCount - attackFrames - 1))
    }
