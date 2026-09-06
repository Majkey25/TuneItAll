package com.tuneitall.tuner.audio

import android.content.Context
import android.database.Cursor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.music.SongEvent
import com.tuneitall.tuner.music.StreamingHarmonicFeatureExtractor
import com.tuneitall.tuner.music.StreamingTempoAnalyzer
import com.tuneitall.tuner.music.TempoEstimate
import com.tuneitall.tuner.music.analyzeChords
import com.tuneitall.tuner.music.analyzeNotes
import com.tuneitall.tuner.music.checkAnalysisCancellation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

enum class SongDecodeError {
    NO_AUDIO_TRACK,
    TOO_LONG,
    UNSUPPORTED_PCM,
    DECODE_FAILED,
}

class SongDecodeException(val reason: SongDecodeError, cause: Throwable? = null) : Exception(reason.name, cause)

data class SongAnalysisResult(
    val durationMillis: Long,
    val events: List<SongEvent>,
)

class SongAudioDecoder(context: Context) {
    private val applicationContext = context.applicationContext

    fun analyze(
        uri: Uri,
        mode: SongAnalysisMode = SongAnalysisMode.CHORDS,
        noteRange: NoteRange = NoteRange.ANY,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): SongAnalysisResult {
        var extractor: StreamingHarmonicFeatureExtractor? = null
        val durationMillis = decode(uri, isCancelled, onProgress) { sampleRate, mono ->
            val activeExtractor = extractor ?: StreamingHarmonicFeatureExtractor(sampleRate, isCancelled = isCancelled)
                .also { extractor = it }
            activeExtractor.accept(mono)
        }
        val frames = extractor?.finish().orEmpty()
        onProgress(95)
        val events = when (mode) {
            SongAnalysisMode.CHORDS, SongAnalysisMode.POWER -> analyzeChords(frames, mode, durationMillis, isCancelled)
            SongAnalysisMode.NOTES -> analyzeNotes(frames, noteRange, durationMillis, isCancelled)
        }
        checkAnalysisCancellation(isCancelled)
        onProgress(100)
        return SongAnalysisResult(durationMillis, events)
    }

    fun analyzeTempo(
        uri: Uri,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): TempoEstimate? {
        var analyzer: StreamingTempoAnalyzer? = null
        decode(uri, isCancelled, onProgress) { sampleRate, mono ->
            val activeAnalyzer = analyzer ?: StreamingTempoAnalyzer(sampleRate, isCancelled = isCancelled).also { analyzer = it }
            activeAnalyzer.accept(mono)
        }
        val result = analyzer?.finish()
        checkAnalysisCancellation(isCancelled)
        onProgress(100)
        return result
    }

    private fun decode(
        uri: Uri,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
        onSamples: (Int, FloatArray) -> Unit,
    ): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(applicationContext, uri, emptyMap())
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw SongDecodeException(SongDecodeError.NO_AUDIO_TRACK)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw SongDecodeException(SongDecodeError.NO_AUDIO_TRACK)
            val durationMicros = inputFormat.longOrDefault(MediaFormat.KEY_DURATION, -1L)
            if (durationMicros > MAX_DURATION_MICROS) throw SongDecodeException(SongDecodeError.TOO_LONG)

            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            val decodedClock = DecodedAudioClock()
            val downmixer = PcmDownmixer()
            var lastProgress = -1

            while (!outputEnded) {
                if (isCancelled()) throw CancellationException("Song analysis cancelled")
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_MICROS)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val pcm = outputBuffer.slice().order(ByteOrder.nativeOrder())
                            val sampleRate = outputFormat.intOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE,
                                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            )
                            val channels = outputFormat.intOrDefault(
                                MediaFormat.KEY_CHANNEL_COUNT,
                                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                            )
                            val encoding = outputFormat.intOrDefault(
                                MediaFormat.KEY_PCM_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT,
                            )
                            val mono = when (encoding) {
                                AudioFormat.ENCODING_PCM_16BIT -> downmixer.pcm16ToMono(pcm, channels)
                                AudioFormat.ENCODING_PCM_FLOAT -> downmixer.pcmFloatToMono(pcm, channels)
                                else -> throw SongDecodeException(SongDecodeError.UNSUPPORTED_PCM)
                            }
                            decodedClock.accept(sampleRate, mono.size)
                            onSamples(sampleRate, mono)
                            if (durationMicros > 0L) {
                                val progress = (bufferInfo.presentationTimeUs * 90L / durationMicros).toInt().coerceIn(0, 89)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            checkAnalysisCancellation(isCancelled)
            onProgress(90)
            return decodedClock.durationMillis
        } catch (error: CancellationException) {
            throw error
        } catch (error: SongDecodeException) {
            throw error
        } catch (error: Exception) {
            throw SongDecodeException(SongDecodeError.DECODE_FAILED, error)
        } finally {
            codec?.let { activeCodec ->
                runCatching { activeCodec.stop() }
                runCatching { activeCodec.release() }
            }
            runCatching { extractor.release() }
        }
    }
}

internal class DecodedAudioClock {
    private var sampleRate = 0
    private var sampleCount = 0L

    val durationMillis: Long
        get() = if (sampleRate == 0) 0L else sampleCount * MILLIS_PER_SECOND / sampleRate

    fun accept(rate: Int, count: Int) {
        if (rate !in 8_000..192_000 || count < 0 || (sampleRate != 0 && rate != sampleRate)) {
            throw SongDecodeException(SongDecodeError.UNSUPPORTED_PCM)
        }
        if (sampleCount + count > rate * MAX_DURATION_SECONDS) {
            throw SongDecodeException(SongDecodeError.TOO_LONG)
        }
        sampleRate = rate
        sampleCount += count
    }
}

internal fun audioDisplayName(context: Context, uri: Uri): String {
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getString(0).orEmpty() else uri.lastPathSegment.orEmpty()
    } catch (_: RuntimeException) {
        uri.lastPathSegment.orEmpty()
    } finally {
        cursor?.close()
    }.ifBlank { "Audio" }
}

internal class PcmDownmixer {
    private var selectedChannel = 0
    private var useChannel = false
    private var channelWeight = 0f

    fun pcm16ToMono(buffer: ByteBuffer, channelCount: Int): FloatArray =
        toMono(buffer, channelCount, Short.SIZE_BYTES)

    fun pcmFloatToMono(buffer: ByteBuffer, channelCount: Int): FloatArray =
        toMono(buffer, channelCount, Float.SIZE_BYTES)

    private fun toMono(buffer: ByteBuffer, channelCount: Int, sampleBytes: Int): FloatArray {
        require(channelCount > 0)
        require(buffer.remaining() % sampleBytes == 0 && buffer.remaining() / sampleBytes % channelCount == 0) {
            "PCM buffer must contain complete frames"
        }
        val samples = buffer.slice().order(ByteOrder.nativeOrder())
        val frames = samples.remaining() / sampleBytes / channelCount
        if (frames == 0) return FloatArray(0)
        val mono = FloatArray(frames)
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        var monoEnergy = 0.0
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channelCount) {
                val sample = sampleAt(samples, (frame * channelCount + channel) * sampleBytes, sampleBytes)
                sum += sample
                if (channelCount == 2) {
                    val energy = sample.toDouble() * sample
                    if (channel == 0) leftEnergy += energy else rightEnergy += energy
                }
            }
            mono[frame] = sum / channelCount
            monoEnergy += mono[frame].toDouble() * mono[frame]
        }
        if (channelCount != 2) {
            useChannel = false
            channelWeight = 0f
            return mono
        }
        val strongestEnergy = maxOf(leftEnergy, rightEnergy)
        if (!useChannel && monoEnergy < strongestEnergy * PHASE_CANCELLATION_ENTER) {
            if (channelWeight == 0f) selectedChannel = if (leftEnergy >= rightEnergy) 0 else 1
            useChannel = true
        } else if (useChannel && monoEnergy >= strongestEnergy * PHASE_CANCELLATION_EXIT) {
            useChannel = false
        }
        if (!useChannel && channelWeight == 0f) return mono
        // ponytail: one channel during cancellation; combine channel spectra if independent parts must survive.
        for (frame in mono.indices) {
            channelWeight = (channelWeight + if (useChannel) CHANNEL_FADE_STEP else -CHANNEL_FADE_STEP).coerceIn(0f, 1f)
            val selected = sampleAt(samples, (frame * 2 + selectedChannel) * sampleBytes, sampleBytes)
            mono[frame] += channelWeight * (selected - mono[frame])
        }
        return mono
    }

    private fun sampleAt(buffer: ByteBuffer, offset: Int, sampleBytes: Int): Float {
        if (sampleBytes == Short.SIZE_BYTES) return buffer.getShort(offset) / 32_768f
        val sample = buffer.getFloat(offset)
        require(sample.isFinite()) { "PCM float samples must be finite" }
        return sample.coerceIn(-1f, 1f)
    }
}

private fun MediaFormat.intOrDefault(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default

private fun MediaFormat.longOrDefault(key: String, default: Long): Long = if (containsKey(key)) getLong(key) else default

private const val CODEC_TIMEOUT_MICROS = 10_000L
private const val MAX_DURATION_SECONDS = 30L * 60L
private const val MAX_DURATION_MICROS = MAX_DURATION_SECONDS * 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val PHASE_CANCELLATION_ENTER = 0.10
private const val PHASE_CANCELLATION_EXIT = 0.25
private const val CHANNEL_FADE_STEP = 1f / 128f
