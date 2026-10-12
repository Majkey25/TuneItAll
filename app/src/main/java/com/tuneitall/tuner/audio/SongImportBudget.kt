package com.tuneitall.tuner.audio

internal class SongImportBudget(private val nanoTime: () -> Long = System::nanoTime) {
    private val started = nanoTime()
    private var lastOutput = started
    private var packets = 0
    private var bytes = 0L
    private var lastTimestamp = -1L

    fun check() {
        if (nanoTime() - started >= MAX_WORK_NANOS) fail()
    }

    fun sourceSize(size: Long) {
        check()
        if (size > MAX_SOURCE_BYTES) fail()
    }

    fun packet(size: Int, timestamp: Long) {
        check()
        if (size < 0 || timestamp < 0 || timestamp < lastTimestamp ||
            ++packets > MAX_PACKETS || bytes + size > MAX_SOURCE_BYTES
        ) fail()
        bytes += size
        lastTimestamp = timestamp
    }

    fun decoding(output: Boolean) {
        check()
        val now = nanoTime()
        if (output) lastOutput = now
        if (now - lastOutput >= MAX_STALL_NANOS) fail()
    }

    fun beginDecoding() {
        check()
        lastOutput = nanoTime()
    }

    private fun fail(): Nothing = throw SongDecodeException(SongDecodeError.DECODE_FAILED)

    companion object {
        const val MAX_SOURCE_BYTES = 512L * 1024 * 1024
        const val MAX_PCM_BYTES = 1024 * 1024
        const val MAX_CHANNEL_SAMPLES = 192_000_000L
        const val MAX_PACKETS = 500_000
        const val MAX_WORK_NANOS = 300_000_000_000L
        const val MAX_STALL_NANOS = 5_000_000_000L
    }
}
