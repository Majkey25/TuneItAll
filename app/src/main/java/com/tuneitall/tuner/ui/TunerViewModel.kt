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
import com.tuneitall.tuner.audio.AudioInputError
import com.tuneitall.tuner.audio.CONFIRMATION_CHIME_DURATION_MILLIS
import com.tuneitall.tuner.audio.ConfirmationChimePlayer
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.FeedbackInputGate
import com.tuneitall.tuner.audio.ReferenceTonePlayer
import com.tuneitall.tuner.audio.YinPitchDetector
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
    val sensitivity: DetectionSensitivity = DetectionSensitivity.DEFAULT,
    val tuningConfirmed: Boolean = false,
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferences(application)
    private val audioInput = AudioInput(application)
    private val detector = YinPitchDetector()
    private val engine = TunerEngine()
    private val tonePlayer = ReferenceTonePlayer()
    private val confirmationPlayer = ConfirmationChimePlayer()
    private val confirmationTracker = InTuneConfirmationTracker()
    private val readingRetainer = TunerReadingRetainer()
    private val feedbackInputGate = FeedbackInputGate()
    private var referenceToneStopJob: Job? = null
    @Volatile
    private var foreground = false

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
            favoriteIds = preferences.favoriteIds,
            customTunings = initialCustomTunings,
            reading = null,
            microphoneGranted = false,
            microphonePermanentlyDenied = false,
            listening = false,
            referenceTonePlaying = false,
            error = null,
            sensitivity = preferences.sensitivity,
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
        audioInput.stop()
        stopReferenceTone()
        confirmationPlayer.stop()
        confirmationTracker.reset()
        readingRetainer.reset()
        feedbackInputGate.reset()
        mutableUiState.update {
            it.copy(listening = false, referenceTonePlaying = false, reading = null, tuningConfirmed = false)
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        mutableUiState.update {
            it.copy(
                microphoneGranted = granted,
                microphonePermanentlyDenied = !granted && permanentlyDenied,
                error = null,
            )
        }
        if (granted) {
            startAudioIfReady()
        } else {
            audioInput.stop()
            confirmationTracker.reset()
            readingRetainer.reset()
            feedbackInputGate.reset()
            mutableUiState.update {
                it.copy(listening = false, reading = null, tuningConfirmed = false)
            }
        }
    }

    fun selectMode(mode: TunerMode) {
        stopReferenceTone()
        confirmationTracker.reset()
        readingRetainer.reset()
        preferences.mode = mode
        mutableUiState.update { it.copy(mode = mode, reading = null, tuningConfirmed = false) }
    }

    fun selectStringAndPlayReference(index: Int) {
        require(index in mutableUiState.value.tuning.notesLowToHigh.indices) {
            "Selected string is outside the active tuning"
        }
        preferences.mode = TunerMode.MANUAL
        confirmationTracker.reset()
        readingRetainer.reset()
        mutableUiState.update {
            it.copy(mode = TunerMode.MANUAL, selectedString = index, reading = null, tuningConfirmed = false)
        }
        playReferenceTone()
    }

    fun selectTuning(tuning: TuningPreset) {
        stopReferenceTone()
        confirmationTracker.reset()
        readingRetainer.reset()
        val layout = mutableUiState.value.headstockLayout.takeIf { it in tuning.layouts } ?: tuning.layouts.first()
        preferences.lastTuningId = tuning.id
        preferences.headstockLayout = layout
        mutableUiState.update {
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
        confirmationTracker.reset()
        readingRetainer.reset()
        preferences.referencePitch = referencePitch
        mutableUiState.update {
            it.copy(referencePitch = referencePitch, reading = null, tuningConfirmed = false)
        }
    }

    fun setSensitivity(sensitivity: DetectionSensitivity) {
        confirmationTracker.reset()
        readingRetainer.reset()
        preferences.sensitivity = sensitivity
        mutableUiState.update {
            it.copy(sensitivity = sensitivity, reading = null, tuningConfirmed = false)
        }
    }

    fun setNotation(notation: NoteNotation) {
        preferences.notation = notation
        mutableUiState.update { it.copy(notation = notation) }
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
            confirmationTracker.reset()
            mutableUiState.update {
                it.copy(referenceTonePlaying = true, reading = null, tuningConfirmed = false, error = null)
            }
            referenceToneStopJob = viewModelScope.launch {
                delay(REFERENCE_PREVIEW_MILLIS)
                referenceToneStopJob = null
                stopReferenceTone()
            }
        } catch (_: RuntimeException) {
            tonePlayer.stop()
            mutableUiState.update {
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
        mutableUiState.update { it.copy(referenceTonePlaying = false) }
    }

    override fun onCleared() {
        audioInput.close()
        tonePlayer.close()
        confirmationPlayer.close()
        super.onCleared()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudio() {
        mutableUiState.update { it.copy(listening = true, error = null) }
        audioInput.start(
            windowSize = ANALYSIS_WINDOW_SIZE,
            onWindow = { samples, sampleRate ->
                val state = mutableUiState.value
                if (state.referenceTonePlaying || !feedbackInputGate.accepts(SystemClock.elapsedRealtime())) {
                    return@start
                }
                val range = pitchSearchRange(
                    state.mode,
                    state.tuning,
                    state.selectedString,
                    state.referencePitch,
                )
                val estimate = detector.detect(
                    samples,
                    sampleRate,
                    range.minHertz,
                    range.maxHertz,
                    state.sensitivity,
                )
                val rawReading = estimate?.let {
                    engine.update(
                        estimate = it,
                        mode = state.mode,
                        tuning = state.tuning,
                        selectedString = state.selectedString,
                        referencePitch = state.referencePitch,
                        sensitivity = state.sensitivity,
                    )
                }
                val nowMillis = SystemClock.elapsedRealtime()
                viewModelScope.launch {
                    if (!isCurrentDetectionContext(state)) return@launch
                    val displayReading = readingRetainer.update(rawReading, nowMillis)
                    val playConfirmation = confirmationTracker.update(
                        rawReading?.target,
                        rawReading?.inTune == true,
                        nowMillis,
                    )
                    mutableUiState.update { current ->
                        if (isCurrentDetectionContext(state)) {
                            current.copy(reading = displayReading, tuningConfirmed = confirmationTracker.isConfirmed)
                        } else {
                            current
                        }
                    }
                    if (playConfirmation && mutableUiState.value.tuningConfirmed) {
                        feedbackInputGate.suppress(
                            nowMillis = SystemClock.elapsedRealtime(),
                            durationMillis = CONFIRMATION_INPUT_SUPPRESSION_MILLIS,
                        )
                        try {
                            confirmationPlayer.play()
                        } catch (_: RuntimeException) {
                            feedbackInputGate.reset()
                            mutableUiState.update {
                                it.copy(error = AudioInputError.InitializationFailed("Confirmation sound is unavailable"))
                            }
                        }
                    }
                }
            },
            onError = { error ->
                confirmationTracker.reset()
                readingRetainer.reset()
                mutableUiState.update {
                    it.copy(listening = false, reading = null, tuningConfirmed = false, error = error)
                }
            },
        )
    }

    private fun startAudioIfReady() {
        val state = mutableUiState.value
        if (!foreground || !state.microphoneGranted || state.listening) return
        if (
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableUiState.update { it.copy(microphoneGranted = false, listening = false) }
            return
        }
        startAudio()
    }

    private fun isCurrentDetectionContext(captured: TunerUiState): Boolean {
        val current = mutableUiState.value
        return foreground &&
            current.listening &&
            !current.referenceTonePlaying &&
            feedbackInputGate.accepts(SystemClock.elapsedRealtime()) &&
            current.mode == captured.mode &&
            current.tuning == captured.tuning &&
            current.selectedString == captured.selectedString &&
            current.referencePitch == captured.referencePitch &&
            current.sensitivity == captured.sensitivity
    }

    private companion object {
        const val DEFAULT_TUNING_ID = "guitar-6-standard"
        const val ANALYSIS_WINDOW_SIZE = 4_096
        const val REFERENCE_PREVIEW_MILLIS = 1_050L
        const val CONFIRMATION_INPUT_SUPPRESSION_MILLIS = CONFIRMATION_CHIME_DURATION_MILLIS + 180L
    }
}
