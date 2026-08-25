package com.tuneitall.tuner.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TunerAudioSettingsTest {
    @Test
    fun `universal settings keep capture sensitive and the needle stable`() {
        assertEquals(
            TunerAudioSettings(
                sensitivity = DetectionSensitivity(100),
                response = ResponseMode.BALANCED,
                needleStability = 80,
                noiseRejection = 20,
                harmonicProtection = 90,
                inTuneCents = 3,
                confirmationMillis = 900,
                readingHoldMillis = 1_000,
                inputSource = AudioInputSource.AUTO,
            ),
            TunerProfile.BALANCED.settings,
        )
    }

    @Test
    fun `unplugged electric preset separates quiet capture from display stability`() {
        val settings = assertNotNull(
            TunerProfile.entries.firstOrNull { it.name == "UNPLUGGED_ELECTRIC" },
        ).settings

        assertEquals(100, settings.sensitivity.value)
        assertEquals(ResponseMode.STABLE, settings.response)
        assertEquals(85, settings.needleStability)
        assertEquals(0, settings.noiseRejection)
        assertEquals(1_000L, settings.readingHoldMillis)
        assertEquals(AudioInputSource.AUTO, settings.inputSource)
    }

    @Test
    fun `unsafe audio settings are rejected`() {
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(needleStability = 101) }
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(inTuneCents = 0) }
        assertFailsWith<IllegalArgumentException> { TunerAudioSettings(confirmationMillis = 50) }
    }
}
