package com.tuneitall.tuner.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongAudioDecoderTest {
    @Test
    fun `decoded duration follows PCM and rejects rate changes and excess samples`() {
        val clock = DecodedAudioClock()
        clock.accept(48_000, 24_000)
        clock.accept(48_000, 24_000)
        assertEquals(1000L, clock.durationMillis)
        assertEquals(SongDecodeError.UNSUPPORTED_PCM,
            assertFailsWith<SongDecodeException> { clock.accept(44_100, 1024) }.reason)
        assertEquals(SongDecodeError.TOO_LONG,
            assertFailsWith<SongDecodeException> { clock.accept(48_000, 48_000 * 1800) }.reason)
        assertEquals(1000L, clock.durationMillis)
    }
    @Test
    fun `pcm16 decoder averages interleaved channels into bounded mono`() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buffer.putShort(Short.MAX_VALUE)
        buffer.putShort(Short.MIN_VALUE)
        buffer.putShort(16_384)
        buffer.putShort(16_384)
        buffer.flip()

        assertContentEquals(floatArrayOf(-1f / 65_536f, 0.5f), PcmDownmixer().pcm16ToMono(buffer, channelCount = 2))
    }

    @Test
    fun `pcm16 decoder rejects incomplete frames and invalid channel counts`() {
        assertFailsWith<IllegalArgumentException> { PcmDownmixer().pcm16ToMono(ByteBuffer.allocate(2), channelCount = 0) }
        assertFailsWith<IllegalArgumentException> { PcmDownmixer().pcm16ToMono(ByteBuffer.allocate(6), channelCount = 2) }
    }

    @Test
    fun `pcm16 decoder preserves opposite phase stereo tone`() {
        val tone = ShortArray(1_024) { (12_000 * sin(2.0 * PI * it / 64)).toInt().toShort() }
        val stereo = ByteBuffer.allocate(tone.size * 4).order(ByteOrder.nativeOrder())
        tone.forEach { sample -> stereo.putShort(sample).putShort((-sample).toShort()) }
        stereo.flip()

        val mono = PcmDownmixer().pcm16ToMono(stereo, channelCount = 2)

        assertTrue(mono.drop(128).sumOf { it.toDouble() * it } > 10.0)
        for (index in 128 until tone.size) {
            assertEquals(tone[index] / 32_768f, mono[index], 1e-6f)
        }
    }

    @Test
    fun `phase fallback keeps one channel across buffers then returns to normal stereo`() {
        val downmixer = PcmDownmixer()
        val left = FloatArray(256) { (0.4 * sin(2.0 * PI * it / 32)).toFloat() }
        val first = downmixer.pcmFloatToMono(stereoFloats(left, left.map { -it }.toFloatArray()), 2)
        assertContentEquals(left.drop(128).toFloatArray(), first.drop(128).toFloatArray())

        val louderRight = left.map { -it * 1.1f }.toFloatArray()
        val next = downmixer.pcmFloatToMono(stereoFloats(left, louderRight), 2)
        left.indices.forEach { assertEquals(left[it], next[it], 1e-6f) }

        val normalRight = left.map { it * 0.5f }.toFloatArray()
        val normal = downmixer.pcmFloatToMono(stereoFloats(left, normalRight), 2)
        for (index in 128 until left.size) assertEquals(left[index] * 0.75f, normal[index], 1e-6f)
    }

    @Test
    fun `float PCM remains averaged and bounded and rejects malformed samples`() {
        val downmixer = PcmDownmixer()
        assertContentEquals(
            floatArrayOf(0.75f, -0.75f, 1f),
            downmixer.pcmFloatToMono(stereoFloats(floatArrayOf(1f, -1f, 2f), floatArrayOf(0.5f, -0.5f, 2f)), 2),
        )
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                downmixer.pcmFloatToMono(stereoFloats(floatArrayOf(0.5f), floatArrayOf(invalid)), 2)
            }
        }
        assertFailsWith<IllegalArgumentException> { downmixer.pcmFloatToMono(ByteBuffer.allocate(4), 2) }
        assertFailsWith<IllegalArgumentException> { downmixer.pcmFloatToMono(ByteBuffer.allocate(8), 0) }
        assertFailsWith<IllegalArgumentException> { downmixer.pcm16ToMono(ByteBuffer.allocate(1), 1) }
        assertContentEquals(FloatArray(0), downmixer.pcmFloatToMono(ByteBuffer.allocate(0), 2))
    }
}

private fun stereoFloats(left: FloatArray, right: FloatArray): ByteBuffer {
    require(left.size == right.size)
    return ByteBuffer.allocate(left.size * Float.SIZE_BYTES * 2).order(ByteOrder.nativeOrder()).apply {
        left.indices.forEach { putFloat(left[it]).putFloat(right[it]) }
        flip()
    }
}
