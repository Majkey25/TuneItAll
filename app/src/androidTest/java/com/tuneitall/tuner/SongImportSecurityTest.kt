package com.tuneitall.tuner

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.audio.SongDecodeError
import com.tuneitall.tuner.audio.SongDecodeException
import com.tuneitall.tuner.audio.SongImportBudget
import com.tuneitall.tuner.audio.runSongImport
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.music.NoteEvent
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.fft
import com.tuneitall.tuner.ui.ChordViewModel
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SongImportSecurityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val baseUri = Uri.parse("content://${context.packageName}.song-import-test")

    @Test
    fun fftCancellationHonorsImportBoundaries() = verifyFftCancellationBoundaries(::fft)

    @Test
    fun sameSongReanalysisKeepsMutedPlaybackAndPosition() = runBlocking {
        val file = File(context.cacheDir, "playback-security-test.wav")
        file.writeBytes(songImportTestWav(30))
        val model = ChordViewModel(context.applicationContext as Application)
        val field = ChordViewModel::class.java.getDeclaredField("player").apply { isAccessible = true }
        try {
            instrumentation.runOnMainSync { model.loadSong(Uri.fromFile(file)) }
            await { model.uiState.value.prepared && !model.uiState.value.analyzing }
            val player = field.get(model) as android.media.MediaPlayer
            instrumentation.runOnMainSync {
                player.setVolume(0f, 0f)
                model.seekTo(1000)
                model.playPause()
                model.setSongAnalysisMode(SongAnalysisMode.NOTES)
            }
            await { !model.uiState.value.analyzing }
            assertSame(player, field.get(model))
            assertTrue(model.uiState.value.playing)
            assertTrue(model.uiState.value.positionMillis >= 1000)
            instrumentation.runOnMainSync { model.setNoteRange(NoteRange.GUITAR) }
            await { !model.uiState.value.analyzing }
            assertSame(player, field.get(model))
            assertTrue(model.uiState.value.playing)
            assertTrue(model.uiState.value.positionMillis >= 1000)
        } finally {
            instrumentation.runOnMainSync { model.clearSong() }
            file.delete()
        }
    }

    @Test
    fun delayedNormalProviderStillDecodesTheNote() = runBlocking {
        control("reset")
        try {
            val progress = mutableListOf<Int>()
            val result = runSongImport { cancelled ->
                SongAudioDecoder(context).analyze(baseUri.buildUpon().appendPath("delayed").build(),
                    mode = SongAnalysisMode.NOTES, isCancelled = cancelled, onProgress = { progress += it })
            }
            assertEquals(1000L, result.durationMillis)
            assertEquals(listOf(69), result.events.filterIsInstance<NoteEvent>().map { it.midiNote })
            assertEquals(100, progress.last())
            assertTrue(progress.size <= 93)
            assertTrue(progress.zipWithNext().all { (before, after) -> after > before })
        } finally {
            control("release")
        }
    }

    @Test
    fun assetSlicePreservesItsOffsetAndLength() = checkProviderAudio("slice")

    @Test
    fun finitePipeDoesNotWaitForProviderToCloseItsWriter() = checkProviderAudio("finite")

    @Test
    fun truncatedFinitePipeDoesNotAnalyzeAPartialSlice() = runBlocking {
        control("reset")
        try {
            try {
                runSongImport { cancelled ->
                    SongAudioDecoder(context).analyze(baseUri.buildUpon().appendPath("truncated").build(),
                        mode = SongAnalysisMode.NOTES, isCancelled = cancelled)
                }
                throw AssertionError("Accepted an incomplete asset slice")
            } catch (_: SongDecodeException) {
                Unit
            }
        } finally {
            control("release")
        }
    }

    private fun checkProviderAudio(path: String) = runBlocking {
        control("reset")
        try {
            val result = runSongImport { cancelled ->
                SongAudioDecoder(context).analyze(baseUri.buildUpon().appendPath(path).build(),
                    mode = SongAnalysisMode.NOTES, isCancelled = cancelled)
            }
            assertEquals(1000L, result.durationMillis)
            assertEquals(listOf(69), result.events.filterIsInstance<NoteEvent>().map { it.midiNote })
        } finally {
            control("release")
        }
    }

    @Test
    fun metadataCancellationStopsCooperativeProvider() = runBlocking {
        control("reset")
        val task = launch(Dispatchers.Default) {
            runSongImport { cancelled -> SongAudioDecoder(context).analyze(baseUri.buildUpon().appendPath("cooperative").build(), isCancelled = cancelled) }
        }
        try {
            await { control("status").getInt("queries") == 1 }
            task.cancelAndJoin()
            await { control("status").getInt("queries") == 0 }
        } finally {
            control("release")
            task.cancelAndJoin()
        }
    }

    @Test
    fun blockedReplacementsStayBoundedAndDoNotDelayUiCancellation() = runBlocking {
        control("reset")
        val uri = baseUri.buildUpon().appendPath("blocked").build()
        val tasks = List(2) {
            launch(Dispatchers.Default) {
                runSongImport { cancelled -> SongAudioDecoder(context).analyze(uri, isCancelled = cancelled) }
            }
        }
        var queued: Job? = null
        try {
            await { control("status").getInt("queries") == 2 }
            val started = SystemClock.elapsedRealtime()
            tasks.forEach { it.cancelAndJoin() }
            assertTrue("UI cancellation waited for the provider", SystemClock.elapsedRealtime() - started < 1000)
            queued = launch(start = CoroutineStart.UNDISPATCHED) {
                runSongImport { cancelled -> SongAudioDecoder(context).analyze(uri, isCancelled = cancelled) }
            }
            repeat(20) {
                try {
                    runSongImport { cancelled -> SongAudioDecoder(context).analyze(uri, isCancelled = cancelled) }
                    throw AssertionError("Queued another blocked import")
                } catch (_: SongDecodeException) {
                    assertEquals(2, control("status").getInt("queries"))
                }
            }
        } finally {
            queued?.cancelAndJoin()
            control("release")
            tasks.forEach { it.cancelAndJoin() }
            await { control("status").getInt("queries") == 0 }
            delay(200)
        }
    }

    @Test
    fun cancellationReclaimsAStalledSourcePipe() = runBlocking {
        control("reset")
        val existingFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        val task = launch(Dispatchers.Default) {
            runSongImport { cancelled -> SongAudioDecoder(context).analyze(baseUri.buildUpon().appendPath("pipe").build(), isCancelled = cancelled) }
        }
        try {
            await("Source pipe did not open") { control("status").getInt("pipes") == 1 }
            task.cancelAndJoin()
            await("Cancelled source worker retained its staging file") {
                context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("song-import-") && it.name !in existingFiles }
            }
            assertEquals("healthy", runSongImport { "healthy" })
        } finally {
            control("release")
            task.cancelAndJoin()
            delay(200)
        }
    }

    @Test
    fun cancelledResultDisposesItsRetainedSource() = runBlocking {
        val file = File.createTempFile("discard-import-", ".audio", context.cacheDir)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = launch(Dispatchers.Default) {
            runSongImport(onDiscard = { discarded: File -> discarded.delete() }) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                file
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            task.cancelAndJoin()
            release.countDown()
            await { !file.exists() }
        } finally {
            release.countDown()
            task.cancelAndJoin()
            file.delete()
        }
    }

    private fun control(method: String): Bundle = requireNotNull(context.contentResolver.call(baseUri, method, null, null))

    private suspend fun await(message: String = "Provider did not reach expected state", condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            assertTrue(message, SystemClock.elapsedRealtime() < deadline)
            delay(25)
        }
    }
}
internal fun verifyFftCancellationBoundaries(
    transform: (DoubleArray, DoubleArray, () -> Boolean) -> Unit,
) {
    for (size in intArrayOf(1, 2, 2048)) {
        val real = DoubleArray(size) { it + 1.0 }
        val imaginary = DoubleArray(size) { -it - 1.0 }
        val originalReal = real.copyOf()
        val originalImaginary = imaginary.copyOf()
        assertThrows("Entry cancellation N=$size", CancellationException::class.java) {
            transform(real, imaginary) { true }
        }
        assertArrayEquals("Cancelled entry changed real N=$size", originalReal, real, 0.0)
        assertArrayEquals("Cancelled entry changed imaginary N=$size", originalImaginary, imaginary, 0.0)
    }

    val lastReal = doubleArrayOf(1.0, 2.0)
    assertThrows("Cancellation after the last butterfly", CancellationException::class.java) {
        transform(lastReal, DoubleArray(2)) { lastReal[0] == 3.0 }
    }
    assertArrayEquals(doubleArrayOf(3.0, -1.0), lastReal, 0.0)

    val stageReal = DoubleArray(2048) { 1.0 }
    assertThrows("Cancellation between FFT stages", CancellationException::class.java) {
        transform(stageReal, DoubleArray(2048)) {
            stageReal[0] == 1024.0 && stageReal[1024] == 1024.0
        }
    }
    assertEquals(1024.0, stageReal[0], 0.0)
    assertEquals(1024.0, stageReal[1024], 0.0)

    for (size in intArrayOf(2048, 4096, 32768)) {
        val real = DoubleArray(size).apply { this[size / 2] = 1.0 }
        val imaginary = DoubleArray(size)
        // This impulse leaves the second half zero until the final FFT stage.
        // Its first butterfly expires the budget; every populated bin counts one butterfly.
        val budget = SongImportBudget {
            if (real[0] == 1.0 && real[size / 2] == 1.0) SongImportBudget.MAX_WORK_NANOS else 0L
        }
        val error = assertThrows("FFT deadline N=$size", SongDecodeException::class.java) {
            transform(real, imaginary) { budget.check(); false }
        }
        assertEquals(SongDecodeError.DECODE_FAILED, error.reason)
        val butterflies = (size / 2 until size).count { real[it] != 0.0 || imaginary[it] != 0.0 }
        assertTrue("Deadline waited $butterflies butterflies N=$size", butterflies in 1..1024)
    }
}
