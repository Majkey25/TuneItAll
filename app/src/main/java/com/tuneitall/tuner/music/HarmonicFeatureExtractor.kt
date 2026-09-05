package com.tuneitall.tuner.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class HarmonicFrame(
    val startMillis: Long,
    val chroma: FloatArray,
    val contextChroma: FloatArray = chroma,
    val bassChroma: FloatArray,
    val noteSalience: FloatArray,
    val tonalStrength: Float,
    val onsetStrength: Float,
    val spectralFlatness: Float = 0f,
) {
    init {
        require(startMillis >= 0L)
        require(chroma.size == PITCH_CLASS_COUNT)
        require(contextChroma.size == PITCH_CLASS_COUNT)
        require(bassChroma.size == PITCH_CLASS_COUNT)
        require(noteSalience.size == NOTE_COUNT)
        require(tonalStrength in 0f..1f)
        require(onsetStrength in 0f..1f)
        require(spectralFlatness in 0f..1f)
    }
}

internal class StreamingHarmonicFeatureExtractor(
    private val sampleRate: Int,
    maxDurationSeconds: Int = MAX_ANALYSIS_SECONDS,
) {
    private val window = FloatArray(FFT_SIZE)
    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val magnitudes = DoubleArray(FFT_SIZE / 2 + 1)
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

    val durationMillis: Long
        get() = totalSamples * MILLIS_PER_SECOND / sampleRate

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
        val noteFrames = rawFrames.mapIndexedTo(ArrayList(rawFrames.size)) { index, frame ->
            starts[index] = frame.startMillis
            collapseToNotes(frame.highResolution, tuningCents).also { frame.highResolution = EMPTY_FEATURES }
        }
        val standardized = standardize(noteFrames)
        noteFrames.clear()
        val onsetStrengths = FloatArray(standardized.size) { onsetStrength(standardized, it) }
        val harmonicFrames = standardized.mapTo(ArrayList(standardized.size), ::harmonicSalience)
        val chordChromas = ArrayList<FloatArray>(standardized.size)
        val bassChromas = ArrayList<FloatArray>(standardized.size)
        standardized.indices.forEach { index ->
            val harmonicWeight = (
                (HARMONIC_BLEND_MAX_FLATNESS - rawFrames[index].spectralFlatness) /
                    (HARMONIC_BLEND_MAX_FLATNESS - HARMONIC_BLEND_MIN_FLATNESS)
                ).coerceIn(0f, 1f)
            val features = FloatArray(NOTE_COUNT) { note ->
                (1f - harmonicWeight) * standardized[index][note] + harmonicWeight * harmonicFrames[index][note]
            }.also(::normalize)
            chordChromas += collapseToChroma(features, normalizeOutput = false)
            bassChromas += collapseToChroma(features, bassOnly = true, normalizeOutput = false)
        }
        standardized.clear()
        val chordFrames = centeredAverage(chordChromas, LOCAL_CHORD_RADIUS)
        val contextFrames = centeredAverage(chordChromas, framesForHalfWindow(CONTEXT_CHORD_WINDOW_SECONDS))
        val bassFrames = centeredAverage(bassChromas, LOCAL_CHORD_RADIUS)
        chordChromas.clear()
        bassChromas.clear()
        val melodyFrames = centeredAverage(harmonicFrames, NOTE_SMOOTH_RADIUS)
        harmonicFrames.clear()

        val result = starts.indices.map { index ->
            val chroma = chordFrames[index]
            HarmonicFrame(
                startMillis = starts[index],
                chroma = chroma,
                contextChroma = contextFrames[index],
                bassChroma = bassFrames[index],
                noteSalience = melodyFrames[index],
                tonalStrength = tonalStrength(chroma),
                onsetStrength = onsetStrengths[index],
                spectralFlatness = rawFrames[index].spectralFlatness,
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
        if (rms < SILENCE_RMS) return RawFrame(startMillis, FloatArray(HIGH_RESOLUTION_BIN_COUNT), 1f)

        fft(real, imaginary)
        val salience = FloatArray(HIGH_RESOLUTION_BIN_COUNT)
        val firstBin = (MIN_FREQUENCY_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtLeast(1)
        val lastBin = (MAX_FREQUENCY_HERTZ * FFT_SIZE / sampleRate).toInt().coerceAtMost(FFT_SIZE / 2 - 2)
        for (bin in firstBin - 1..lastBin + 1) magnitudes[bin] = hypot(real[bin], imaginary[bin])
        var magnitudeTotal = 0.0
        var logMagnitudeTotal = 0.0
        for (bin in firstBin..lastBin) {
            val magnitude = magnitudes[bin]
            magnitudeTotal += magnitude
            logMagnitudeTotal += ln(magnitude + SPECTRAL_EPSILON)
        }
        val binCount = lastBin - firstBin + 1
        val spectralFlatness = if (magnitudeTotal == 0.0) 1f else {
            (exp(logMagnitudeTotal / binCount) / (magnitudeTotal / binCount)).toFloat().coerceIn(0f, 1f)
        }
        for (bin in firstBin..lastBin) {
            val previous = magnitudes[bin - 1]
            val magnitude = magnitudes[bin]
            val next = magnitudes[bin + 1]
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
        return RawFrame(startMillis, salience, spectralFlatness)
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

    private fun standardize(frames: List<FloatArray>): MutableList<FloatArray> {
        val radius = framesForHalfWindow(STANDARDIZATION_WINDOW_SECONDS)
        val sums = DoubleArray(NOTE_COUNT)
        val squareSums = DoubleArray(NOTE_COUNT)
        var start = 0
        var end = minOf(frames.lastIndex, radius)
        for (index in start..end) addFrame(frames[index], sums, squareSums, 1.0)

        return frames.indices.mapTo(ArrayList(frames.size)) { frameIndex ->
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

    private fun centeredAverage(frames: List<FloatArray>, radius: Int): MutableList<FloatArray> {
        val sums = FloatArray(frames.first().size)
        var start = 0
        var end = minOf(frames.lastIndex, radius)
        for (index in start..end) addFrame(frames[index], sums, 1f)

        return frames.indices.mapTo(ArrayList(frames.size)) { frameIndex ->
            val count = end - start + 1
            val average = FloatArray(sums.size) { sums[it] / count }
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

    private data class RawFrame(
        val startMillis: Long,
        var highResolution: FloatArray,
        val spectralFlatness: Float,
    )
}

private fun collapseToChroma(
    notes: FloatArray,
    bassOnly: Boolean = false,
    normalizeOutput: Boolean = true,
): FloatArray {
    val chroma = FloatArray(PITCH_CLASS_COUNT)
    notes.forEachIndexed { noteIndex, salience ->
        val midi = MIN_MIDI + noteIndex
        if (bassOnly && midi > BASS_MAX_MIDI) return@forEachIndexed
        val weight = if (bassOnly) 1f / (1f + BASS_ROLLOFF * (midi - MIN_MIDI)) else 1f
        chroma[Math.floorMod(midi, PITCH_CLASS_COUNT)] += salience * weight
    }
    if (normalizeOutput) normalize(chroma)
    return chroma
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

private fun harmonicSalience(notes: FloatArray): FloatArray = FloatArray(notes.size) { candidate ->
    var score = 0f
    var supportingHarmonics = 0
    HARMONIC_OFFSETS.indices.forEach { index ->
        val salience = notes.getOrElse(candidate + HARMONIC_OFFSETS[index]) { 0f }
        score += HARMONIC_WEIGHTS[index] * salience
        if (salience >= MIN_SUPPORTING_HARMONIC) supportingHarmonics++
    }
    score + supportingHarmonics * HARMONIC_COUNT_BONUS
}.also(::normalize)

private fun tonalStrength(chroma: FloatArray): Float {
    if (chroma.all { it == 0f }) return 0f
    val flatMaximum = 1.0 / sqrt(PITCH_CLASS_COUNT.toDouble())
    return ((chroma.max() - flatMaximum) / (1.0 - flatMaximum)).toFloat().coerceIn(0f, 1f)
}

private fun onsetStrength(frames: List<FloatArray>, index: Int): Float {
    if (index == 0) return 0f
    var positiveFlux = 0f
    var total = 0f
    frames[index].indices.forEach { noteIndex ->
        val current = frames[index][noteIndex]
        positiveFlux += (current - frames[index - 1][noteIndex]).coerceAtLeast(0f)
        total += current
    }
    return if (total > 0f) (positiveFlux / total).coerceIn(0f, 1f) else 0f
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
private const val SPECTRAL_EPSILON = 1e-12
private const val STANDARDIZATION_EPSILON = 0.08
private const val RAW_FEATURE_WEIGHT = 0.7
private const val WHITENED_FEATURE_WEIGHT = 0.3
private const val HARMONIC_BLEND_MIN_FLATNESS = 0.30f
private const val HARMONIC_BLEND_MAX_FLATNESS = 0.45f
private const val STANDARDIZATION_WINDOW_SECONDS = 6.0
private const val LOCAL_CHORD_RADIUS = 1
private const val CONTEXT_CHORD_WINDOW_SECONDS = 1.5
private const val NOTE_SMOOTH_RADIUS = 1
private val HARMONIC_OFFSETS = intArrayOf(0, 12, 19, 24, 28, 31)
private val HARMONIC_WEIGHTS = floatArrayOf(1f, 0.707f, 0.577f, 0.5f, 0.447f, 0.408f)
private const val MIN_SUPPORTING_HARMONIC = 0.04f
private const val HARMONIC_COUNT_BONUS = 0.12f
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_ANALYSIS_SECONDS = 30 * 60
private val EMPTY_FEATURES = FloatArray(0)
