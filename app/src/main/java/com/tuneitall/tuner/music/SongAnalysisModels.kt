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

fun <T : SongEvent> songEventAt(events: List<T>, positionMillis: Long): T? {
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

private const val DISPLAY_HOLD_MILLIS = 500L
