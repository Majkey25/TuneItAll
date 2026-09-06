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
    isCancelled: () -> Boolean = { false },
): List<ChordEvent> {
    checkAnalysisCancellation(isCancelled)
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
        checkAnalysisCancellation(isCancelled)
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
        val bass = detectInversionBass(sourceChord, segment, endMillis)
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
        checkAnalysisCancellation(isCancelled)
        val state = states[index]
        if (state != activeState) {
            // Half-overlapping windows represent their centers; a change lies between adjacent centers.
            val boundary = (frames[index].startMillis +
                (frames[index].startMillis - frames[index - 1].startMillis) / 2).coerceAtMost(songEndMillis)
            close(boundary, index)
            activeState = state
            activeStart = boundary
            activeFrameStart = index
            confidenceTotal = 0.0
            confidenceCount = 0
        }
        confidenceTotal += stateConfidence(frames, index, chords, mode, state)
        confidenceCount++
    }
    close(songEndMillis, frames.size)
    val accepted = mergeChordGaps(events.filter { it.durationMillis >= MIN_EVENT_MILLIS })
    return if (mode == SongAnalysisMode.CHORDS) refineChordQualities(accepted, frames, isCancelled) else accepted
}

private data class QualitySegment(var start: Int, var end: Int, val state: Int)

private fun refineChordQualities(
    events: List<ChordEvent>,
    frames: List<HarmonicFrame>,
    isCancelled: () -> Boolean,
): List<ChordEvent> = buildList {
    var eventIndex = 0
    var frameIndex = 0
    fun boundary(index: Int): Long = if (index == 0) frames.first().startMillis else {
        frames[index].startMillis + (frames[index].startMillis - frames[index - 1].startMillis) / 2
    }
    while (eventIndex < events.size) {
        checkAnalysisCancellation(isCancelled)
        val firstEvent = eventIndex
        val start = events[eventIndex].startMillis
        val root = events[eventIndex].chord.rootPitchClass
        var end = events[eventIndex++].endMillis
        while (eventIndex < events.size && events[eventIndex].startMillis == end &&
            events[eventIndex].chord.rootPitchClass == root
        ) end = events[eventIndex++].endMillis
        while (frameIndex < frames.size && boundary(frameIndex) < start) frameIndex++
        val firstFrame = frameIndex
        while (frameIndex < frames.size && boundary(frameIndex) < end) frameIndex++
        val segmentFrames = frames.subList(firstFrame, frameIndex)
        if (segmentFrames.isEmpty()) {
            addAll(events.subList(firstEvent, eventIndex))
            continue
        }
        val qualities = instructionalChordQualities.map { Chord(root, it) }
        var baselineEvent = firstEvent
        val baselineStates = IntArray(segmentFrames.size) { index ->
            val time = boundary(firstFrame + index)
            while (baselineEvent + 1 < eventIndex && time >= events[baselineEvent].endMillis) baselineEvent++
            qualities.indexOfFirst { it.quality == events[baselineEvent].chord.quality } + 1
        }
        val states = viterbi(segmentFrames.size, qualities.size + 1, segmentFrames, qualities, baselineStates) { index ->
            checkAnalysisCancellation(isCancelled)
            val local = DoubleArray(qualities.size) { qualityEmission(segmentFrames, index, qualities[it]) }
            val baseline = baselineStates[index] - 1
            if (local[baseline] == INVALID_EMISSION) local[baseline] = chordEmission(segmentFrames[index], qualities[baseline])
            val best = local.max()
            DoubleArray(qualities.size + 1) { state ->
                if (state == 0) Double.NEGATIVE_INFINITY else {
                    val score = local[state - 1]
                    if (best - score <= CONTEXT_TIE_MARGIN) score + CONTEXT_TIE_WEIGHT * chordEmission(
                        segmentFrames[index], qualities[state - 1], segmentFrames[index].contextChroma,
                    ) else score
                }
            }
        }
        fun time(index: Int): Long = when (index) {
            0 -> start
            states.size -> end
            else -> boundary(firstFrame + index)
        }
        val segments = mutableListOf<QualitySegment>()
        states.forEachIndexed { index, state ->
            if (segments.lastOrNull()?.state == state) segments.last().end = index + 1
            else segments += QualitySegment(index, index + 1, state)
        }
        var index = 0
        while (index < segments.size && segments.size > 1) {
            checkAnalysisCancellation(isCancelled)
            val segment = segments[index]
            if (time(segment.end) - time(segment.start) >= MIN_EVENT_MILLIS) {
                index++
                continue
            }
            val neighbor = listOf(index - 1, index + 1).filter { it in segments.indices }.maxBy { candidate ->
                val chord = qualities[segments[candidate].state - 1]
                (segment.start until segment.end).sumOf { qualityEmission(segmentFrames, it, chord) }
            }
            segments[neighbor].start = minOf(segments[neighbor].start, segment.start)
            segments[neighbor].end = maxOf(segments[neighbor].end, segment.end)
            segments.removeAt(index)
            index = maxOf(0, index - 1)
            if (index + 1 < segments.size && segments[index].state == segments[index + 1].state) {
                segments[index].end = segments.removeAt(index + 1).end
            }
        }
        for (segment in segments) {
            val segmentStart = time(segment.start)
            val segmentEnd = time(segment.end)
            val confidence = events.subList(firstEvent, eventIndex).sumOf { event ->
                val overlap = (minOf(event.endMillis, segmentEnd) - maxOf(event.startMillis, segmentStart)).coerceAtLeast(0L)
                overlap * event.confidence
            } / (segmentEnd - segmentStart)
            val chord = qualities[segment.state - 1]
            add(ChordEvent(segmentStart, segmentEnd, chord.copy(bassPitchClass = detectInversionBass(
                chord, segmentFrames.subList(segment.start, segment.end), segmentEnd,
            )), confidence.coerceIn(0.0, 1.0)))
        }
    }
}

private fun qualityEmission(frames: List<HarmonicFrame>, index: Int, chord: Chord): Double {
    if (!hasStableDefiningIntervals(frames, index, chord)) return INVALID_EMISSION
    val frame = frames[index]
    val score = chordEmission(frame, chord)
    val intervals = DEFINING_INTERVALS.getValue(chord.quality)
    val observed = hasDefiningIntervals(frame.observedChroma, chord, intervals) &&
        ((index > 0 && hasDefiningIntervals(frames[index - 1].observedChroma, chord, intervals)) ||
            (index < frames.lastIndex && hasDefiningIntervals(frames[index + 1].observedChroma, chord, intervals)))
    if (intervals.isNotEmpty() && !observed) return INVALID_EMISSION
    return if (score > 0.0 && observed) {
        score + QUALITY_PRIOR_PENALTY.getValue(chord.quality)
    } else score
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
    // Resolve the root first so extension evidence cannot turn a chord into its relative chord.
    val root = chords[localScores.indices.maxBy(localScores::get)].rootPitchClass
    chords.forEachIndexed { index, chord ->
        if (chord.rootPitchClass == root && localScores[index] > 0.0) {
            val notes = chord.pitchClasses.map(frame.chroma::get)
            val evidence = notes.min() / notes.max().coerceAtLeast(MIN_DEFINING_SALIENCE)
            localScores[index] = (localScores[index] + QUALITY_PRIOR_PENALTY.getValue(chord.quality) * evidence)
                .coerceAtMost(1.0)
        }
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
    // Inferred subharmonics cannot supply a second chord note. Use the same local window as the chord evidence.
    val observed = frame.localObservedChroma
    val observedThreshold = observed.max() * MIN_CHORD_NOTE_RATIO
    if (chord.pitchClasses.count { observed[it] >= observedThreshold && observed[it] > 0f } < 2) return 0.0
    val inChord = chord.pitchClasses.map { chroma[it].toDouble() }
    if (inChord.count { it >= chroma.max() * MIN_CHORD_NOTE_RATIO && it > 0.0 } < 2) return 0.0
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

private fun detectInversionBass(chord: Chord, frames: List<HarmonicFrame>, endMillis: Long): Int? {
    val supportedMillis = LongArray(PITCH_CLASS_COUNT)
    val supportedFrames = IntArray(PITCH_CLASS_COUNT)
    frames.forEachIndexed { index, frame ->
        val candidate = chord.pitchClasses.maxBy(frame.bassChroma::get)
        val value = frame.bassChroma[candidate]
        val root = frame.bassChroma[chord.rootPitchClass]
        if (candidate != chord.rootPitchClass && value >= MIN_INVERSION_SALIENCE && value >= INVERSION_ROOT_RATIO * root) {
            supportedMillis[candidate] += (frames.getOrNull(index + 1)?.startMillis ?: endMillis) - frame.startMillis
            supportedFrames[candidate]++
        }
    }
    val dominant = supportedMillis.indices.maxBy(supportedMillis::get)
    // A passing bass must not label the entire segment as an inversion.
    return dominant.takeIf {
        supportedFrames[it] >= MIN_INVERSION_FRAMES &&
            supportedMillis[it] * 2 > endMillis - frames.first().startMillis
    }
}

internal fun viterbi(emissions: List<DoubleArray>, frames: List<HarmonicFrame>): IntArray {
    require(emissions.size == frames.size)
    val stateCount = emissions.first().size
    require(stateCount >= 2 && emissions.all { it.size == stateCount })
    return viterbi(emissions.size, stateCount, frames, emissionAt = emissions::get)
}

private inline fun viterbi(
    frameCount: Int,
    stateCount: Int,
    frames: List<HarmonicFrame>,
    chords: List<Chord> = emptyList(),
    baselineStates: IntArray = IntArray(0),
    emissionAt: (Int) -> DoubleArray,
): IntArray {
    val qualityTransitions = List(stateCount) { state ->
        if (state == 0 || chords.isEmpty()) emptyList() else {
            val source = chords[state - 1]
            chords.indices.filter { it != state - 1 && chords[it].rootPitchClass == source.rootPitchClass }.map { index ->
                val from = source.pitchClasses.toSet()
                val to = chords[index].pitchClasses.toSet()
                val distance = ((from - to).size + (to - from).size).toDouble() / (from + to).size
                (index + 1) to CHORD_TRANSITION * distance
            }
        }
    }
    val backPointers = Array(frameCount) { ShortArray(stateCount) }
    var previous = emissionAt(0)
    if (baselineStates.isNotEmpty()) previous.indices.forEach { state ->
        if (state != baselineStates[0]) previous[state] -= MIN_SCORE_MARGIN
    }
    var previousInstantState = previous.indices.maxBy(previous::get)
    for (frameIndex in 1 until frameCount) {
        val emissions = emissionAt(frameIndex)
        val instantState = emissions.indices.maxBy(emissions::get)
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
            val deviation = baselineStates.isNotEmpty() && state != baselineStates[frameIndex]
            val entryPenalty = if (deviation) MIN_SCORE_MARGIN else 0.0
            val stayPenalty = if (deviation && state == baselineStates[frameIndex - 1]) MIN_SCORE_MARGIN else 0.0
            var bestPrevious = state
            var bestScore = previous[state] - stayPenalty
            val noChordToChord = previous[0] - noChordPenalty - entryPenalty
            if (noChordToChord > bestScore) {
                bestPrevious = 0
                bestScore = noChordToChord
            }
            val otherChord = if (bestChord == state) secondChord else bestChord
            if (otherChord >= 0 && previous[otherChord] - chordPenalty - entryPenalty > bestScore) {
                bestPrevious = otherChord
                bestScore = previous[otherChord] - chordPenalty - entryPenalty
            }
            for ((candidate, cost) in qualityTransitions[state]) {
                if (previousInstantState == 0 || instantState == 0 ||
                    chords[state - 1].rootPitchClass != chords[previousInstantState - 1].rootPitchClass ||
                    chords[state - 1].rootPitchClass != chords[instantState - 1].rootPitchClass
                ) continue
                val score = previous[candidate] - cost * transitionScale - entryPenalty
                if (score > bestScore) {
                    bestPrevious = candidate
                    bestScore = score
                }
            }
            current[state] = bestScore + emissions[state]
            backPointers[frameIndex][state] = bestPrevious.toShort()
        }
        previous = current
        previousInstantState = instantState
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
private const val MIN_CHORD_NOTE_RATIO = 0.10
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
