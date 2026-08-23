package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.MidiNote
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

enum class ChordQuality(val intervals: Set<Int>) {
    MAJOR(setOf(0, 4, 7)),
    MINOR(setOf(0, 3, 7)),
    DOMINANT_SEVENTH(setOf(0, 4, 7, 10)),
}

data class Chord(
    val rootPitchClass: Int,
    val quality: ChordQuality,
) {
    init {
        require(rootPitchClass in 0..11)
    }

    val pitchClasses: Set<Int> = quality.intervals.mapTo(mutableSetOf()) { (rootPitchClass + it) % 12 }

    fun transpose(semitones: Int): Chord = copy(rootPitchClass = Math.floorMod(rootPitchClass + semitones, 12))
}

data class ChordVoicing(val frets: List<Int>) {
    init {
        require(frets.size in 4..9)
        require(frets.all { it in -1..12 })
        require(frets.count { it >= 0 } >= 3)
    }
}

fun trainerChoices(answer: Chord, seed: Int): List<Chord> {
    val random = Random(seed)
    val distractors = buildList {
        for (root in 0..11) {
            ChordQuality.entries.forEach { quality ->
                Chord(root, quality).takeIf { it != answer }?.let(::add)
            }
        }
    }.shuffled(random).take(3)
    return (distractors + answer).shuffled(Random(seed xor TRAINER_SHUFFLE_SALT))
}

fun voicingFrequencies(openNotes: List<MidiNote>, voicing: ChordVoicing): DoubleArray {
    require(openNotes.size == voicing.frets.size)
    return voicing.frets.mapIndexedNotNull { index, fret ->
        fret.takeIf { it >= 0 }?.let { openNotes[index].value + it }
    }.distinct().map { midi -> 440.0 * 2.0.pow((midi - 69) / 12.0) }.toDoubleArray()
}

fun findPlayableVoicing(
    openNotes: List<MidiNote>,
    chord: Chord,
    maxFret: Int = 12,
): ChordVoicing? {
    require(openNotes.size in 4..9)
    require(maxFret in 4..12)
    var best: ScoredVoicing? = null
    var visitedNodes = 0

    for (windowStart in 1..maxFret) {
        val windowEnd = min(maxFret, windowStart + MAX_FRET_SPAN)
        val choices = openNotes.map { note ->
            buildList {
                add(-1)
                if (note.value % 12 in chord.pitchClasses) add(0)
                for (fret in windowStart..windowEnd) {
                    if ((note.value + fret) % 12 in chord.pitchClasses) add(fret)
                }
            }
        }
        val current = IntArray(openNotes.size) { -1 }

        fun search(stringIndex: Int, sounded: Int) {
            if (++visitedNodes > MAX_SEARCH_NODES) return
            if (sounded + openNotes.size - stringIndex < chord.pitchClasses.size) return
            if (stringIndex == openNotes.size) {
                val scored = scoreVoicing(openNotes, chord, current) ?: return
                if (best == null || scored.score > requireNotNull(best).score) best = scored
                return
            }
            choices[stringIndex].forEach { fret ->
                current[stringIndex] = fret
                search(stringIndex + 1, sounded + if (fret >= 0) 1 else 0)
            }
        }
        search(0, 0)
    }
    return best?.let { ChordVoicing(it.frets.toList()) }
}

private fun scoreVoicing(openNotes: List<MidiNote>, chord: Chord, frets: IntArray): ScoredVoicing? {
    val sounded = frets.indices.filter { frets[it] >= 0 }
    if (sounded.size < 3) return null
    val pitchClasses = sounded.mapTo(mutableSetOf()) { (openNotes[it].value + frets[it]) % 12 }
    if (!pitchClasses.containsAll(chord.pitchClasses)) return null
    val fretted = frets.filter { it > 0 }
    val fretSpan = if (fretted.isEmpty()) 0 else requireNotNull(fretted.maxOrNull()) - requireNotNull(fretted.minOrNull())
    if (fretSpan > MAX_FRET_SPAN) return null
    val lowest = sounded.first()
    val bassPitchClass = (openNotes[lowest].value + frets[lowest]) % 12
    val distinctFrets = fretted.toSet().size
    val muted = frets.count { it < 0 }
    val open = frets.count { it == 0 }
    val highestFret = max(0, fretted.maxOrNull() ?: 0)
    val score = sounded.size * 20 +
        open * 3 +
        (if (bassPitchClass == chord.rootPitchClass) 32 else 0) -
        muted * 5 -
        distinctFrets * 2 -
        fretSpan * 3 -
        highestFret * 5
    return ScoredVoicing(frets.copyOf(), score)
}

private data class ScoredVoicing(val frets: IntArray, val score: Int)

private const val MAX_FRET_SPAN = 4
private const val MAX_SEARCH_NODES = 250_000
private const val TRAINER_SHUFFLE_SALT = 0x5A17
