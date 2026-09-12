package com.tuneitall.tuner

import android.Manifest
import android.app.Application
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
import com.tuneitall.tuner.audio.AdaptiveNoiseFloor
import com.tuneitall.tuner.audio.PitchFrame
import com.tuneitall.tuner.audio.FeedbackInputGate
import com.tuneitall.tuner.audio.ResponseMode
import com.tuneitall.tuner.audio.createConfirmationChime
import com.tuneitall.tuner.audio.isDetectorVoiced
import com.tuneitall.tuner.audio.PitchTracker
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.YinPitchDetector
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import com.tuneitall.tuner.ui.TunerViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TunerAudioRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changingDetectionContextPreservesPendingSpeakerFeedback() {
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext<Application>())
        val original = viewModel.uiState.value
        // Exercise the real reset path without adding a production-only test hook or playing sound.
        val field = TunerViewModel::class.java.getDeclaredField("feedbackInputGate").apply { isAccessible = true }
        val gate = field.get(viewModel) as FeedbackInputGate
        gate.suppress(1_000L, 400L)
        try {
            viewModel.selectMode(if (original.mode == TunerMode.CHROMATIC) TunerMode.AUTO else TunerMode.CHROMATIC)
            assertFalse("Mode change cleared the speaker tail", gate.accepts(1_100L))
            val response = if (original.audioSettings.response == ResponseMode.FAST) ResponseMode.STABLE else ResponseMode.FAST
            viewModel.setTunerAudioSettings(original.audioSettings.copy(response = response))
            assertFalse("Settings change cleared the speaker tail", gate.accepts(1_200L))
            viewModel.selectTuning(requireNotNull(TuningCatalog.byId("bass-5-standard")))
            assertFalse("Tuning change cleared the speaker tail", gate.accepts(1_399L))
            assertTrue("Speaker protection did not expire", gate.accepts(1_400L))
        } finally {
            viewModel.selectMode(original.mode)
            viewModel.setTunerAudioSettings(original.audioSettings)
            viewModel.selectTuning(original.tuning)
            viewModel.setHeadstockLayout(original.headstockLayout)
        }
    }

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
    fun quietRetuneKeepsTheFundamentalAfterCandidateDropout() {
        for ((initialHertz, seed) in listOf(329.6276 to 81, 146.8324 to 31, 195.9977 to 31)) {
            checkQuietRetune(initialHertz, seed)
        }
    }

    private fun checkQuietRetune(initialHertz: Double, seed: Int) {
        val rate = 48_000
        val expected = initialHertz * 2.0.pow(30.0 / 1200.0)
        val random = Random(seed)
        var phase = 0.0
        val samples = ShortArray(rate * 3) { index ->
            val hertz = if (index < rate / 2) initialHertz else expected
            val amplitude = if (index < rate / 2) 0.01 else 0.0001
            phase += 2 * PI * hertz / rate
            val signal = amplitude * (sin(phase) + 0.45 * sin(2 * phase))
            ((signal + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
        }
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val tracker = PitchTracker()
        val settings = TunerAudioSettings()
        val timings = mutableListOf<Double>()
        val settled = mutableListOf<Double?>()
        for (end in 8192..samples.size step 2048) {
            val window = samples.copyOfRange(end - 8192, end)
            val start = SystemClock.elapsedRealtimeNanos()
            val frame = detector.analyze(window, rate, range.minHertz, range.maxHertz)
            val estimate = tracker.update(frame, settings)
            val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
            if (end * 1000L / rate in 1500L..2500L) {
                timings += elapsed
                settled += estimate?.hertz
            }
        }
        val correct = settled.count { it != null && abs(MusicMath.cents(it, expected)) <= 10.0 }
        val p95 = timings.sorted()[(timings.size * 0.95).toInt()]
        Log.i("TunerAudioQA", "quiet retune initial=$initialHertz seed=$seed correct=$correct/${settled.size} p95Ms=$p95")
        assertTrue("Quiet retune latched a wrong pitch: $settled", correct >= settled.size * 0.9)
        assertTrue("Retune DSP exceeds the audio hop: $p95", p95 < 42.7)
    }

    @Test
    fun quietLowNotesRemainAccurateWithinAudioHopBudget() {
        checkQuietRange(listOf(30.8677, 41.2034, 82.4069, 110.0), 25.0, 150.0)
    }

    @Test
    fun quietGuitarNotesRemainAccurateWithinDefaultAutoHopBudget() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val reference = ReferencePitch(440.0)
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, reference)
        checkQuietRange(tuning.notesLowToHigh.map { MusicMath.frequency(it, reference) }, range.minHertz, range.maxHertz)
    }

    @Test
    fun quietGuitarStaysFreshDuringConfirmationEcho() {
        val rate = 48_000
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val chime = createConfirmationChime(sampleRate = rate)
        val timings = mutableListOf<Double>()
        for (note in tuning.notesLowToHigh) {
            val hertz = MusicMath.frequency(note, ReferencePitch(440.0))
            val random = Random(note.value)
            val samples = ShortArray(2 * rate) { index ->
                val phase = 2 * PI * hertz * index / rate
                val guitar = 0.00035 * (sin(phase) + 0.4 * sin(2 * phase) + 0.2 * sin(3 * phase))
                val direct = chime.getOrNull(index - rate - rate / 50)?.toDouble() ?: 0.0
                val echo = chime.getOrNull(index - rate - rate * 3 / 50)?.toDouble() ?: 0.0
                ((guitar + 0.00016 * random.nextDouble(-1.0, 1.0)) * Short.MAX_VALUE +
                    (direct + 0.4 * echo) * 0.1).roundToInt().toShort()
            }
            val detector = YinPitchDetector()
            val tracker = PitchTracker()
            val floor = AdaptiveNoiseFloor()
            val settings = TunerAudioSettings()
            var measured = 0
            var primed = 0
            var maxCents = 0.0
            for (end in 8192..samples.size step 2048) {
                val nowMillis = end * 1000L / rate
                val window = samples.copyOfRange(end - 8192, end)
                val started = SystemClock.elapsedRealtimeNanos()
                val frame = detector.analyze(
                    window, rate, range.minHertz, range.maxHertz,
                    rejectConfirmation = nowMillis in 1000L until 1400L,
                )
                floor.observe(frame.rms, frame.isDetectorVoiced)
                val estimate = tracker.update(
                    if (floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection)) frame
                    else PitchFrame(emptyList(), frame.rms, frame.peak, 1.0),
                    settings,
                )
                val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                if (nowMillis in 700L..1000L) {
                    assertTrue("Quiet control missing: ${note.value}", estimate != null)
                    assertTrue(abs(MusicMath.cents(requireNotNull(estimate).hertz, hertz)) <= 3.0)
                    primed++
                }
                if (nowMillis in 1000L..1300L) {
                    timings += elapsed
                    assertTrue("Chime interrupted ${note.value}", estimate != null)
                    val error = abs(MusicMath.cents(requireNotNull(estimate).hertz, hertz))
                    assertTrue("Chime changed ${note.value} by $error cents", error <= 3.0)
                    maxCents = maxOf(maxCents, error)
                    measured++
                }
            }
            assertEquals(7, primed)
            assertEquals(7, measured)
            Log.i("TunerAudioQA", "confirmation echo midi=${note.value} fresh=$measured/7 maxCents=$maxCents")
        }
        val p95 = timings.sorted()[(timings.size * 0.95).toInt()]
        Log.i("TunerAudioQA", "confirmation rejection p95Ms=$p95")
        assertTrue("Feedback rejection exceeds audio hop: $p95", p95 < 42.7)
    }

    @Test
    fun ambiguousDecayRetainsCurrentFundamentalWithinAudioHopBudget() {
        val encoded = requireNotNull(javaClass.getResourceAsStream("/audio/quiet-d3-decay.s16le.b64"))
            .bufferedReader().use { it.readText() }
        val bytes = Base64.getDecoder().decode(encoded.trim())
        assertEquals(16_384, bytes.size)
        assertEquals("d27fc8f5fd3c04276591da234f9875600a50e58b320ecce28733765a95f29aea",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(8192).also { buffer.get(it) }
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        val detector = YinPitchDetector()
        val timings = mutableListOf<Double>()
        repeat(55) { iteration ->
            val started = SystemClock.elapsedRealtimeNanos()
            val frame = detector.analyze(samples, 48_000, range.minHertz, range.maxHertz)
            val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            if (iteration >= 5) timings += elapsed
            assertTrue("Missing continuation-only D3: $frame", frame.candidates.any {
                it.probability == 0.0 && abs(MusicMath.cents(it.hertz, 146.8324)) <= 25.0
            })
        }
        val p95 = timings.sorted()[(timings.size * 0.95).toInt()]
        Log.i("TunerAudioQA", "ambiguous D3 refinement p95Ms=$p95 averageMs=${timings.average()}")
        assertTrue("Ambiguous refinement exceeds the 42.7 ms audio hop: $p95", p95 < 42.7)
    }

    private fun checkQuietRange(frequencies: List<Double>, minimum: Double, maximum: Double) {
        val detector = YinPitchDetector()
        val timings = mutableListOf<Double>()
        frequencies.forEach { frequency ->
            repeat(12) { frame ->
                val samples = ShortArray(8192) { index ->
                    val time = (index + frame * 2048) / 48_000.0
                    ((0.0004 * sin(2 * PI * frequency * time) +
                        0.00016 * sin(4 * PI * frequency * time) +
                        0.004 * sin(2 * PI * 6421.0 * time)) * 32767).toInt().toShort()
                }
                val start = SystemClock.elapsedRealtimeNanos()
                val result = detector.analyze(samples, 48_000, minimum, maximum)
                val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                if (frame > 1) timings += elapsed
                val candidate = result.candidates.maxByOrNull { it.probability }
                assertTrue("Missing low note $frequency", candidate != null)
                val cents = 1200.0 * ln(requireNotNull(candidate).hertz / frequency) / ln(2.0)
                assertTrue("$frequency Hz error=$cents cents", abs(cents) < 5.0)
            }
        }
        val p95 = timings.sorted()[(timings.size * 0.95).toInt().coerceAtMost(timings.lastIndex)]
        Log.i("TunerAudioQA", "quiet DSP range=$minimum..$maximum p95Ms=$p95 averageMs=${timings.average()}")
        assertTrue("DSP exceeds the 42.7 ms microphone hop: $p95", p95 < 42.7)
    }
}
