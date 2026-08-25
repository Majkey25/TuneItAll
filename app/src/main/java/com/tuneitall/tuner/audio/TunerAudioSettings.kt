package com.tuneitall.tuner.audio

enum class ResponseMode {
    FAST,
    BALANCED,
    STABLE,
}

data class TunerAudioSettings(
    val sensitivity: DetectionSensitivity = DetectionSensitivity.DEFAULT,
    val response: ResponseMode = ResponseMode.BALANCED,
    val needleStability: Int = 80,
    val noiseRejection: Int = 20,
    val harmonicProtection: Int = 90,
    val inTuneCents: Int = 3,
    val confirmationMillis: Long = 900,
    val readingHoldMillis: Long = 1_000,
    val inputSource: AudioInputSource = AudioInputSource.AUTO,
) {
    init {
        require(needleStability in 0..100)
        require(noiseRejection in 0..100)
        require(harmonicProtection in 0..100)
        require(inTuneCents in 1..10)
        require(confirmationMillis in 100L..1_000L && confirmationMillis % 50L == 0L)
        require(readingHoldMillis in 0L..1_000L && readingHoldMillis % 50L == 0L)
    }
}

enum class TunerProfile(val settings: TunerAudioSettings) {
    BALANCED(TunerAudioSettings()),
    QUIET_ROOM(TunerAudioSettings(noiseRejection = 15, harmonicProtection = 75)),
    UNPLUGGED_ELECTRIC(
        TunerAudioSettings(
            response = ResponseMode.STABLE,
            needleStability = 85,
            noiseRejection = 0,
            harmonicProtection = 95,
        ),
    ),
    NOISY_ROOM(TunerAudioSettings(sensitivity = DetectionSensitivity(70), noiseRejection = 70, harmonicProtection = 95)),
    FAST_RESPONSE(
        TunerAudioSettings(
            response = ResponseMode.FAST,
            needleStability = 35,
            harmonicProtection = 60,
            readingHoldMillis = 450,
        ),
    ),
}
