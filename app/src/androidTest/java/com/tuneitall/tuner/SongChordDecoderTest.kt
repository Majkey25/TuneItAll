package com.tuneitall.tuner

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import org.junit.Assert.assertEquals
import org.junit.Test

class SongChordDecoderTest {
    @Test
    fun localWavDecoderRecognizesDistortedEPowerChord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "distorted-e5.wav")
        try {
            file.writeBytes(distortedPowerChordWav())

            val result = SongAudioDecoder(context).analyze(Uri.fromFile(file))

            assertEquals(Chord(4, ChordQuality.POWER), result.events.maxBy(ChordEvent::durationMillis).chord)
        } finally {
            file.delete()
        }
    }
}

private fun distortedPowerChordWav(): ByteArray {
    val sampleCount = SAMPLE_RATE * DURATION_SECONDS
    val dataSize = sampleCount * Short.SIZE_BYTES
    return ByteBuffer.allocate(WAV_HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVEfmt ".toByteArray())
        putInt(16)
        putShort(1.toShort())
        putShort(1.toShort())
        putInt(SAMPLE_RATE)
        putInt(SAMPLE_RATE * Short.SIZE_BYTES)
        putShort(Short.SIZE_BYTES.toShort())
        putShort(Short.SIZE_BITS.toShort())
        put("data".toByteArray())
        putInt(dataSize)
        repeat(sampleCount) { frame ->
            val root = sin(2.0 * PI * ROOT_HERTZ * frame / SAMPLE_RATE)
            val fifth = sin(2.0 * PI * ROOT_HERTZ * 1.5 * frame / SAMPLE_RATE)
            val sample = (0.75 * tanh(3.5 * (root + 0.8 * fifth)) * Short.MAX_VALUE).roundToInt()
            putShort(sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }.array()
}

private const val SAMPLE_RATE = 48_000
private const val DURATION_SECONDS = 4
private const val ROOT_HERTZ = 82.41
private const val WAV_HEADER_SIZE = 44
