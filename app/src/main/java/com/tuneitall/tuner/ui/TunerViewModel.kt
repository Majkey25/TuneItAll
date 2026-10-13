package com.tuneitall.tuner.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuneitall.tuner.audio.AudioInput
import com.tuneitall.tuner.audio.AudioInputCapabilities
import com.tuneitall.tuner.audio.AudioInputError
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.AdaptiveNoiseFloor
import com.tuneitall.tuner.audio.ConfirmationChimePlayer
import com.tuneitall.tuner.audio.CONFIRMATION_CHIME_HERTZ
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
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val appearance: AppearanceSettings = AppearanceSettings(),
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

private data class CaptureRequest(val generation: Long, val source: AudioInputSource?)
private data class ReferenceRequest(val generation: Long, val hertz: Double?)
private data class DetectionResult(
    val captured: DetectionCallbackToken,
    val reading: TunerReading?,
    val confirmed: Boolean,
    val revision: Long,
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
    @Volatile
    private var audioSessionGeneration = 0L
    private var detectionContextRevision = 0L
    private var detectionResultRevision = 0L
    private var pendingConfirmation = false
    @Volatile
    private var referenceGeneration = 0L
    @Volatile
    private var cleared = false
    private val audioControlScope = CoroutineScope(SupervisorJob())
    private val captureRequests = MutableStateFlow(CaptureRequest(0L, null))
    private val referenceRequests = MutableStateFlow(ReferenceRequest(0L, null))
    private val detectionResults = Channel<DetectionResult>(Channel.CONFLATED)
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
            appearance = preferences.appearance,
            favoriteIds = preferences.favoriteIds.intersect(
                (TuningCatalog.presets + initialCustomTunings).mapTo(mutableSetOf(), TuningPreset::id),
            ),
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

    init {
        audioControlScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) {
                    captureRequests.collect { request ->
                        try {
                            audioInput.stop()
                            if (
                                isActive && !cleared && foreground && tunerActive &&
                                request.generation == audioSessionGeneration && request.source != null
                            ) {
                                startAudio(request)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: RuntimeException) {
                            invalidateAudioSession(request.generation, scheduleStop = false) {
                                it.copy(
                                    listening = false,
                                    reading = null,
                                    tuningConfirmed = false,
                                    error = AudioInputError.InitializationFailed(error.message ?: error.javaClass.simpleName),
                                )
                            }
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { audioInput.close() }
            }
        }
        audioControlScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) {
                    referenceRequests.collectLatest { request ->
                        if (!isCurrentReference(request.generation)) return@collectLatest
                        try {
                            if (request.hertz == null) {
                                stopReferenceOutput(request.generation)
                            } else {
                                val context = currentCoroutineContext()
                                tonePlayer.play(request.hertz) { context.isActive && isCurrentReference(request.generation) }
                                delay(REFERENCE_PREVIEW_MILLIS)
                                stopReferenceTone(request.generation)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: RuntimeException) {
                            synchronized(pitchPipelineLock) {
                                if (!isCurrentReference(request.generation)) return@synchronized
                                mutableUiState.update {
                                    it.copy(error = AudioInputError.InitializationFailed("Reference tone output is unavailable"))
                                }
                            }
                            stopReferenceOutput(request.generation)
                        }
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { tonePlayer.close() }
            }
        }
        viewModelScope.launch {
            for (result in detectionResults) applyDetectionResult(result)
        }
    }

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
        }
    }

    fun retryAudio() {
        if (!mutableUiState.value.microphoneGranted) return
        invalidateAudioSession {
            it.copy(listening = false, reading = null, tuningConfirmed = false, error = null)
        }
        startAudioIfReady()
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
            if (sourceChanged) {
                audioSessionGeneration++
                captureRequests.value = CaptureRequest(audioSessionGeneration, null)
            }
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

    fun setAppearance(appearance: AppearanceSettings) {
        preferences.appearance = appearance
        mutableUiState.update { it.copy(appearance = appearance) }
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

    fun deleteCustomTuning(tuningId: String) {
        val state = mutableUiState.value
        val updated = state.customTunings.filterNot { it.id == tuningId }
        if (updated.size == state.customTunings.size) return
        val favorites = state.favoriteIds - tuningId
        preferences.customTunings = updated
        preferences.favoriteIds = favorites
        mutableUiState.update { it.copy(customTunings = updated, favoriteIds = favorites) }
        if (state.tuning.id == tuningId) selectTuning(requireNotNull(TuningCatalog.byId(DEFAULT_TUNING_ID)))
    }

    private fun playReferenceTone() {
        if (cleared || !foreground || !tunerActive) return
        tonePlayer.invalidatePending()
        synchronized(pitchPipelineLock) {
            if (cleared || !foreground || !tunerActive) return
            val state = mutableUiState.value
            val target = state.tuning.notesLowToHigh[state.selectedString]
            referenceGeneration++
            updateDetectionContextLocked {
                it.copy(referenceTonePlaying = true, reading = null, tuningConfirmed = false, error = if (it.listening) null else it.error)
            }
            referenceRequests.value = ReferenceRequest(referenceGeneration, MusicMath.frequency(target, state.referencePitch))
        }
    }

    private fun stopReferenceTone(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && expectedGeneration != referenceGeneration) return
        tonePlayer.invalidatePending()
        synchronized(pitchPipelineLock) {
            if (expectedGeneration != null && expectedGeneration != referenceGeneration) return@synchronized
            referenceGeneration++
            referenceRequests.value = ReferenceRequest(referenceGeneration, null)
        }
    }

    private fun isCurrentReference(generation: Long): Boolean = !cleared && generation == referenceGeneration

    private fun stopReferenceOutput(generation: Long) {
        tonePlayer.stop {
            synchronized(pitchPipelineLock) {
                if (!isCurrentReference(generation) || !mutableUiState.value.referenceTonePlaying) return@synchronized
                updateDetectionContextLocked { it.copy(referenceTonePlaying = false) }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        tonePlayer.invalidatePending()
        audioControlScope.cancel()
        detectionResults.close()
        confirmationPlayer.close()
        super.onCleared()
    }

    private fun startAudio(request: CaptureRequest) {
        val sessionGeneration = request.generation
        if (cleared || !foreground || !tunerActive || sessionGeneration != audioSessionGeneration) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            invalidateAudioSession(sessionGeneration) {
                it.copy(microphoneGranted = false, listening = false, reading = null, tuningConfirmed = false)
            }
            return
        }
        audioInput.start(
            windowSize = ANALYSIS_WINDOW_SIZE,
            source = requireNotNull(request.source),
            onWindow = { samples, sampleRate, windowGeneration ->
                val captured = synchronized(pitchPipelineLock) {
                    val state = mutableUiState.value
                    if (
                        !isDetectionActive(state) || sessionGeneration != audioSessionGeneration ||
                        windowGeneration != detectionContextRevision
                    ) {
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
                val settings = captured.state.audioSettings
                val feedbackActive = !feedbackInputGate.accepts(SystemClock.elapsedRealtime())
                if (feedbackActive && range.maxHertz >= CONFIRMATION_CHIME_HERTZ / 2.0) return@start
                val frame = detector.analyze(
                    samples,
                    sampleRate,
                    range.minHertz,
                    range.maxHertz,
                    rejectConfirmation = feedbackActive,
                )
                synchronized(pitchPipelineLock) {
                    if (!isCurrentDetectionContextLocked(captured)) return@synchronized
                    noiseFloor.observe(frame.rms, frame.isDetectorVoiced)
                    val tracked = if (
                        noiseFloor.accepts(
                            frame.rms,
                            settings.sensitivity,
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
                    val rawReading = tracked?.let {
                        engine.update(
                            estimate = it,
                            mode = captured.state.mode,
                            tuning = captured.state.tuning,
                            selectedString = captured.state.selectedString,
                            referencePitch = captured.state.referencePitch,
                            settings = settings,
                        )
                    }
                    publishReadingLocked(captured, rawReading, SystemClock.elapsedRealtime())
                }
            },
            onStarted = { capabilities ->
                synchronized(pitchPipelineLock) {
                    if (!cleared && sessionGeneration == audioSessionGeneration) {
                        mutableUiState.update { it.copy(audioInputCapabilities = capabilities) }
                    }
                }
            },
            onError = { error ->
                invalidateAudioSession(sessionGeneration) {
                    it.copy(listening = false, reading = null, tuningConfirmed = false, error = error)
                }
            },
            captureGeneration = {
                synchronized(pitchPipelineLock) {
                    if (isDetectionActive(mutableUiState.value) && sessionGeneration == audioSessionGeneration) {
                        detectionContextRevision
                    } else {
                        null
                    }
                }
            },
        )
        if (cleared || !foreground || !tunerActive || sessionGeneration != audioSessionGeneration) audioInput.stop()
    }

    private fun publishReadingLocked(captured: DetectionCallbackToken, rawReading: TunerReading?, nowMillis: Long) {
        val settings = captured.state.audioSettings
        val displayReading = readingRetainer.update(rawReading, nowMillis, settings.readingHoldMillis)
        val playConfirmation = confirmationTracker.update(
            rawReading?.target,
            rawReading?.inTune == true,
            nowMillis,
            settings.confirmationMillis,
        )
        pendingConfirmation = confirmationTracker.isConfirmed && (pendingConfirmation || playConfirmation)
        detectionResultRevision++
        detectionResults.trySend(DetectionResult(captured, displayReading, confirmationTracker.isConfirmed, detectionResultRevision))
    }

    private fun applyDetectionResult(result: DetectionResult) {
        val captured = result.captured
        val playbackFailure = synchronized(pitchPipelineLock) {
            val current = mutableUiState.value
            if (!isDetectionActive(current) || result.revision != detectionResultRevision) return@synchronized null
            var failure: RuntimeException? = null
            val applied = mutateIfCurrentDetectionCallback(
                captured,
                current,
                audioSessionGeneration,
                detectionContextRevision,
            ) {
                mutableUiState.update {
                    it.copy(reading = result.reading, tuningConfirmed = result.confirmed)
                }
                if (pendingConfirmation && result.confirmed) {
                    pendingConfirmation = false
                    try {
                        feedbackInputGate.suppress(
                            nowMillis = SystemClock.elapsedRealtime(),
                            durationMillis = CONFIRMATION_FEEDBACK_MILLIS,
                        )
                        confirmationPlayer.play()
                    } catch (error: RuntimeException) {
                        feedbackInputGate.reset()
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

    private fun startAudioIfReady() {
        val state = mutableUiState.value
        if (cleared || !foreground || !tunerActive || !state.microphoneGranted || state.listening) return
        if (
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableUiState.update { it.copy(microphoneGranted = false, listening = false) }
            return
        }
        synchronized(pitchPipelineLock) {
            val generation = prepareAudioStart()
            captureRequests.value = CaptureRequest(generation, mutableUiState.value.audioSettings.inputSource)
        }
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
        return !cleared && foreground &&
            tunerActive &&
            current.listening &&
            !current.referenceTonePlaying
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
        scheduleStop: Boolean = true,
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
        if (scheduleStop) captureRequests.value = CaptureRequest(audioSessionGeneration, null)
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
        pendingConfirmation = false
        // The speaker/room tail outlives a mode, tuning, or settings change.
    }

    private fun stopTunerAudio() {
        invalidateAudioSession {
            it.copy(listening = false, reading = null, tuningConfirmed = false)
        }
        stopReferenceTone()
        confirmationPlayer.stop()
    }

    private companion object {
        const val DEFAULT_TUNING_ID = "guitar-6-standard"
        const val ANALYSIS_WINDOW_SIZE = 8_192
        const val REFERENCE_PREVIEW_MILLIS = 1_050L
        // ponytail: bounded to short output/room tails; measure longer routes before widening.
        const val CONFIRMATION_FEEDBACK_MILLIS = 400L
    }
}
