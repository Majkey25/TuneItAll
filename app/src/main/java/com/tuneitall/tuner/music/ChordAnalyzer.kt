package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.log2
import kotlin.math.sqrt

data class ChordMatch(val chord: Chord, val confidence: Double)

data class ChordEvent(
    override val startMillis: Long,
    override val endMillis: Long,
    val chord: Chord,
    override val confidence: Double,
) : SongEvent {
    init {
        require(startMillis >= 0L)
        require(endMillis > startMillis)
        require(confidence in 0.0..1.0)
    }
}

fun chordEventAt(events: List<ChordEvent>, positionMillis: Long): ChordEvent? {
    require(positionMillis >= 0L)
    var low = 0
    var high = events.lastIndex
    while (low <= high) {
        val middle = (low + high).ushr(1)
        val event = events[middle]
        when {
            positionMillis < event.startMillis -> high = middle - 1
            positionMillis >= event.endMillis -> low = middle + 1
            else -> return event
        }
    }
    if (low >= events.size) return null
    val previous = events.getOrNull(high) ?: return null
    return previous.takeIf { positionMillis - it.endMillis <= DISPLAY_HOLD_MILLIS }
}

fun matchChord(chroma: DoubleArray): ChordMatch? {
    require(chroma.size == PITCH_CLASS_COUNT)
    require(chroma.all { it.isFinite() && it >= 0.0 })
    val chromaNorm = sqrt(chroma.sumOf { it * it })
    if (chromaNorm < MIN_CHROMA_NORM) return null
    var best: ChordMatch? = null
    var secondScore = Double.NEGATIVE_INFINITY

    for (quality in ChordQuality.entries) {
        val templateNorm = sqrt(quality.intervals.size.toDouble())
        for (root in 0 until PITCH_CLASS_COUNT) {
            val chord = Chord(root, quality)
            val score = chord.pitchClasses.sumOf(chroma::get) / (chromaNorm * templateNorm)
            if (best == null || score > best.confidence) {
                secondScore = best?.confidence ?: secondScore
                best = ChordMatch(chord, score.coerceIn(0.0, 1.0))
            } else if (score > secondScore) {
                secondScore = score
            }
        }
    }
    val winner = requireNotNull(best)
    if (winner.confidence < MIN_TEMPLATE_SCORE || winner.confidence - secondScore < MIN_SCORE_MARGIN) return null
    return winner
}

class StreamingChordAnalyzer internal constructor(
    private val sampleRate: Int,
    maxDurationSeconds: Int = MAX_ANALYSIS_SECONDS,
) {
    private val window = FloatArray(FFT_SIZE)
    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val frames = mutableListOf<ChordFrame>()
    private var fill = 0
    private var frameStart = 0L
    private var totalSamples = 0L
    private val maxSamples: Long

    init {
        require(sampleRate in 8_000..192_000)
        require(maxDurationSeconds in 1..MAX_ANALYSIS_SECONDS)
        maxSamples = Math.multiplyExact(sampleRate.toLong(), maxDurationSeconds.toLong())
    }

    fun accept(samples: FloatArray) {
        require(samples.all(Float::isFinite))
        require(totalSamples + samples.size <= maxSamples) { "Song analysis exceeds the duration limit" }
        var sourceOffset = 0
        totalSamples += samples.size
        while (sourceOffset < samples.size) {
            val count = minOf(FFT_SIZE - fill, samples.size - sourceOffset)
            samples.copyInto(window, fill, sourceOffset, sourceOffset + count)
            fill += count
            sourceOffset += count
            if (fill == FFT_SIZE) {
                frames += ChordFrame(
                    startMillis = frameStart * MILLIS_PER_SECOND / sampleRate,
                    match = matchChord(extractChroma()),
                )
                window.copyInto(window, 0, HOP_SIZE, FFT_SIZE)
                fill = FFT_SIZE - HOP_SIZE
                frameStart += HOP_SIZE
            }
        }
    }

    fun finish(): List<ChordEvent> {
        if (frames.isEmpty() && fill >= MIN_PARTIAL_WINDOW) {
            window.fill(0f, fill)
            frames += ChordFrame(0L, matchChord(extractChroma()))
        }
        if (frames.isEmpty()) return emptyList()
        val smoothed = frames.indices.map { index -> smoothMatch(index) }
        val frameDuration = FFT_SIZE.toLong() * MILLIS_PER_SECOND / sampleRate
        val songEnd = maxOf(totalSamples * MILLIS_PER_SECOND / sampleRate, frameDuration)
        val events = mutableListOf<ChordEvent>()
        var activeChord: Chord? = null
        var activeStart = 0L
        var confidenceTotal = 0.0
        var confidenceCount = 0

        fun close(endMillis: Long) {
            val chord = activeChord ?: return
            if (endMillis > activeStart) {
                events += ChordEvent(activeStart, endMillis, chord, confidenceTotal / confidenceCount)
            }
            activeChord = null
            confidenceTotal = 0.0
            confidenceCount = 0
        }

        smoothed.forEachIndexed { index, match ->
            val start = frames[index].startMillis
            if (match?.chord != activeChord) {
                close(start)
                if (match != null) {
                    activeChord = match.chord
                    activeStart = start
                }
            }
            if (match != null && match.chord == activeChord) {
                confidenceTotal += match.confidence
                confidenceCount++
            }
        }
        close(songEnd)
        return events.filter { it.durationMillis >= MIN_EVENT_MILLIS }
    }

    private fun smoothMatch(index: Int): ChordMatch? {
        val start = maxOf(0, index - SMOOTH_RADIUS)
        val end = minOf(frames.lastIndex, index + SMOOTH_RADIUS)
        val matches = (start..end).mapNotNull { frames[it].match }
        if (matches.isEmpty()) return null
        val winner = matches.groupingBy(ChordMatch::chord).eachCount().maxBy { it.value }.key
        val winningMatches = matches.filter { it.chord == winner }
        if (frames.size > 1 && winningMatches.size < MIN_SMOOTH_VOTES) return null
        return ChordMatch(winner, winningMatches.map(ChordMatch::confidence).average())
    }

    private fun extractChroma(): DoubleArray {
        for (index in window.indices) {
            val hann = 0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
            real[index] = window[index] * hann
            imaginary[index] = 0.0
        }
        fft(real, imaginary)
        val chroma = DoubleArray(PITCH_CLASS_COUNT)
        val firstBin = (MIN_ANALYSIS_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtLeast(1)
        val lastBin = (MAX_ANALYSIS_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtMost(FFT_SIZE / 2 - 1)
        for (bin in firstBin..lastBin) {
            val magnitude = hypot(real[bin], imaginary[bin])
            val previous = hypot(real[bin - 1], imaginary[bin - 1])
            val next = hypot(real[bin + 1], imaginary[bin + 1])
            if (magnitude <= previous || magnitude < next) continue
            val frequency = bin.toDouble() * sampleRate / FFT_SIZE
            val midi = 69.0 + 12.0 * log2(frequency / 440.0)
            val pitchClass = Math.floorMod(kotlin.math.round(midi).toInt(), PITCH_CLASS_COUNT)
            chroma[pitchClass] += ln1p(magnitude) / frequency
        }
        val norm = sqrt(chroma.sumOf { it * it })
        if (norm > 0.0) chroma.indices.forEach { chroma[it] /= norm }
        return chroma
    }

    private data class ChordFrame(val startMillis: Long, val match: ChordMatch?)
}

private const val PITCH_CLASS_COUNT = 12
private const val FFT_SIZE = 8_192
private const val HOP_SIZE = 4_096
private const val MIN_PARTIAL_WINDOW = FFT_SIZE / 2
private const val MIN_ANALYSIS_HERTZ = 65.0
private const val MAX_ANALYSIS_HERTZ = 2_000.0
private const val MIN_CHROMA_NORM = 1e-9
private const val MIN_TEMPLATE_SCORE = 0.72
private const val MIN_SCORE_MARGIN = 0.025
private const val SMOOTH_RADIUS = 2
private const val MIN_SMOOTH_VOTES = 2
private const val MIN_EVENT_MILLIS = 300L
private const val DISPLAY_HOLD_MILLIS = 500L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_ANALYSIS_SECONDS = 30 * 60
