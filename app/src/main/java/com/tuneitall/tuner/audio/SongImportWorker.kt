package com.tuneitall.tuner.audio

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

// One queued handoff avoids rejecting a new import while a completed worker returns.
private val songImportWorkers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1)) { task ->
    Thread(task, "song-import").apply { isDaemon = true }
}.apply { allowCoreThreadTimeOut(true) }

internal suspend fun <T> runSongImport(
    onDiscard: (T) -> Unit = {},
    block: (() -> Boolean) -> T,
): T = try {
    withTimeout(SongImportBudget.MAX_WORK_NANOS / 1_000_000) {
        suspendCancellableCoroutine { continuation ->
            val cancelled = AtomicBoolean()
            val task = Runnable {
                try {
                    val result = block { cancelled.get() }
                    continuation.resume(result) { _, discarded, _ -> onDiscard(discarded) }
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation {
                cancelled.set(true)
                songImportWorkers.remove(task)
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            try {
                songImportWorkers.execute(task)
                if (!continuation.isActive) songImportWorkers.remove(task)
            } catch (error: RejectedExecutionException) {
                continuation.resumeWithException(SongDecodeException(SongDecodeError.DECODE_FAILED, error))
            }
        }
    }
} catch (error: TimeoutCancellationException) {
    throw SongDecodeException(SongDecodeError.DECODE_FAILED, error)
}
