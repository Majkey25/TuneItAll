package com.tuneitall.tuner.music

import com.tuneitall.tuner.model.MidiNote
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

data class ChordVoicing(
    val frets: List<Int>,
    val fingers: List<Int> = List(frets.size) { 0 },
    val barres: List<Int> = emptyList(),
    val baseFret: Int = 1,
) {
    init {
        require(frets.size in 4..9)
        require(frets.all { it in -1..24 })
        require(frets.count { it >= 0 } >= 3)
        require(fingers.size == frets.size)
        require(fingers.all { it in 0..4 })
        require(barres.all { it in 1..24 })
        require(baseFret in 1..24)
    }
}

data class NoteQuestion(
    val answerPitchClass: Int,
    val midiNote: Int,
    val choices: List<Int>,
) {
    init {
        require(answerPitchClass in 0..11)
        require(midiNote == MIDDLE_C_MIDI + answerPitchClass)
        require(choices.size == NOTE_CHOICE_COUNT)
        require(choices.toSet().size == NOTE_CHOICE_COUNT)
        require(answerPitchClass in choices)
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

fun noteQuestion(seed: Int): NoteQuestion {
    val answer = Math.floorMod(seed * NOTE_QUESTION_STEP, 12)
    val distractors = (0..11).filter { it != answer }.shuffled(Random(seed)).take(NOTE_CHOICE_COUNT - 1)
    return NoteQuestion(
        answerPitchClass = answer,
        midiNote = MIDDLE_C_MIDI + answer,
        choices = (distractors + answer).shuffled(Random(seed xor NOTE_TRAINER_SHUFFLE_SALT)),
    )
}

fun midiToHertz(midiNote: Int): Double {
    require(midiNote in MidiNote.MIN_VALUE..MidiNote.MAX_VALUE)
    return 440.0 * 2.0.pow((midiNote - 69) / 12.0)
}

fun voicingFrequencies(openNotes: List<MidiNote>, voicing: ChordVoicing): DoubleArray {
    require(openNotes.size == voicing.frets.size)
    return voicing.frets.mapIndexedNotNull { index, fret ->
        fret.takeIf { it >= 0 }?.let { openNotes[index].value + it }
    }.distinct().map(::midiToHertz).toDoubleArray()
}

private const val TRAINER_SHUFFLE_SALT = 0x5A17
private const val NOTE_TRAINER_SHUFFLE_SALT = 0x2C71
private const val NOTE_QUESTION_STEP = 5
private const val NOTE_CHOICE_COUNT = 4
private const val MIDDLE_C_MIDI = 60
