package com.tuneitall.tuner.ui

import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetronomeViewModelTest {
    @Test
    fun `tap tempo uses median of last five valid intervals`() {
        val tapTempo = TapTempo()

        assertNull(tapTempo.tap(0L))
        tapTempo.tap(500L)
        tapTempo.tap(900L)
        tapTempo.tap(1_500L)
        tapTempo.tap(1_980L)

        assertEquals(Bpm(120), tapTempo.tap(2_500L))
    }

    @Test
    fun `tap tempo resets after two seconds`() {
        val tapTempo = TapTempo()

        assertNull(tapTempo.tap(0L))
        assertEquals(Bpm(120), tapTempo.tap(500L))
        assertNull(tapTempo.tap(2_501L))
        assertEquals(Bpm(120), tapTempo.tap(3_001L))
    }

    @Test
    fun `tap tempo accepts exact 30 to 400 BPM boundaries`() {
        val fastest = TapTempo()
        assertNull(fastest.tap(0L))
        assertEquals(Bpm(400), fastest.tap(150L))

        val slowest = TapTempo()
        assertNull(slowest.tap(0L))
        assertEquals(Bpm(30), slowest.tap(2_000L))
        assertNull(slowest.tap(4_001L))
    }

    @Test
    fun `direct entry clamps bounded controls and ignores invalid choices`() {
        assertEquals(Bpm(20), clampedBpm(19))
        assertEquals(Bpm(400), clampedBpm(401))
        assertEquals(0, clampedVolume(-1))
        assertEquals(100, clampedVolume(101))

        val initial = MetronomeSettings()
        listOf<(MetronomeSettings) -> MetronomeSettings>(
            { it.copy(numerator = 0) },
            { it.copy(numerator = 13) },
            { it.copy(denominator = 3) },
            { it.copy(subdivision = 0) },
            { it.copy(subdivision = 5) },
            { it.copy(accentEvery = 1) },
            { it.copy(accentEvery = 13) },
            { it.copy(countIn = 3) },
        ).forEach { invalid ->
            assertEquals(initial, validatedSettings(initial, invalid))
        }
    }
}
