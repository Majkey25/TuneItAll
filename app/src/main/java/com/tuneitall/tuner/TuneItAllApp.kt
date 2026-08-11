package com.tuneitall.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.CustomTuningScreen
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.TuningLibraryScreen

sealed interface AppScreen {
    data object Tuner : AppScreen
    data object Library : AppScreen
    data object CustomTuning : AppScreen
    data object Settings : AppScreen
    data object About : AppScreen
}

@Composable
fun TuneItAllApp(
    state: TunerUiState,
    viewModel: TunerViewModel,
    openApplicationSettings: () -> Unit,
) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Tuner) }
    BackHandler(enabled = screen != AppScreen.Tuner) { screen = AppScreen.Tuner }

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
            onReferencePitchChanged = viewModel::setReferencePitch,
            onNotationChanged = viewModel::setNotation,
            onLayoutChanged = viewModel::setHeadstockLayout,
            onSensitivityChanged = viewModel::setSensitivity,
            onOpenAbout = { screen = AppScreen.About },
            onBack = { screen = AppScreen.Tuner },
        )

        AppScreen.About -> AboutScreen(onBack = { screen = AppScreen.Settings })
    }
}
