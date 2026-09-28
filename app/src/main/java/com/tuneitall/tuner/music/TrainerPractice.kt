package com.tuneitall.tuner.music

import kotlin.random.Random

enum class NoteBank { LEARNING, LEARNED }

data class NoteTrainingSets(
    val learning: Set<Int> = setOf(0, 2, 4),
    val learned: Set<Int> = emptySet(),
) {
    init {
        require((learning + learned).all { it in 0..11 })
        require(learning.intersect(learned).isEmpty())
    }

    fun notes(bank: NoteBank): Set<Int> = if (bank == NoteBank.LEARNING) learning else learned

    fun update(bank: NoteBank, notes: Set<Int>): NoteTrainingSets = when (bank) {
        NoteBank.LEARNING -> NoteTrainingSets(notes.toSet(), learned - notes)
        NoteBank.LEARNED -> NoteTrainingSets(learning - notes, notes.toSet())
    }
}

fun <T> nextTrainerItem(items: List<T>, previous: T? = null, random: Random = Random.Default): T {
    require(items.isNotEmpty())
    return items.filter { it != previous }.ifEmpty { items }.random(random)
}
