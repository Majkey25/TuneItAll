package com.tuneitall.tuner.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.media.AudioFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class SongAudioDecoderTest {
    @Test
    fun `decoded duration follows frames and rejects format changes and excess frames`() {
        val clock = DecodedAudioClock()
        clock.accept(48_000, 24_000, 2)
        clock.accept(48_000, 24_000, 2)
        assertEquals(1000L, clock.durationMillis)
        assertEquals(SongDecodeError.UNSUPPORTED_PCM,
            assertFailsWith<SongDecodeException> { clock.accept(44_100, 1024, 2) }.reason)
        assertEquals(SongDecodeError.UNSUPPORTED_PCM,
            assertFailsWith<SongDecodeException> { clock.accept(48_000, 1024, 1) }.reason)
        assertEquals(SongDecodeError.TOO_LONG,
            assertFailsWith<SongDecodeException> { clock.accept(48_000, 48_000 * 1800, 2) }.reason)
        assertEquals(1000L, clock.durationMillis)
    }
    @Test
    fun `pcm16 decoder preserves every channel including opposite polarity`() {
        val source = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 16_384, -16_384, 0, 8_192)
        val buffer = ByteBuffer.allocate(source.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        source.forEach(buffer::putShort)
        buffer.flip()

        assertContentEquals(source.map { it / 32_768f }.toFloatArray(),
            decodePcmSamples(buffer, 2, AudioFormat.ENCODING_PCM_16BIT))
        assertEquals(0, buffer.position())
    }

    @Test
    fun `pcm decoder rejects incomplete frames and invalid formats`() {
        for (channels in listOf(0, 9)) {
            assertEquals(SongDecodeError.UNSUPPORTED_PCM,
                assertFailsWith<SongDecodeException> {
                    decodePcmSamples(ByteBuffer.allocate(0), channels, AudioFormat.ENCODING_PCM_16BIT)
                }.reason)
        }
        for (bytes in listOf(1, 2, 6)) {
            assertFailsWith<IllegalArgumentException> {
                decodePcmSamples(ByteBuffer.allocate(bytes), 2, AudioFormat.ENCODING_PCM_16BIT)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            decodePcmSamples(ByteBuffer.allocate(4), 2, AudioFormat.ENCODING_PCM_FLOAT)
        }
        assertEquals(SongDecodeError.UNSUPPORTED_PCM,
            assertFailsWith<SongDecodeException> {
                decodePcmSamples(ByteBuffer.allocate(0), 1, AudioFormat.ENCODING_PCM_8BIT)
            }.reason)
    }

    @Test
    fun `float PCM remains interleaved and bounded and rejects nonfinite samples`() {
        assertContentEquals(floatArrayOf(1f, -1f, 0.5f, -0.5f, 1f, -1f),
            decodePcmSamples(floats(1f, -1f, 0.5f, -0.5f, 2f, -2f), 2, AudioFormat.ENCODING_PCM_FLOAT))
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                decodePcmSamples(floats(0.5f, invalid), 2, AudioFormat.ENCODING_PCM_FLOAT)
            }
        }
        assertContentEquals(FloatArray(0),
            decodePcmSamples(ByteBuffer.allocate(0), 8, AudioFormat.ENCODING_PCM_FLOAT))
    }

    @Test
    fun `mono and multichannel conversion honor the buffer slice without consuming it`() {
        val buffer = floats(0.9f, 0.25f, -0.5f, 0.8f)
        buffer.position(4)
        buffer.limit(12)
        assertContentEquals(floatArrayOf(0.25f, -0.5f),
            decodePcmSamples(buffer, 1, AudioFormat.ENCODING_PCM_FLOAT))
        assertEquals(4, buffer.position())
        assertEquals(12, buffer.limit())
        val surround = FloatArray(8) { it / 8f }
        assertContentEquals(surround, decodePcmSamples(floats(*surround), 8, AudioFormat.ENCODING_PCM_FLOAT))
    }

    private fun floats(vararg samples: Float): ByteBuffer =
        ByteBuffer.allocate(samples.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
            samples.forEach(::putFloat)
            flip()
        }
}
