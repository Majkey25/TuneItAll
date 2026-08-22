package com.tuneitall.tuner.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuneitall.tuner.audio.MetronomePlayer
import com.tuneitall.tuner.audio.MetronomeStopResult
import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import com.tuneitall.tuner.metronome.MetronomeSound
import com.tuneitall.tuner.storage.UserPreferences
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MetronomeError {
    OUTPUT_UNAVAILABLE,
    PLAYBACK_STOPPED,
    STOP_FAILED,
}

data class MetronomeUiState(
    val settings: MetronomeSettings = MetronomeSettings(),
    val starting: Boolean = false,
    val playing: Boolean = false,
    val stopping: Boolean = false,
    val muted: Boolean = false,
    val phase: Double = 0.0,
    val error: MetronomeError? = null,
)

class MetronomeViewModel internal constructor(
    application: Application,
    private val player: MetronomePlayer,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, MetronomePlayer())

    private val tapTempo = TapTempo()
    private val lock = Any()
    private val preferences = UserPreferences(application)
    private val mutableUiState = MutableStateFlow(
        MetronomeUiState(
            settings = preferences.metronomeSettings,
            muted = preferences.metronomeMuted,
        ),
    )
    private var playbackGeneration = 0L
    private var startInFlightGeneration: Long? = null
    private var startCancellation: AtomicBoolean? = null
    private var phaseJob: Job? = null
    val uiState: StateFlow<MetronomeUiState> = mutableUiState.asStateFlow()

    fun startAsync() {
        val request = reserveStart() ?: return
        viewModelScope.launch(blockingDispatcher) { completeStart(request) }
    }

    fun stopAsync() {
        val generation = reserveStop() ?: return
        viewModelScope.launch(blockingDispatcher) { completeStop(generation) }
    }

    fun start() {
        val request = reserveStart() ?: return
        completeStart(request)
    }

    fun stop() {
        val generation = reserveStop() ?: return
        completeStop(generation)
    }

    private fun reserveStart(): StartRequest? = synchronized(lock) {
        val state = mutableUiState.value
        if (startInFlightGeneration != null || state.starting || state.playing || state.stopping) {
            return@synchronized null
        }
        playbackGeneration++
        phaseJob?.cancel()
        phaseJob = null
        mutableUiState.update {
            it.copy(starting = true, playing = false, stopping = false, phase = 0.0, error = null)
        }
        StartRequest(playbackGeneration, state.playbackSettings(), AtomicBoolean()).also {
            startInFlightGeneration = it.generation
            startCancellation = it.cancellation
        }
    }

    private fun completeStart(request: StartRequest) {
        val error = try {
            player.start(request.settings) { request.cancellation.get() }
            null
        } catch (failure: RuntimeException) {
            failure
        }
        val staleGeneration = synchronized(lock) {
            if (request.generation != playbackGeneration) return@synchronized playbackGeneration
            startInFlightGeneration = null
            startCancellation = null
            if (error == null) {
                player.update(mutableUiState.value.playbackSettings())
                mutableUiState.update {
                    it.copy(starting = false, playing = true, stopping = false, error = null)
                }
                phaseJob = viewModelScope.launch { monitorPlayback(request.generation) }
            } else {
                val status = player.status
                Log.e(TAG, "Metronome start failed; player=${status.failure}", error)
                mutableUiState.update {
                    it.copy(
                        starting = false,
                        playing = false,
                        stopping = status.stopping,
                        phase = 0.0,
                        error = MetronomeError.OUTPUT_UNAVAILABLE,
                    )
                }
                if (status.stopping) launchStopMonitorLocked(request.generation)
            }
            null
        }
        if (staleGeneration != null) {
            val result = player.stop()
            synchronized(lock) {
                if (startInFlightGeneration == request.generation) startInFlightGeneration = null
                if (startCancellation === request.cancellation) startCancellation = null
                if (staleGeneration == playbackGeneration) applyStopResultLocked(staleGeneration, result)
            }
        }
    }

    private fun reserveStop(): Long? = synchronized(lock) {
        val state = mutableUiState.value
        if (startInFlightGeneration == null && !state.starting && !state.playing && !state.stopping) {
            return@synchronized null
        }
        playbackGeneration++
        startCancellation?.set(true)
        phaseJob?.cancel()
        phaseJob = null
        mutableUiState.update {
            it.copy(starting = false, playing = false, stopping = true, phase = 0.0, error = null)
        }
        playbackGeneration
    }

    private fun completeStop(generation: Long) {
        val result = player.stop()
        synchronized(lock) {
            if (generation != playbackGeneration) return
            applyStopResultLocked(generation, result)
        }
    }

    fun onStop() = stopAsync()

    fun setBpm(value: Int) = setSettings { it.copy(bpm = clampedBpm(value)) }

    fun setNumerator(value: Int) = setSettings { it.copy(numerator = value) }

    fun setDenominator(value: Int) = setSettings { it.copy(denominator = value) }

    fun setSubdivision(value: Int) = setSettings { it.copy(subdivision = value) }

    fun setAccentEvery(value: Int?) = setSettings { it.copy(accentEvery = value) }

    fun setSound(value: MetronomeSound) = setSettings { it.copy(sound = value) }

    fun setVolume(value: Int) = setSettings { it.copy(volume = clampedVolume(value)) }

    fun setCountIn(value: Int) = setSettings { it.copy(countIn = value) }

    fun setMuted(muted: Boolean) = synchronized(lock) {
        mutableUiState.update { it.copy(muted = muted) }
        preferences.metronomeMuted = muted
        val updated = mutableUiState.value
        if (updated.playing) player.update(updated.playbackSettings())
    }

    fun tap(nowMillis: Long = SystemClock.elapsedRealtime()) = synchronized(lock) {
        tapTempo.tap(nowMillis)?.let { bpm -> setSettingsLocked { it.copy(bpm = bpm) } }
    }

    override fun onCleared() {
        synchronized(lock) {
            playbackGeneration++
            startCancellation?.set(true)
            phaseJob?.cancel()
            phaseJob = null
        }
        player.requestStop()
        super.onCleared()
    }

    private fun setSettings(transform: (MetronomeSettings) -> MetronomeSettings) = synchronized(lock) {
        setSettingsLocked(transform)
    }

    private fun setSettingsLocked(transform: (MetronomeSettings) -> MetronomeSettings) {
        mutableUiState.update { current ->
            current.copy(settings = validatedSettings(current.settings, transform))
        }
        val updated = mutableUiState.value
        preferences.metronomeSettings = updated.settings
        if (updated.playing) player.update(updated.playbackSettings())
    }

    private suspend fun monitorPlayback(generation: Long) {
        while (currentCoroutineContext().isActive) {
            synchronized(lock) {
                if (generation != playbackGeneration) return
                val status = player.status
                if (!status.running) {
                    Log.e(TAG, "Metronome playback stopped; player=${status.failure}")
                    mutableUiState.update {
                        it.copy(
                            starting = false,
                            playing = false,
                            stopping = false,
                            phase = 0.0,
                            error = MetronomeError.PLAYBACK_STOPPED,
                        )
                    }
                    phaseJob = null
                    return
                }
                mutableUiState.update { it.copy(phase = player.phase()) }
            }
            delay(PHASE_UPDATE_MILLIS)
        }
    }

    private suspend fun monitorStop(generation: Long) {
        while (currentCoroutineContext().isActive) {
            delay(PHASE_UPDATE_MILLIS)
            if (synchronized(lock) { generation != playbackGeneration }) return
            if (!player.status.running) {
                val result = player.stop()
                synchronized(lock) {
                    if (generation != playbackGeneration) return
                    if (result != MetronomeStopResult.STOPPING) {
                        applyStopResultLocked(generation, result)
                        return
                    }
                }
            }
        }
    }

    private fun applyStopResultLocked(generation: Long, result: MetronomeStopResult) {
        when (result) {
            MetronomeStopResult.STOPPED -> {
                mutableUiState.update {
                    it.copy(starting = false, playing = false, stopping = false, phase = 0.0, error = null)
                }
                phaseJob = null
            }

            MetronomeStopResult.FAILED -> {
                Log.e(TAG, "Metronome stop failed; player=${player.status.failure}")
                mutableUiState.update {
                    it.copy(
                        starting = false,
                        playing = false,
                        stopping = false,
                        phase = 0.0,
                        error = MetronomeError.STOP_FAILED,
                    )
                }
                phaseJob = null
            }

            MetronomeStopResult.STOPPING -> {
                mutableUiState.update {
                    it.copy(
                        starting = false,
                        playing = false,
                        stopping = true,
                        phase = 0.0,
                        error = null,
                    )
                }
                launchStopMonitorLocked(generation)
            }
        }
    }

    private fun launchStopMonitorLocked(generation: Long) {
        phaseJob?.cancel()
        phaseJob = viewModelScope.launch { monitorStop(generation) }
    }

    private fun MetronomeUiState.playbackSettings(): MetronomeSettings =
        if (muted) settings.copy(volume = 0) else settings

    private data class StartRequest(
        val generation: Long,
        val settings: MetronomeSettings,
        val cancellation: AtomicBoolean,
    )

    private companion object {
        const val TAG = "TuneItAll-Metronome"
        const val PHASE_UPDATE_MILLIS = 16L
    }
}

internal fun clampedBpm(value: Int): Bpm = Bpm(value.coerceIn(20, 400))

internal fun clampedVolume(value: Int): Int = value.coerceIn(0, 100)

internal fun validatedSettings(
    current: MetronomeSettings,
    transform: (MetronomeSettings) -> MetronomeSettings,
): MetronomeSettings = try {
    transform(current)
} catch (_: IllegalArgumentException) {
    current
}

internal class TapTempo {
    private val intervals = ArrayDeque<Long>(MAX_INTERVALS)
    private var lastTapMillis: Long? = null

    fun tap(nowMillis: Long): Bpm? {
        require(nowMillis >= 0L)
        val previous = lastTapMillis
        require(previous == null || nowMillis >= previous)
        lastTapMillis = nowMillis
        if (previous == null) return null

        val interval = nowMillis - previous
        if (interval > RESET_MILLIS) {
            intervals.clear()
            return null
        }
        if (interval < MIN_INTERVAL_MILLIS) return null
        if (intervals.size == MAX_INTERVALS) intervals.removeFirst()
        intervals.addLast(interval)

        val sorted = intervals.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle].toDouble()
        }
        return Bpm((60_000.0 / median).roundToInt())
    }

    private companion object {
        const val MAX_INTERVALS = 5
        const val RESET_MILLIS = 2_000L
        const val MIN_INTERVAL_MILLIS = 150L
    }
}
