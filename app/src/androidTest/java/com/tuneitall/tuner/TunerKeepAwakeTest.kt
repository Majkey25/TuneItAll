package com.tuneitall.tuner

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TunerKeepAwakeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listeningKeepsScreenOnAcrossModesAndReleasesWhenStoppedOrRemoved() {
        var state by mutableStateOf(TunerViewModel(ApplicationProvider.getApplicationContext()).uiState.value)
        var visible by mutableStateOf(true)
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            TuneItAllTheme {
                if (visible) {
                    TunerScreen(
                        state = state,
                        onModeSelected = {}, onStringSelected = {}, onToggleFavorite = {},
                        onOpenLibrary = {}, onOpenSettings = {}, onOpenApplicationSettings = {},
                    )
                }
            }
        }
        composeRule.runOnIdle { assertFalse(view.keepScreenOn) }
        for (mode in TunerMode.entries) {
            // No recording or playback: silence must not release the screen-on request.
            composeRule.runOnIdle { state = state.copy(mode = mode, listening = true, microphoneGranted = true) }
            composeRule.runOnIdle { assertTrue("Screen must stay on in $mode without a pitch reading", view.keepScreenOn) }
        }
        composeRule.runOnIdle { state = state.copy(listening = false) }
        composeRule.runOnIdle { assertFalse("Stopped tuner must release the screen", view.keepScreenOn) }
        composeRule.runOnIdle { state = state.copy(listening = true) }
        composeRule.runOnIdle { assertTrue(view.keepScreenOn) }
        composeRule.runOnIdle { visible = false }
        composeRule.runOnIdle { assertFalse("Leaving the tuner must release the screen", view.keepScreenOn) }
    }
}
