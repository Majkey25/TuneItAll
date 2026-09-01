package com.tuneitall.tuner.music

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChordAnalyzerTest {
    @Test
    fun `template matcher recognizes major minor and dominant seventh chroma`() {
        assertEquals(Chord(0, ChordQuality.MAJOR), matchChord(chroma(0, 4, 7))?.chord)
        assertEquals(Chord(9, ChordQuality.MINOR), matchChord(chroma(9, 0, 4))?.chord)
        assertEquals(Chord(7, ChordQuality.DOMINANT_SEVENTH), matchChord(chroma(7, 11, 2, 5))?.chord)
        assertEquals(null, matchChord(DoubleArray(12)))
    }

    @Test
    fun `template matcher recognizes a distorted E power chord`() {
        val chroma = DoubleArray(12) { 0.02 }.apply {
            this[4] = 1.0
            this[11] = 0.86
            this[8] = 0.18
        }

        assertEquals(Chord(4, ChordQuality.POWER), matchChord(chroma)?.chord)
    }

    @Test
    fun `streaming analyzer finds a stable synthetic C major segment`() {
        val sampleRate = 11_025
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(sineChord(sampleRate, seconds = 2, frequencies = doubleArrayOf(261.63, 329.63, 392.0)))

        val events = analyzer.finish()

        assertTrue(events.isNotEmpty())
        assertEquals(Chord(0, ChordQuality.MAJOR), events.maxBy(ChordEvent::durationMillis).chord)
        assertTrue(events.maxOf(ChordEvent::durationMillis) >= 1_000L)
    }

    @Test
    fun `streaming analyzer preserves a clear C to G chord change`() {
        val sampleRate = 11_025
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(sineChord(sampleRate, seconds = 3, frequencies = doubleArrayOf(261.63, 329.63, 392.0)))
        analyzer.accept(sineChord(sampleRate, seconds = 3, frequencies = doubleArrayOf(196.0, 246.94, 293.66)))

        val events = analyzer.finish()
        val cIndex = events.indexOfFirst { it.chord == Chord(0, ChordQuality.MAJOR) }
        val gIndex = events.indexOfLast { it.chord == Chord(7, ChordQuality.MAJOR) }

        assertTrue(cIndex >= 0, events.toString())
        assertTrue(gIndex > cIndex, events.toString())
    }

    @Test
    fun `streaming analyzer recognizes a clipped E power chord`() {
        val sampleRate = 48_000
        val analyzer = StreamingChordAnalyzer(sampleRate, SongAnalysisMode.POWER)
        analyzer.accept(distortedPowerChord(sampleRate, seconds = 4, rootHertz = 82.41))

        val event = analyzer.finish().maxBy(ChordEvent::durationMillis)

        assertEquals(Chord(4, ChordQuality.POWER), event.chord)
    }

    @Test
    fun `power mode keeps a distorted riff stable through drum transients`() {
        val analyzer = StreamingChordAnalyzer(48_000, SongAnalysisMode.POWER)
        analyzer.accept(noisyPowerRiff(48_000, seconds = 6, rootHertz = 82.41))

        val events = analyzer.finish()
        val longest = events.maxBy(ChordEvent::durationMillis)

        assertEquals(Chord(4, ChordQuality.POWER), longest.chord)
        assertTrue(longest.durationMillis >= 4_000L, events.toString())
        assertTrue(events.all { it.chord.quality == ChordQuality.POWER })
    }

    @Test
    fun `classic mode does not turn an E power chord into E7`() {
        val analyzer = StreamingChordAnalyzer(48_000, SongAnalysisMode.CHORDS)
        analyzer.accept(noisyPowerRiff(48_000, seconds = 6, rootHertz = 82.41))

        val events = analyzer.finish()

        assertTrue(events.none { it.chord == Chord(4, ChordQuality.DOMINANT_SEVENTH) }, events.toString())
    }

    @Test
    fun `clear chord chroma survives low full mix tonal contrast`() {
        val chroma = FloatArray(12) { 0.10f }.apply {
            this[4] = 0.70f
            this[8] = 0.65f
            this[11] = 0.65f
        }
        val bass = FloatArray(12).apply { this[4] = 0.80f }
        val frames = List(20) { index ->
            HarmonicFrame(
                startMillis = index * 100L,
                chroma = chroma,
                bassChroma = bass,
                noteSalience = FloatArray(88),
                tonalStrength = 0.06f,
                onsetStrength = 0f,
            )
        }

        val event = analyzeChords(frames, SongAnalysisMode.CHORDS, songEndMillis = 2_000L)
            .maxBy(ChordEvent::durationMillis)

        assertEquals(Chord(4, ChordQuality.MAJOR), event.chord)
    }

    @Test
    fun `classic mode accepts a persistent genuine G7`() {
        val analyzer = StreamingChordAnalyzer(48_000, SongAnalysisMode.CHORDS)
        analyzer.accept(sineChord(48_000, 5, 196.0, 246.94, 293.66, 349.23))

        val event = analyzer.finish().maxBy(ChordEvent::durationMillis)

        assertEquals(Chord(7, ChordQuality.DOMINANT_SEVENTH), event.chord)
        assertTrue(event.durationMillis >= 3_000L)
    }

    @Test
    fun `changing broadband noise produces no chord events`() {
        val analyzer = StreamingChordAnalyzer(48_000, SongAnalysisMode.CHORDS)
        analyzer.accept(changingNoise(48_000, seconds = 4))

        val events = analyzer.finish()

        assertTrue(events.isEmpty(), events.toString())
    }

    @Test
    fun `streaming analyzer rejects input beyond its bounded duration`() {
        val analyzer = StreamingChordAnalyzer(sampleRate = 8_000, maxDurationSeconds = 1)

        assertFailsWith<IllegalArgumentException> { analyzer.accept(FloatArray(8_001)) }
    }

    @Test
    fun `real GuitarSet rock excerpt follows annotated chord roots without blank gaps`() {
        val (sampleRate, samples) = loadMonoPcm16("/guitarset/00_Rock1-130-A_comp_mic.wav")
        val analyzer = StreamingChordAnalyzer(sampleRate)
        analyzer.accept(samples)

        val events = analyzer.finish()
        val annotations = listOf(
            AnnotatedRoot(0L, 7_385L, 9),
            AnnotatedRoot(7_385L, 11_077L, 2),
            AnnotatedRoot(11_077L, 14_769L, 9),
            AnnotatedRoot(14_769L, 16_615L, 4),
            AnnotatedRoot(16_615L, 18_462L, 2),
            AnnotatedRoot(18_462L, 22_147L, 9),
        )
        val checkpoints = annotations.flatMap { annotation ->
            ((annotation.startMillis + 650L) until (annotation.endMillis - 650L) step 250L)
                .map { it to annotation.rootPitchClass }
        }
        val detected = checkpoints.map { (time, _) -> chordEventAt(events, time) }
        val covered = detected.count { it != null }
        val correct = detected.zip(checkpoints).count { (event, checkpoint) ->
            event?.chord?.rootPitchClass == checkpoint.second
        }

        assertEquals(checkpoints.size, covered, "events=$events")
        assertEquals(checkpoints.size, correct, "events=$events")
        assertEquals(Chord(2, ChordQuality.SUSPENDED_SECOND), chordEventAt(events, 17_600L)?.chord)
    }

    private fun chroma(vararg pitchClasses: Int) = DoubleArray(12) { index ->
        if (index in pitchClasses) 1.0 else 0.02
    }

    private fun sineChord(sampleRate: Int, seconds: Int, frequencies: DoubleArray): FloatArray =
        FloatArray(sampleRate * seconds) { frame ->
            val value = frequencies.sumOf { frequency -> sin(2.0 * PI * frequency * frame / sampleRate) }
            (value / frequencies.size).toFloat()
        }

    private fun distortedPowerChord(sampleRate: Int, seconds: Int, rootHertz: Double): FloatArray =
        FloatArray(sampleRate * seconds) { frame ->
            val root = sin(2.0 * PI * rootHertz * frame / sampleRate)
            val fifth = sin(2.0 * PI * rootHertz * 1.5 * frame / sampleRate)
            (0.75 * tanh(3.5 * (root + 0.8 * fifth))).toFloat()
        }

    private fun loadMonoPcm16(resource: String): Pair<Int, FloatArray> {
        val bytes = requireNotNull(javaClass.getResourceAsStream(resource)).use { it.readBytes() }
        require(bytes.copyOfRange(0, 4).decodeToString() == "RIFF")
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(header.getShort(22).toInt() == 1 && header.getShort(34).toInt() == 16)
        val samples = FloatArray((bytes.size - WAV_HEADER_BYTES) / Short.SIZE_BYTES) { index ->
            header.getShort(WAV_HEADER_BYTES + index * Short.SIZE_BYTES) / Short.MAX_VALUE.toFloat()
        }
        return header.getInt(24) to samples
    }

    private data class AnnotatedRoot(
        val startMillis: Long,
        val endMillis: Long,
        val rootPitchClass: Int,
    )

    private companion object {
        const val WAV_HEADER_BYTES = 44
    }
}
