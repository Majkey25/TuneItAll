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
            listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH).forEach { quality ->
                repeat(PITCH_CLASS_COUNT) { root -> add(Chord(root, quality)) }
            }
        }
    }
    val emissions = frames.map { frame ->
        DoubleArray(chords.size + 1).also { scores ->
            scores[0] = NO_CHORD_BASE + NO_CHORD_TONAL_WEIGHT * (1.0 - frame.tonalStrength)
            chords.forEachIndexed { index, chord ->
                scores[index + 1] = chordEmission(frame, chord)
            }
        }
    }
    val states = viterbi(emissions)
    val events = mutableListOf<ChordEvent>()
    var activeState = states.first()
    var activeStart = frames.first().startMillis
    var confidenceTotal = emissions.first()[activeState]
    var confidenceCount = 1

    fun close(endMillis: Long) {
        if (activeState == 0 || endMillis <= activeStart) return
        val sourceChord = chords[activeState - 1]
        val duration = endMillis - activeStart
        val chord = if (sourceChord.quality == ChordQuality.DOMINANT_SEVENTH && duration < MIN_SEVENTH_MILLIS) {
            Chord(sourceChord.rootPitchClass, ChordQuality.MAJOR)
        } else {
            sourceChord
        }
        events += ChordEvent(
            startMillis = activeStart,
            endMillis = endMillis,
            chord = chord,
            confidence = (confidenceTotal / confidenceCount).coerceIn(0.0, 1.0),
        )
    }

    for (index in 1 until states.size) {
        val state = states[index]
        if (state != activeState) {
            close(frames[index].startMillis)
            activeState = state
            activeStart = frames[index].startMillis
            confidenceTotal = 0.0
            confidenceCount = 0
        }
        confidenceTotal += emissions[index][state]
        confidenceCount++
    }
    close(songEndMillis)
    return mergeChordGaps(events.filter { it.durationMillis >= MIN_EVENT_MILLIS })
}

private fun chordEmission(frame: HarmonicFrame, chord: Chord): Double {
    if (frame.tonalStrength <= 0f) return 0.0
    if (chord.quality == ChordQuality.DOMINANT_SEVENTH && !hasStableSeventh(frame.chroma, chord.rootPitchClass)) {
        return INVALID_EMISSION
    }
    val inChord = chord.pitchClasses.map { frame.chroma[it].toDouble() }
    val outOfChord = frame.chroma.indices.filterNot(chord.pitchClasses::contains).map { frame.chroma[it].toDouble() }
    val inAverage = inChord.average()
    val minimum = inChord.min()
    val outAverage = outOfChord.average()
    val bassRoot = frame.bassChroma[chord.rootPitchClass]
    val extensionBonus = if (chord.quality == ChordQuality.DOMINANT_SEVENTH) {
        SEVENTH_BONUS * frame.chroma[(chord.rootPitchClass + 10) % PITCH_CLASS_COUNT]
    } else {
        0.0
    }
    return (
        IN_CHORD_WEIGHT * inAverage + MINIMUM_NOTE_WEIGHT * minimum + BASS_ROOT_WEIGHT * bassRoot -
            OUT_OF_CHORD_WEIGHT * outAverage + extensionBonus
        ).coerceIn(0.0, 1.0)
}

private fun hasStableSeventh(chroma: FloatArray, root: Int): Boolean {
    val seventh = chroma[(root + 10) % PITCH_CLASS_COUNT]
    if (seventh < MIN_SEVENTH_SALIENCE) return false
    val chordPitches = setOf(root, (root + 4) % PITCH_CLASS_COUNT, (root + 7) % PITCH_CLASS_COUNT, (root + 10) % PITCH_CLASS_COUNT)
    val outside = chroma.indices.filterNot(chordPitches::contains).map(chroma::get).sorted()
    return seventh >= SEVENTH_OUTSIDE_RATIO * outside[outside.size / 2]
}

private fun viterbi(emissions: List<DoubleArray>): IntArray {
    val stateCount = emissions.first().size
    val backPointers = Array(emissions.size) { ByteArray(stateCount) }
    var previous = emissions.first().copyOf()
    for (frameIndex in 1 until emissions.size) {
        val current = DoubleArray(stateCount)
        for (state in 0 until stateCount) {
            var bestPrevious = state
            var bestScore = previous[state]
            for (candidate in 0 until stateCount) {
                if (candidate == state) continue
                val penalty = if (candidate == 0 || state == 0) NO_CHORD_TRANSITION else CHORD_TRANSITION
                val score = previous[candidate] - penalty
                if (score > bestScore) {
                    bestScore = score
                    bestPrevious = candidate
                }
            }
            current[state] = bestScore + emissions[frameIndex][state]
            backPointers[frameIndex][state] = bestPrevious.toByte()
        }
        previous = current
    }
    val states = IntArray(emissions.size)
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
private const val MIN_EVENT_MILLIS = 300L
private const val MIN_SEVENTH_MILLIS = 750L
private const val MAX_BRIDGE_GAP_MILLIS = 350L
private const val DISPLAY_HOLD_MILLIS = 500L
private const val NO_CHORD_BASE = 0.12
private const val NO_CHORD_TONAL_WEIGHT = 0.40
private const val NO_CHORD_TRANSITION = 0.08
private const val CHORD_TRANSITION = 0.18
private const val IN_CHORD_WEIGHT = 0.60
private const val MINIMUM_NOTE_WEIGHT = 0.15
private const val BASS_ROOT_WEIGHT = 0.20
private const val OUT_OF_CHORD_WEIGHT = 0.35
private const val SEVENTH_BONUS = 0.15
private const val MIN_SEVENTH_SALIENCE = 0.24f
private const val SEVENTH_OUTSIDE_RATIO = 2f
private const val INVALID_EMISSION = -1.0
private const val MAX_ANALYSIS_SECONDS = 30 * 60
