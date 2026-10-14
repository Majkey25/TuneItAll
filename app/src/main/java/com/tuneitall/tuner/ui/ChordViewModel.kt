package com.tuneitall.tuner.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuneitall.tuner.audio.SongAudioDecoder
import com.tuneitall.tuner.audio.SongDecodeError
import com.tuneitall.tuner.audio.SongDecodeException
import com.tuneitall.tuner.audio.runSongImport
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.AcousticArrangement
import com.tuneitall.tuner.music.ArrangementMode
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.music.SongEvent
import com.tuneitall.tuner.music.arrangeForStandardE
import java.util.concurrent.CancellationException
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ChordTab {
    LIBRARY,
    SONG,
}

enum class SongChordError {
    NO_AUDIO_TRACK,
    TOO_LONG,
    UNSUPPORTED_PCM,
    DECODE_FAILED,
    NO_CHORDS,
    PLAYBACK_FAILED,
}

data class ChordUiState(
    val tab: ChordTab = ChordTab.LIBRARY,
    val selectedChord: Chord = Chord(0, ChordQuality.MAJOR),
    val selectedTuningId: String = DEFAULT_CHORD_TUNING_ID,
    val transposeSemitones: Int = 0,
    val analysisMode: SongAnalysisMode = SongAnalysisMode.CHORDS,
    val noteRange: NoteRange = NoteRange.ANY,
    val arrangementMode: ArrangementMode = ArrangementMode.EXACT,
    val arrangement: AcousticArrangement? = null,
    val fileName: String? = null,
    val analyzing: Boolean = false,
    val analysisProgress: Int = 0,
    val events: List<SongEvent> = emptyList(),
    val prepared: Boolean = false,
    val playing: Boolean = false,
    val durationMillis: Long = 0L,
    val positionMillis: Long = 0L,
    val analysisError: SongChordError? = null,
    val playbackError: SongChordError? = null,
)

class ChordViewModel internal constructor(
    application: Application,
    private val decoder: SongAudioDecoder,
    private val arrangementDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, SongAudioDecoder(application))

    private val mutableUiState = MutableStateFlow(ChordUiState())
    private var analysisJob: Job? = null
    @Volatile
    private var analysisGeneration = 0L
    private var arrangementJob: Job? = null
    private var arrangementGeneration = 0L
    private var positionJob: Job? = null
    private var player: MediaPlayer? = null
    private var playbackSource: File? = null
    private var currentSongUri: Uri? = null
    private var analysisPausedForScreen = false
    val uiState: StateFlow<ChordUiState> = mutableUiState.asStateFlow()

    fun setTab(tab: ChordTab) {
        if (mutableUiState.value.tab == ChordTab.SONG && tab == ChordTab.LIBRARY) pausePlayback()
        mutableUiState.update { it.copy(tab = tab) }
    }

    fun setChord(chord: Chord) = mutableUiState.update { it.copy(selectedChord = chord) }

    fun setTuning(id: String) {
        require(id.isNotBlank())
        mutableUiState.update { it.copy(selectedTuningId = id) }
    }

    fun setTranspose(semitones: Int) {
        val bounded = semitones.coerceIn(MIN_TRANSPOSE, MAX_TRANSPOSE)
        if (mutableUiState.value.transposeSemitones == bounded) return
        cancelArrangement()
        mutableUiState.update { it.copy(transposeSemitones = bounded, arrangement = null) }
        rebuildArrangement()
    }

    fun setArrangementMode(mode: ArrangementMode) {
        if (mutableUiState.value.arrangementMode == mode) return
        cancelArrangement()
        mutableUiState.update { it.copy(arrangementMode = mode, arrangement = null) }
        rebuildArrangement()
    }

    fun setSongAnalysisMode(mode: SongAnalysisMode) {
        if (mutableUiState.value.analysisMode == mode) return
        cancelArrangement()
        mutableUiState.update { it.copy(analysisMode = mode, events = emptyList(), arrangement = null, analysisError = null) }
        currentSongUri?.let(::startAnalysis)
    }

    fun setNoteRange(range: NoteRange) {
        if (mutableUiState.value.noteRange == range) return
        if (mutableUiState.value.analysisMode == SongAnalysisMode.NOTES) {
            cancelArrangement()
            mutableUiState.update { it.copy(noteRange = range, events = emptyList(), arrangement = null, analysisError = null) }
            currentSongUri?.let(::startAnalysis)
        } else {
            mutableUiState.update { it.copy(noteRange = range) }
        }
    }

    fun loadSong(uri: Uri) {
        cancelArrangement()
        analysisJob?.cancel()
        analysisGeneration++
        analysisPausedForScreen = false
        currentSongUri = uri
        releasePlayer()
        mutableUiState.update {
            it.copy(
                tab = ChordTab.SONG,
                fileName = uri.lastPathSegment.orEmpty().ifBlank { "Audio" },
                analyzing = false,
                analysisProgress = 0,
                events = emptyList(),
                arrangement = null,
                prepared = false,
                playing = false,
                durationMillis = 0L,
                positionMillis = 0L,
                analysisError = null,
                playbackError = null,
            )
        }
        startAnalysis(uri)
    }

    private fun startAnalysis(uri: Uri) {
        cancelArrangement()
        analysisJob?.cancel()
        val generation = ++analysisGeneration
        val request = mutableUiState.value
        val sourceAtStart = playbackSource
        val analysisUri = sourceAtStart?.let(Uri::fromFile) ?: uri
        mutableUiState.update {
            it.copy(analyzing = true, analysisProgress = 0, events = emptyList(), arrangement = null, analysisError = null)
        }
        analysisJob = viewModelScope.launch {
            try {
                val result = runSongImport(onDiscard = { it.sourceFile?.delete() }) { cancelled ->
                    decoder.analyze(
                        uri = analysisUri,
                        mode = request.analysisMode,
                        noteRange = request.noteRange,
                        isCancelled = cancelled,
                        retainSource = sourceAtStart == null,
                        onDisplayName = { name ->
                            if (sourceAtStart == null) launch(Dispatchers.Main.immediate) {
                                if (generation == analysisGeneration) {
                                    mutableUiState.update { it.copy(fileName = name) }
                                }
                            }
                        },
                        onProgress = { progress ->
                            launch(Dispatchers.Main.immediate) {
                                if (generation == analysisGeneration) {
                                    mutableUiState.update { state -> state.copy(analysisProgress = progress) }
                                }
                            }
                        },
                    )
                }
                if (generation != analysisGeneration) {
                    result.sourceFile?.delete()
                    return@launch
                }
                if (sourceAtStart == null) {
                    releasePlayer()
                    playbackSource = result.sourceFile
                    result.sourceFile?.let { preparePlayer(Uri.fromFile(it)) }
                }
                while (true) {
                    val arrangementState = mutableUiState.value.copy(events = result.events)
                    val arrangement = buildArrangement(arrangementState)
                    if (generation != analysisGeneration) return@launch
                    val current = mutableUiState.value
                    if (current.transposeSemitones != arrangementState.transposeSemitones ||
                        current.arrangementMode != arrangementState.arrangementMode
                    ) continue
                    if (mutableUiState.compareAndSet(
                        current,
                        current.copy(
                            analyzing = false,
                            analysisProgress = 100,
                            events = result.events,
                            durationMillis = maxOf(current.durationMillis, result.durationMillis),
                            arrangement = arrangement,
                            analysisError = if (result.events.isEmpty()) SongChordError.NO_CHORDS else null,
                        ),
                    )) break
                }
            } catch (_: CancellationException) {
                Unit
            } catch (error: SongDecodeException) {
                if (generation == analysisGeneration) {
                    mutableUiState.update { state ->
                        state.copy(analyzing = false, analysisError = error.reason.toUiError())
                    }
                }
            } catch (_: RuntimeException) {
                if (generation == analysisGeneration) {
                    mutableUiState.update { state ->
                        state.copy(analyzing = false, analysisError = SongChordError.DECODE_FAILED)
                    }
                }
            }
        }
    }

    fun playPause() {
        val activePlayer = player ?: return
        if (!mutableUiState.value.prepared) return
        try {
            if (activePlayer.isPlaying) {
                pausePlayback()
            } else {
                activePlayer.start()
                mutableUiState.update { it.copy(playing = true, playbackError = null) }
                monitorPosition(activePlayer)
            }
        } catch (_: IllegalStateException) {
            mutableUiState.update { it.copy(playing = false, playbackError = SongChordError.PLAYBACK_FAILED) }
        }
    }

    fun seekTo(positionMillis: Long) {
        val activePlayer = player ?: return
        if (!mutableUiState.value.prepared) return
        val bounded = positionMillis.coerceIn(0L, mutableUiState.value.durationMillis)
        try {
            activePlayer.seekTo(bounded, MediaPlayer.SEEK_CLOSEST)
            mutableUiState.update { it.copy(positionMillis = bounded) }
        } catch (_: IllegalStateException) {
            mutableUiState.update { it.copy(playbackError = SongChordError.PLAYBACK_FAILED) }
        }
    }

    fun onScreenActive(active: Boolean) {
        if (active) {
            if (analysisPausedForScreen) {
                analysisPausedForScreen = false
                currentSongUri?.let(::startAnalysis)
            }
            return
        }
        pausePlayback()
        if (mutableUiState.value.analyzing) {
            analysisJob?.cancel()
            analysisGeneration++
            analysisPausedForScreen = true
        }
        mutableUiState.update { it.copy(analyzing = false) }
    }

    fun clearSong() {
        cancelArrangement()
        analysisJob?.cancel()
        analysisGeneration++
        analysisPausedForScreen = false
        currentSongUri = null
        releasePlayer()
        mutableUiState.update { state ->
            state.copy(
                fileName = null,
                analyzing = false,
                analysisProgress = 0,
                events = emptyList(),
                arrangement = null,
                prepared = false,
                playing = false,
                durationMillis = 0L,
                positionMillis = 0L,
                analysisError = null,
                playbackError = null,
            )
        }
    }

    override fun onCleared() {
        cancelArrangement()
        analysisJob?.cancel()
        analysisGeneration++
        analysisPausedForScreen = false
        currentSongUri = null
        releasePlayer()
        super.onCleared()
    }

    private fun preparePlayer(uri: Uri) {
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            next.setDataSource(getApplication(), uri)
            next.setOnPreparedListener { preparedPlayer ->
                if (player !== preparedPlayer) return@setOnPreparedListener
                mutableUiState.update {
                    it.copy(
                        prepared = true,
                        durationMillis = maxOf(it.durationMillis, preparedPlayer.duration.toLong()),
                        playbackError = null,
                    )
                }
            }
            next.setOnCompletionListener { completedPlayer ->
                if (player !== completedPlayer) return@setOnCompletionListener
                positionJob?.cancel()
                mutableUiState.update {
                    it.copy(playing = false, positionMillis = it.durationMillis)
                }
            }
            next.setOnErrorListener { failedPlayer, _, _ ->
                if (player === failedPlayer) {
                    player = null
                    positionJob?.cancel()
                    positionJob = null
                    runCatching { failedPlayer.release() }
                    mutableUiState.update {
                        it.copy(
                            prepared = false,
                            playing = false,
                            playbackError = SongChordError.PLAYBACK_FAILED,
                        )
                    }
                }
                true
            }
            next.prepareAsync()
        } catch (_: Exception) {
            if (player === next) player = null
            next.release()
            mutableUiState.update { it.copy(playbackError = SongChordError.PLAYBACK_FAILED) }
        }
    }

    private fun monitorPosition(activePlayer: MediaPlayer) {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive && player === activePlayer) {
                val position = runCatching { activePlayer.currentPosition.toLong() }.getOrNull() ?: break
                mutableUiState.update { it.copy(positionMillis = position) }
                delay(POSITION_UPDATE_MILLIS)
            }
        }
    }

    private fun releasePlayer() {
        positionJob?.cancel()
        positionJob = null
        val activePlayer = player
        player = null
        try {
            activePlayer?.release()
        } finally {
            playbackSource?.delete()
            playbackSource = null
        }
    }

    private fun pausePlayback() {
        positionJob?.cancel()
        val activePlayer = player
        try {
            val position = if (activePlayer != null && mutableUiState.value.prepared) {
                if (activePlayer.isPlaying) activePlayer.pause()
                activePlayer.currentPosition.toLong()
            } else mutableUiState.value.positionMillis
            mutableUiState.update { it.copy(playing = false, positionMillis = position) }
        } catch (_: IllegalStateException) {
            mutableUiState.update { it.copy(playing = false, playbackError = SongChordError.PLAYBACK_FAILED) }
        }
    }

    private fun cancelArrangement() {
        arrangementJob?.cancel()
        arrangementJob = null
        arrangementGeneration++
    }

    private fun rebuildArrangement() {
        val request = mutableUiState.value
        if (request.analysisMode != SongAnalysisMode.CHORDS || request.events.isEmpty()) return
        val generation = arrangementGeneration
        val songGeneration = analysisGeneration
        arrangementJob = viewModelScope.launch {
            try {
                val arrangement = buildArrangement(request)
                mutableUiState.update { state ->
                    if (generation == arrangementGeneration && songGeneration == analysisGeneration &&
                        state.events === request.events && state.transposeSemitones == request.transposeSemitones &&
                        state.arrangementMode == request.arrangementMode && state.analysisMode == request.analysisMode
                    ) state.copy(arrangement = arrangement, analysisError = null) else state
                }
            } catch (_: CancellationException) {
                Unit
            } catch (_: RuntimeException) {
                if (generation == arrangementGeneration && songGeneration == analysisGeneration) {
                    mutableUiState.update { it.copy(analysisError = SongChordError.DECODE_FAILED) }
                }
            }
        }
    }

    private suspend fun buildArrangement(state: ChordUiState): AcousticArrangement? {
        if (state.analysisMode != SongAnalysisMode.CHORDS) return null
        if (state.events.isEmpty()) return null
        val catalog = ChordShapeCatalog.load(getApplication<Application>().resources)
        return withContext(arrangementDispatcher) {
            val chords = state.events.filterIsInstance<ChordEvent>().map { event ->
                event.chord.transpose(state.transposeSemitones)
            }
            if (chords.isEmpty()) null else arrangeForStandardE(chords, catalog, state.arrangementMode)
        }
    }

    private fun SongDecodeError.toUiError(): SongChordError = when (this) {
        SongDecodeError.NO_AUDIO_TRACK -> SongChordError.NO_AUDIO_TRACK
        SongDecodeError.TOO_LONG -> SongChordError.TOO_LONG
        SongDecodeError.UNSUPPORTED_PCM -> SongChordError.UNSUPPORTED_PCM
        SongDecodeError.DECODE_FAILED -> SongChordError.DECODE_FAILED
    }

    private companion object {
        const val POSITION_UPDATE_MILLIS = 100L
        const val MIN_TRANSPOSE = -12
        const val MAX_TRANSPOSE = 12
    }
}

private const val DEFAULT_CHORD_TUNING_ID = "guitar-6-standard"
