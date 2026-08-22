package com.tuneitall.tuner.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuneitall.tuner.audio.AudioInput
import com.tuneitall.tuner.audio.AudioInputCapabilities
import com.tuneitall.tuner.audio.AudioInputError
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.AdaptiveNoiseFloor
import com.tuneitall.tuner.audio.CONFIRMATION_CHIME_DURATION_MILLIS
import com.tuneitall.tuner.audio.ConfirmationChimePlayer
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.FeedbackInputGate
import com.tuneitall.tuner.audio.PitchFrame
import com.tuneitall.tuner.audio.PitchTracker
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.audio.YinPitchDetector
import com.tuneitall.tuner.audio.isDetectorVoiced
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.tuner.InTuneConfirmationTracker
import com.tuneitall.tuner.tuner.MusicMath
import com.tuneitall.tuner.tuner.TunerEngine
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.TunerReading
import com.tuneitall.tuner.tuner.TunerReadingRetainer
import com.tuneitall.tuner.tuner.pitchSearchRange
import com.tuneitall.tuner.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerUiState(
    val mode: TunerMode,
    val tuning: TuningPreset,
    val selectedString: Int,
    val headstockLayout: HeadstockLayout,
    val referencePitch: ReferencePitch,
    val notation: NoteNotation,
    val favoriteIds: Set<String>,
    val customTunings: List<TuningPreset>,
    val reading: TunerReading?,
    val microphoneGranted: Boolean,
    val microphonePermanentlyDenied: Boolean,
    val listening: Boolean,
    val referenceTonePlaying: Boolean,
    val error: AudioInputError?,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val audioSettings: TunerAudioSettings = TunerProfile.BALANCED.settings,
    val audioInputCapabilities: AudioInputCapabilities = AudioInputCapabilities(
        rawSupported = false,
        activeSource = null,
    ),
    val tuningConfirmed: Boolean = false,
) {
    val sensitivity: DetectionSensitivity
        get() = audioSettings.sensitivity
}

internal fun sameDetectionContext(captured: TunerUiState, current: TunerUiState): Boolean =
    current.mode == captured.mode &&
        current.tuning == captured.tuning &&
        current.selectedString == captured.selectedString &&
        current.referencePitch == captured.referencePitch &&
        current.audioSettings == captured.audioSettings

internal data class DetectionCallbackToken(
    val state: TunerUiState,
    val audioSessionGeneration: Long,
    val detectionContextRevision: Long,
)

internal fun isCurrentDetectionCallback(
    captured: DetectionCallbackToken,
    current: TunerUiState,
    audioSessionGeneration: Long,
    detectionContextRevision: Long,
): Boolean =
    captured.audioSessionGeneration == audioSessionGeneration &&
        captured.detectionContextRevision == detectionContextRevision &&
        sameDetectionContext(captured.state, current)

internal fun mutateIfCurrentDetectionCallback(
    captured: DetectionCallbackToken,
    current: TunerUiState,
    audioSessionGeneration: Long,
    detectionContextRevision: Long,
    mutation: () -> Unit,
): Boolean {
    if (!isCurrentDetectionCallback(captured, current, audioSessionGeneration, detectionContextRevision)) return false
    mutation()
    return true
}

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val audioInput = AudioInput(application)
    private val detector = YinPitchDetector()
    private val noiseFloor = AdaptiveNoiseFloor()
    private val pitchTracker = PitchTracker()
    private val engine = TunerEngine()
    private val tonePlayer = ReferenceTonePlayer()
    private val confirmationPlayer = ConfirmationChimePlayer()
    private val confirmationTracker = InTuneConfirmationTracker()
    private val readingRetainer = TunerReadingRetainer()
    private val feedbackInputGate = FeedbackInputGate()
    private val pitchPipelineLock = Any()
    private var audioSessionGeneration = 0L
    private var detectionContextRevision = 0L
    private var referenceToneStopJob: Job? = null
    @Volatile
    private var foreground = false
    @Volatile
    private var tunerActive = true

    private val initialCustomTunings = preferences.customTunings
    private val initialTuning = (TuningCatalog.presets + initialCustomTunings)
        .firstOrNull { it.id == preferences.lastTuningId }
        ?: requireNotNull(TuningCatalog.byId(DEFAULT_TUNING_ID))
    private val initialLayout = preferences.headstockLayout.takeIf { it in initialTuning.layouts }
        ?: initialTuning.layouts.first()
    private val mutableUiState = MutableStateFlow(
        TunerUiState(
            mode = preferences.mode,
            tuning = initialTuning,
            selectedString = 0,
            headstockLayout = initialLayout,
            referencePitch = preferences.referencePitch,
            notation = preferences.notation,
            themeMode = preferences.themeMode,
            favoriteIds = preferences.favoriteIds,
            customTunings = initialCustomTunings,
            reading = null,
            microphoneGranted = false,
            microphonePermanentlyDenied = false,
            listening = false,
            referenceTonePlaying = false,
            error = null,
            audioSettings = preferences.tunerAudioSettings,
        ),
    )
    val uiState: StateFlow<TunerUiState> = mutableUiState.asStateFlow()

    fun onStart() {
        foreground = true
        val granted = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        mutableUiState.update {
            it.copy(
                microphoneGranted = granted,
                microphonePermanentlyDenied = if (granted) false else it.microphonePermanentlyDenied,
            )
        }
        startAudioIfReady()
    }

    fun onStop() {
        foreground = false
        stopTunerAudio()
    }

    fun setTunerActive(active: Boolean) {
        if (tunerActive == active) return
        tunerActive = active
        if (active) startAudioIfReady() else stopTunerAudio()
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (granted) {
            mutableUiState.update {
                it.copy(microphoneGranted = true, microphonePermanentlyDenied = false, error = null)
            }
            startAudioIfReady()
        } else {
            invalidateAudioSession {
                it.copy(
                    microphoneGranted = false,
                    microphonePermanentlyDenied = permanentlyDenied,
                    listening = false,
                    reading = null,
                    tuningConfirmed = false,
                    error = null,
                )
            }
            audioInput.stop()
        }
    }

    fun selectMode(mode: TunerMode) {
        stopReferenceTone()
        preferences.mode = mode
        updateDetectionContext { it.copy(mode = mode, reading = null, tuningConfirmed = false) }
    }

    fun selectStringAndPlayReference(index: Int) {
        require(index in mutableUiState.value.tuning.notesLowToHigh.indices) {
            "Selected string is outside the active tuning"
        }
        preferences.mode = TunerMode.MANUAL
        updateDetectionContext {
            it.copy(mode = TunerMode.MANUAL, selectedString = index, reading = null, tuningConfirmed = false)
        }
        playReferenceTone()
    }

    fun selectTuning(tuning: TuningPreset) {
        stopReferenceTone()
        val layout = mutableUiState.value.headstockLayout.takeIf { it in tuning.layouts } ?: tuning.layouts.first()
        preferences.lastTuningId = tuning.id
        preferences.headstockLayout = layout
        updateDetectionContext {
            it.copy(
                tuning = tuning,
                selectedString = 0,
                headstockLayout = layout,
                reading = null,
                tuningConfirmed = false,
            )
        }
    }

    fun setHeadstockLayout(layout: HeadstockLayout) {
        require(layout in mutableUiState.value.tuning.layouts) { "Layout does not match the active tuning" }
        preferences.headstockLayout = layout
        mutableUiState.update { it.copy(headstockLayout = layout) }
    }

    fun toggleFavorite(tuningId: String) {
        val favorites = mutableUiState.value.favoriteIds.toMutableSet()
        if (!favorites.add(tuningId)) favorites.remove(tuningId)
        preferences.favoriteIds = favorites
        mutableUiState.update { it.copy(favoriteIds = favorites.toSet()) }
    }

    fun setReferencePitch(referencePitch: ReferencePitch) {
        stopReferenceTone()
        preferences.referencePitch = referencePitch
        updateDetectionContext {
            it.copy(referencePitch = referencePitch, reading = null, tuningConfirmed = false)
        }
    }

    fun setSensitivity(sensitivity: DetectionSensitivity) {
        setTunerAudioSettings(mutableUiState.value.audioSettings.copy(sensitivity = sensitivity))
    }

    fun setTunerAudioSettings(settings: TunerAudioSettings) {
        val restartAudio = synchronized(pitchPipelineLock) {
            val current = mutableUiState.value
            if (settings == current.audioSettings) return
            preferences.tunerAudioSettings = settings
            val sourceChanged = settings.inputSource != current.audioSettings.inputSource
            if (sourceChanged) audioSessionGeneration++
            updateDetectionContextLocked {
                it.copy(
                    audioSettings = settings,
                    listening = if (sourceChanged) false else it.listening,
                    audioInputCapabilities = if (sourceChanged) {
                        it.audioInputCapabilities.copy(activeSource = null)
                    } else {
                        it.audioInputCapabilities
                    },
                    reading = null,
                    tuningConfirmed = false,
                )
            }
            sourceChanged
        }
        if (!restartAudio) return
        audioInput.stop()
        startAudioIfReady()
    }

    fun setNotation(notation: NoteNotation) {
        preferences.notation = notation
        mutableUiState.update { it.copy(notation = notation) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        preferences.themeMode = themeMode
        mutableUiState.update { it.copy(themeMode = themeMode) }
    }

    fun saveCustomTuning(tuning: TuningPreset) {
        require(TuningCatalog.byId(tuning.id) == null) { "Custom tuning ID conflicts with a built-in tuning" }
        val current = mutableUiState.value.customTunings
        require(current.none { it.id == tuning.id }) { "Custom tuning ID already exists" }
        val updated = current + tuning
        preferences.customTunings = updated
        mutableUiState.update { it.copy(customTunings = updated) }
        selectTuning(tuning)
    }

    private fun playReferenceTone() {
        referenceToneStopJob?.cancel()
        referenceToneStopJob = null
        val state = mutableUiState.value
        val target = state.tuning.notesLowToHigh[state.selectedString]
        try {
            tonePlayer.play(MusicMath.frequency(target, state.referencePitch))
            updateDetectionContext {
                it.copy(referenceTonePlaying = true, reading = null, tuningConfirmed = false, error = null)
            }
            referenceToneStopJob = viewModelScope.launch {
                delay(REFERENCE_PREVIEW_MILLIS)
                referenceToneStopJob = null
                stopReferenceTone()
            }
        } catch (_: RuntimeException) {
            tonePlayer.stop()
            updateDetectionContext {
                it.copy(
                    referenceTonePlaying = false,
                    error = AudioInputError.InitializationFailed("Reference tone output is unavailable"),
                )
            }
        }
    }

    private fun stopReferenceTone() {
        referenceToneStopJob?.cancel()
        referenceToneStopJob = null
        tonePlayer.stop()
        updateDetectionContext { it.copy(referenceTonePlaying = false) }
    }

    override fun onCleared() {
        audioInput.close()
        tonePlayer.close()
        confirmationPlayer.close()
        super.onCleared()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudio() {
        val sessionGeneration = prepareAudioStart()
        audioInput.start(
            windowSize = ANALYSIS_WINDOW_SIZE,
            source = mutableUiState.value.audioSettings.inputSource,
            onWindow = { samples, sampleRate ->
                val captured = synchronized(pitchPipelineLock) {
                    val state = mutableUiState.value
                    if (!isDetectionActive(state) || sessionGeneration != audioSessionGeneration) {
                        return@synchronized null
                    }
                    DetectionCallbackToken(state, sessionGeneration, detectionContextRevision)
                } ?: return@start
                val range = pitchSearchRange(
                    captured.state.mode,
                    captured.state.tuning,
                    captured.state.selectedString,
                    captured.state.referencePitch,
                )
                val pipelineResult = synchronized(pitchPipelineLock) {
                    if (!isCurrentDetectionContextLocked(captured)) return@synchronized null
                    val settings = captured.state.audioSettings
                    val frame = detector.analyze(
                        samples,
                        sampleRate,
                        range.minHertz,
                        range.maxHertz,
                    )
                    noiseFloor.observe(frame.rms, frame.isDetectorVoiced)
                    val tracked = if (
                        noiseFloor.accepts(
                            frame.rms,
                            settings.sensitivity.minimumRms,
                            settings.noiseRejection,
                        )
                    ) {
                        pitchTracker.update(frame, settings)
                    } else {
                        pitchTracker.update(
                            PitchFrame(
                                candidates = emptyList(),
                                rms = frame.rms,
                                peak = frame.peak,
                                unvoicedProbability = 1.0,
                            ),
                            settings,
                        )
                        null
                    }
                    Pair(
                        tracked?.let {
                            engine.update(
                                estimate = it,
                                mode = captured.state.mode,
                                tuning = captured.state.tuning,
                                selectedString = captured.state.selectedString,
                                referencePitch = captured.state.referencePitch,
                                settings = settings,
                            )
                        },
                        settings,
                    )
                }
                val (rawReading, settings) = pipelineResult ?: return@start
                val nowMillis = SystemClock.elapsedRealtime()
                viewModelScope.launch {
                    val playbackFailure = synchronized(pitchPipelineLock) {
                        val current = mutableUiState.value
                        if (!isDetectionActive(current)) return@synchronized null
                        var failure: RuntimeException? = null
                        val applied = mutateIfCurrentDetectionCallback(
                            captured,
                            current,
                            audioSessionGeneration,
                            detectionContextRevision,
                        ) {
                            val displayReading = readingRetainer.update(
                                rawReading,
                                nowMillis,
                                settings.readingHoldMillis,
                            )
                            val playConfirmation = confirmationTracker.update(
                                rawReading?.target,
                                rawReading?.inTune == true,
                                nowMillis,
                                settings.confirmationMillis,
                            )
                            mutableUiState.update {
                                it.copy(
                                    reading = displayReading,
                                    tuningConfirmed = confirmationTracker.isConfirmed,
                                )
                            }
                            if (playConfirmation && confirmationTracker.isConfirmed) {
                                try {
                                    confirmationPlayer.play()
                                    feedbackInputGate.suppress(
                                        nowMillis = SystemClock.elapsedRealtime(),
                                        durationMillis = CONFIRMATION_INPUT_SUPPRESSION_MILLIS,
                                    )
                                } catch (error: RuntimeException) {
                                    failure = error
                                }
                            }
                        }
                        if (!applied) return@synchronized null
                        failure
                    }
                    if (playbackFailure != null) {
                        synchronized(pitchPipelineLock) {
                            if (!isCurrentDetectionContextLocked(captured)) return@synchronized
                            mutableUiState.update {
                                it.copy(error = AudioInputError.InitializationFailed("Confirmation sound is unavailable"))
                            }
                        }
                    }
                }
            },
            onStarted = { capabilities ->
                synchronized(pitchPipelineLock) {
                    if (sessionGeneration == audioSessionGeneration) {
                        mutableUiState.update { it.copy(audioInputCapabilities = capabilities) }
                    }
                }
            },
            onError = { error ->
                invalidateAudioSession(sessionGeneration) {
                    it.copy(listening = false, reading = null, tuningConfirmed = false, error = error)
                }
            },
        )
    }

    private fun startAudioIfReady() {
        val state = mutableUiState.value
        if (!foreground || !tunerActive || !state.microphoneGranted || state.listening) return
        if (
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableUiState.update { it.copy(microphoneGranted = false, listening = false) }
            return
        }
        startAudio()
    }

    private fun isCurrentDetectionContextLocked(captured: DetectionCallbackToken): Boolean {
        val current = mutableUiState.value
        return isDetectionActive(current) &&
            isCurrentDetectionCallback(
                captured,
                current,
                audioSessionGeneration,
                detectionContextRevision,
            )
    }

    private fun isDetectionActive(current: TunerUiState): Boolean {
        return foreground &&
            tunerActive &&
            current.listening &&
            !current.referenceTonePlaying &&
            feedbackInputGate.accepts(SystemClock.elapsedRealtime())
    }

    private fun updateDetectionContext(transform: (TunerUiState) -> TunerUiState) = synchronized(pitchPipelineLock) {
        updateDetectionContextLocked(transform)
    }

    private fun prepareAudioStart(): Long = synchronized(pitchPipelineLock) {
        audioSessionGeneration++
        updateDetectionContextLocked {
            it.copy(
                listening = true,
                error = null,
                audioInputCapabilities = it.audioInputCapabilities.copy(activeSource = null),
            )
        }
        audioSessionGeneration
    }

    private fun invalidateAudioSession(
        expectedSessionGeneration: Long? = null,
        transform: (TunerUiState) -> TunerUiState,
    ): Boolean = synchronized(pitchPipelineLock) {
        if (expectedSessionGeneration != null && expectedSessionGeneration != audioSessionGeneration) {
            return@synchronized false
        }
        audioSessionGeneration++
        updateDetectionContextLocked { current ->
            transform(current).let {
                it.copy(audioInputCapabilities = it.audioInputCapabilities.copy(activeSource = null))
            }
        }
        true
    }

    private fun updateDetectionContextLocked(transform: (TunerUiState) -> TunerUiState) {
        mutableUiState.update(transform)
        detectionContextRevision++
        resetDetectionStateLocked()
    }

    private fun resetDetectionStateLocked() {
        noiseFloor.reset()
        pitchTracker.reset()
        engine.reset()
        readingRetainer.reset()
        confirmationTracker.reset()
        feedbackInputGate.reset()
    }

    private fun stopTunerAudio() {
        invalidateAudioSession {
            it.copy(listening = false, referenceTonePlaying = false, reading = null, tuningConfirmed = false)
        }
        audioInput.stop()
        stopReferenceTone()
        confirmationPlayer.stop()
    }

    private companion object {
        const val DEFAULT_TUNING_ID = "guitar-6-standard"
        const val ANALYSIS_WINDOW_SIZE = 4_096
        const val REFERENCE_PREVIEW_MILLIS = 1_050L
        const val CONFIRMATION_INPUT_SUPPRESSION_MILLIS = CONFIRMATION_CHIME_DURATION_MILLIS + 180L
    }
}
