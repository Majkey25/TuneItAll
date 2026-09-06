package com.tuneitall.tuner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.audio.AudioInput
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.YinPitchDetector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TunerAudioRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportFloatMicrophonePrecisionWithoutSavingAudio() {
        composeRule.setContent { }
        val minimum = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        assumeTrue("Device does not support float microphone capture", minimum > 0)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 8192))
            .build()
        try {
            assumeTrue("Float microphone did not initialize", recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            val samples = FloatArray(2048)
            var total = 0
            var nonzero = 0
            var fractionalPcm16 = 0
            repeat(24) {
                val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                assertTrue("Float microphone read failed: $count", count > 0)
                total += count
                for (index in 0 until count) {
                    val value = samples[index]
                    assertTrue("Microphone returned non-finite PCM", value.isFinite())
                    if (value != 0f) nonzero++
                    val scaled = value * 32768.0
                    if (abs(scaled - round(scaled)) > 0.001) fractionalPcm16++
                }
            }
            Log.i("TunerAudioQA", "float capture samples=$total nonzero=$nonzero subPcm16Steps=$fractionalPcm16")
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }

    @Test
    fun microphoneDeliversRegularOverlappingWindowsAndCanRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        composeRule.setContent { }
        val input = AudioInput(context)
        try {
            repeat(2) { run ->
                val completed = CountDownLatch(30)
                val timestamps = mutableListOf<Long>()
                var failure: String? = null
                input.start(
                    windowSize = 8192,
                    source = AudioInputSource.AUTO,
                    onWindow = { window, rate ->
                        if (completed.count > 0) {
                            if (window.size != 8192 || rate != 48_000) {
                                failure = "Unexpected microphone format: ${window.size} samples at $rate Hz"
                            }
                            timestamps += SystemClock.elapsedRealtimeNanos()
                            completed.countDown()
                        }
                    },
                    onStarted = { Log.i("TunerAudioQA", "capture=$it") },
                    onError = { failure = it.toString() },
                )
                val received = completed.await(8, TimeUnit.SECONDS)
                input.stop()
                assertTrue("capture error=$failure; windows=${timestamps.size}", received && failure == null)
                val intervals = timestamps.zipWithNext { first, second -> (second - first) / 1_000_000.0 }
                val average = intervals.average()
                val bursts = intervals.count { it < 10.0 }
                Log.i("TunerAudioQA", "run=$run averageMs=$average bursts=$bursts intervals=$intervals")
                assertTrue("Unexpected microphone cadence: $average ms", average in 30.0..65.0)
                assertTrue("Microphone callbacks arrive in bursts: $intervals", bursts <= 4)
            }
        } finally {
            input.close()
        }
    }

    @Test
    fun quietLowNotesRemainAccurateWithinAudioHopBudget() {
        val detector = YinPitchDetector()
        val timings = mutableListOf<Double>()
        listOf(30.8677, 41.2034, 82.4069, 110.0).forEach { frequency ->
            repeat(12) { frame ->
                val samples = ShortArray(8192) { index ->
                    val time = (index + frame * 2048) / 48_000.0
                    ((0.0004 * sin(2 * PI * frequency * time) +
                        0.00016 * sin(4 * PI * frequency * time) +
                        0.004 * sin(2 * PI * 6421.0 * time)) * 32767).toInt().toShort()
                }
                val start = SystemClock.elapsedRealtimeNanos()
                val result = detector.analyze(samples, 48_000, 25.0, 150.0)
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                if (frame > 1) timings += elapsed
                val candidate = result.candidates.maxByOrNull { it.probability }
                assertTrue("Missing low note $frequency", candidate != null)
                val cents = 1200.0 * ln(requireNotNull(candidate).hertz / frequency) / ln(2.0)
                assertTrue("$frequency Hz error=$cents cents", abs(cents) < 5.0)
            }
        }
        val p95 = timings.sorted()[(timings.size * 0.95).toInt().coerceAtMost(timings.lastIndex)]
        Log.i("TunerAudioQA", "quiet low-note DSP p95Ms=$p95 averageMs=${timings.average()}")
        assertTrue("DSP exceeds the 42.7 ms microphone hop: $p95", p95 < 42.7)
    }
}
