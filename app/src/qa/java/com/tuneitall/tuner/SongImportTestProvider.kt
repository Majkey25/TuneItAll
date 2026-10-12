package com.tuneitall.tuner

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** QA APK only. Runs in a separate process; never included in a shipped app. */
class SongImportTestProvider : ContentProvider() {
    private val queries = AtomicInteger()
    private val pipes = AtomicReference<Array<ParcelFileDescriptor>?>()
    @Volatile private var gate = CountDownLatch(1)

    override fun onCreate() = true
    override fun getType(uri: Uri) = "audio/wav"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        query(uri, projection, selection, selectionArgs, sortOrder, null)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?): Cursor {
        if (uri.lastPathSegment in listOf("blocked", "cooperative")) {
            queries.incrementAndGet()
            val currentGate = gate
            try {
                val deadline = SystemClock.elapsedRealtime() + 60_000
                while (!currentGate.await(25, TimeUnit.MILLISECONDS)) {
                    if (uri.lastPathSegment == "cooperative" && cancellationSignal?.isCanceled == true) {
                        throw OperationCanceledException()
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) throw IllegalStateException("Test gate expired")
                }
            } finally {
                queries.decrementAndGet()
            }
        }
        if (uri.lastPathSegment == "delayed") SystemClock.sleep(6000)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("test.wav")) }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = openAssetFile(uri, mode, null)

    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?,
        signal: CancellationSignal?): AssetFileDescriptor = openAssetFile(uri, "r", signal)

    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor {
        if (uri.lastPathSegment in listOf("delayed", "slice")) {
            val file = File(requireNotNull(context).cacheDir, "security-test.wav")
            val wave = songImportTestWav(1)
            val offset = if (uri.lastPathSegment == "slice") 17 else 0
            file.writeBytes(ByteArray(offset) + wave + ByteArray(if (offset > 0) 8 else 0))
            return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                offset.toLong(), wave.size.toLong())
        }
        check(uri.lastPathSegment in listOf("pipe", "finite", "truncated"))
        val pair = ParcelFileDescriptor.createPipe()
        pipes.getAndSet(pair)?.forEach { it.close() }
        signal?.setOnCancelListener { closePipes() }
        if (uri.lastPathSegment in listOf("finite", "truncated")) {
            val wave = songImportTestWav(1)
            Thread({
                var written = 0
                while (written < wave.size) written += Os.write(pair[1].fileDescriptor, wave, written, wave.size - written)
                if (uri.lastPathSegment == "truncated") pair[1].close()
            }, "finite-test-pipe").apply { isDaemon = true; start() }
            return AssetFileDescriptor(pair[0], 0, wave.size.toLong() + if (uri.lastPathSegment == "truncated") 1 else 0)
        }
        return AssetFileDescriptor(pair[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        when (method) {
            "reset" -> { gate.countDown(); gate = CountDownLatch(1); closePipes() }
            "release" -> { gate.countDown(); closePipes(); File(requireNotNull(context).cacheDir, "security-test.wav").delete() }
        }
        return Bundle().apply {
            putInt("queries", queries.get())
            putInt("pipes", if (pipes.get() == null) 0 else 1)
        }
    }

    private fun closePipes() { pipes.getAndSet(null)?.forEach { runCatching { it.close() } } }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

internal fun songImportTestWav(seconds: Int): ByteArray {
    val rate = 8000
    val samples = rate * seconds
    val dataSize = samples * 2
    return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVEfmt ".toByteArray())
        putInt(16)
        putShort(1)
        putShort(1)
        putInt(rate)
        putInt(rate * 2)
        putShort(2)
        putShort(16)
        put("data".toByteArray())
        putInt(dataSize)
        repeat(samples) { putShort((10000 * kotlin.math.sin(2 * Math.PI * 440 * it / rate)).toInt().toShort()) }
    }.array()
}
