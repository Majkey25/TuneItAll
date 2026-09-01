package com.tuneitall.tuner.audio

import android.media.MediaRecorder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class AudioPipelineTest {
    @Test
    fun `audio capabilities can report no active source`() {
        assertEquals(
            null,
            AudioInputCapabilities(rawSupported = false, activeSource = null).activeSource,
        )
    }

    @Test
    fun `released worker joins and clears exact ownership`() {
        val ownership = AudioWorkerOwnership()
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val worker = Thread {
            try {
                started.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (_: InterruptedException) {
                    interrupted.countDown()
                }
            } finally {
                ownership.release(Thread.currentThread())
            }
        }

        assertTrue(ownership.start(worker))
        assertTrue(started.await(1, TimeUnit.SECONDS))

        assertTrue(ownership.stopAndJoin(1_000))
        assertTrue(interrupted.await(1, TimeUnit.SECONDS))
        assertFalse(worker.isAlive)
        assertTrue(ownership.available)
    }

    @Test
    fun `timed out live worker remains owned until it exits`() {
        val ownership = AudioWorkerOwnership()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Thread {
            try {
                started.countDown()
                while (true) {
                    try {
                        release.await()
                        break
                    } catch (_: InterruptedException) {
                        // Stay alive until the test releases this worker.
                    }
                }
            } finally {
                ownership.release(Thread.currentThread())
            }
        }

        assertTrue(ownership.start(worker))
        assertTrue(started.await(1, TimeUnit.SECONDS))

        var secondClaimed = false
        try {
            assertFalse(ownership.stopAndJoin(10))
            assertFalse(ownership.available)
            assertFalse(ownership.start(Thread {}) { secondClaimed = true })
            assertFalse(secondClaimed)
        } finally {
            release.countDown()
            worker.join(1_000)
        }
        assertFalse(worker.isAlive)
        assertTrue(ownership.available)
    }

    @Test
    fun `auto capture prefers the cleanest supported source while raw stays explicit`() {
        assertEquals(
            MediaRecorder.AudioSource.UNPROCESSED,
            resolveAudioSource(AudioInputSource.AUTO, rawSupported = true),
        )
        assertEquals(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            resolveAudioSource(AudioInputSource.AUTO, rawSupported = false),
        )
        assertEquals(
            MediaRecorder.AudioSource.UNPROCESSED,
            resolveAudioSource(AudioInputSource.RAW, rawSupported = true),
        )
        assertEquals(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            resolveAudioSource(AudioInputSource.COMPATIBLE, rawSupported = true),
        )
        assertEquals(AudioInputSource.AUTO, audioInputSource(MediaRecorder.AudioSource.MIC))
        assertFailsWith<IllegalArgumentException> {
            resolveAudioSource(AudioInputSource.RAW, rawSupported = false)
        }
    }

    @Test
    fun `auto capture falls back from raw to clean compatible then microphone`() {
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            ),
            audioSourceAttempts(AudioInputSource.AUTO, rawSupported = true).toList(),
        )
        assertEquals(
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC),
            audioSourceAttempts(AudioInputSource.AUTO, rawSupported = false).toList(),
        )
        assertEquals(
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION),
            audioSourceAttempts(AudioInputSource.RAW, rawSupported = false).toList(),
        )
        assertEquals(
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION),
            audioSourceAttempts(AudioInputSource.COMPATIBLE, rawSupported = true).toList(),
        )
    }

    @Test
    fun `only automatic microphone capture controls instrument preprocessing`() {
        assertTrue(usesInstrumentCaptureEffects(MediaRecorder.AudioSource.MIC))
        assertFalse(usesInstrumentCaptureEffects(MediaRecorder.AudioSource.UNPROCESSED))
        assertFalse(usesInstrumentCaptureEffects(MediaRecorder.AudioSource.VOICE_RECOGNITION))
    }

    @Test
    fun `two sample decimation averages without PCM overflow`() {
        val output = ShortArray(3)

        decimate(
            shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, 1, 3, -5, -1),
            output,
        )

        assertContentEquals(shortArrayOf(Short.MAX_VALUE, 2, -3), output)
    }

    @Test
    fun `four sample decimation averages without PCM overflow`() {
        val output = ShortArray(2)

        decimate(
            shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE, -8, -4, 4, 8),
            output,
        )

        assertContentEquals(shortArrayOf(Short.MAX_VALUE, 0), output)
    }

    @Test
    fun `window assembler emits overlapping windows at a bounded hop`() {
        val snapshots = mutableListOf<List<Short>>()
        var reusedBuffer: ShortArray? = null
        val assembler = AudioWindowAssembler(windowSize = 4, hopSize = 2) { window ->
            if (reusedBuffer == null) reusedBuffer = window else assertSame(reusedBuffer, window)
            snapshots += window.toList()
        }

        assembler.append(shortArrayOf(1, 2, 3), 3)
        assertTrue(snapshots.isEmpty())
        assembler.append(shortArrayOf(4), 1)
        assembler.append(shortArrayOf(5, 6, 7, 8, 9), 5)

        assertEquals(
            listOf(
                listOf<Short>(1, 2, 3, 4),
                listOf<Short>(3, 4, 5, 6),
                listOf<Short>(5, 6, 7, 8),
            ),
            snapshots,
        )
    }

    @Test
    fun `window assembler validates sizes and append count`() {
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(1, 1) {} }
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(4, 0) {} }
        assertFailsWith<IllegalArgumentException> { AudioWindowAssembler(4, 5) {} }

        val assembler = AudioWindowAssembler(4, 2) {}
        assertFailsWith<IllegalArgumentException> { assembler.append(shortArrayOf(1), 2) }
    }

    @Test
    fun `reference tone buffer has one second duration and faded edges`() {
        val samples = createToneBuffer(hertz = 1_000.0)

        assertEquals(48_000, samples.size)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.maxOf { abs(it.toInt()) } in 20_000..Short.MAX_VALUE.toInt())
        assertTrue(abs(samples[12].toInt()) < abs(samples[468].toInt()))
        assertTrue(abs(samples[samples.lastIndex - 12].toInt()) < abs(samples[samples.lastIndex - 468].toInt()))
    }

    @Test
    fun `low reference tone includes harmonics audible on a phone speaker`() {
        val samples = createToneBuffer(hertz = 82.0)
        val fundamental = amplitudeAt(samples, 82.0)

        assertTrue(amplitudeAt(samples, 164.0) > fundamental * 0.20)
        assertTrue(amplitudeAt(samples, 246.0) > fundamental * 0.10)
    }

    @Test
    fun `standard guitar references have comparable phone speaker energy`() {
        val buffers = listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)
            .map(::createToneBuffer)
        val levels = buffers.map(::phoneSpeakerRms)
        val peaks = buffers.map { samples -> samples.maxOf { abs(it.toInt()) } }

        assertTrue(peaks.max() <= 29_000, "peaks=$peaks")
        assertTrue(
            levels.min() >= levels.max() * 0.75,
            "phoneSpeakerRms=$levels peaks=$peaks",
        )
    }

    @Test
    fun `reference tone omits harmonics above Nyquist`() {
        val samples = createToneBuffer(hertz = 10_000.0)
        val fundamental = amplitudeAt(samples, 10_000.0)

        assertTrue(amplitudeAt(samples, 18_000.0) < fundamental * 0.05)
    }

    @Test
    fun `reference tone reaches a quiet tail before playback ends`() {
        val samples = createToneBuffer(hertz = 82.0)
        val finalFiftyMilliseconds = samples.takeLast(2_400)

        assertTrue(finalFiftyMilliseconds.maxOf { abs(it.toInt()) } < 1_500)
    }

    @Test
    fun `reference tone switching uses a short fade to silence`() {
        val fade = referenceToneSwitchFade()

        assertTrue(fade.durationMillis in 30L..80L)
        assertEquals(listOf(0f, 1f), fade.times)
        assertEquals(listOf(1f, 0f), fade.volumes)
    }

    @Test
    fun `chord tone mixes several notes with bounded faded PCM`() {
        val samples = createChordToneBuffer(doubleArrayOf(261.63, 329.63, 392.0))

        assertEquals(48_000, samples.size)
        assertEquals(0, samples.first().toInt())
        assertEquals(0, samples.last().toInt())
        assertTrue(samples.any { it != 0.toShort() })
        assertTrue(samples.maxOf { abs(it.toInt()) } <= 29_000)
        assertTrue(amplitudeAt(samples, 261.63) > 100.0)
        assertTrue(amplitudeAt(samples, 329.63) > 100.0)
        assertTrue(amplitudeAt(samples, 392.0) > 100.0)
    }

    @Test
    fun `reference tone rejects unsafe frequencies`() {
        listOf(0.0, -1.0, Double.NaN, 24_000.0).forEach { hertz ->
            assertFailsWith<IllegalArgumentException> { createToneBuffer(hertz) }
        }
    }

    private fun amplitudeAt(samples: ShortArray, hertz: Double, sampleRate: Int = 48_000): Double {
        var real = 0.0
        var imaginary = 0.0
        samples.forEachIndexed { index, sample ->
            val phase = 2.0 * PI * hertz * index / sampleRate
            real += sample * cos(phase)
            imaginary -= sample * sin(phase)
        }
        return hypot(real, imaginary) / samples.size
    }

    private fun phoneSpeakerRms(samples: ShortArray, sampleRate: Int = 48_000): Double {
        val rc = 1.0 / (2.0 * PI * 180.0)
        val alpha = rc / (rc + 1.0 / sampleRate)
        var previousInput = 0.0
        var previousOutput = 0.0
        var energy = 0.0
        samples.forEach { sample ->
            val output = alpha * (previousOutput + sample - previousInput)
            previousInput = sample.toDouble()
            previousOutput = output
            energy += output * output
        }
        return sqrt(energy / samples.size)
    }
}
