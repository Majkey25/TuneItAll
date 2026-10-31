package com.tuneitall.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.AppearanceScreen
import com.tuneitall.tuner.ui.AutoScrollRoute
import com.tuneitall.tuner.ui.AppBottomBar
import com.tuneitall.tuner.ui.CustomTuningScreen
import com.tuneitall.tuner.ui.ChordViewModel
import com.tuneitall.tuner.ui.ChordsScreen
import com.tuneitall.tuner.ui.PrimaryDestination
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.SettingsSection
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeViewModel
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.TuningLibraryScreen
import com.tuneitall.tuner.ui.TrainerScreen
import com.tuneitall.tuner.ui.TrainerViewModel

enum class AppScreen {
    Tuner,
    Metronome,
    Chords,
    Library,
    Trainer,
    CustomTuning,
    Settings,
    Appearance,
    About,
    AutoScroll,
}

internal fun parentScreen(screen: AppScreen): AppScreen? = when (screen) {
    AppScreen.Tuner -> null
    AppScreen.Metronome, AppScreen.Chords, AppScreen.Library, AppScreen.Trainer,
    AppScreen.Settings, AppScreen.AutoScroll -> AppScreen.Tuner
    AppScreen.CustomTuning -> AppScreen.Library
    AppScreen.About, AppScreen.Appearance -> AppScreen.Settings
}

@Composable
fun TuneItAllApp(
    state: TunerUiState,
    viewModel: TunerViewModel,
    openApplicationSettings: () -> Unit,
    openSupportPage: () -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    requestMicrophonePermission: () -> Unit = {},
    metronomeViewModel: MetronomeViewModel = composeViewModel(),
    chordViewModel: ChordViewModel? = null,
    trainerViewModel: TrainerViewModel? = null,
) {
    val resources = LocalResources.current
    var chordCatalog by remember(resources) { mutableStateOf<ChordShapeCatalog?>(null) }
    val tunings = remember(state.customTunings) { TuningCatalog.presets + state.customTunings }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Tuner) }
    val screenStateHolder = rememberSaveableStateHolder()
    var settingsReturnScreen by rememberSaveable { mutableStateOf(AppScreen.Tuner) }
    var settingsInitialSection by rememberSaveable { mutableStateOf(SettingsSection.TUNER) }
    val metronomeState by metronomeViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(screen, resources) {
        if (chordCatalog == null && screen in listOf(AppScreen.Chords, AppScreen.Trainer)) {
            chordCatalog = ChordShapeCatalog.load(resources)
        }
    }
    LifecycleStartEffect(metronomeViewModel) {
        onStopOrDispose { metronomeViewModel.onStop() }
    }
    LaunchedEffect(screen) {
        viewModel.setTunerActive(screen == AppScreen.Tuner)
        val metronomeActive = screen == AppScreen.Metronome ||
            (screen in listOf(AppScreen.Settings, AppScreen.Appearance, AppScreen.About) && settingsReturnScreen == AppScreen.Metronome)
        if (!metronomeActive) {
            metronomeViewModel.stopAsync()
            metronomeViewModel.cancelTempoAnalysis()
        }
    }
    val parent = if (screen == AppScreen.Settings) settingsReturnScreen else parentScreen(screen)
    BackHandler(enabled = parent != null) {
        if (screen == AppScreen.CustomTuning) screenStateHolder.removeState(screen.name)
        screen = requireNotNull(parent)
    }
    val primaryDestination = when (screen) {
        AppScreen.Tuner -> PrimaryDestination.TUNER
        AppScreen.Metronome -> PrimaryDestination.METRONOME
        AppScreen.Chords -> PrimaryDestination.CHORDS
        AppScreen.AutoScroll -> PrimaryDestination.AUTO_SCROLL
        AppScreen.Trainer -> PrimaryDestination.TRAINER
        AppScreen.Library, AppScreen.CustomTuning, AppScreen.Settings, AppScreen.Appearance, AppScreen.About -> null
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (primaryDestination != null) {
                AppBottomBar(
                    selected = primaryDestination,
                    onSelect = { destination ->
                        screen = when (destination) {
                            PrimaryDestination.TUNER -> AppScreen.Tuner
                            PrimaryDestination.METRONOME -> AppScreen.Metronome
                            PrimaryDestination.CHORDS -> AppScreen.Chords
                            PrimaryDestination.AUTO_SCROLL -> AppScreen.AutoScroll
                            PrimaryDestination.TRAINER -> AppScreen.Trainer
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)) {
            screenStateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                AppScreen.Tuner -> TunerScreen(
                    state = state,
                    onModeSelected = viewModel::selectMode,
                    onStringSelected = viewModel::selectStringAndPlayReference,
                    onToggleFavorite = { viewModel.toggleFavorite(state.tuning.id) },
                    onOpenLibrary = { screen = AppScreen.Library },
                    onOpenSettings = {
                        screenStateHolder.removeState(AppScreen.Settings.name)
                        settingsReturnScreen = AppScreen.Tuner
                        settingsInitialSection = SettingsSection.TUNER
                        screen = AppScreen.Settings
                    },
                    onRequestMicrophonePermission = requestMicrophonePermission,
                    onRetryAudio = viewModel::retryAudio,
                    onOpenApplicationSettings = openApplicationSettings,
                )

                AppScreen.Metronome -> MetronomeScreen(
                    state = metronomeState,
                    phaseProvider = metronomeViewModel::phase,
                    onBpmChange = metronomeViewModel::setBpm,
                    onNumeratorChange = metronomeViewModel::setNumerator,
                    onDenominatorChange = metronomeViewModel::setDenominator,
                    onSubdivisionChange = metronomeViewModel::setSubdivision,
                    onAccentEveryChange = metronomeViewModel::setAccentEvery,
                    onTap = metronomeViewModel::tap,
                    onStart = metronomeViewModel::startAsync,
                    onStop = metronomeViewModel::stopAsync,
                    onSoundChange = metronomeViewModel::setSound,
                    onVolumeChange = metronomeViewModel::setVolume,
                    onMutedChange = metronomeViewModel::setMuted,
                    onCountInChange = metronomeViewModel::setCountIn,
                    onLoadTempoSong = metronomeViewModel::loadTempoSong,
                    onApplyDetectedTempo = metronomeViewModel::applyDetectedTempo,
                    onOpenSettings = {
                        screenStateHolder.removeState(AppScreen.Settings.name)
                        settingsReturnScreen = AppScreen.Metronome
                        settingsInitialSection = SettingsSection.METRONOME
                        screen = AppScreen.Settings
                    },
                )

                AppScreen.Chords -> {
                    val activeViewModel = chordViewModel ?: composeViewModel()
                    val activeState by activeViewModel.uiState.collectAsStateWithLifecycle()
                    LifecycleStartEffect(activeViewModel) {
                        activeViewModel.onScreenActive(true)
                        onStopOrDispose { activeViewModel.onScreenActive(false) }
                    }
                    val catalog = chordCatalog
                    if (catalog == null) {
                        Box(Modifier.fillMaxSize().testTag("chords_screen"), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else ChordsScreen(
                        state = activeState,
                        tunings = tunings,
                        notation = state.notation,
                        catalog = catalog,
                        onTabSelected = activeViewModel::setTab,
                        onChordSelected = activeViewModel::setChord,
                        onTuningSelected = activeViewModel::setTuning,
                        onTransposeChanged = activeViewModel::setTranspose,
                        onAnalysisModeSelected = activeViewModel::setSongAnalysisMode,
                        onArrangementModeSelected = activeViewModel::setArrangementMode,
                        onNoteRangeSelected = activeViewModel::setNoteRange,
                        onLoadSong = activeViewModel::loadSong,
                        onPlayPause = activeViewModel::playPause,
                        onSeek = activeViewModel::seekTo,
                        onClearSong = activeViewModel::clearSong,
                    )
                }

                AppScreen.Library -> TuningLibraryScreen(
                    presets = tunings,
                    favoriteIds = state.favoriteIds,
                    notation = state.notation,
                    onSelect = { tuning ->
                        viewModel.selectTuning(tuning)
                        screen = AppScreen.Tuner
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onCreateCustom = { screen = AppScreen.CustomTuning },
                    onBack = { screen = AppScreen.Tuner },
                    customIds = state.customTunings.mapTo(mutableSetOf(), TuningPreset::id),
                    onDeleteCustom = viewModel::deleteCustomTuning,
                )

                AppScreen.CustomTuning -> CustomTuningScreen(
                    existing = tunings,
                    customCount = state.customTunings.size,
                    onSave = { tuning ->
                        viewModel.saveCustomTuning(tuning)
                        screenStateHolder.removeState(AppScreen.CustomTuning.name)
                        screen = AppScreen.Tuner
                    },
                    onBack = {
                        screenStateHolder.removeState(AppScreen.CustomTuning.name)
                        screen = AppScreen.Library
                    },
                )

                AppScreen.Trainer -> {
                    val activeViewModel = trainerViewModel ?: composeViewModel()
                    val activeStats by activeViewModel.stats.collectAsStateWithLifecycle()
                    val noteSets by activeViewModel.noteSets.collectAsStateWithLifecycle()
                    val catalog = chordCatalog
                    if (catalog == null) {
                        Box(Modifier.fillMaxSize().testTag("trainer_screen"), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else TrainerScreen(
                        stats = activeStats,
                        tunings = tunings,
                        notation = state.notation,
                        catalog = catalog,
                        onRecord = activeViewModel::record,
                        onReset = activeViewModel::reset,
                        noteSets = noteSets,
                        onNoteSetsChanged = activeViewModel::setNoteSets,
                    )
                }

                AppScreen.Settings -> SettingsScreen(
                    state = state,
                    metronomeState = metronomeState,
                    initialSection = settingsInitialSection,
                    appLanguage = appLanguage,
                    onAppLanguageChanged = onAppLanguageChanged,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onOpenAppearance = {
                        settingsInitialSection = SettingsSection.GENERAL
                        screen = AppScreen.Appearance
                    },
                    onReferencePitchChanged = viewModel::setReferencePitch,
                    onNotationChanged = viewModel::setNotation,
                    onLayoutChanged = viewModel::setHeadstockLayout,
                    onAudioSettingsChanged = viewModel::setTunerAudioSettings,
                    onMetronomeNumeratorChanged = metronomeViewModel::setNumerator,
                    onMetronomeDenominatorChanged = metronomeViewModel::setDenominator,
                    onMetronomeSubdivisionChanged = metronomeViewModel::setSubdivision,
                    onMetronomeAccentChanged = metronomeViewModel::setAccentEvery,
                    onMetronomeSoundChanged = metronomeViewModel::setSound,
                    onMetronomeVolumeChanged = metronomeViewModel::setVolume,
                    onMetronomeMutedChanged = metronomeViewModel::setMuted,
                    onMetronomeCountInChanged = metronomeViewModel::setCountIn,
                    onOpenAbout = { screen = AppScreen.About },
                    onSupport = openSupportPage,
                    onBack = { screen = settingsReturnScreen },
                )

                AppScreen.Appearance -> AppearanceScreen(
                    appearance = state.appearance,
                    themeMode = state.themeMode,
                    onAppearanceChanged = viewModel::setAppearance,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onBack = { screen = AppScreen.Settings },
                )

                AppScreen.About -> AboutScreen(
                    onBack = { screen = AppScreen.Settings },
                    onSupport = openSupportPage,
                )

                AppScreen.AutoScroll -> AutoScrollRoute()
            }
            }
        }
    }
}
