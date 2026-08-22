package com.tuneitall.tuner.metronome

@JvmInline
value class Bpm(val value: Int) {
    init {
        require(value in 20..400)
    }
}

enum class MetronomeSound {
    WOOD,
    CLICK,
    RIM,
}

data class MetronomeSettings(
    val bpm: Bpm = Bpm(120),
    val numerator: Int = 4,
    val denominator: Int = 4,
    val subdivision: Int = 1,
    val accentEvery: Int? = null,
    val volume: Int = 80,
    val countIn: Int = 0,
    val sound: MetronomeSound = MetronomeSound.WOOD,
) {
    init {
        require(numerator in 1..12)
        require(denominator in setOf(2, 4, 8, 16))
        require(subdivision in 1..4)
        require(accentEvery == null || accentEvery in 2..12)
        require(volume in 0..100)
        require(countIn in setOf(0, 1, 2, 4))
    }
}
