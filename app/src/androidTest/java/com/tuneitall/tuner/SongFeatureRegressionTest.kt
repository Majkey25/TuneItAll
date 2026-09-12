package com.tuneitall.tuner

import android.os.SystemClock
import android.util.Log
import com.tuneitall.tuner.music.HarmonicFrame
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.StreamingHarmonicFeatureExtractor
import com.tuneitall.tuner.music.analyzeNotes
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFeatureRegressionTest {
    @Test
    fun noteDecoderConsidersAllPreviousPitches() {
        val first = FloatArray(88).apply {
            this[61 - 21] = 0.30f
            this[63 - 21] = 0.45f
            this[100 - 21] = 0.50f
            val norm = sqrt(sumOf { it.toDouble() * it })
            indices.forEach { this[it] = (this[it] / norm).toFloat() }
        }
        val second = FloatArray(88).apply { this[60 - 21] = 1f }
        val frames = listOf(first, second).mapIndexed { index, salience ->
            HarmonicFrame(index * 200L, FloatArray(12), bassChroma = FloatArray(12),
                noteSalience = salience, tonalStrength = 1f, onsetStrength = 0f)
        }
        val notes = analyzeNotes(frames, NoteRange.ANY, 400L)
        assertEquals(listOf(63, 60), notes.map { it.midiNote })
        assertEquals(listOf(0L, 200L), notes.map { it.startMillis })
    }

    @Test
    fun fullLengthNoteDecodingHasBoundedCost() {
        val salience = FloatArray(88).apply { this[60 - 21] = 1f }
        val frames = List(21_177) { index ->
            HarmonicFrame(index * 85L, FloatArray(12), bassChroma = FloatArray(12),
                noteSalience = salience, tonalStrength = 1f, onsetStrength = 0f)
        }
        val started = SystemClock.elapsedRealtime()
        val notes = analyzeNotes(frames, NoteRange.ANY, 1_800_000L)
        val elapsed = SystemClock.elapsedRealtime() - started
        assertEquals(1, notes.size)
        assertEquals(60, notes.single().midiNote)
        assertEquals(1_800_000L, notes.single().endMillis)
        assertTrue("Thirty-minute note decoding took $elapsed ms", elapsed < 10_000L)
        Log.i("SongFeatureQA", "note decode frames=${frames.size} elapsedMs=$elapsed")
    }

    @Test
    fun silentOutroDoesNotBecomeAnEightSecondNote() {
        val rate = 48_000
        val frequencies = doubleArrayOf(130.81, 164.81, 196.0)
        val samples = FloatArray(rate * 10) { index ->
            if (index >= rate * 2) 0f else {
                (0.75 * frequencies.sumOf { sin(2 * PI * it * index / rate) } / frequencies.size).toFloat()
            }
        }
        // Deliberately no AudioTrack/player: this test analyzes PCM without playback.
        val started = SystemClock.elapsedRealtime()
        val frames = StreamingHarmonicFeatureExtractor(rate).apply { accept(samples) }.finish()
        val notes = analyzeNotes(frames, NoteRange.ANY, 10_000L)
        val elapsed = SystemClock.elapsedRealtime() - started
        val silence = frames.filter { it.startMillis >= 3_000L }

        assertTrue("Initial music was not analyzed", notes.any { it.startMillis < 2_000L })
        assertTrue("Invented notes in silence: $notes", notes.all { it.endMillis <= 3_000L })
        assertTrue(silence.isNotEmpty())
        assertTrue("Silent features contain residual notes", silence.all { frame ->
            listOf(frame.chroma, frame.contextChroma, frame.noteSalience, frame.bassChroma, frame.observedChroma)
                .all { values -> values.all { it == 0f } }
        })
        assertTrue("Ten-second analysis exceeded real time: $elapsed ms", elapsed < 10_000L)
        Log.i("SongFeatureQA", "silent outro frames=${silence.size} notes=$notes elapsedMs=$elapsed")
    }
}
