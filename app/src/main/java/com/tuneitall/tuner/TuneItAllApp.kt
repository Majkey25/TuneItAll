package com.tuneitall.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.ui.AboutScreen
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

sealed interface AppScreen {
    data object Tuner : AppScreen
    data object Metronome : AppScreen
    data object Chords : AppScreen
    data object Library : AppScreen
    data object Trainer : AppScreen
    data object CustomTuning : AppScreen
    data object Settings : AppScreen
    data object About : AppScreen
    data object AutoScroll : AppScreen
}

internal fun parentScreen(screen: AppScreen): AppScreen? = when (screen) {
    AppScreen.Tuner -> null
    AppScreen.Metronome, AppScreen.Chords, AppScreen.Library, AppScreen.Trainer, AppScreen.Settings -> AppScreen.Tuner
    AppScreen.CustomTuning -> AppScreen.Library
    AppScreen.About -> AppScreen.Settings
    AppScreen.AutoScroll -> AppScreen.Chords
}

@Composable
fun TuneItAllApp(
    state: TunerUiState,
    viewModel: TunerViewModel,
    openApplicationSettings: () -> Unit,
    openSupportPage: () -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    metronomeViewModel: MetronomeViewModel = composeViewModel(),
    chordViewModel: ChordViewModel? = null,
    trainerViewModel: TrainerViewModel? = null,
) {
    val resources = LocalResources.current
    val chordCatalog = remember(resources) {
        lazy(LazyThreadSafetyMode.NONE) { ChordShapeCatalog.fromResources(resources) }
    }
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Tuner) }
    var settingsReturnScreen: AppScreen by remember { mutableStateOf(AppScreen.Tuner) }
    var settingsInitialSection by remember { mutableStateOf(SettingsSection.TUNER) }
    val metronomeState by metronomeViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(metronomeViewModel) {
        onStopOrDispose { metronomeViewModel.onStop() }
    }
    LaunchedEffect(screen) {
        viewModel.setTunerActive(screen == AppScreen.Tuner)
        val metronomeActive = screen == AppScreen.Metronome ||
            (screen == AppScreen.Settings && settingsReturnScreen == AppScreen.Metronome)
        if (!metronomeActive) {
            metronomeViewModel.stopAsync()
        }
    }
    val parent = if (screen == AppScreen.Settings) settingsReturnScreen else parentScreen(screen)
    BackHandler(enabled = parent != null) { screen = requireNotNull(parent) }
    val primaryDestination = when (screen) {
        AppScreen.Tuner -> PrimaryDestination.TUNER
        AppScreen.Metronome -> PrimaryDestination.METRONOME
        AppScreen.Chords -> PrimaryDestination.CHORDS
        AppScreen.Library -> PrimaryDestination.LIBRARY
        AppScreen.Trainer -> PrimaryDestination.TRAINER
        AppScreen.CustomTuning, AppScreen.Settings, AppScreen.About, AppScreen.AutoScroll -> null
    }

    Scaffold(
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
                            PrimaryDestination.LIBRARY -> AppScreen.Library
                            PrimaryDestination.TRAINER -> AppScreen.Trainer
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            when (screen) {
                AppScreen.Tuner -> TunerScreen(
                    state = state,
                    onModeSelected = viewModel::selectMode,
                    onStringSelected = viewModel::selectStringAndPlayReference,
                    onToggleFavorite = { viewModel.toggleFavorite(state.tuning.id) },
                    onOpenLibrary = { screen = AppScreen.Library },
                    onOpenSettings = {
                        settingsReturnScreen = AppScreen.Tuner
                        settingsInitialSection = SettingsSection.TUNER
                        screen = AppScreen.Settings
                    },
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
                    onOpenSettings = {
                        settingsReturnScreen = AppScreen.Metronome
                        settingsInitialSection = SettingsSection.METRONOME
                        screen = AppScreen.Settings
                    },
                )

                AppScreen.Chords -> {
                    val activeViewModel = chordViewModel ?: composeViewModel()
                    val activeState by activeViewModel.uiState.collectAsStateWithLifecycle()
                    DisposableEffect(activeViewModel) {
                        activeViewModel.onScreenActive(true)
                        onDispose { activeViewModel.onScreenActive(false) }
                    }
                    ChordsScreen(
                        state = activeState,
                        tunings = TuningCatalog.presets + state.customTunings,
                        notation = state.notation,
                        catalog = chordCatalog.value,
                        onTabSelected = activeViewModel::setTab,
                        onChordSelected = activeViewModel::setChord,
                        onTuningSelected = activeViewModel::setTuning,
                        onTransposeChanged = activeViewModel::setTranspose,
                        onLoadSong = activeViewModel::loadSong,
                        onPlayPause = activeViewModel::playPause,
                        onSeek = activeViewModel::seekTo,
                        onClearSong = activeViewModel::clearSong,
                        onOpenAutoScroll = { screen = AppScreen.AutoScroll },
                    )
                }

                AppScreen.Library -> TuningLibraryScreen(
                    presets = TuningCatalog.presets + state.customTunings,
                    favoriteIds = state.favoriteIds,
                    notation = state.notation,
                    onSelect = { tuning ->
                        viewModel.selectTuning(tuning)
                        screen = AppScreen.Tuner
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onCreateCustom = { screen = AppScreen.CustomTuning },
                    onBack = { screen = AppScreen.Tuner },
                )

                AppScreen.CustomTuning -> CustomTuningScreen(
                    existing = state.customTunings,
                    onSave = { tuning ->
                        viewModel.saveCustomTuning(tuning)
                        screen = AppScreen.Tuner
                    },
                    onBack = { screen = AppScreen.Library },
                )

                AppScreen.Trainer -> {
                    val activeViewModel = trainerViewModel ?: composeViewModel()
                    val activeStats by activeViewModel.stats.collectAsStateWithLifecycle()
                    TrainerScreen(
                        stats = activeStats,
                        tunings = TuningCatalog.presets + state.customTunings,
                        notation = state.notation,
                        catalog = chordCatalog.value,
                        onRecord = activeViewModel::record,
                        onReset = activeViewModel::reset,
                    )
                }

                AppScreen.Settings -> SettingsScreen(
                    state = state,
                    metronomeState = metronomeState,
                    initialSection = settingsInitialSection,
                    appLanguage = appLanguage,
                    onAppLanguageChanged = onAppLanguageChanged,
                    onThemeModeChanged = viewModel::setThemeMode,
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
                    onBack = { screen = settingsReturnScreen },
                )

                AppScreen.About -> AboutScreen(
                    onBack = { screen = AppScreen.Settings },
                    onSupport = openSupportPage,
                )

                AppScreen.AutoScroll -> AutoScrollRoute(onBack = { screen = AppScreen.Chords })
            }
        }
    }
}
