package com.tuneitall.tuner.music

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional frontend experiment. Basic Pitch trained on GuitarSet; this is not a held-out model test. */
class BasicPitchCorpusTest {
    @Test
    fun `evaluate frozen note posteriors with unchanged chord decoder`() {
        val directory = listOf(File(".reference/tmp/chord-benchmark"), File("../.reference/tmp/chord-benchmark"))
            .firstOrNull { File(it, "model-eval/basic_pitch/manifest.json").isFile }
        assumeTrue("Export the optional Basic Pitch posterior corpus first", directory != null)
        val root = requireNotNull(directory)
        val posteriorRoot = File(root, "model-eval/basic_pitch")
        val references = JSONObject(File(root, "manifest.json").readText()).getJSONArray("tracks")
        val tracks = JSONObject(File(posteriorRoot, "manifest.json").readText()).getJSONArray("tracks")
        val totals = VARIANTS.associateWith { Scores() }
        println("BASIC_PITCH,threshold,track,root,quality_supported,quality_duration,coverage,events,millis")
        repeat(tracks.length()) { trackIndex ->
            val track = tracks.getJSONObject(trackIndex)
            val reference = references.getJSONObject(trackIndex)
            assertEquals(reference.getString("track_id"), track.getString("track_id"))
            assertEquals(reference.getString("wav_sha256"), track.getString("source_wav_sha256"))
            val frameCount = track.getInt("frames")
            val notes = readPosteriors(posteriorRoot, track.getJSONObject("notes"), frameCount)
            val onsets = readPosteriors(posteriorRoot, track.getJSONObject("onsets"), frameCount)
            val timesJson = JSONArray(File(posteriorRoot, track.getString("timestamps_ms")).readText())
            assertEquals(frameCount, timesJson.length())
            val times = LongArray(frameCount, timesJson::getLong)
            assertTrue((1 until times.size).all { times[it] > times[it - 1] })
            val duration = (reference.getDouble("duration_seconds") * 1000).toLong()
            val profileFrame = times.indices.minBy { kotlin.math.abs(times[it] - 1000L) }
            val profile = (0 until NOTE_COUNT).sortedByDescending { notes[profileFrame * NOTE_COUNT + it] }.take(6)
            println("BP_PROFILE,${track.getString("track_id")},${times[profileFrame]}," +
                profile.joinToString(";") { "${it + MIN_MIDI}:${notes[profileFrame * NOTE_COUNT + it]}" })
            VARIANTS.forEach { variant ->
                val started = System.nanoTime()
                val frames = harmonicFrames(notes, onsets, times, duration, variant.threshold, variant.floor)
                val events = analyzeChords(frames, SongAnalysisMode.CHORDS, duration)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                val scores = evaluate(reference.getJSONArray("performed_chords"), events, duration)
                totals.getValue(variant).add(scores)
                println("BASIC_PITCH,${variant.name},${track.getString("track_id")},${scores.rootRatio}," +
                    "${scores.qualityRatio},${scores.supported},${scores.coverageRatio},${events.size},$elapsed")
                events.forEach { event ->
                    println("BP_PRED,${variant.name},${track.getString("track_id")},${event.startMillis}," +
                        "${event.endMillis},${event.chord.rootPitchClass},${event.chord.quality},${event.confidence}")
                }
            }
        }
        totals.forEach { (variant, scores) ->
            println("BASIC_PITCH,${variant.name},TOTAL,${scores.rootRatio},${scores.qualityRatio}," +
                "${scores.supported},${scores.coverageRatio}")
        }
    }

    @Test
    fun `compare fixed posterior variants on independent synthetic probes`() {
        val directory = listOf(File(".reference/tmp/chord-benchmark"), File("../.reference/tmp/chord-benchmark"))
            .map { File(it, "model-eval/basic_pitch/synthetic") }
            .firstOrNull { File(it, "manifest.json").isFile }
        assumeTrue("Generate the independent synthetic posterior probes first", directory != null)
        val root = requireNotNull(directory)
        val tracks = JSONObject(File(root, "manifest.json").readText()).getJSONArray("tracks")
        println("SYNTHETIC,variant,track,root,quality_supported,coverage_or_false_positive_fraction,events")
        repeat(tracks.length()) { index ->
            val track = tracks.getJSONObject(index)
            val name = track.getString("track_id")
            val frameCount = track.getInt("frames")
            val notes = readPosteriors(root, track.getJSONObject("notes"), frameCount)
            val onsets = readPosteriors(root, track.getJSONObject("onsets"), frameCount)
            val timesJson = JSONArray(File(root, track.getString("timestamps_ms")).readText())
            assertEquals(frameCount, timesJson.length())
            val times = LongArray(frameCount, timesJson::getLong)
            val duration = (track.getDouble("duration_seconds") * 1000).toLong()
            val mode = SongAnalysisMode.valueOf(track.getString("analysis_mode"))
            val audio = File(root, track.getString("wav_path")).readBytes()
            assertEquals(track.getString("wav_sha256"), sha256(audio))
            assertEquals("data", audio.decodeToString(36, 40))
            assertEquals(track.getInt("sample_count") * 2 + 44, audio.size)
            val pcm = ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN)
            val samples = FloatArray(track.getInt("sample_count")) { pcm.getShort(44 + it * 2) / 32768f }
            val dsp = StreamingChordAnalyzer(track.getInt("sample_rate"), mode).apply { accept(samples) }.finish()
            val annotations = track.getJSONArray("performed_chords")
            fun report(variant: String, events: List<ChordEvent>) {
                val scores = evaluate(annotations, events, duration)
                if (annotations.length() == 0) {
                    println("SYNTHETIC,$variant,$name,NA,NA,${scores.covered.toDouble() / duration},${events.size}")
                } else {
                    println("SYNTHETIC,$variant,$name,${scores.rootRatio},${scores.qualityRatio}," +
                        "${scores.coverageRatio},${events.size}")
                }
            }
            report("DSP", dsp)
            VARIANTS.forEach { variant ->
                val frames = harmonicFrames(notes, onsets, times, duration, variant.threshold, variant.floor)
                report(variant.name, analyzeChords(frames, mode, duration))
            }
        }
    }

    private fun readPosteriors(root: File, descriptor: JSONObject, frames: Int): FloatArray {
        val bytes = File(root, descriptor.getString("path")).readBytes()
        assertEquals(frames * NOTE_COUNT * Float.SIZE_BYTES, bytes.size)
        assertEquals(descriptor.getString("sha256"), sha256(bytes))
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(frames * NOTE_COUNT) { data.get() }.also { values ->
            assertTrue(values.all { it.isFinite() && it in 0f..1f })
        }
    }

    private fun harmonicFrames(
        notes: FloatArray,
        onsets: FloatArray,
        times: LongArray,
        duration: Long,
        threshold: Float,
        floor: Float,
    ): List<HarmonicFrame> {
        val count = ((duration + HOP_MILLIS - 1) / HOP_MILLIS).toInt()
        val saliences = Array(count) { FloatArray(NOTE_COUNT) }
        val onsetStrengths = FloatArray(count)
        val samples = IntArray(count)
        times.forEachIndexed { frame, timestamp ->
            val bin = (timestamp / HOP_MILLIS).toInt()
            if (bin >= count) return@forEachIndexed
            samples[bin]++
            repeat(NOTE_COUNT) { note ->
                val probability = notes[frame * NOTE_COUNT + note]
                if (probability >= threshold) saliences[bin][note] += (probability - floor).coerceAtLeast(0f)
                onsetStrengths[bin] = maxOf(onsetStrengths[bin], onsets[frame * NOTE_COUNT + note])
            }
        }
        val chromas = Array(count) { FloatArray(PITCH_CLASSES) }
        val bass = Array(count) { FloatArray(PITCH_CLASSES) }
        saliences.forEachIndexed { frame, probabilities ->
            if (samples[frame] > 0) probabilities.indices.forEach { probabilities[it] /= samples[frame] }
            probabilities.forEachIndexed { note, value ->
                val midi = MIN_MIDI + note
                chromas[frame][midi % PITCH_CLASSES] += value
                if (midi <= BASS_MAX_MIDI) bass[frame][midi % PITCH_CLASSES] += value / (1f + 0.08f * note)
            }
        }
        return saliences.indices.map { frame ->
            val context = FloatArray(PITCH_CLASSES)
            for (neighbor in maxOf(0, frame - CONTEXT_RADIUS)..minOf(count - 1, frame + CONTEXT_RADIUS)) {
                context.indices.forEach { context[it] += chromas[neighbor][it] }
            }
            normalize(context)
            val chroma = chromas[frame].copyOf().also(::normalize)
            val flatMaximum = 1.0 / sqrt(PITCH_CLASSES.toDouble())
            val strength = ((chroma.max() - flatMaximum) / (1 - flatMaximum)).toFloat().coerceIn(0f, 1f)
            HarmonicFrame(
                startMillis = frame * HOP_MILLIS,
                chroma = chroma,
                contextChroma = context,
                bassChroma = bass[frame].also(::normalize),
                noteSalience = saliences[frame].also(::normalize),
                tonalStrength = strength,
                onsetStrength = onsetStrengths[frame],
                spectralFlatness = if (chroma.all { it == 0f }) 1f else 0f,
            )
        }
    }

    private fun normalize(values: FloatArray) {
        val norm = sqrt(values.sumOf { it.toDouble() * it })
        if (norm > 0.0) values.indices.forEach { values[it] = (values[it] / norm).toFloat() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun evaluate(annotations: JSONArray, events: List<ChordEvent>, duration: Long): Scores {
        val result = Scores(covered = events.sumOf(ChordEvent::durationMillis))
        repeat(annotations.length()) { index ->
            val annotation = annotations.getJSONObject(index)
            val label = annotation.getString("label")
            val root = requireNotNull(ROOTS[label.substringBefore(':')])
            val quality = QUALITIES[label.substringAfter(':').substringBefore('/')]
            val start = (annotation.getDouble("start_seconds") * 1000).roundToLong()
            val end = minOf(duration, (annotation.getDouble("end_seconds") * 1000).roundToLong())
            result.labelled += end - start
            if (quality != null) result.supported += end - start
            events.forEach { event ->
                val overlap = (minOf(end, event.endMillis) - maxOf(start, event.startMillis)).coerceAtLeast(0L)
                if (event.chord.rootPitchClass == root) {
                    result.rootCorrect += overlap
                    if (quality != null && event.chord.quality == quality) result.qualityCorrect += overlap
                }
            }
        }
        return result
    }

    private data class Scores(
        var rootCorrect: Long = 0,
        var qualityCorrect: Long = 0,
        var labelled: Long = 0,
        var supported: Long = 0,
        var covered: Long = 0,
    ) {
        val rootRatio get() = rootCorrect.toDouble() / labelled
        val qualityRatio get() = if (supported == 0L) "NA" else (qualityCorrect.toDouble() / supported).toString()
        val coverageRatio get() = covered.toDouble() / labelled
        fun add(other: Scores) {
            rootCorrect += other.rootCorrect
            qualityCorrect += other.qualityCorrect
            labelled += other.labelled
            supported += other.supported
            covered += other.covered
        }
    }

    private data class Variant(val name: String, val threshold: Float, val floor: Float)

    private companion object {
        val VARIANTS = listOf(
            Variant("gate_020", 0.20f, 0f),
            Variant("gate_030", 0.30f, 0f),
            Variant("floor_010", 0f, 0.10f),
            Variant("gate_020_floor_010", 0.20f, 0.10f),
        )
        const val NOTE_COUNT = 88
        const val MIN_MIDI = 21
        const val BASS_MAX_MIDI = 60
        const val PITCH_CLASSES = 12
        const val HOP_MILLIS = 93L
        const val CONTEXT_RADIUS = 8
        val ROOTS = mapOf("C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3,
            "E" to 4, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8,
            "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11)
        val QUALITIES = mapOf("maj" to ChordQuality.MAJOR, "min" to ChordQuality.MINOR,
            "7" to ChordQuality.DOMINANT_SEVENTH, "maj7" to ChordQuality.MAJOR_SEVENTH,
            "min7" to ChordQuality.MINOR_SEVENTH, "maj6" to ChordQuality.MAJOR_SIXTH,
            "min6" to ChordQuality.MINOR_SIXTH, "sus2" to ChordQuality.SUSPENDED_SECOND,
            "sus4" to ChordQuality.SUSPENDED_FOURTH, "dim" to ChordQuality.DIMINISHED,
            "aug" to ChordQuality.AUGMENTED, "hdim7" to ChordQuality.HALF_DIMINISHED_SEVENTH,
            "5" to ChordQuality.POWER, "add9" to ChordQuality.ADD_NINTH,
            "minadd9" to ChordQuality.MINOR_ADD_NINTH)
    }
}
