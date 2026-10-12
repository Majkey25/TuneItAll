package com.tuneitall.tuner

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.music.ArrangementMode
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.NoteEvent
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.music.arrangeForStandardE
import com.tuneitall.tuner.ui.ChordTab
import com.tuneitall.tuner.ui.ChordViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordViewModelTest {
    @Test
    fun changingToNotesKeepsTheSongAndPublishesOnlyNoteEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        val file = File(context.cacheDir, "mode-change-melody.wav")
        file.writeBytes(melodyWav())
        val viewModel = ChordViewModel(application)
        try {
            viewModel.loadSong(Uri.fromFile(file))
            viewModel.setSongAnalysisMode(SongAnalysisMode.NOTES)

            val state = withTimeout(15_000L) {
                viewModel.uiState.first {
                    it.analysisMode == SongAnalysisMode.NOTES && !it.analyzing && it.events.isNotEmpty()
                }
            }

            assertEquals(file.name, state.fileName)
            assertTrue(state.events.all { it is NoteEvent })
        } finally {
            viewModel.clearSong()
            file.delete()
        }
    }

    @Test
    fun returningFromLibraryKeepsNativePlaybackPausedUntilExplicitPlay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "tab-pause-melody.wav")
        file.writeBytes(melodyWav())
        val viewModel = ChordViewModel(context.applicationContext as Application)
        try {
            onMain {
                viewModel.loadSong(Uri.fromFile(file))
                viewModel.setSongAnalysisMode(SongAnalysisMode.NOTES)
            }
            withTimeout(15_000L) { viewModel.uiState.first { it.prepared && !it.analyzing } }
            val field = ChordViewModel::class.java.getDeclaredField("player").apply { isAccessible = true }
            val player = field.get(viewModel) as MediaPlayer
            onMain {
                player.setVolume(0f, 0f)
                viewModel.playPause()
                assertTrue(player.isPlaying)

                viewModel.setTab(ChordTab.LIBRARY)
                assertFalse(player.isPlaying)
                assertFalse(viewModel.uiState.value.playing)
                val pausedPosition = viewModel.uiState.value.positionMillis

                viewModel.setTab(ChordTab.SONG)
                assertFalse(player.isPlaying)
                assertFalse(viewModel.uiState.value.playing)
                assertEquals(pausedPosition, viewModel.uiState.value.positionMillis)

                viewModel.playPause()
                assertTrue(player.isPlaying)
                assertTrue(viewModel.uiState.value.playing)
            }
        } finally {
            onMain(viewModel::clearSong)
            file.delete()
        }
    }

    @Test
    fun initialArrangementUsesLatestTransposeAndLibraryDoesNotCancelAnalysis() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "initial-arrangement.wav")
        file.writeBytes(chordWav())
        val dispatcher = ControlledArrangementDispatcher()
        val viewModel = ChordViewModel(context.applicationContext as Application, SongAudioDecoder(context), dispatcher)
        try {
            onMain { viewModel.loadSong(Uri.fromFile(file)) }
            val original = dispatcher.takeNext()
            onMain {
                viewModel.setTranspose(5)
                viewModel.setTab(ChordTab.LIBRARY)
                assertTrue(viewModel.uiState.value.analyzing)
                assertTrue(viewModel.uiState.value.events.isEmpty())
                assertNull(viewModel.uiState.value.arrangement)
            }
            original.run()
            dispatcher.runUntil { !viewModel.uiState.value.analyzing }

            val state = viewModel.uiState.value
            val chords = state.events.filterIsInstance<ChordEvent>().map { it.chord.transpose(5) }
            assertTrue(chords.isNotEmpty())
            assertEquals(ChordTab.LIBRARY, state.tab)
            assertEquals(5, state.transposeSemitones)
            assertEquals(chords, requireNotNull(state.arrangement).instructions.map { it.soundingChord })
        } finally {
            onMain(viewModel::clearSong)
            dispatcher.runAll()
            file.delete()
        }
    }

    @Test
    fun completedOldRebuildCannotPublishOverLatestSettingsOrClearedSong() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "superseded-arrangement.wav")
        file.writeBytes(chordWav())
        val dispatcher = ControlledArrangementDispatcher()
        val viewModel = ChordViewModel(context.applicationContext as Application, SongAudioDecoder(context), dispatcher)
        try {
            onMain { viewModel.loadSong(Uri.fromFile(file)) }
            dispatcher.runUntil { viewModel.uiState.value.arrangement != null }
            val events = viewModel.uiState.value.events.filterIsInstance<ChordEvent>()
            assertTrue(events.isNotEmpty())
            val initialState = viewModel.uiState.value
            onMain { viewModel.setNoteRange(NoteRange.GUITAR) }
            assertSame(initialState.events, viewModel.uiState.value.events)
            assertSame(initialState.arrangement, viewModel.uiState.value.arrangement)

            onMain { viewModel.setTranspose(1) }
            val outdated = dispatcher.takeNext()
            onMain {
                val worker = Thread(outdated)
                worker.start()
                worker.join(5_000L)
                assertFalse("Arrangement worker did not complete", worker.isAlive)
                viewModel.setTranspose(2)
                viewModel.setArrangementMode(ArrangementMode.SIMPLIFIED)
                assertNull(viewModel.uiState.value.arrangement)
            }
            dispatcher.runUntil { viewModel.uiState.value.arrangement != null }
            val state = viewModel.uiState.value
            assertEquals(2, state.transposeSemitones)
            assertEquals(ArrangementMode.SIMPLIFIED, state.arrangementMode)
            assertEquals(
                arrangeForStandardE(events.map { it.chord.transpose(2) }, ChordShapeCatalog.load(context.resources), ArrangementMode.SIMPLIFIED),
                state.arrangement,
            )

            onMain { viewModel.setTranspose(3) }
            val beforeClear = dispatcher.takeNext()
            onMain(viewModel::clearSong)
            beforeClear.run()
            dispatcher.runAll()
            assertTrue(viewModel.uiState.value.events.isEmpty())
            assertNull(viewModel.uiState.value.arrangement)
            assertNull(viewModel.uiState.value.fileName)
        } finally {
            onMain(viewModel::clearSong)
            dispatcher.runAll()
            file.delete()
        }
    }

    @Test
    fun switchingToNotesDiscardsPendingChordArrangement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "pending-mode-arrangement.wav")
        file.writeBytes(chordWav())
        val dispatcher = ControlledArrangementDispatcher()
        val viewModel = ChordViewModel(context.applicationContext as Application, SongAudioDecoder(context), dispatcher)
        try {
            onMain { viewModel.loadSong(Uri.fromFile(file)) }
            dispatcher.runUntil { viewModel.uiState.value.arrangement != null }
            onMain { viewModel.setTranspose(1) }
            val pending = dispatcher.takeNext()
            onMain { viewModel.setSongAnalysisMode(SongAnalysisMode.NOTES) }
            pending.run()
            dispatcher.runAll()
            val state = withTimeout(15_000L) {
                viewModel.uiState.first { it.analysisMode == SongAnalysisMode.NOTES && !it.analyzing }
            }
            assertNull(state.arrangement)
            assertTrue(state.events.isNotEmpty())
            assertTrue(state.events.all { it is NoteEvent })
        } finally {
            onMain(viewModel::clearSong)
            dispatcher.runAll()
            file.delete()
        }
    }
}

private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }

private class ControlledArrangementDispatcher : CoroutineDispatcher() {
    private val queued = LinkedBlockingQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queued.add(block)
    }

    fun takeNext(): Runnable = requireNotNull(queued.poll(15, TimeUnit.SECONDS)) { "No arrangement work was dispatched" }

    fun runUntil(complete: () -> Boolean) {
        while (!complete()) {
            takeNext().run()
            onMain {}
        }
    }

    fun runAll() {
        onMain {}
        while (true) {
            val next = queued.poll() ?: return
            next.run()
            onMain {}
        }
    }
}

private fun melodyWav(): ByteArray {
    val frequencies = doubleArrayOf(440.0, 523.25, 659.25)
    val framesPerNote = SAMPLE_RATE
    val sampleCount = framesPerNote * frequencies.size
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
            val frequency = frequencies[frame / framesPerNote]
            val sample = (0.7 * sin(2.0 * PI * frequency * frame / SAMPLE_RATE) * Short.MAX_VALUE).roundToInt()
            putShort(sample.toShort())
        }
    }.array()
}

private fun chordWav(): ByteArray {
    val frequencies = doubleArrayOf(164.8138, 246.9417, 329.6276, 415.3047)
    val amplitudes = doubleArrayOf(0.70, 0.48, 0.30, 0.18)
    val dataSize = SAMPLE_RATE * 2 * Short.SIZE_BYTES
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
        repeat(SAMPLE_RATE * 2) { frame ->
            val sample = 0.55 * frequencies.indices.sumOf { note ->
                amplitudes[note] * sin(2.0 * PI * frequencies[note] * frame / SAMPLE_RATE)
            }
            putShort((sample * Short.MAX_VALUE).roundToInt().toShort())
        }
    }.array()
}

private const val SAMPLE_RATE = 48_000
private const val WAV_HEADER_SIZE = 44
