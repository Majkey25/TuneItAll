package com.tuneitall.tuner.music

import kotlin.math.abs

internal fun analyzeNotes(
    frames: List<HarmonicFrame>,
    range: NoteRange,
    songEndMillis: Long,
): List<NoteEvent> {
    if (frames.isEmpty() || songEndMillis <= 0L) return emptyList()
    val midiRange = range.midiRange
    val emissions = frames.map { frame ->
        DoubleArray(midiRange.count() + 1).also { scores ->
            scores[0] = NO_NOTE_BASE + NO_NOTE_TONAL_WEIGHT * (1.0 - frame.tonalStrength)
            midiRange.forEachIndexed { index, midi -> scores[index + 1] = noteEmission(frame, midi) }
        }
    }
    val states = decodeNoteStates(frames, emissions, midiRange)
    val events = mutableListOf<NoteEvent>()
    var activeState = states.first()
    var activeStart = frames.first().startMillis
    var confidenceTotal = emissions.first()[activeState]
    var confidenceCount = 1

    fun close(endMillis: Long) {
        if (activeState == 0 || endMillis <= activeStart) return
        events += NoteEvent(
            startMillis = activeStart,
            endMillis = endMillis,
            midiNote = midiRange.first + activeState - 1,
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
    return mergeNoteGaps(events.filter { it.durationMillis >= MIN_NOTE_MILLIS })
}

private fun noteEmission(frame: HarmonicFrame, midi: Int): Double {
    val index = midi - NoteRange.ANY.midiRange.first
    val salience = frame.noteSalience.getOrElse(index) { 0f }
    val lowerHarmonic = listOf(index - 12, index - 19, index - 24)
        .mapNotNull(frame.noteSalience::getOrNull)
        .maxOrNull()
        ?: 0f
    val independent = (salience - HARMONIC_PENALTY * lowerHarmonic).coerceAtLeast(0f)
    return if (independent < MIN_NOTE_SALIENCE) 0.0 else independent.toDouble()
}

private fun decodeNoteStates(
    frames: List<HarmonicFrame>,
    emissions: List<DoubleArray>,
    midiRange: IntRange,
): IntArray {
    val stateCount = emissions.first().size
    val backPointers = Array(emissions.size) { ByteArray(stateCount) }
    var previous = emissions.first().copyOf()
    for (frameIndex in 1 until emissions.size) {
        val current = DoubleArray(stateCount)
        val globalBest = previous.indices.maxBy(previous::get)
        for (state in 0 until stateCount) {
            val candidates = intArrayOf(
                state,
                0,
                globalBest,
                state - 12,
                state + 12,
                state - 2,
                state - 1,
                state + 1,
                state + 2,
            )
            var bestPrevious = state
            var bestScore = Double.NEGATIVE_INFINITY
            candidates.forEach { candidate ->
                if (candidate !in 0 until stateCount) return@forEach
                val score = previous[candidate] - noteTransitionPenalty(
                    fromState = candidate,
                    toState = state,
                    onsetStrength = frames[frameIndex].onsetStrength,
                    midiRange = midiRange,
                )
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

private fun noteTransitionPenalty(fromState: Int, toState: Int, onsetStrength: Float, midiRange: IntRange): Double {
    if (fromState == toState) return 0.0
    if (fromState == 0 || toState == 0) return NO_NOTE_TRANSITION
    val fromMidi = midiRange.first + fromState - 1
    val toMidi = midiRange.first + toState - 1
    val jump = abs(toMidi - fromMidi).coerceAtMost(MAX_PENALIZED_JUMP)
    val onsetMultiplier = 1.0 - ONSET_TRANSITION_RELIEF * onsetStrength
    return (NOTE_CHANGE_BASE + NOTE_JUMP_PENALTY * jump) * onsetMultiplier
}

private fun mergeNoteGaps(events: List<NoteEvent>): List<NoteEvent> = buildList {
    events.forEach { event ->
        val previous = lastOrNull()
        if (previous != null && previous.midiNote == event.midiNote && event.startMillis - previous.endMillis <= MAX_NOTE_GAP_MILLIS) {
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

private const val NO_NOTE_BASE = 0.18
private const val NO_NOTE_TONAL_WEIGHT = 0.55
private const val NO_NOTE_TRANSITION = 0.08
private const val NOTE_CHANGE_BASE = 0.14
private const val NOTE_JUMP_PENALTY = 0.012
private const val ONSET_TRANSITION_RELIEF = 0.75
private const val MAX_PENALIZED_JUMP = 24
private const val HARMONIC_PENALTY = 0.85f
private const val MIN_NOTE_SALIENCE = 0.18f
private const val MIN_NOTE_MILLIS = 120L
private const val MAX_NOTE_GAP_MILLIS = 120L
