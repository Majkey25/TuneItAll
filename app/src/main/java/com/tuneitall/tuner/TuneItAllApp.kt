package com.tuneitall.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.AppBottomBar
import com.tuneitall.tuner.ui.CustomTuningScreen
import com.tuneitall.tuner.ui.PrimaryDestination
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeViewModel
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.TuningLibraryScreen

sealed interface AppScreen {
    data object Tuner : AppScreen
    data object Metronome : AppScreen
    data object Library : AppScreen
    data object CustomTuning : AppScreen
    data object Settings : AppScreen
    data object About : AppScreen
}

internal fun parentScreen(screen: AppScreen): AppScreen? = when (screen) {
    AppScreen.Tuner -> null
    AppScreen.Metronome, AppScreen.Library, AppScreen.Settings -> AppScreen.Tuner
    AppScreen.CustomTuning -> AppScreen.Library
    AppScreen.About -> AppScreen.Settings
}

@Composable
fun TuneItAllApp(
    state: TunerUiState,
    viewModel: TunerViewModel,
    openApplicationSettings: () -> Unit,
    metronomeViewModel: MetronomeViewModel = composeViewModel(),
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Tuner) }
    val metronomeState by metronomeViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(metronomeViewModel) {
        onStopOrDispose { metronomeViewModel.onStop() }
    }
    LaunchedEffect(screen) {
        viewModel.setTunerActive(screen == AppScreen.Tuner)
        if (screen != AppScreen.Metronome) metronomeViewModel.stopAsync()
    }
    val parent = parentScreen(screen)
    BackHandler(enabled = parent != null) { screen = requireNotNull(parent) }
    val primaryDestination = when (screen) {
        AppScreen.Tuner -> PrimaryDestination.TUNER
        AppScreen.Metronome -> PrimaryDestination.METRONOME
        AppScreen.Library -> PrimaryDestination.LIBRARY
        AppScreen.CustomTuning, AppScreen.Settings, AppScreen.About -> null
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
                            PrimaryDestination.LIBRARY -> AppScreen.Library
                            PrimaryDestination.CHORDS, PrimaryDestination.TRAINER -> screen
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
                    onOpenSettings = { screen = AppScreen.Settings },
                    onOpenApplicationSettings = openApplicationSettings,
                )

                AppScreen.Metronome -> MetronomeScreen(
                    state = metronomeState,
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
                )

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

                AppScreen.Settings -> SettingsScreen(
                    state = state,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onReferencePitchChanged = viewModel::setReferencePitch,
                    onNotationChanged = viewModel::setNotation,
                    onLayoutChanged = viewModel::setHeadstockLayout,
                    onAudioSettingsChanged = viewModel::setTunerAudioSettings,
                    onOpenAbout = { screen = AppScreen.About },
                    onBack = { screen = AppScreen.Tuner },
                )

                AppScreen.About -> AboutScreen(onBack = { screen = AppScreen.Settings })
            }
        }
    }
}
