package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TunerAudioSettingsTest {
    @Test
    fun `balanced settings match documented safe defaults`() {
        assertEquals(
            TunerAudioSettings(
                sensitivity = DetectionSensitivity(100),
                response = ResponseMode.BALANCED,
                needleStability = 65,
                noiseRejection = 30,
                harmonicProtection = 80,
                inTuneCents = 3,
                confirmationMillis = 250,
                readingHoldMillis = 250,
                inputSource = AudioInputSource.AUTO,
            ),
            TunerProfile.BALANCED.settings,
        )
    }

    @Test
    fun `unsafe audio settings are rejected`() {
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(needleStability = 101) }
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(inTuneCents = 0) }
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(confirmationMillis = 50) }
    }
}
