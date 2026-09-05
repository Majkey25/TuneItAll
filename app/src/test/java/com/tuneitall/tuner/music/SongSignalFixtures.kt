package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

internal fun sine(sampleRate: Int, seconds: Int, hertz: Double): FloatArray =
    FloatArray(sampleRate * seconds) { frame ->
        (0.7 * sin(2.0 * PI * hertz * frame / sampleRate)).toFloat()
    }

internal fun sineChord(sampleRate: Int, seconds: Int, vararg frequencies: Double): FloatArray =
    FloatArray(sampleRate * seconds) { frame ->
        val sample = frequencies.sumOf { frequency -> sin(2.0 * PI * frequency * frame / sampleRate) }
        (0.75 * sample / frequencies.size).toFloat()
    }

internal fun sineChord(sampleRate: Int, seconds: Double, vararg frequencies: Double): FloatArray =
    FloatArray((sampleRate * seconds).roundToInt()) { frame ->
        val sample = frequencies.sumOf { frequency -> sin(2.0 * PI * frequency * frame / sampleRate) }
        (0.75 * sample / frequencies.size).toFloat()
    }

internal fun concatenate(vararg parts: FloatArray): FloatArray {
    val result = FloatArray(parts.sumOf(FloatArray::size))
    var offset = 0
    parts.forEach { part ->
        part.copyInto(result, offset)
        offset += part.size
    }
    return result
}

internal fun noisyPowerRiff(sampleRate: Int, seconds: Int, rootHertz: Double): FloatArray {
    val random = Random(2_026)
    return FloatArray(sampleRate * seconds) { frame ->
        val root = sin(2.0 * PI * rootHertz * frame / sampleRate)
        val fifth = sin(2.0 * PI * rootHertz * 1.5 * frame / sampleRate)
        val clipped = 0.62 * tanh(3.2 * (root + 0.75 * fifth))
        val impulseOffset = frame % (sampleRate / 2)
        val drum = if (impulseOffset < sampleRate / 100) {
            (1.0 - impulseOffset.toDouble() / (sampleRate / 100)) * random.nextDouble(-0.8, 0.8)
        } else {
            0.0
        }
        (clipped + drum).coerceIn(-1.0, 1.0).toFloat()
    }
}

internal fun changingNoise(sampleRate: Int, seconds: Int, seed: Int = 4_404): FloatArray {
    val random = Random(seed)
    return FloatArray(sampleRate * seconds) { frame ->
        val envelope = 0.15 + 0.25 * sin(2.0 * PI * frame / (sampleRate / 2.0))
        (envelope * random.nextDouble(-1.0, 1.0)).toFloat()
    }
}

internal fun harmonicTone(sampleRate: Int, seconds: Int, fundamentalHertz: Double): FloatArray =
    FloatArray(sampleRate * seconds) { frame ->
        val sample = 0.70 * sin(2.0 * PI * fundamentalHertz * 2.0 * frame / sampleRate) +
            0.48 * sin(2.0 * PI * fundamentalHertz * 3.0 * frame / sampleRate) +
            0.30 * sin(2.0 * PI * fundamentalHertz * 4.0 * frame / sampleRate) +
            0.18 * sin(2.0 * PI * fundamentalHertz * 5.0 * frame / sampleRate)
        (sample * 0.55).coerceIn(-1.0, 1.0).toFloat()
    }
