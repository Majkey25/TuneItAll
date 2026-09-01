package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class HarmonicFrame(
    val startMillis: Long,
    val chroma: FloatArray,
    val bassChroma: FloatArray,
    val noteSalience: FloatArray,
    val tonalStrength: Float,
) {
    init {
        require(startMillis >= 0L)
        require(chroma.size == PITCH_CLASS_COUNT)
        require(bassChroma.size == PITCH_CLASS_COUNT)
        require(noteSalience.size == NOTE_COUNT)
        require(tonalStrength in 0f..1f)
    }
}

internal class StreamingHarmonicFeatureExtractor(
    private val sampleRate: Int,
    maxDurationSeconds: Int = MAX_ANALYSIS_SECONDS,
) {
    private val window = FloatArray(FFT_SIZE)
    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val rawFrames = mutableListOf<RawFrame>()
    private val maxSamples: Long
    private var fill = 0
    private var frameStart = 0L
    private var totalSamples = 0L
    private var tuningSin = 0.0
    private var tuningCos = 0.0
    private var finishedFrames: List<HarmonicFrame>? = null

    init {
        require(sampleRate in 8_000..192_000)
        require(maxDurationSeconds in 1..MAX_ANALYSIS_SECONDS)
        maxSamples = Math.multiplyExact(sampleRate.toLong(), maxDurationSeconds.toLong())
    }

    fun accept(samples: FloatArray) {
        check(finishedFrames == null) { "Song analysis already finished" }
        require(samples.all(Float::isFinite))
        require(totalSamples + samples.size <= maxSamples) { "Song analysis exceeds the duration limit" }
        totalSamples += samples.size
        var sourceOffset = 0
        while (sourceOffset < samples.size) {
            val count = minOf(FFT_SIZE - fill, samples.size - sourceOffset)
            samples.copyInto(window, fill, sourceOffset, sourceOffset + count)
            fill += count
            sourceOffset += count
            if (fill == FFT_SIZE) {
                rawFrames += extractFrame(frameStart * MILLIS_PER_SECOND / sampleRate)
                window.copyInto(window, 0, HOP_SIZE, FFT_SIZE)
                fill = FFT_SIZE - HOP_SIZE
                frameStart += HOP_SIZE
            }
        }
    }

    fun finish(): List<HarmonicFrame> {
        finishedFrames?.let { return it }
        if (rawFrames.isEmpty() && fill >= MIN_PARTIAL_WINDOW) {
            window.fill(0f, fill)
            rawFrames += extractFrame(0L)
        }
        if (rawFrames.isEmpty()) return emptyList()

        val tuningCents = if (tuningSin == 0.0 && tuningCos == 0.0) {
            0.0
        } else {
            (kotlin.math.atan2(tuningSin, tuningCos) / (2.0 * PI) * CENTS_PER_SEMITONE)
                .coerceIn(-MAX_TUNING_CENTS, MAX_TUNING_CENTS)
        }
        val starts = LongArray(rawFrames.size)
        val noteFrames = rawFrames.mapIndexed { index, frame ->
            starts[index] = frame.startMillis
            collapseToNotes(frame.highResolution, tuningCents).also { frame.highResolution = EMPTY_FEATURES }
        }
        val standardized = standardize(noteFrames)
        val chordFrames = centeredAverage(standardized, framesForHalfWindow(CHORD_WINDOW_SECONDS))
        val melodyFrames = centeredAverage(standardized, NOTE_SMOOTH_RADIUS)

        val result = starts.indices.map { index ->
            val chroma = FloatArray(PITCH_CLASS_COUNT)
            val bassChroma = FloatArray(PITCH_CLASS_COUNT)
            chordFrames[index].forEachIndexed { noteIndex, salience ->
                val midi = MIN_MIDI + noteIndex
                val pitchClass = Math.floorMod(midi, PITCH_CLASS_COUNT)
                chroma[pitchClass] += salience
                if (midi <= BASS_MAX_MIDI) {
                    bassChroma[pitchClass] += salience / (1f + BASS_ROLLOFF * (midi - MIN_MIDI))
                }
            }
            normalize(chroma)
            normalize(bassChroma)
            HarmonicFrame(
                startMillis = starts[index],
                chroma = chroma,
                bassChroma = bassChroma,
                noteSalience = melodyFrames[index],
                tonalStrength = tonalStrength(chroma),
            )
        }
        rawFrames.clear()
        finishedFrames = result
        return result
    }

    private fun extractFrame(startMillis: Long): RawFrame {
        var squareTotal = 0.0
        for (index in window.indices) {
            val sample = window[index].toDouble()
            squareTotal += sample * sample
            val hann = 0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
            real[index] = sample * hann
            imaginary[index] = 0.0
        }
        val rms = sqrt(squareTotal / window.size)
        if (rms < SILENCE_RMS) return RawFrame(startMillis, FloatArray(HIGH_RESOLUTION_BIN_COUNT))

        fft(real, imaginary)
        val salience = FloatArray(HIGH_RESOLUTION_BIN_COUNT)
        val firstBin = (MIN_FREQUENCY_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtLeast(1)
        val lastBin = (MAX_FREQUENCY_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtMost(FFT_SIZE / 2 - 2)
        for (bin in firstBin..lastBin) {
            val previous = hypot(real[bin - 1], imaginary[bin - 1])
            val magnitude = hypot(real[bin], imaginary[bin])
            val next = hypot(real[bin + 1], imaginary[bin + 1])
            if (magnitude <= previous || magnitude < next) continue
            val denominator = previous - 2.0 * magnitude + next
            val offset = if (kotlin.math.abs(denominator) < PEAK_EPSILON) {
                0.0
            } else {
                (0.5 * (previous - next) / denominator).coerceIn(-0.5, 0.5)
            }
            val frequency = (bin + offset) * sampleRate / FFT_SIZE
            if (frequency !in MIN_FREQUENCY_HERTZ..MAX_FREQUENCY_HERTZ) continue
            val midi = 69.0 + SEMITONES_PER_OCTAVE * log2(frequency / 440.0)
            val highResolutionIndex = ((midi - MIN_MIDI) * BINS_PER_SEMITONE).roundToInt()
            if (highResolutionIndex !in salience.indices) continue
            val weight = ln1p(magnitude) / sqrt(frequency)
            salience[highResolutionIndex] += weight.toFloat()
            val semitoneOffset = midi - kotlin.math.round(midi)
            val angle = 2.0 * PI * semitoneOffset
            tuningSin += weight * sin(angle)
            tuningCos += weight * cos(angle)
        }
        normalize(salience)
        return RawFrame(startMillis, salience)
    }

    private fun collapseToNotes(highResolution: FloatArray, tuningCents: Double): FloatArray {
        val notes = FloatArray(NOTE_COUNT)
        highResolution.forEachIndexed { index, value ->
            if (value <= 0f) return@forEachIndexed
            val rawMidi = MIN_MIDI + index.toDouble() / BINS_PER_SEMITONE
            val correctedMidi = rawMidi - tuningCents / CENTS_PER_SEMITONE
            val noteIndex = correctedMidi.roundToInt() - MIN_MIDI
            if (noteIndex in notes.indices) notes[noteIndex] += value
        }
        normalize(notes)
        return notes
    }

    private fun standardize(frames: List<FloatArray>): List<FloatArray> {
        val radius = framesForHalfWindow(STANDARDIZATION_WINDOW_SECONDS)
        val sums = DoubleArray(NOTE_COUNT)
        val squareSums = DoubleArray(NOTE_COUNT)
        var start = 0
        var end = minOf(frames.lastIndex, radius)
        for (index in start..end) addFrame(frames[index], sums, squareSums, 1.0)

        return frames.indices.map { frameIndex ->
            val count = end - start + 1
            val standardized = FloatArray(NOTE_COUNT) { noteIndex ->
                val value = frames[frameIndex][noteIndex].toDouble()
                val mean = sums[noteIndex] / count
                val variance = (squareSums[noteIndex] / count - mean * mean).coerceAtLeast(0.0)
                val positiveZ = ((value - mean) / (sqrt(variance) + STANDARDIZATION_EPSILON)).coerceAtLeast(0.0)
                (RAW_FEATURE_WEIGHT * value + WHITENED_FEATURE_WEIGHT * positiveZ / (1.0 + positiveZ)).toFloat()
            }
            normalize(standardized)

            val nextStart = maxOf(0, frameIndex + 1 - radius)
            val nextEnd = minOf(frames.lastIndex, frameIndex + 1 + radius)
            while (start < nextStart) {
                addFrame(frames[start], sums, squareSums, -1.0)
                start++
            }
            while (end < nextEnd) {
                end++
                addFrame(frames[end], sums, squareSums, 1.0)
            }
            standardized
        }
    }

    private fun centeredAverage(frames: List<FloatArray>, radius: Int): List<FloatArray> {
        val sums = FloatArray(NOTE_COUNT)
        var start = 0
        var end = minOf(frames.lastIndex, radius)
        for (index in start..end) addFrame(frames[index], sums, 1f)

        return frames.indices.map { frameIndex ->
            val count = end - start + 1
            val average = FloatArray(NOTE_COUNT) { sums[it] / count }
            normalize(average)

            val nextStart = maxOf(0, frameIndex + 1 - radius)
            val nextEnd = minOf(frames.lastIndex, frameIndex + 1 + radius)
            while (start < nextStart) {
                addFrame(frames[start], sums, -1f)
                start++
            }
            while (end < nextEnd) {
                end++
                addFrame(frames[end], sums, 1f)
            }
            average
        }
    }

    private fun framesForHalfWindow(seconds: Double): Int =
        (seconds * sampleRate / HOP_SIZE / 2.0).roundToInt().coerceAtLeast(1)

    private data class RawFrame(val startMillis: Long, var highResolution: FloatArray)
}

private fun addFrame(frame: FloatArray, sums: DoubleArray, squareSums: DoubleArray, direction: Double) {
    frame.indices.forEach { index ->
        val value = frame[index].toDouble()
        sums[index] += direction * value
        squareSums[index] += direction * value * value
    }
}

private fun addFrame(frame: FloatArray, sums: FloatArray, direction: Float) {
    frame.indices.forEach { index -> sums[index] += direction * frame[index] }
}

private fun normalize(values: FloatArray) {
    val norm = sqrt(values.sumOf { it.toDouble() * it })
    if (norm > 0.0) values.indices.forEach { values[it] = (values[it] / norm).toFloat() }
}

private fun tonalStrength(chroma: FloatArray): Float {
    if (chroma.all { it == 0f }) return 0f
    val flatMaximum = 1.0 / sqrt(PITCH_CLASS_COUNT.toDouble())
    return ((chroma.max() - flatMaximum) / (1.0 - flatMaximum)).toFloat().coerceIn(0f, 1f)
}

internal fun fft(real: DoubleArray, imaginary: DoubleArray) {
    require(real.size == imaginary.size && real.size.countOneBits() == 1)
    var reversed = 0
    for (index in 1 until real.size) {
        var bit = real.size shr 1
        while (reversed and bit != 0) {
            reversed = reversed xor bit
            bit = bit shr 1
        }
        reversed = reversed xor bit
        if (index < reversed) {
            val realValue = real[index]
            real[index] = real[reversed]
            real[reversed] = realValue
            val imaginaryValue = imaginary[index]
            imaginary[index] = imaginary[reversed]
            imaginary[reversed] = imaginaryValue
        }
    }
    var length = 2
    while (length <= real.size) {
        val angle = -2.0 * PI / length
        val stepReal = cos(angle)
        val stepImaginary = sin(angle)
        for (block in real.indices step length) {
            var weightReal = 1.0
            var weightImaginary = 0.0
            for (offset in 0 until length / 2) {
                val even = block + offset
                val odd = even + length / 2
                val oddReal = real[odd] * weightReal - imaginary[odd] * weightImaginary
                val oddImaginary = real[odd] * weightImaginary + imaginary[odd] * weightReal
                real[odd] = real[even] - oddReal
                imaginary[odd] = imaginary[even] - oddImaginary
                real[even] += oddReal
                imaginary[even] += oddImaginary
                val nextWeightReal = weightReal * stepReal - weightImaginary * stepImaginary
                weightImaginary = weightReal * stepImaginary + weightImaginary * stepReal
                weightReal = nextWeightReal
            }
        }
        length = length shl 1
    }
}

private const val FFT_SIZE = 8_192
private const val HOP_SIZE = 4_096
private const val MIN_PARTIAL_WINDOW = FFT_SIZE / 2
private const val MIN_MIDI = 21
private const val MAX_MIDI = 108
private const val NOTE_COUNT = MAX_MIDI - MIN_MIDI + 1
private const val PITCH_CLASS_COUNT = 12
private const val BINS_PER_SEMITONE = 3
private const val HIGH_RESOLUTION_BIN_COUNT = NOTE_COUNT * BINS_PER_SEMITONE
private const val MIN_FREQUENCY_HERTZ = 27.5
private const val MAX_FREQUENCY_HERTZ = 4_200.0
private const val BASS_MAX_MIDI = 60
private const val BASS_ROLLOFF = 0.08f
private const val CENTS_PER_SEMITONE = 100.0
private const val MAX_TUNING_CENTS = 50.0
private const val SEMITONES_PER_OCTAVE = 12.0
private const val SILENCE_RMS = 1e-5
private const val PEAK_EPSILON = 1e-12
private const val STANDARDIZATION_EPSILON = 0.08
private const val RAW_FEATURE_WEIGHT = 0.7
private const val WHITENED_FEATURE_WEIGHT = 0.3
private const val STANDARDIZATION_WINDOW_SECONDS = 6.0
private const val CHORD_WINDOW_SECONDS = 2.0
private const val NOTE_SMOOTH_RADIUS = 1
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_ANALYSIS_SECONDS = 30 * 60
private val EMPTY_FEATURES = FloatArray(0)
