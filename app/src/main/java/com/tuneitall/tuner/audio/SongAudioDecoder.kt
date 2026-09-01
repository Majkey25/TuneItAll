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
            val activeExtractor = extractor ?: StreamingHarmonicFeatureExtractor(sampleRate).also { extractor = it }
            activeExtractor.accept(mono)
        }
        val frames = extractor?.finish().orEmpty()
        val events = when (mode) {
            SongAnalysisMode.CHORDS, SongAnalysisMode.POWER -> analyzeChords(frames, mode, durationMillis)
            SongAnalysisMode.NOTES -> analyzeNotes(frames, noteRange, durationMillis)
        }
        return SongAnalysisResult(durationMillis, events)
    }

    fun analyzeTempo(
        uri: Uri,
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): TempoEstimate? {
        var analyzer: StreamingTempoAnalyzer? = null
        decode(uri, isCancelled, onProgress) { sampleRate, mono ->
            val activeAnalyzer = analyzer ?: StreamingTempoAnalyzer(sampleRate).also { analyzer = it }
            activeAnalyzer.accept(mono)
        }
        return analyzer?.finish()
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
            var decodedFrames = 0L
            var decodedSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
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
                                AudioFormat.ENCODING_PCM_16BIT -> pcm16ToMono(pcm, channels)
                                AudioFormat.ENCODING_PCM_FLOAT -> pcmFloatToMono(pcm, channels)
                                else -> throw SongDecodeException(SongDecodeError.UNSUPPORTED_PCM)
                            }
                            onSamples(sampleRate, mono)
                            decodedFrames += mono.size
                            decodedSampleRate = sampleRate
                            if (durationMicros > 0L) {
                                val progress = (bufferInfo.presentationTimeUs * 100L / durationMicros).toInt().coerceIn(0, 99)
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
            onProgress(100)
            val durationMillis = if (durationMicros > 0L) {
                durationMicros / MICROS_PER_MILLISECOND
            } else {
                decodedFrames * MILLIS_PER_SECOND / decodedSampleRate
            }
            return durationMillis
        } catch (error: CancellationException) {
            throw error
        } catch (error: SongDecodeException) {
            throw error
        } catch (error: Exception) {
            throw SongDecodeException(SongDecodeError.DECODE_FAILED, error)
        } finally {
            codec?.let { activeCodec ->
                runCatching { activeCodec.stop() }
                activeCodec.release()
            }
            extractor.release()
        }
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

internal fun pcm16ToMono(buffer: ByteBuffer, channelCount: Int): FloatArray {
    require(channelCount > 0)
    require(buffer.remaining() % (Short.SIZE_BYTES * channelCount) == 0) { "PCM16 buffer must contain complete frames" }
    val samples = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
    val frames = samples.remaining() / channelCount
    return FloatArray(frames) {
        var sum = 0f
        repeat(channelCount) { sum += samples.get() / 32_768f }
        sum / channelCount
    }
}

private fun pcmFloatToMono(buffer: ByteBuffer, channelCount: Int): FloatArray {
    require(channelCount > 0)
    require(buffer.remaining() % (Float.SIZE_BYTES * channelCount) == 0) { "PCM float buffer must contain complete frames" }
    val samples = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
    val frames = samples.remaining() / channelCount
    return FloatArray(frames) {
        var sum = 0f
        repeat(channelCount) { sum += samples.get().coerceIn(-1f, 1f) }
        sum / channelCount
    }
}

private fun MediaFormat.intOrDefault(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default

private fun MediaFormat.longOrDefault(key: String, default: Long): Long = if (containsKey(key)) getLong(key) else default

private const val CODEC_TIMEOUT_MICROS = 10_000L
private const val MAX_DURATION_MICROS = 30L * 60L * 1_000_000L
private const val MICROS_PER_MILLISECOND = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
