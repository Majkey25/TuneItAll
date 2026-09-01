package com.tuneitall.tuner

import android.app.Application
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.music.NoteEvent
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.ui.ChordViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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

private const val SAMPLE_RATE = 48_000
private const val WAV_HEADER_SIZE = 44
