package com.tuneitall.tuner.music

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
    return songEventAt(events, positionMillis)
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
    sampleRate: Int,
    private val mode: SongAnalysisMode = SongAnalysisMode.CHORDS,
    maxDurationSeconds: Int = MAX_ANALYSIS_SECONDS,
) {
    private val extractor = StreamingHarmonicFeatureExtractor(sampleRate, maxDurationSeconds)

    init {
        require(mode != SongAnalysisMode.NOTES)
    }

    fun accept(samples: FloatArray) = extractor.accept(samples)

    fun finish(): List<ChordEvent> = analyzeChords(extractor.finish(), mode, extractor.durationMillis)
}

internal fun analyzeChords(
    frames: List<HarmonicFrame>,
    mode: SongAnalysisMode,
    songEndMillis: Long,
): List<ChordEvent> {
    require(mode != SongAnalysisMode.NOTES)
    if (frames.isEmpty() || songEndMillis <= 0L) return emptyList()
    val chords = if (mode == SongAnalysisMode.POWER) {
        List(PITCH_CLASS_COUNT) { root -> Chord(root, ChordQuality.POWER) }
    } else {
        buildList {
            instructionalChordQualities.forEach { quality ->
                repeat(PITCH_CLASS_COUNT) { root -> add(Chord(root, quality)) }
            }
        }
    }
    val states = viterbi(frames.size, chords.size + 1, frames) { frameIndex ->
        emissionScores(frames, frameIndex, chords, mode)
    }
    val events = mutableListOf<ChordEvent>()
    var activeState = states.first()
    var activeStart = frames.first().startMillis
    var confidenceTotal = stateConfidence(frames, 0, chords, mode, activeState)
    var confidenceCount = 1
    var activeFrameStart = 0

    fun close(endMillis: Long, endFrame: Int) {
        if (activeState == 0 || endMillis <= activeStart) return
        val sourceChord = chords[activeState - 1]
        val segment = frames.subList(activeFrameStart, endFrame)
        val bass = detectInversionBass(sourceChord, segment)
        val chord = sourceChord.copy(bassPitchClass = bass)
        val confidence = (confidenceTotal / confidenceCount).coerceIn(0.0, 1.0)
        val spectralFlatness = segment.sumOf { it.spectralFlatness.toDouble() } / segment.size
        if (confidence < MIN_EVENT_CONFIDENCE || spectralFlatness >= MAX_EVENT_SPECTRAL_FLATNESS) return
        events += ChordEvent(
            startMillis = activeStart,
            endMillis = endMillis,
            chord = chord,
            confidence = confidence,
        )
    }

    for (index in 1 until states.size) {
        val state = states[index]
        if (state != activeState) {
            close(frames[index].startMillis, index)
            activeState = state
            activeStart = frames[index].startMillis
            activeFrameStart = index
            confidenceTotal = 0.0
            confidenceCount = 0
        }
        confidenceTotal += stateConfidence(frames, index, chords, mode, state)
        confidenceCount++
    }
    close(songEndMillis, frames.size)
    return mergeChordGaps(events.filter { it.durationMillis >= MIN_EVENT_MILLIS })
}

private fun emissionScores(
    frames: List<HarmonicFrame>,
    frameIndex: Int,
    chords: List<Chord>,
    mode: SongAnalysisMode,
): DoubleArray {
    val frame = frames[frameIndex]
    val localScores = DoubleArray(chords.size) { chordIndex ->
        stateConfidence(frames, frameIndex, chords, mode, chordIndex + 1)
    }
    val bestLocal = localScores.max()
    return DoubleArray(chords.size + 1).also { scores ->
        scores[0] = NO_CHORD_BASE + NO_CHORD_TONAL_WEIGHT * (1.0 - frame.tonalStrength)
        chords.forEachIndexed { index, chord ->
            val local = localScores[index]
            scores[index + 1] = if (bestLocal - local <= CONTEXT_TIE_MARGIN) {
                (local + CONTEXT_TIE_WEIGHT * chordEmission(frame, chord, frame.contextChroma)).coerceAtMost(1.0)
            } else {
                local
            }
        }
    }
}

private fun stateConfidence(
    frames: List<HarmonicFrame>,
    frameIndex: Int,
    chords: List<Chord>,
    mode: SongAnalysisMode,
    state: Int,
): Double {
    if (state == 0) return 0.0
    val chord = chords[state - 1]
    return if (mode == SongAnalysisMode.CHORDS && !hasStableDefiningIntervals(frames, frameIndex, chord)) {
        INVALID_EMISSION
    } else {
        chordEmission(frames[frameIndex], chord)
    }
}

private fun chordEmission(frame: HarmonicFrame, chord: Chord, chroma: FloatArray = frame.chroma): Double {
    if (frame.tonalStrength < MIN_TONAL_STRENGTH) return 0.0
    val inChord = chord.pitchClasses.map { chroma[it].toDouble() }
    val outOfChord = chroma.indices.filterNot(chord.pitchClasses::contains).map { chroma[it].toDouble() }
    val inAverage = inChord.average()
    val minimum = inChord.min()
    val outAverage = outOfChord.average()
    val bassRoot = frame.bassChroma[chord.rootPitchClass]
    val extensionBonus = BONUS_INTERVALS.getValue(chord.quality).sumOf { interval ->
        EXTENSION_BONUS * chroma[(chord.rootPitchClass + interval) % PITCH_CLASS_COUNT]
    }
    return (
        IN_CHORD_WEIGHT * inAverage + MINIMUM_NOTE_WEIGHT * minimum + BASS_ROOT_WEIGHT * bassRoot -
            OUT_OF_CHORD_WEIGHT * outAverage + extensionBonus - QUALITY_PRIOR_PENALTY.getValue(chord.quality)
    ).coerceIn(0.0, 1.0)
}

private fun hasStableDefiningIntervals(frames: List<HarmonicFrame>, index: Int, chord: Chord): Boolean {
    val intervals = DEFINING_INTERVALS.getValue(chord.quality)
    if (intervals.isEmpty()) return true
    if (!hasDefiningIntervals(frames[index].chroma, chord, intervals)) return false
    return (index > 0 && hasDefiningIntervals(frames[index - 1].chroma, chord, intervals)) ||
        (index < frames.lastIndex && hasDefiningIntervals(frames[index + 1].chroma, chord, intervals))
}

private fun hasDefiningIntervals(chroma: FloatArray, chord: Chord, intervals: Set<Int>): Boolean {
    val outside = chroma.indices.filterNot(chord.pitchClasses::contains).map(chroma::get).sorted()
    val threshold = maxOf(MIN_DEFINING_SALIENCE, DEFINING_OUTSIDE_RATIO * outside[outside.size / 2])
    return intervals.all { chroma[(chord.rootPitchClass + it) % PITCH_CLASS_COUNT] >= threshold }
}

private fun detectInversionBass(chord: Chord, frames: List<HarmonicFrame>): Int? {
    var previous = -1
    var consecutive = 0
    frames.forEach { frame ->
        val candidate = chord.pitchClasses.maxBy(frame.bassChroma::get)
        val value = frame.bassChroma[candidate]
        val root = frame.bassChroma[chord.rootPitchClass]
        if (candidate != chord.rootPitchClass && value >= MIN_INVERSION_SALIENCE && value >= INVERSION_ROOT_RATIO * root) {
            consecutive = if (candidate == previous) consecutive + 1 else 1
            previous = candidate
            if (consecutive >= MIN_INVERSION_FRAMES) return candidate
        } else {
            previous = -1
            consecutive = 0
        }
    }
    return null
}

internal fun viterbi(emissions: List<DoubleArray>, frames: List<HarmonicFrame>): IntArray {
    require(emissions.size == frames.size)
    val stateCount = emissions.first().size
    require(stateCount >= 2 && emissions.all { it.size == stateCount })
    return viterbi(emissions.size, stateCount, frames, emissions::get)
}

private inline fun viterbi(
    frameCount: Int,
    stateCount: Int,
    frames: List<HarmonicFrame>,
    emissionAt: (Int) -> DoubleArray,
): IntArray {
    val backPointers = Array(frameCount) { ShortArray(stateCount) }
    var previous = emissionAt(0)
    for (frameIndex in 1 until frameCount) {
        val emissions = emissionAt(frameIndex)
        val transitionScale = (1.0 - ONSET_TRANSITION_DISCOUNT * frames[frameIndex].onsetStrength)
            .coerceIn(MIN_TRANSITION_SCALE, 1.0)
        val current = DoubleArray(stateCount)
        var bestChord = 1
        var secondChord = -1
        for (state in 2 until stateCount) {
            if (previous[state] > previous[bestChord]) {
                secondChord = bestChord
                bestChord = state
            } else if (secondChord < 0 || previous[state] > previous[secondChord]) {
                secondChord = state
            }
        }

        val noChordPenalty = NO_CHORD_TRANSITION * transitionScale
        val chordPenalty = CHORD_TRANSITION * transitionScale
        var bestPrevious = 0
        var bestScore = previous[0]
        val chordToNoChord = previous[bestChord] - noChordPenalty
        if (chordToNoChord > bestScore) {
            bestPrevious = bestChord
            bestScore = chordToNoChord
        }
        current[0] = bestScore + emissions[0]
        backPointers[frameIndex][0] = bestPrevious.toShort()

        for (state in 1 until stateCount) {
            var bestPrevious = state
            var bestScore = previous[state]
            val noChordToChord = previous[0] - noChordPenalty
            if (noChordToChord > bestScore) {
                bestPrevious = 0
                bestScore = noChordToChord
            }
            val otherChord = if (bestChord == state) secondChord else bestChord
            if (otherChord >= 0 && previous[otherChord] - chordPenalty > bestScore) {
                bestPrevious = otherChord
                bestScore = previous[otherChord] - chordPenalty
            }
            current[state] = bestScore + emissions[state]
            backPointers[frameIndex][state] = bestPrevious.toShort()
        }
        previous = current
    }
    val states = IntArray(frameCount)
    states[states.lastIndex] = previous.indices.maxBy(previous::get)
    for (frameIndex in states.lastIndex downTo 1) {
        states[frameIndex - 1] = backPointers[frameIndex][states[frameIndex]].toInt()
    }
    return states
}

private fun mergeChordGaps(events: List<ChordEvent>): List<ChordEvent> = buildList {
    events.forEach { event ->
        val previous = lastOrNull()
        if (previous != null && previous.chord == event.chord && event.startMillis - previous.endMillis <= MAX_BRIDGE_GAP_MILLIS) {
            val combinedDuration = previous.durationMillis + event.durationMillis
            this[lastIndex] = previous.copy(
                endMillis = event.endMillis,
                confidence = (
                    previous.confidence * previous.durationMillis + event.confidence * event.durationMillis
                    ) / combinedDuration,
            )
        } else {
            add(event)
        }
    }
}

private const val PITCH_CLASS_COUNT = 12
private const val MIN_CHROMA_NORM = 1e-9
private const val MIN_TEMPLATE_SCORE = 0.72
private const val MIN_SCORE_MARGIN = 0.025
private const val MIN_EVENT_MILLIS = 200L
private const val MIN_EVENT_CONFIDENCE = 0.18
private const val MAX_EVENT_SPECTRAL_FLATNESS = 0.82
private const val MAX_BRIDGE_GAP_MILLIS = 100L
private const val NO_CHORD_BASE = 0.05
private const val NO_CHORD_TONAL_WEIGHT = 0.12
private const val NO_CHORD_TRANSITION = 0.08
private const val CHORD_TRANSITION = 0.18
private const val IN_CHORD_WEIGHT = 0.60
private const val MINIMUM_NOTE_WEIGHT = 0.15
private const val BASS_ROOT_WEIGHT = 0.10
private const val OUT_OF_CHORD_WEIGHT = 0.35
private const val EXTENSION_BONUS = 0.15
private const val MIN_DEFINING_SALIENCE = 0.18f
private const val DEFINING_OUTSIDE_RATIO = 1.6f
private const val MIN_INVERSION_SALIENCE = 0.10f
private const val INVERSION_ROOT_RATIO = 1.2f
private const val MIN_INVERSION_FRAMES = 2
private const val INVALID_EMISSION = -1.0
private const val MIN_TONAL_STRENGTH = 0.05f
private const val CONTEXT_TIE_MARGIN = 0.04
private const val CONTEXT_TIE_WEIGHT = 0.04
private const val ONSET_TRANSITION_DISCOUNT = 0.85
private const val MIN_TRANSITION_SCALE = 0.15
private const val MAX_ANALYSIS_SECONDS = 30 * 60

private val DEFINING_INTERVALS = mapOf(
    ChordQuality.MAJOR to emptySet(),
    ChordQuality.MINOR to emptySet(),
    ChordQuality.SUSPENDED_SECOND to emptySet(),
    ChordQuality.SUSPENDED_FOURTH to setOf(5),
    ChordQuality.DIMINISHED to setOf(6),
    ChordQuality.AUGMENTED to setOf(8),
    ChordQuality.MAJOR_SIXTH to setOf(9),
    ChordQuality.MINOR_SIXTH to setOf(9),
    ChordQuality.DOMINANT_SEVENTH to setOf(10),
    ChordQuality.MAJOR_SEVENTH to setOf(11),
    ChordQuality.MINOR_SEVENTH to setOf(10),
    ChordQuality.HALF_DIMINISHED_SEVENTH to setOf(6, 10),
    ChordQuality.ADD_NINTH to setOf(2),
    ChordQuality.MINOR_ADD_NINTH to setOf(2),
    ChordQuality.POWER to emptySet(),
)

private val QUALITY_PRIOR_PENALTY = ChordQuality.entries.associateWith { quality ->
    when (quality) {
        ChordQuality.MAJOR,
        ChordQuality.MINOR,
        ChordQuality.SUSPENDED_SECOND,
        ChordQuality.DOMINANT_SEVENTH,
        ChordQuality.POWER,
        -> 0.0

        else -> 0.10
    }
}

private val BONUS_INTERVALS = mapOf(
    ChordQuality.MAJOR to emptySet(),
    ChordQuality.MINOR to emptySet(),
    ChordQuality.SUSPENDED_SECOND to emptySet(),
    ChordQuality.SUSPENDED_FOURTH to emptySet(),
    ChordQuality.DIMINISHED to emptySet(),
    ChordQuality.AUGMENTED to emptySet(),
    ChordQuality.MAJOR_SIXTH to setOf(9),
    ChordQuality.MINOR_SIXTH to setOf(9),
    ChordQuality.DOMINANT_SEVENTH to setOf(10),
    ChordQuality.MAJOR_SEVENTH to setOf(11),
    ChordQuality.MINOR_SEVENTH to setOf(10),
    ChordQuality.HALF_DIMINISHED_SEVENTH to setOf(6, 10),
    ChordQuality.ADD_NINTH to setOf(2),
    ChordQuality.MINOR_ADD_NINTH to setOf(2),
    ChordQuality.POWER to emptySet(),
)
