package com.tuneitall.tuner.music

enum class SongAnalysisMode {
    CHORDS,
    NOTES,
    POWER,
}

enum class NoteRange(val midiRange: IntRange) {
    ANY(21..108),
    GUITAR(40..88),
    BASS(28..72),
    VIOLIN(55..100),
    PIANO(21..108),
}

sealed interface SongEvent {
    val startMillis: Long
    val endMillis: Long
    val confidence: Double

    val durationMillis: Long
        get() = endMillis - startMillis
}

data class NoteEvent(
    override val startMillis: Long,
    override val endMillis: Long,
    val midiNote: Int,
    override val confidence: Double,
) : SongEvent {
    init {
        require(startMillis >= 0L)
        require(endMillis > startMillis)
        require(midiNote in 0..127)
        require(confidence in 0.0..1.0)
    }
}
