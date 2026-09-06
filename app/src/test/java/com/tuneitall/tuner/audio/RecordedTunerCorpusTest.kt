package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional real-electric-guitar identity benchmark. MIDI labels do not establish cent accuracy. */
class RecordedTunerCorpusTest {
    @Test
    fun `evaluate frozen monophonic electric guitar windows`() {
        val directory = listOf(File(".reference/tmp/egset12"), File("../.reference/tmp/egset12"))
            .firstOrNull { File(it, "manifest.json").isFile }
        assumeTrue("Acquire optional EGSet12 tracks 01, 03 and 09 before this benchmark", directory != null)
        val root = requireNotNull(directory)
        val manifest = JSONObject(File(root, "manifest.json").readText())
        assertEquals("CC-BY-4.0", manifest.getString("license"))
        assertEquals(0.1, manifest.getDouble("attack_margin_seconds"))
        assertEquals(0.05, manifest.getDouble("tail_margin_seconds"))
        assertEquals(WINDOW_SIZE, manifest.getInt("window_size"))
        assertEquals(HOP_SIZE, manifest.getInt("hop_size"))
        val tracks = manifest.getJSONArray("tracks")
        assertEquals(3, tracks.length())
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.CHROMATIC, tuning, 0, ReferencePitch(440.0))
        val totals = GAINS.associateWith { Counts() }
        var windowCount = 0
        println("RECORDED,track,gain,windows,voiced,nominal_correct_50c,octave_errors,coverage,agreement")
        repeat(tracks.length()) { index ->
            val track = tracks.getJSONObject(index)
            val id = track.getString("id")
            assertEquals(listOf("01", "03", "09")[index], id)
            val files = track.getJSONArray("files")
            assertEquals(2, files.length())
            val audio = readPcm24(verifiedFile(root, "$id.wav", files.getJSONObject(1)))
            verifiedFile(root, "$id.jams", files.getJSONObject(0))
            assertEquals(track.getJSONObject("audio").getInt("sample_count"), audio.sampleCount)
            val windows = track.getJSONArray("eligible_windows")
            assertEquals(listOf(45, 23, 25)[index], windows.length())
            windowCount += windows.length()
            for (gain in GAINS) {
                val detector = YinPitchDetector()
                val tracker = PitchTracker()
                val settings = TunerAudioSettings()
                val counts = Counts()
                var previousEnd = -1
                repeat(windows.length()) { windowIndex ->
                    val annotation = windows.getJSONObject(windowIndex)
                    val end = annotation.getInt("end_sample")
                    val midi = annotation.getInt("midi")
                    require(end > previousEnd && end in WINDOW_SIZE..audio.sampleCount && end % HOP_SIZE == 0)
                    require(midi in 0..127)
                    if (end != previousEnd + HOP_SIZE) tracker.reset()
                    previousEnd = end
                    val samples = ShortArray(WINDOW_SIZE) { sampleIndex ->
                        val offset = audio.dataOffset + (end - WINDOW_SIZE + sampleIndex) * 6
                        val raw = (audio.bytes[offset].toInt() and 0xff) or
                            ((audio.bytes[offset + 1].toInt() and 0xff) shl 8) or
                            (audio.bytes[offset + 2].toInt() shl 16)
                        (raw * gain / 256.0).roundToInt().coerceIn(-32768, 32767).toShort()
                    }
                    val frame = detector.analyze(samples, SAMPLE_RATE, range.minHertz, range.maxHertz)
                    val estimate = tracker.update(frame, settings)
                    counts.windows++
                    if (estimate != null) {
                        counts.voiced++
                        val nominalError = abs(100.0 * (69.0 + 12.0 * log2(estimate.hertz / 440.0) - midi))
                        if (nominalError <= 50.0) counts.correct++
                        if (abs(nominalError - 1200.0) <= 50.0) counts.octaves++
                    }
                }
                counts.report(id, gain)
                totals.getValue(gain).add(counts)
            }
        }
        assertEquals(93, windowCount)
        totals.forEach { (gain, counts) -> counts.report("TOTAL", gain) }
        // Diagnostic only until a frozen baseline is measured. No invented accuracy threshold.
    }

    private fun verifiedFile(root: File, expectedName: String, metadata: JSONObject): ByteArray {
        assertEquals(expectedName, metadata.getString("path"))
        val bytes = File(root, expectedName).readBytes()
        assertEquals(metadata.getInt("bytes"), bytes.size)
        for ((algorithm, key) in listOf("MD5" to "md5", "SHA-256" to "sha256")) {
            val actual = MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(metadata.getString(key).substringAfter(':'), actual, "$expectedName $algorithm")
        }
        return bytes
    }

    private fun readPcm24(bytes: ByteArray): Pcm24 {
        require(bytes.size >= 44 && bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WAVE")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(bytes.size - 8, buffer.getInt(4))
        var offset = 12
        var formatChecked = false
        var dataOffset = -1
        var sampleCount = 0
        while (offset + 8 <= bytes.size) {
            val id = bytes.decodeToString(offset, offset + 4)
            val size = buffer.getInt(offset + 4)
            offset += 8
            require(size >= 0 && size <= bytes.size - offset)
            when (id) {
                "fmt " -> {
                    require(!formatChecked && size == 16)
                    assertEquals(1, buffer.getShort(offset).toInt())
                    assertEquals(2, buffer.getShort(offset + 2).toInt())
                    assertEquals(SAMPLE_RATE, buffer.getInt(offset + 4))
                    assertEquals(SAMPLE_RATE * 6, buffer.getInt(offset + 8))
                    assertEquals(6, buffer.getShort(offset + 12).toInt())
                    assertEquals(24, buffer.getShort(offset + 14).toInt())
                    formatChecked = true
                }
                "data" -> {
                    require(formatChecked && dataOffset == -1 && size > 0 && size % 6 == 0)
                    dataOffset = offset
                    sampleCount = size / 6
                }
            }
            offset += size + size % 2
        }
        assertTrue(formatChecked && dataOffset >= 0 && offset == bytes.size)
        return Pcm24(bytes, dataOffset, sampleCount)
    }

    private data class Pcm24(val bytes: ByteArray, val dataOffset: Int, val sampleCount: Int)

    private class Counts {
        var windows = 0
        var voiced = 0
        var correct = 0
        var octaves = 0

        fun add(other: Counts) {
            windows += other.windows
            voiced += other.voiced
            correct += other.correct
            octaves += other.octaves
        }

        fun report(track: String, gain: Double) {
            println("RECORDED,$track,$gain,$windows,$voiced,$correct,$octaves," +
                "${voiced.toDouble() / windows},${correct.toDouble() / windows}")
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48000
        const val WINDOW_SIZE = 8192
        const val HOP_SIZE = 2048
        val GAINS = listOf(1.0, 0.01, 0.001)
    }
}
