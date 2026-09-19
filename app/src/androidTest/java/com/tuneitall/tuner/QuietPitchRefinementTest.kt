package com.tuneitall.tuner

import android.util.Log
import com.tuneitall.tuner.audio.PitchTracker
import com.tuneitall.tuner.audio.AdaptiveNoiseFloor
import com.tuneitall.tuner.audio.isDetectorVoiced
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.YinPitchDetector
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietPitchRefinementTest {
    @Test
    fun coldQuietAcquisitionDoesNotLatchTheOctaveBelow() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        for ((rate, base, shift) in listOf(Triple(44_100, 146.8324, -30.0), Triple(48_000, 329.6276, 30.0))) {
            val frequency = base * 2.0.pow(shift / 1200.0)
            val random = Random(211)
            val samples = ShortArray(rate * 2) { index ->
                val phase = 2 * PI * frequency * index / rate + 0.7
                val amplitude = 0.0001 * minOf(index / (rate * 0.005), 1.0)
                val tone = amplitude * (sin(phase) + 0.45 * sin(2 * phase + 0.4) + 0.2 * sin(3 * phase - 0.3))
                ((tone + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
            }
            val detector = YinPitchDetector()
            val tracker = PitchTracker()
            val floor = AdaptiveNoiseFloor()
            val settings = TunerAudioSettings()
            var total = 0
            var correct = 0
            for (end in 8192..samples.size step 2048) {
                val frame = detector.analyze(samples.copyOfRange(end - 8192, end), rate, range.minHertz, range.maxHertz)
                floor.observe(frame.rms, frame.isDetectorVoiced)
                assertTrue(floor.accepts(frame.rms, settings.sensitivity, settings.noiseRejection))
                val pitch = tracker.update(frame, settings)
                if (end * 1000L / rate in 250L..1500L) {
                    total++
                    if (pitch != null) {
                        assertTrue("Wrong cold $frequency Hz: $pitch", abs(MusicMath.cents(pitch.hertz, frequency)) <= 10.0)
                        correct++
                    }
                }
            }
            Log.i("QuietRefinementQA", "cold rate=$rate frequency=$frequency correct=$correct/$total")
            assertTrue("Cold dropout: $correct/$total", correct >= total * 0.9)
        }
    }

    @Test
    fun highFrequencyRefinementFitsTheChromaticAudioHop() {
        for (frequency in listOf(1000.0, 2000.0, 4000.0)) {
            val detector = YinPitchDetector()
            val tracker = PitchTracker()
            val settings = TunerAudioSettings()
            val times = mutableListOf<Long>()
            repeat(30) { frameIndex ->
                val samples = ShortArray(8192) { index ->
                    val phase = 2 * PI * frequency * (index + frameIndex * 2048) / 48_000
                    ((0.2 * sin(phase) + 0.05 * sin(2 * phase)) * 32768.0).roundToInt().toShort()
                }
                times += measureNanoTime {
                    val frame = detector.analyze(samples, 48_000, 27.5, 4300.0)
                    val pitch = tracker.update(frame, settings)
                    assertTrue("Lost $frequency Hz: $frame", pitch != null && abs(MusicMath.cents(pitch.hertz, frequency)) < 3.0)
                }
            }
            val sorted = times.drop(5).sorted()
            val p95 = sorted[(sorted.size * 0.95).toInt()] / 1e6
            Log.i("QuietRefinementQA", "chromatic $frequency p95Ms=$p95")
            assertTrue("High-frequency DSP exceeds the audio hop: $p95", p95 < 42.67)
        }
    }

    @Test
    fun quietDecayRemainsAccurateWithinTheAudioHop() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))
        for (frequency in listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)) {
            val random = Random(51)
            val samples = ShortArray(48_000 * 4) { index ->
                val age = index / 48_000.0 - 1.0
                val amplitude = if (age in 0.0..2.5) 0.0007 * minOf(age / 0.008, 1.0) * exp(-age / 0.75) else 0.0
                val phase = 2 * PI * frequency * age
                val tone = amplitude * (sin(phase) + 0.45 * sin(2 * phase) + 0.2 * sin(3 * phase))
                ((tone + 0.0004 * random.nextDouble(-1.0, 1.0)) * 32768.0).roundToInt().toShort()
            }
            val detector = YinPitchDetector()
            val tracker = PitchTracker()
            val settings = TunerAudioSettings()
            val runtimes = mutableListOf<Long>()
            var total = 0
            var correct = 0
            for (end in 8192..samples.size step 2048) {
                val window = samples.copyOfRange(end - 8192, end)
                val elapsed = measureNanoTime {
                    val frame = detector.analyze(window, 48_000, range.minHertz, range.maxHertz)
                    val pitch = tracker.update(frame, settings)
                    if (end * 1000L / 48_000 in 1250L..3250L) {
                        total++
                        if (pitch != null) {
                            val error = abs(MusicMath.cents(pitch.hertz, frequency))
                            assertTrue("Inaccurate fresh $frequency Hz: $error cents", error <= 10.0)
                            correct++
                        }
                    }
                }
                runtimes += elapsed
            }
            val sorted = runtimes.drop(10).sorted()
            val p95 = sorted[(sorted.size * 0.95).toInt()] / 1e6
            Log.i("QuietRefinementQA", "$frequency correct=$correct/$total p95Ms=$p95")
            assertTrue("$frequency Hz: $correct/$total", correct >= total * 0.9)
            assertTrue("$frequency Hz exceeds the 42.67 ms audio hop: $p95", p95 < 42.67)
        }
    }
}
