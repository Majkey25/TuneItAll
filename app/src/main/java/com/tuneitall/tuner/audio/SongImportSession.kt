package com.tuneitall.tuner.audio

import android.os.CancellationSignal
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class SongImportSession(private val isCancelled: () -> Boolean) : Closeable {
    val budget = SongImportBudget()
    val signal = CancellationSignal()
    private val resource = AtomicReference<Closeable?>()

    init {
        if (!slots.tryAcquire()) throw SongDecodeException(SongDecodeError.DECODE_FAILED)
    }

    // A provider may ignore cancellation. Never accumulate replacement workers behind it.
    private val watchdog = watchdogs.scheduleWithFixedDelay({
        if (isCancelled() || runCatching { budget.check() }.isFailure) {
            runCatching { resource.getAndSet(null)?.close() }
            signal.cancel()
        }
    }, 0, 50, TimeUnit.MILLISECONDS)

    fun check() {
        if (isCancelled()) throw CancellationException("Song analysis cancelled")
        budget.check()
    }

    fun attach(closeable: Closeable) {
        check()
        resource.set(closeable)
        if (signal.isCanceled) {
            resource.getAndSet(null)?.close()
            check()
        }
    }

    override fun close() {
        watchdog.cancel(false)
        try {
            resource.getAndSet(null)?.close()
        } finally {
            slots.release()
        }
    }

    companion object {
        private val slots = Semaphore(2)
        private val watchdogs = ScheduledThreadPoolExecutor(2) { task ->
            Thread(task, "song-import-watchdog").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
