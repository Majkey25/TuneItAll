package com.tuneitall.tuner

import android.os.SystemClock
import android.util.Log
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.StreamingHarmonicFeatureExtractor
import com.tuneitall.tuner.music.analyzeNotes
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFeatureRegressionTest {
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
