package com.tuneitall.tuner.model

@JvmInline
value class MidiNote(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "MIDI note must be between $MIN_VALUE and $MAX_VALUE" }
    }

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 127
    }
}

@JvmInline
value class ReferencePitch(val hertz: Double) {
    init {
        require(hertz.isFinite() && hertz in MIN_HERTZ..MAX_HERTZ) {
            "Reference pitch must be finite and between $MIN_HERTZ and $MAX_HERTZ Hz"
        }
    }

    companion object {
        const val DEFAULT_HERTZ = 440.0
        const val MIN_HERTZ = 410.0
        const val MAX_HERTZ = 480.0
    }
}

enum class Instrument {
    GUITAR,
    BASS,
    UKULELE,
    VIOLIN,
    VIOLA,
    CELLO,
    MANDOLIN,
    CHROMATIC,
}

enum class HeadstockLayout {
    INLINE_4,
    SPLIT_2_2,
    INLINE_5,
    SPLIT_3_2,
    SPLIT_3_3,
    INLINE_6,
    INLINE_7,
    SPLIT_4_3,
    INLINE_8,
    SPLIT_4_4,
    INLINE_9,
    SPLIT_5_4,
}

val HeadstockLayout.stringCount: Int
    get() = when (this) {
        HeadstockLayout.INLINE_4,
        HeadstockLayout.SPLIT_2_2,
        -> 4

        HeadstockLayout.INLINE_5,
        HeadstockLayout.SPLIT_3_2,
        -> 5

        HeadstockLayout.SPLIT_3_3,
        HeadstockLayout.INLINE_6,
        -> 6

        HeadstockLayout.INLINE_7,
        HeadstockLayout.SPLIT_4_3,
        -> 7

        HeadstockLayout.INLINE_8,
        HeadstockLayout.SPLIT_4_4,
        -> 8

        HeadstockLayout.INLINE_9,
        HeadstockLayout.SPLIT_5_4,
        -> 9
    }

data class TuningPreset(
    val id: String,
    val name: String,
    val instrument: Instrument,
    val notesLowToHigh: List<MidiNote>,
    val layouts: Set<HeadstockLayout>,
) {
    init {
        require(id.isNotBlank()) { "Tuning ID must not be blank" }
        require(name.isNotBlank()) { "Tuning name must not be blank" }
        require(instrument != Instrument.CHROMATIC) { "Chromatic mode does not use tuning presets" }
        require(notesLowToHigh.size in instrument.validStringCounts()) {
            "${instrument.name} does not support ${notesLowToHigh.size} strings"
        }
        require(layouts.isNotEmpty()) { "A tuning preset must support at least one headstock layout" }
        require(layouts.all { it.stringCount == notesLowToHigh.size }) {
            "Every headstock layout must match the tuning string count"
        }
    }
}

private fun Instrument.validStringCounts(): Set<Int> = when (this) {
    Instrument.GUITAR -> setOf(6, 7, 8, 9)
    Instrument.BASS -> setOf(4, 5, 6)
    Instrument.VIOLIN,
    Instrument.VIOLA,
    Instrument.CELLO,
    Instrument.MANDOLIN,
    Instrument.UKULELE,
    -> setOf(4)

    Instrument.CHROMATIC -> emptySet()
}
