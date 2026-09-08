package com.tuneitall.tuner.audio

import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.pitchSearchRange
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuietDecayPitchCandidateTest {
    @Test
    fun `weak current fundamental is retained without acquisition weight`() {
        // Canonical D3 decay, seed 51, 48 kHz PCM16: samples [118784, 126976).
        // Source PCM SHA-256: 6c087a792b2182330bf280b4b58649694681bcb7d08aad06e68dc07bd125e24c.
        val encoded = requireNotNull(javaClass.getResourceAsStream("/audio/quiet-d3-decay.s16le.b64"))
            .bufferedReader().use { it.readText() }
        val bytes = Base64.getDecoder().decode(encoded.trim())
        assertEquals(16_384, bytes.size)
        assertEquals("d27fc8f5fd3c04276591da234f9875600a50e58b320ecce28733765a95f29aea",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(8192).also { buffer.get(it) }
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
        val range = pitchSearchRange(TunerMode.AUTO, tuning, 0, ReferencePitch(440.0))

        val frame = YinPitchDetector().analyze(samples, 48_000, range.minHertz, range.maxHertz)
        // This checks candidate identity, not the final tuner accuracy target.
        val fundamental = assertNotNull(frame.candidates.firstOrNull {
            abs(MusicMath.cents(it.hertz, 146.8324)) <= 25.0
        }, frame.toString())

        assertEquals(0.0, fundamental.probability)
        assertTrue(frame.candidates.sumOf(PitchCandidate::probability) <= 1.000001)
    }
}
