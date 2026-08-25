package com.tuneitall.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.trainerChoices
import com.tuneitall.tuner.music.noteQuestion
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import com.tuneitall.tuner.ui.ChordTab
import com.tuneitall.tuner.ui.ChordUiState
import com.tuneitall.tuner.ui.ChordsScreen
import com.tuneitall.tuner.ui.AutoScrollScreen
import com.tuneitall.tuner.ui.TrainerScreen
import com.tuneitall.tuner.ui.formatChord
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicToolsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
    private val catalog by lazy {
        ChordShapeCatalog.fromResources(InstrumentationRegistry.getInstrumentation().targetContext.resources)
    }

    @Test
    fun chordLibraryChangesRootAndQualityAndRendersVoicing() {
        var state by mutableStateOf(ChordUiState())
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = { state = state.copy(tab = it) },
                    onChordSelected = { state = state.copy(selectedChord = it) },
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("chord_root_9").performScrollTo().performClick()
        compose.onNodeWithTag("chord_quality_minor").performClick()

        compose.onNodeWithTag("selected_chord_label").assertTextEquals("Am")
        compose.onNodeWithTag("chord_diagram").assertIsDisplayed()
        compose.runOnIdle { assertEquals(Chord(9, ChordQuality.MINOR), state.selectedChord) }
    }

    @Test
    fun autoScrollSetupExplainsMissingPermissions() {
        compose.setContent {
            TuneItAllTheme {
                AutoScrollScreen(
                    overlayAllowed = false,
                    accessibilityEnabled = false,
                    speed = 15,
                    onSpeedChanged = {},
                    onOpenOverlaySettings = {},
                    onOpenAccessibilitySettings = {},
                    onShowControls = {},
                )
            }
        }

        compose.onNodeWithTag("auto_scroll_screen").assertIsDisplayed()
        compose.onNodeWithTag("auto_scroll_speed").assertTextEquals("15")
        compose.onNodeWithTag("auto_scroll_overlay_permission").assertIsDisplayed()
        compose.onNodeWithTag("auto_scroll_accessibility_permission").assertIsDisplayed()
        compose.onNodeWithTag("auto_scroll_show_controls").assertIsNotEnabled()
    }

    @Test
    fun songTimelineTransposesCurrentChordWithoutChangingAnalysis() {
        val original = ChordEvent(0L, 4_000L, Chord(0, ChordQuality.MAJOR), 0.9)
        var state by mutableStateOf(
            ChordUiState(
                tab = ChordTab.SONG,
                fileName = "test.wav",
                events = listOf(original),
                prepared = true,
                durationMillis = 4_000L,
                positionMillis = 1_000L,
            ),
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = { state = state.copy(tab = it) },
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = { state = state.copy(transposeSemitones = it) },
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("current_song_chord").assertTextEquals("C")
        compose.onNodeWithTag("transpose_up").performClick()

        compose.onNodeWithTag("current_song_chord").assertTextEquals("C♯")
        compose.runOnIdle { assertEquals(listOf(original), state.events) }
    }

    @Test
    fun trainerQuizRecordsOneAnswerAndKeepsTheAnswerHiddenUntilSelection() {
        var recorded: Boolean? = null
        val answer = Chord(2, ChordQuality.MINOR)
        val firstChoice = trainerChoices(answer, seed = 1).first()
        compose.setContent {
            TuneItAllTheme {
                TrainerScreen(
                    stats = TrainerStats(),
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onRecord = { recorded = it },
                    onReset = {},
                )
            }
        }

        compose.onNodeWithTag("trainer_mode_quiz").performClick()
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
        compose.onNodeWithTag("chord_diagram_label").assertDoesNotExist()
        compose.onNodeWithText(formatChord(firstChoice, NoteNotation.SHARPS)).performClick()

        compose.onNodeWithTag("trainer_feedback").assertIsDisplayed()
        compose.onNodeWithTag("chord_diagram").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(recorded != null)
            assertEquals(firstChoice == answer, recorded)
        }
    }

    @Test
    fun noteTrainerRecordsOneAnswerWithoutRevealingItFirst() {
        var recorded: Boolean? = null
        val question = noteQuestion(seed = 1)
        val firstChoice = question.choices.first()
        compose.setContent {
            TuneItAllTheme {
                TrainerScreen(
                    stats = TrainerStats(),
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onRecord = { recorded = it },
                    onReset = {},
                )
            }
        }

        compose.onNodeWithTag("trainer_exercise_notes").performClick()
        compose.onNodeWithTag("trainer_note_feedback").assertDoesNotExist()
        compose.onNodeWithTag("trainer_note_play").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_answer_$firstChoice").performClick()

        compose.onNodeWithTag("trainer_note_feedback").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(firstChoice == question.answerPitchClass, recorded)
        }
    }
}
