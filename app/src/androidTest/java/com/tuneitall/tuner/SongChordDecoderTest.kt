package com.tuneitall.tuner

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.SongAnalysisMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SongChordDecoderTest {
    @Test
    fun localWavDecoderRecognizesDistortedEPowerChord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "distorted-e5.wav")
        try {
            file.writeBytes(distortedPowerChordWav())

            val result = SongAudioDecoder(context).analyze(
                uri = Uri.fromFile(file),
                mode = SongAnalysisMode.POWER,
            )

            val event = result.events.filterIsInstance<ChordEvent>().maxBy(ChordEvent::durationMillis)
            assertEquals(Chord(4, ChordQuality.POWER), event.chord)
        } finally {
            file.delete()
        }
    }

    @Test
    fun externalDenseSongKeepsCoverageAndRuntimeBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "dense-song-qa.mp3")
        assumeTrue("Push the private QA fixture into the target cache before this test", file.isFile)
        assertEquals(DENSE_SONG_SHA256, sha256(file))

        val started = SystemClock.elapsedRealtime()
        val result = SongAudioDecoder(context).analyze(Uri.fromFile(file))
        val elapsed = SystemClock.elapsedRealtime() - started
        val labelledMillis = result.events.filterIsInstance<ChordEvent>().sumOf(ChordEvent::durationMillis)
        val coverage = labelledMillis.toDouble() / result.durationMillis
        println("dense-song duration=${result.durationMillis}ms events=${result.events.size} coverage=$coverage elapsed=${elapsed}ms")

        assertTrue("coverage=$coverage", coverage >= 0.85)
        assertTrue("elapsed=${elapsed}ms", elapsed <= 30_000L)
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
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
private const val DENSE_SONG_SHA256 = "B816E91DB8E4379532686D5F1D541C92B54E5A4D2D291FA2B426CEFFBD05AE00"
