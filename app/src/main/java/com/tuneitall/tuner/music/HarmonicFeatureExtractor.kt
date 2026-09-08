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
    val observedChroma: FloatArray = chroma,
) {
    init {
        require(startMillis >= 0L)
        require(chroma.size == PITCH_CLASS_COUNT)
        require(observedChroma.size == PITCH_CLASS_COUNT)
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
    private val isCancelled: () -> Boolean = { false },
    private val channelCount: Int = 1,
) {
    init {
        require(sampleRate in 8_000..192_000)
        require(channelCount in 1..8)
    }

    private val fftSize = Integer.highestOneBit((sampleRate * MIN_WINDOW_SECONDS).roundToInt() - 1) shl 1
    private val windowSize = minOf(fftSize, (sampleRate * MAX_WINDOW_SECONDS).roundToInt())
    private val hopSize = windowSize / 2
    private val hannWindow = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) }
    private val windows = Array(channelCount) { FloatArray(windowSize) }
    private val real = DoubleArray(fftSize)
    private val imaginary = DoubleArray(fftSize)
    private val magnitudes = DoubleArray(fftSize / 2 + 1)
    private val rawFrames = mutableListOf<RawFrame>()
    private val maxFrames: Long
    private var fill = 0
    private var frameStart = 0L
    private var totalFrames = 0L
    private var tuningSin = 0.0
    private var tuningCos = 0.0
    private var finishedFrames: List<HarmonicFrame>? = null

    init {
        require(maxDurationSeconds in 1..MAX_ANALYSIS_SECONDS)
        maxFrames = Math.multiplyExact(sampleRate.toLong(), maxDurationSeconds.toLong())
    }

    val durationMillis: Long
        get() = totalFrames * MILLIS_PER_SECOND / sampleRate

    fun accept(samples: FloatArray) {
        checkAnalysisCancellation(isCancelled)
        check(finishedFrames == null) { "Song analysis already finished" }
        require(samples.size % channelCount == 0) { "Audio input must contain complete sample frames" }
        require(samples.all(Float::isFinite))
        val sampleFrames = samples.size / channelCount
        require(totalFrames + sampleFrames <= maxFrames) { "Song analysis exceeds the duration limit" }
        totalFrames += sampleFrames
        var sourceFrame = 0
        while (sourceFrame < sampleFrames) {
            checkAnalysisCancellation(isCancelled)
            val count = minOf(windowSize - fill, sampleFrames - sourceFrame)
            if (channelCount == 1) {
                samples.copyInto(windows[0], fill, sourceFrame, sourceFrame + count)
            } else {
                for (frame in 0 until count) {
                    for (channel in 0 until channelCount) {
                        windows[channel][fill + frame] = samples[(sourceFrame + frame) * channelCount + channel]
                    }
                }
            }
            fill += count
            sourceFrame += count
            if (fill == windowSize) {
                rawFrames += extractFrame(frameStart * MILLIS_PER_SECOND / sampleRate)
                windows.forEach { window -> window.copyInto(window, 0, hopSize, windowSize) }
                fill = windowSize - hopSize
                frameStart += hopSize
            }
        }
    }

    fun finish(): List<HarmonicFrame> {
        checkAnalysisCancellation(isCancelled)
        finishedFrames?.let { return it }
        if (fill >= windowSize / 2) {
            windows.forEach { it.fill(0f, fill) }
            rawFrames += extractFrame(frameStart * MILLIS_PER_SECOND / sampleRate)
        }
        if (rawFrames.isEmpty()) return emptyList<HarmonicFrame>().also { finishedFrames = it }

        val tuningCents = if (tuningSin == 0.0 && tuningCos == 0.0) {
            0.0
        } else {
            (kotlin.math.atan2(tuningSin, tuningCos) / (2.0 * PI) * CENTS_PER_SEMITONE)
                .coerceIn(-MAX_TUNING_CENTS, MAX_TUNING_CENTS)
        }
        val starts = LongArray(rawFrames.size)
        val noteFrames = rawFrames.mapIndexedTo(ArrayList(rawFrames.size)) { index, frame ->
            checkAnalysisCancellation(isCancelled)
            starts[index] = frame.startMillis
            collapseToNotes(frame.highResolution, tuningCents).also { frame.highResolution = EMPTY_FEATURES }
        }
        val standardized = standardize(noteFrames)
        noteFrames.clear()
        val onsetStrengths = FloatArray(standardized.size) {
            checkAnalysisCancellation(isCancelled)
            onsetStrength(standardized, it)
        }
        val harmonicFrames = standardized.mapTo(ArrayList(standardized.size)) {
            checkAnalysisCancellation(isCancelled)
            harmonicSalience(it)
        }
        val chordChromas = ArrayList<FloatArray>(standardized.size)
        val observedChromas = ArrayList<FloatArray>(standardized.size)
        val bassChromas = ArrayList<FloatArray>(standardized.size)
        standardized.indices.forEach { index ->
            checkAnalysisCancellation(isCancelled)
            observedChromas += collapseToChroma(standardized[index])
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
            checkAnalysisCancellation(isCancelled)
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
                observedChroma = observedChromas[index],
            )
        }
        rawFrames.clear()
        finishedFrames = result
        return result
    }

    private fun extractFrame(startMillis: Long): RawFrame {
        if (channelCount == 1) return extractMonoFrame(startMillis)
        var squareTotal = 0.0
        windows.forEach { window ->
            window.forEach { sample -> squareTotal += sample.toDouble() * sample }
        }
        val rms = sqrt(squareTotal / (windowSize * channelCount))
        if (rms < SILENCE_RMS) return RawFrame(startMillis, FloatArray(HIGH_RESOLUTION_BIN_COUNT), 1f)

        val firstBin = (MIN_FREQUENCY_HERTZ * fftSize / sampleRate).toInt().coerceAtLeast(1)
        val lastBin = (MAX_FREQUENCY_HERTZ * fftSize / sampleRate).toInt().coerceAtMost(fftSize / 2 - 2)
        magnitudes.fill(0.0, firstBin - 1, lastBin + 2)
        for (channel in windows.indices step 2) {
            checkAnalysisCancellation(isCancelled)
            val left = windows[channel]
            val right = windows.getOrNull(channel + 1)
            for (index in left.indices) {
                real[index] = left[index] * hannWindow[index]
                imaginary[index] = (right?.get(index) ?: 0f) * hannWindow[index]
            }
            real.fill(0.0, windowSize)
            imaginary.fill(0.0, windowSize)
            fft(real, imaginary)
            for (bin in firstBin - 1..lastBin + 1) {
                // Packing two real channels: |L[k]|² + |R[k]|² = (|Z[k]|² + |Z[-k]|²) / 2.
                val mirror = fftSize - bin
                magnitudes[bin] += (real[bin] * real[bin] + imaginary[bin] * imaginary[bin] +
                    real[mirror] * real[mirror] + imaginary[mirror] * imaginary[mirror]) / 2.0
            }
        }
        for (bin in firstBin - 1..lastBin + 1) magnitudes[bin] = sqrt(magnitudes[bin] / channelCount)
        return extractFeatures(startMillis, firstBin, lastBin)
    }

    private fun extractMonoFrame(startMillis: Long): RawFrame {
        val window = windows[0]
        var squareTotal = 0.0
        for (index in window.indices) {
            val sample = window[index].toDouble()
            squareTotal += sample * sample
            real[index] = sample * hannWindow[index]
            imaginary[index] = 0.0
        }
        val rms = sqrt(squareTotal / window.size)
        if (rms < SILENCE_RMS) return RawFrame(startMillis, FloatArray(HIGH_RESOLUTION_BIN_COUNT), 1f)

        real.fill(0.0, windowSize)
        imaginary.fill(0.0, windowSize)
        fft(real, imaginary)
        val firstBin = (MIN_FREQUENCY_HERTZ * fftSize / sampleRate).toInt().coerceAtLeast(1)
        val lastBin = (MAX_FREQUENCY_HERTZ * fftSize / sampleRate).toInt().coerceAtMost(fftSize / 2 - 2)
        for (bin in firstBin - 1..lastBin + 1) magnitudes[bin] = hypot(real[bin], imaginary[bin])
        return extractFeatures(startMillis, firstBin, lastBin)
    }

    private fun extractFeatures(startMillis: Long, firstBin: Int, lastBin: Int): RawFrame {
        val salience = FloatArray(HIGH_RESOLUTION_BIN_COUNT)
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
            val frequency = (bin + offset) * sampleRate / fftSize
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
            checkAnalysisCancellation(isCancelled)
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
            checkAnalysisCancellation(isCancelled)
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
        (seconds * sampleRate / hopSize / 2.0).roundToInt().coerceAtLeast(1)

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

private const val MIN_WINDOW_SECONDS = 8192.0 / 48000.0
private const val MAX_WINDOW_SECONDS = 8192.0 / 44100.0
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
