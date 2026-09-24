package com.tuneitall.tuner

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.NoteEvent
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.SongAnalysisMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SongChordDecoderTest {
    @Test
    fun localWavTempoRecognizesEqualLoudnessTimbreChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "timbre-tempo.wav")
        try {
            for (rate in listOf(44_100, 48_000)) for (channels in listOf(1, 2)) {
                file.writeBytes(pcm16Wav(channels = channels, seconds = 16, sampleRate = rate) { frame, channel ->
                    val frequency = if (frame / (rate / 2) % 2 == 0) 100.0 else 2_000.0
                    val value = 0.5 * sin(2 * PI * frequency * frame / rate)
                    if (channel == 0) value else -value
                })
                val result = SongAudioDecoder(context).analyzeTempo(Uri.fromFile(file))
                assertTrue("$rate Hz/$channels channels: $result", result != null && abs(result.bpm - 120) <= 2)
                assertTrue("Weak match: $result", requireNotNull(result).confidence > 0.7)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavNoiseDoesNotClaimAStrongTempo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "noise-tempo.wav")
        val random = kotlin.random.Random(42)
        try {
            file.writeBytes(pcm16Wav(channels = 1, seconds = 24) { _, _ -> random.nextDouble() * 0.5 - 0.25 })
            val result = SongAudioDecoder(context).analyzeTempo(Uri.fromFile(file))
            assertTrue("Noise reported a strong rhythm: $result", result == null || result.confidence < 0.2)
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavLowStringsSurviveSampleRateAndReferenceOffsets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "low-string.wav")
        try {
            for ((range, midi) in listOf(NoteRange.BASS to 21, NoteRange.BASS to 23, NoteRange.GUITAR to 23)) {
                for (rate in listOf(44_100, 48_000)) for (reference in listOf(432.0, 440.0, 444.0)) {
                    for (gain in listOf(0.01, 0.3)) {
                        val frequency = reference * 2.0.pow((midi - 69) / 12.0)
                        file.writeBytes(pcm16Wav(channels = 1, seconds = 2, sampleRate = rate) { frame, _ ->
                            gain * sin(2 * PI * frequency * frame / rate)
                        })
                        val events = SongAudioDecoder(context).analyze(Uri.fromFile(file), SongAnalysisMode.NOTES, range)
                            .events.filterIsInstance<NoteEvent>()
                        assertEquals("$range/$rate/$reference/$gain: $events", listOf(midi), events.map { it.midiNote })
                        assertEquals(0L, events.single().startMillis)
                        assertEquals(2000L, events.single().endMillis)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavOutOfBandTonesDoNotInventMusic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "out-of-band.wav")
        try {
            for (frequency in listOf(20.0, 24.0, 26.0)) for (rate in listOf(44_100, 48_000)) {
                for (gain in listOf(0.006, 0.6)) {
                    file.writeBytes(pcm16Wav(channels = 1, seconds = 2, sampleRate = rate) { frame, _ ->
                        gain * sin(2 * PI * frequency * frame / rate)
                    })
                    for (mode in listOf(SongAnalysisMode.NOTES, SongAnalysisMode.CHORDS)) {
                        val events = SongAudioDecoder(context).analyze(Uri.fromFile(file), mode).events
                        assertTrue("$frequency Hz/$rate/$mode/$gain: $events", events.isEmpty())
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavAmbiguousWeakSeventhKeepsItsBassRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ambiguous-weak-seventh.wav")
        val frequencies = intArrayOf(45, 48, 52, 55).map { 440.0 * 2.0.pow((it - 69) / 12.0) }
        try {
            for (seventh in listOf(0.04, 0.08)) {
                val amplitudes = doubleArrayOf(0.70, 0.48, 0.30, seventh)
                file.writeBytes(pcm16Wav(channels = 1, seconds = 2) { frame, _ ->
                    0.0055 * frequencies.indices.sumOf { note ->
                        amplitudes[note] * sin(2 * PI * frequencies[note] * frame / SAMPLE_RATE)
                    }
                })
                val events = SongAudioDecoder(context).analyze(Uri.fromFile(file)).events.filterIsInstance<ChordEvent>()
                assertEquals(events.toString(), listOf(Chord(9, ChordQuality.MINOR)), events.map { it.chord })
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavSpreadMajorVoicingsKeepTheirRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "spread-major-chord.wav")
        val intervals = intArrayOf(0, 7, 12, 16)
        val amplitudes = doubleArrayOf(0.70, 0.48, 0.30, 0.18)
        try {
            for (root in 52..63) for (gain in listOf(1.0, 0.01)) {
                val frequencies = intervals.map { 440.0 * 2.0.pow((root + it - 69) / 12.0) }
                file.writeBytes(pcm16Wav(channels = 1, seconds = 2) { frame, _ ->
                    0.55 * gain * frequencies.indices.sumOf { note ->
                        amplitudes[note] * sin(2 * PI * frequencies[note] * frame / SAMPLE_RATE)
                    }
                })
                val events = SongAudioDecoder(context).analyze(Uri.fromFile(file)).events.filterIsInstance<ChordEvent>()
                assertEquals("root=$root gain=$gain: $events", listOf(Chord(root % 12, ChordQuality.MAJOR)), events.map { it.chord })
                assertEquals(0L, events.single().startMillis)
                assertEquals(2000L, events.single().endMillis)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavDoesNotInventChordsAroundAShortRealChord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "single-note-chord.wav")
        try {
            file.writeBytes(pcm16Wav(channels = 1, seconds = 3) { frame, _ ->
                val frequencies = if (frame / SAMPLE_RATE == 1) doubleArrayOf(293.6648, 349.2282, 440.0)
                    else doubleArrayOf(440.0)
                0.6 * frequencies.sumOf { sin(2 * PI * it * frame / SAMPLE_RATE) } / frequencies.size
            })
            val events = SongAudioDecoder(context).analyze(Uri.fromFile(file)).events.filterIsInstance<ChordEvent>()
            assertEquals(listOf(Chord(2, ChordQuality.MINOR)), events.map { it.chord })
            assertTrue(events.toString(), events.single().startMillis in 800L..1200L)
            assertTrue(events.toString(), events.single().endMillis in 1800L..2200L)
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavRetainsMajorAndMinorNinthsWithoutVirtualBassInversions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ninth-chord.wav")
        try {
            for (root in listOf(40, 45, 49)) for ((quality, third) in listOf(
                ChordQuality.ADD_NINTH to 4, ChordQuality.MINOR_ADD_NINTH to 3,
            )) {
                val frequencies = intArrayOf(0, third, 7, 14).map { 440.0 * 2.0.pow((root + it - 69) / 12.0) }
                file.writeBytes(pcm16Wav(channels = 1, seconds = 2) { frame, _ ->
                    0.6 * frequencies.sumOf { sin(2 * PI * it * frame / SAMPLE_RATE) } / frequencies.size
                })
                val events = SongAudioDecoder(context).analyze(Uri.fromFile(file)).events.filterIsInstance<ChordEvent>()
                assertEquals(events.toString(), listOf(Chord(root % 12, quality)), events.map { it.chord })
                assertEquals(0L, events.single().startMillis)
                assertEquals(2000L, events.single().endMillis)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavViolinMelodySurvivesBassWithoutEarlyChangesOrBlankGaps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "melody-with-bass.wav")
        try {
            for (lastNote in listOf(64, 76, 88)) {
                val frequencies = doubleArrayOf(440.0, 523.25, 440.0 * 2.0.pow((lastNote - 69) / 12.0))
                file.writeBytes(pcm16Wav(channels = 1, seconds = 3) { frame, _ ->
                    0.6 * sin(2 * PI * frequencies[frame / SAMPLE_RATE] * frame / SAMPLE_RATE) +
                        0.15 * sin(2 * PI * 82.40689 * frame / SAMPLE_RATE)
                })
                val notes = SongAudioDecoder(context).analyze(Uri.fromFile(file), SongAnalysisMode.NOTES, NoteRange.VIOLIN)
                    .events.filterIsInstance<NoteEvent>()
                assertEquals(notes.toString(), listOf(69, 72, lastNote), notes.map { it.midiNote })
                assertEquals(0L, notes.first().startMillis)
                assertEquals(3000L, notes.last().endMillis)
                notes.drop(1).forEachIndexed { index, note ->
                    assertTrue(notes.toString(), abs(note.startMillis - (index + 1) * 1000L) <= 50L)
                    assertEquals(notes[index].endMillis, note.startMillis)
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavNoteModeKeepsMelodyAndEndsAtSilence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "note-melody.wav")
        val frequencies = doubleArrayOf(440.0, 523.25, 659.25)
        try {
            file.writeBytes(pcm16Wav(channels = 1, seconds = 8) { frame, _ ->
                val frequency = frequencies.getOrNull(frame / (SAMPLE_RATE * 2))
                if (frequency == null) 0.0 else 0.6 * sin(2.0 * PI * frequency * frame / SAMPLE_RATE)
            })
            val result = SongAudioDecoder(context).analyze(Uri.fromFile(file), SongAnalysisMode.NOTES)
            val notes = result.events.filterIsInstance<NoteEvent>()
            assertEquals(8_000L, result.durationMillis)
            assertEquals(listOf(69, 72, 76), notes.map { it.midiNote })
            assertTrue(notes.all { it.durationMillis >= 1_500L && it.endMillis <= 6_300L })
        } finally {
            file.delete()
        }
    }

    @Test
    fun stereoWavKeepsChordTonesThatCancelBeneathCenteredBass() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "stereo-phase-chord.wav")
        try {
            file.writeBytes(pcm16Wav(channels = 2, seconds = 4) { frame, channel ->
                val time = frame.toDouble() / SAMPLE_RATE
                val bass = 0.3 * sin(2.0 * PI * 130.81 * time)
                val chord = 0.16 * (sin(2.0 * PI * 261.63 * time) +
                    sin(2.0 * PI * 329.63 * time) + sin(2.0 * PI * 392.0 * time))
                bass + if (channel == 0) chord else -chord
            })

            val result = SongAudioDecoder(context).analyze(Uri.fromFile(file))
            val events = result.events.filterIsInstance<ChordEvent>()
            assertEquals(4_000L, result.durationMillis)
            assertTrue("Expected C major, got $events", events.isNotEmpty())
            assertEquals(Chord(0, ChordQuality.MAJOR), events.maxBy(ChordEvent::durationMillis).chord)
            assertTrue(events.filter { it.chord == Chord(0, ChordQuality.MAJOR) }.sumOf(ChordEvent::durationMillis) >= 3_400L)
        } finally {
            file.delete()
        }
    }

    @Test
    fun stereoWavKeepsTempoWhenCenteredToneMasksOppositePhaseBeats() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "stereo-phase-tempo.wav")
        try {
            file.writeBytes(pcm16Wav(channels = 2, seconds = 12) { frame, channel ->
                val background = 0.3 * sin(2.0 * PI * 220.0 * frame / SAMPLE_RATE)
                val withinBeat = frame % (SAMPLE_RATE / 2)
                val beat = if (withinBeat < SAMPLE_RATE / 50) {
                    0.3 * (1.0 - withinBeat.toDouble() / (SAMPLE_RATE / 50)) *
                        sin(2.0 * PI * 1_000.0 * frame / SAMPLE_RATE)
                } else 0.0
                background + if (channel == 0) beat else -beat
            })

            val estimate = SongAudioDecoder(context).analyzeTempo(Uri.fromFile(file))
            assertTrue("Expected 120 BPM, got $estimate", estimate != null && kotlin.math.abs(estimate.bpm - 120) <= 2)
        } finally {
            file.delete()
        }
    }

    @Test
    fun localWavDecoderRecognizesDistortedEPowerChord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "distorted-e5.wav")
        try {
            file.writeBytes(distortedPowerChordWav())

            val result = SongAudioDecoder(context).analyze(
                uri = Uri.fromFile(file),
                mode = SongAnalysisMode.POWER,
            )

            val event = result.events.filterIsInstance<ChordEvent>().maxBy(ChordEvent::durationMillis)
            assertEquals(Chord(4, ChordQuality.POWER), event.chord)
        } finally {
            file.delete()
        }
    }

    @Test
    fun externalDenseSongKeepsCoverageAndRuntimeBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "dense-song-qa.mp3")
        assumeTrue("Push the private QA fixture into the target cache before this test", file.isFile)
        assertEquals(DENSE_SONG_SHA256, sha256(file))

        val started = SystemClock.elapsedRealtime()
        val result = SongAudioDecoder(context).analyze(Uri.fromFile(file))
        val elapsed = SystemClock.elapsedRealtime() - started
        val labelledMillis = result.events.filterIsInstance<ChordEvent>().sumOf(ChordEvent::durationMillis)
        val coverage = labelledMillis.toDouble() / result.durationMillis
        println("dense-song duration=${result.durationMillis}ms events=${result.events.size} coverage=$coverage elapsed=${elapsed}ms")

        assertTrue("coverage=$coverage", coverage >= 0.85)
        assertTrue("elapsed=${elapsed}ms", elapsed <= 30_000L)
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02X".format(it) }
}

private fun distortedPowerChordWav(): ByteArray {
    return pcm16Wav(channels = 1, seconds = DURATION_SECONDS) { frame, _ ->
        val root = sin(2.0 * PI * ROOT_HERTZ * frame / SAMPLE_RATE)
        val fifth = sin(2.0 * PI * ROOT_HERTZ * 1.5 * frame / SAMPLE_RATE)
        0.75 * tanh(3.5 * (root + 0.8 * fifth))
    }
}

private fun pcm16Wav(channels: Int, seconds: Int, sampleRate: Int = SAMPLE_RATE, sample: (Int, Int) -> Double): ByteArray {
    val sampleCount = sampleRate * seconds
    val frameBytes = channels * Short.SIZE_BYTES
    val dataSize = sampleCount * frameBytes
    return ByteBuffer.allocate(WAV_HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray())
        putInt(36 + dataSize)
        put("WAVEfmt ".toByteArray())
        putInt(16)
        putShort(1.toShort())
        putShort(channels.toShort())
        putInt(sampleRate)
        putInt(sampleRate * frameBytes)
        putShort(frameBytes.toShort())
        putShort(Short.SIZE_BITS.toShort())
        put("data".toByteArray())
        putInt(dataSize)
        repeat(sampleCount) { frame ->
            repeat(channels) { channel ->
                val value = (sample(frame, channel) * Short.MAX_VALUE).roundToInt()
                putShort(value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
    }.array()
}

private const val SAMPLE_RATE = 48_000
private const val DURATION_SECONDS = 4
private const val ROOT_HERTZ = 82.41
private const val WAV_HEADER_SIZE = 44
private const val DENSE_SONG_SHA256 = "B816E91DB8E4379532686D5F1D541C92B54E5A4D2D291FA2B426CEFFBD05AE00"
