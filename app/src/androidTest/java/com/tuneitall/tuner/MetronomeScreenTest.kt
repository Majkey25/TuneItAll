package com.tuneitall.tuner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeUiState
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetronomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bpmDraftCommitsOnlyAfterImeDone() {
        var committedBpm = 225
        compose.setContent {
            TuneItAllTheme(darkTheme = false) {
                MetronomeScreen(
                    state = MetronomeUiState(settings = MetronomeSettings(bpm = Bpm(225))),
                    phaseProvider = { 0.0 },
                    onBpmChange = { committedBpm = it },
                    onTap = {},
                    onStart = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithTag("metronome_bpm_input").performTextClearance()
        compose.onNodeWithTag("metronome_bpm_input").performTextInput("400")
        compose.runOnIdle { assertEquals(225, committedBpm) }

        compose.onNodeWithTag("metronome_bpm_input").performImeAction()
        compose.runOnIdle { assertEquals(400, committedBpm) }
    }

    @Test
    fun rhythmSummaryOpensQuickControlsWithoutGlobalSettingsAction() {
        var globalSettingsCount = 0
        compose.setContent {
            TuneItAllTheme(darkTheme = false) {
                MetronomeScreen(
                    state = MetronomeUiState(),
                    phaseProvider = { 0.0 },
                    onBpmChange = {},
                    onTap = {},
                    onStart = {},
                    onStop = {},
                    onOpenSettings = { globalSettingsCount++ },
                )
            }
        }

        compose.onNodeWithTag("metronome_rhythm_summary").performClick()
        compose.onNodeWithTag("metronome_settings_controls").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, globalSettingsCount) }
    }

    @Test
    fun detectedSongTempoCanBeApplied() {
        var applyCount = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TuneItAllTheme(darkTheme = false) {
                MetronomeScreen(
                    state = MetronomeUiState(detectedBpm = 132, tempoConfidence = 0.88),
                    phaseProvider = { 0.0 },
                    onBpmChange = {},
                    onTap = {},
                    onStart = {},
                    onStop = {},
                    onApplyDetectedTempo = { applyCount++ },
                    onOpenSettings = {},
                )
            }
        }

        compose.onNodeWithTag("tempo_choose_audio").assertDoesNotExist()
        compose.onNodeWithTag("tempo_detected").assertDoesNotExist()
        compose.onNodeWithTag("tempo_song_toggle").performScrollTo()
            .assertTextEquals("Get tempo from a song")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithTag("tempo_detected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tempo_rhythm_match").assertTextEquals("Rhythm match: 88%")
        compose.onNodeWithTag("tempo_choose_audio").performScrollTo().assertTextEquals("Analyze a song")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tempo_song_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithTag("tempo_apply").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, applyCount) }
        compose.onNodeWithTag("tempo_song_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("tempo_choose_audio").assertDoesNotExist()
        compose.onNodeWithTag("tempo_detected").assertDoesNotExist()
        compose.onNodeWithTag("metronome_bpm_input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tempo_song_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("tempo_detected").performScrollTo().assertTextEquals("Detected tempo: 132 BPM")
    }
}
