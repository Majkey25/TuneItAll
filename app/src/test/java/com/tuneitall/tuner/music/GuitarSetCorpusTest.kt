package com.tuneitall.tuner.music

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToLong
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals

/** Optional full-recording benchmark; fixture provenance and hashes are frozen in its manifest. */
class GuitarSetCorpusTest {
    @Test
    fun `evaluate frozen performed chord corpus`() {
        val directory = listOf(File(".reference/tmp/chord-benchmark"), File("../.reference/tmp/chord-benchmark"))
            .firstOrNull { File(it, "manifest.json").isFile }
        assumeTrue("Acquire the optional GuitarSet corpus before running this benchmark", directory != null)
        val root = requireNotNull(directory)
        val tracks = JSONObject(File(root, "manifest.json").readText()).getJSONArray("tracks")
        var rootCorrect = 0L
        var qualityCorrect = 0L
        var referenceTotal = 0L
        var qualityTotal = 0L
        var covered = 0L
        var elapsedTotal = 0L
        println("CORPUS,track,root,quality_supported,quality_duration,coverage,events,millis")
        repeat(tracks.length()) { index ->
            val track = tracks.getJSONObject(index)
            val audio = File(root, track.getString("wav_path"))
            val bytes = audio.readBytes()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(track.getString("wav_sha256"), hash)
            val samples = readMonoWav(bytes, track.getInt("sample_rate"))
            val rate = track.getInt("sample_rate")
            val duration = samples.size * 1000L / rate
            val started = System.nanoTime()
            val analyzer = StreamingChordAnalyzer(rate)
            for (offset in samples.indices step 4096) {
                analyzer.accept(samples.copyOfRange(offset, minOf(offset + 4096, samples.size)))
            }
            val events = analyzer.finish()
            val elapsed = (System.nanoTime() - started) / 1_000_000
            events.forEach { event ->
                println("PRED,${track.getString("track_id")},${event.startMillis},${event.endMillis}," +
                    "${event.chord.rootPitchClass},${event.chord.quality},${event.confidence}")
            }
            val annotations = track.getJSONArray("performed_chords")
            var correctRoot = 0L
            var correctQuality = 0L
            var labelled = 0L
            var supported = 0L
            repeat(annotations.length()) { annotationIndex ->
                val annotation = annotations.getJSONObject(annotationIndex)
                val label = annotation.getString("label")
                val pitch = requireNotNull(ROOTS[label.substringBefore(':')]) { "Unknown root: $label" }
                val quality = QUALITIES[label.substringAfter(':').substringBefore('/')]
                val start = (annotation.getDouble("start_seconds") * 1000).roundToLong()
                val end = minOf(duration, (annotation.getDouble("end_seconds") * 1000).roundToLong())
                labelled += end - start
                if (quality != null) supported += end - start
                events.forEach { event ->
                    val overlap = (minOf(end, event.endMillis) - maxOf(start, event.startMillis)).coerceAtLeast(0L)
                    if (event.chord.rootPitchClass == pitch) {
                        correctRoot += overlap
                        if (quality != null && event.chord.quality == quality) correctQuality += overlap
                    }
                }
            }
            val coverage = events.sumOf(ChordEvent::durationMillis)
            println("CORPUS,${track.getString("track_id")},${correctRoot.toDouble() / labelled}," +
                "${if (supported == 0L) "NA" else correctQuality.toDouble() / supported}," +
                "$supported,${coverage.toDouble() / duration},${events.size},$elapsed")
            rootCorrect += correctRoot
            qualityCorrect += correctQuality
            referenceTotal += labelled
            qualityTotal += supported
            covered += coverage
            elapsedTotal += elapsed
        }
        println("CORPUS,TOTAL,${rootCorrect.toDouble() / referenceTotal},${qualityCorrect.toDouble() / qualityTotal}," +
            "$qualityTotal,${covered.toDouble() / referenceTotal},${tracks.length()},$elapsedTotal")
    }

    private fun readMonoWav(bytes: ByteArray, sampleRate: Int): FloatArray {
        require(bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WAVE")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var formatChecked = false
        while (offset + 8 <= bytes.size) {
            val id = bytes.decodeToString(offset, offset + 4)
            val size = buffer.getInt(offset + 4)
            offset += 8
            require(size >= 0 && size <= bytes.size - offset)
            if (id == "fmt ") {
                require(size >= 16)
                assertEquals(1, buffer.getShort(offset).toInt())
                assertEquals(1, buffer.getShort(offset + 2).toInt())
                assertEquals(sampleRate, buffer.getInt(offset + 4))
                assertEquals(16, buffer.getShort(offset + 14).toInt())
                formatChecked = true
            }
            if (id == "data") {
                require(formatChecked && size % 2 == 0)
                return FloatArray(size / 2) { buffer.getShort(offset + it * 2) / 32768f }
            }
            offset += size + size % 2
        }
        error("Missing PCM data")
    }

    private companion object {
        val ROOTS = mapOf("C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
            "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8,
            "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11)
        val QUALITIES = mapOf("maj" to ChordQuality.MAJOR, "min" to ChordQuality.MINOR,
            "7" to ChordQuality.DOMINANT_SEVENTH, "maj7" to ChordQuality.MAJOR_SEVENTH,
            "min7" to ChordQuality.MINOR_SEVENTH, "maj6" to ChordQuality.MAJOR_SIXTH,
            "min6" to ChordQuality.MINOR_SIXTH, "sus2" to ChordQuality.SUSPENDED_SECOND,
            "sus4" to ChordQuality.SUSPENDED_FOURTH, "dim" to ChordQuality.DIMINISHED,
            "aug" to ChordQuality.AUGMENTED, "hdim7" to ChordQuality.HALF_DIMINISHED_SEVENTH,
            "5" to ChordQuality.POWER, "add9" to ChordQuality.ADD_NINTH)
    }
}
