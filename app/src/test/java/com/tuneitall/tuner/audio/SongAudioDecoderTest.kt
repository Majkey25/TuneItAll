package com.tuneitall.tuner.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class SongAudioDecoderTest {
    @Test
    fun `pcm16 decoder averages interleaved channels into bounded mono`() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buffer.putShort(Short.MAX_VALUE)
        buffer.putShort(Short.MIN_VALUE)
        buffer.putShort(16_384)
        buffer.putShort(16_384)
        buffer.flip()

        assertContentEquals(floatArrayOf(-1f / 65_536f, 0.5f), pcm16ToMono(buffer, channelCount = 2))
    }

    @Test
    fun `pcm16 decoder rejects incomplete frames and invalid channel counts`() {
        assertFailsWith<IllegalArgumentException> { pcm16ToMono(ByteBuffer.allocate(2), channelCount = 0) }
        assertFailsWith<IllegalArgumentException> { pcm16ToMono(ByteBuffer.allocate(6), channelCount = 2) }
    }
}
