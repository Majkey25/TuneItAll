package com.tuneitall.tuner.audio

import android.media.MediaRecorder

enum class AudioInputSource {
    AUTO,
    RAW,
    COMPATIBLE,
}

data class AudioInputCapabilities(
    val rawSupported: Boolean,
    val activeSource: AudioInputSource?,
)

internal fun resolveAudioSource(source: AudioInputSource, rawSupported: Boolean): Int = when (source) {
    AudioInputSource.AUTO -> if (rawSupported) {
        MediaRecorder.AudioSource.UNPROCESSED
    } else {
        MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    AudioInputSource.RAW -> {
        require(rawSupported) { "Raw microphone input is unavailable" }
        MediaRecorder.AudioSource.UNPROCESSED
    }

    AudioInputSource.COMPATIBLE -> MediaRecorder.AudioSource.VOICE_RECOGNITION
}

internal fun audioSourceAttempts(source: AudioInputSource, rawSupported: Boolean): IntArray {
    val resolved = if (source == AudioInputSource.RAW && !rawSupported) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION
    } else {
        resolveAudioSource(source, rawSupported)
    }
    return when {
        source == AudioInputSource.AUTO && resolved == MediaRecorder.AudioSource.UNPROCESSED -> intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        source == AudioInputSource.AUTO -> intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
        resolved == MediaRecorder.AudioSource.UNPROCESSED -> intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )
        else -> intArrayOf(resolved)
    }
}

internal fun audioInputSource(audioSource: Int): AudioInputSource = when (audioSource) {
    MediaRecorder.AudioSource.MIC -> AudioInputSource.AUTO
    MediaRecorder.AudioSource.UNPROCESSED -> AudioInputSource.RAW
    MediaRecorder.AudioSource.VOICE_RECOGNITION -> AudioInputSource.COMPATIBLE
    else -> error("Unexpected microphone input source: $audioSource")
}
