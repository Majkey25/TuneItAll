package com.tuneitall.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.music.NoteBank
import com.tuneitall.tuner.music.NoteTrainingSets
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.ui.NoteTrainer
import com.tuneitall.tuner.ui.TrainerMode
import com.tuneitall.tuner.ui.TrainerScreen
import com.tuneitall.tuner.ui.ChordTrainer
import com.tuneitall.tuner.ui.formatChord
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.instructionalChordQualities
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.tuneitall.tuner.storage.TrainerStats
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainerPracticeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resolvedChordQuestionsSurviveModeSwitchAndRestoration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restoration = StateRestorationTester(compose)
        var mode by mutableStateOf(TrainerMode.QUIZ)
        var records = 0
        restoration.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(520.dp)) {
                    ChordTrainer(mode, TuningCatalog.presets, NoteNotation.SHARPS,
                        ChordShapeCatalog.fromResources(context.resources), { records++ }, {}, { _, _ -> }, Modifier)
                }
            }
        }
        val choices = compose.onAllNodes(SemanticsMatcher("Chord answer") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("trainer_answer_") == true
        })
        choices[0].performClick()
        val feedback = compose.onNodeWithTag("trainer_feedback").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.runOnIdle { mode = TrainerMode.QUIZ }
        compose.onNodeWithTag("trainer_feedback").assertTextEquals(feedback)
        choices[0].assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("trainer_feedback").assertTextEquals(feedback)
        choices[0].assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, records) }

        compose.onNodeWithTag("trainer_next_question").performClick()
        compose.onNodeWithTag("trainer_feedback").assertDoesNotExist()
        choices[0].assertIsEnabled()
        compose.onNodeWithTag("trainer_reveal").performClick()
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.runOnIdle { mode = TrainerMode.QUIZ }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("trainer_feedback").assertIsDisplayed()
        choices[0].assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, records) }
    }

    @Test
    fun resolvedNoteQuestionsSurviveModeSwitchAndRestoration() {
        val restoration = StateRestorationTester(compose)
        var mode by mutableStateOf(TrainerMode.QUIZ)
        var records = 0
        restoration.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(520.dp)) {
                    NoteTrainer(mode, NoteTrainingSets(), NoteNotation.SHARPS,
                        {}, { records++ }, {}, { _, _ -> }, {}, false, Modifier)
                }
            }
        }
        compose.onNodeWithTag("trainer_note_answer_0").performClick()
        val feedback = compose.onNodeWithTag("trainer_note_feedback").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.runOnIdle { mode = TrainerMode.QUIZ }
        compose.onNodeWithTag("trainer_note_feedback").assertTextEquals(feedback)
        compose.onNodeWithTag("trainer_note_answer_0").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("trainer_note_feedback").assertTextEquals(feedback)
        compose.onNodeWithTag("trainer_note_answer_0").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, records) }

        compose.onNodeWithTag("trainer_note_next").performClick()
        compose.onNodeWithTag("trainer_note_feedback").assertDoesNotExist()
        compose.onNodeWithTag("trainer_note_answer_0").assertIsEnabled()
        compose.onNodeWithTag("trainer_note_reveal").performClick()
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.runOnIdle { mode = TrainerMode.QUIZ }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("trainer_note_feedback").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_answer_0").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, records) }
    }

    @Test
    fun quizComparisonKeepsTheAnsweredPairAcrossModeSwitch() {
        var mode by mutableStateOf(TrainerMode.QUIZ)
        var sets by mutableStateOf(NoteTrainingSets())
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(520.dp)) {
                    NoteTrainer(mode, sets, NoteNotation.SHARPS,
                        {}, {}, {}, { _, _ -> }, {}, false, Modifier)
                }
            }
        }
        compose.onNodeWithTag("trainer_note_answer_4").performClick()
        compose.onNodeWithTag("trainer_compare_notes").performClick()
        val first = compose.onNodeWithTag("trainer_play_a").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        val second = compose.onNodeWithTag("trainer_play_b").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text
        compose.onNodeWithTag("trainer_close_compare").performClick()
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.runOnIdle { mode = TrainerMode.QUIZ }
        compose.onNodeWithTag("trainer_compare_notes").performClick()
        compose.onNodeWithTag("trainer_play_a").assertTextEquals(first)
        compose.onNodeWithTag("trainer_play_b").assertTextEquals(second)
        compose.onNodeWithTag("trainer_close_compare").performClick()
        compose.runOnIdle { sets = NoteTrainingSets(setOf(0, 2)) }
        compose.onNodeWithTag("trainer_note_feedback").assertDoesNotExist()
        compose.runOnIdle { mode = TrainerMode.LEARN }
        compose.onNodeWithTag("trainer_play_a").assertTextEquals("Hear A · C4")
        compose.onNodeWithTag("trainer_play_b").assertTextEquals("Hear B · D4")
    }

    @Test
    fun trainerListenContentAndControlsStayReachableAtMaximumTextSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = ChordShapeCatalog.fromResources(context.resources)
        compose.setContent {
            TuneItAllTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2.6f)) {
                    Box(Modifier.width(320.dp).height(568.dp)) {
                        TrainerScreen(
                            TrainerStats(4, 9), TuningCatalog.presets, NoteNotation.SHARPS,
                            catalog, {}, {}, NoteTrainingSets((0..11).toSet()), {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("trainer_exercise_notes").performScrollTo().performClick()
        compose.onNodeWithTag("trainer_mode_learn").performScrollTo().performClick()
        compose.onNodeWithTag("trainer_listen_11").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("trainer_pick_A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("trainer_pick_B").assertIsDisplayed()
        compose.onNodeWithTag("trainer_play_a").assertIsDisplayed()
        compose.onNodeWithTag("trainer_play_b").assertIsDisplayed()
        compose.onNodeWithTag("trainer_repeat").assertIsDisplayed()
    }

    @Test
    fun learnNextShufflesAndPlaysTheDisplayedChord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val played = mutableListOf<Chord>()
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(480.dp)) {
                    ChordTrainer(TrainerMode.LEARN, TuningCatalog.presets, NoteNotation.SHARPS,
                        ChordShapeCatalog.fromResources(context.resources), {}, {},
                        { _, chord -> played += chord }, Modifier)
                }
            }
        }
        repeat(12) {
            compose.onNodeWithTag("trainer_next_question").assertIsDisplayed().performClick()
            compose.onNodeWithTag("trainer_chord_label").assertTextEquals(formatChord(played.last(), NoteNotation.SHARPS))
        }
        compose.runOnIdle {
            assertEquals(12, played.size)
            assertTrue(played.zipWithNext().all { (a, b) -> a != b })
            val indices = played.map { it.rootPitchClass * instructionalChordQualities.size + instructionalChordQualities.indexOf(it.quality) }
            assertTrue(indices.zipWithNext().any { (a, b) -> b != (a + 1) % 168 })
        }
    }

    @Test
    fun showingAnswerDoesNotScoreOrPushTheQuizButtonsAway() {
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(320.dp).height(440.dp)) {
                    NoteTrainer(TrainerMode.QUIZ, NoteTrainingSets(), NoteNotation.SHARPS,
                        {}, { records++ }, {}, { _, _ -> }, {}, false, Modifier)
                }
            }
        }
        val before = compose.onNodeWithTag("trainer_note_next").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("trainer_note_reveal").performClick()
        compose.onNodeWithTag("trainer_note_feedback").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_play").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_next").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("trainer_note_next").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("trainer_compare_notes").performClick()
        compose.onNodeWithTag("trainer_play_a").assertIsDisplayed()
        compose.onNodeWithTag("trainer_repeat").assertIsDisplayed()
        compose.onNodeWithTag("trainer_close_compare").performClick()
        compose.runOnIdle { assertEquals(0, records) }
    }

    @Test
    fun trainerStartsInQuizAndLetsYouSkipWithoutGuessing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(520.dp)) {
                    TrainerScreen(
                        TrainerStats(), TuningCatalog.presets, NoteNotation.SHARPS,
                        ChordShapeCatalog.fromResources(context.resources), { records++ }, {},
                        NoteTrainingSets(), {},
                    )
                }
            }
        }
        compose.onNodeWithTag("trainer_mode_quiz").assertIsSelected()
        compose.onNodeWithTag("trainer_next_question").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, records) }
    }

    @Test
    fun wholeTrainerKeepsNoteAnswersAndControlsVisibleWithLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Box(Modifier.width(360.dp).height(520.dp)) {
                        TrainerScreen(
                            TrainerStats(4, 9), TuningCatalog.presets, NoteNotation.SHARPS,
                            ChordShapeCatalog.fromResources(context.resources), { records++ }, {},
                            NoteTrainingSets(), {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("trainer_exercise_notes").performClick()
        compose.onNodeWithTag("trainer_note_answer_0").assertIsDisplayed().performClick()
        compose.onNodeWithTag("trainer_note_answer_4").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_feedback").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_play").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_next").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, records) }
    }

    @Test
    fun nextNotePlaysANewQuestionWithoutRequiringAnAnswer() {
        val played = mutableListOf<Int>()
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(480.dp)) {
                    NoteTrainer(TrainerMode.QUIZ, NoteTrainingSets(), NoteNotation.SHARPS,
                        {}, { records++ }, { played += it }, { _, _ -> }, {}, false, Modifier)
                }
            }
        }
        compose.onNodeWithTag("trainer_note_play").performClick()
        compose.onNodeWithTag("trainer_note_next").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, played.size)
            assertTrue(played[0] != played[1])
            assertEquals(0, records)
        }
    }

    @Test
    fun compareControlsStayVisibleAndReplayBothSelectedNotes() {
        val played = mutableListOf<Int>()
        var repeated: Pair<Int, Int>? = null
        var repeating by mutableStateOf(false)
        compose.setContent {
            TuneItAllTheme(darkTheme = true) {
                Box(Modifier.width(360.dp).height(500.dp)) {
                    NoteTrainer(TrainerMode.LEARN, NoteTrainingSets((0..11).toSet()), NoteNotation.SHARPS,
                        onSetsChanged = {}, onRecord = {}, onPlay = { played += it; repeating = false },
                        onRepeat = { a, b -> repeated = a to b; repeating = true },
                        onStop = { repeating = false }, repeating = repeating, modifier = Modifier)
                }
            }
        }
        compose.onNodeWithTag("trainer_play_a").assertIsDisplayed().assertTextEquals("Hear A · C4").performClick()
        compose.onNodeWithTag("trainer_play_b").assertIsDisplayed().assertTextEquals("Hear B · C♯4").performClick()
        compose.onNodeWithTag("trainer_repeat").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(0, 1), played)
            assertEquals(0 to 1, repeated)
        }
        compose.onNodeWithTag("trainer_repeat").assertTextEquals("Stop repeating").performClick()
        compose.runOnIdle { assertTrue(!repeating) }
        compose.onNodeWithTag("trainer_pick_B").performScrollTo().performClick()
        compose.onNodeWithTag("trainer_pick_B_2").performClick()
        compose.onNodeWithTag("trainer_play_b").assertIsDisplayed().assertTextEquals("Hear B · D4").performClick()
        compose.onNodeWithTag("trainer_repeat").performClick()
        compose.runOnIdle {
            assertEquals(listOf(0, 1, 2), played)
            assertEquals(0 to 2, repeated)
        }
    }

    @Test
    fun editingMovesNotesBetweenBanksAndQuizUsesOnlyTheSelectedSet() {
        var sets by mutableStateOf(NoteTrainingSets())
        var mode by mutableStateOf(TrainerMode.LEARN)
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                Box(Modifier.width(360.dp).height(520.dp)) {
                    NoteTrainer(mode, sets, NoteNotation.SHARPS, { sets = it }, { records++ },
                        {}, { _, _ -> }, {}, false, Modifier)
                }
            }
        }
        compose.onNodeWithTag("trainer_bank_menu").performClick()
        compose.onNodeWithTag("trainer_bank_learned").performClick()
        compose.onNodeWithTag("trainer_edit_notes").performClick()
        compose.onNodeWithTag("trainer_set_note_0").performClick()
        compose.onNodeWithTag("trainer_set_note_2").performClick()
        compose.onNodeWithTag("trainer_set_save").performClick()
        compose.runOnIdle {
            assertEquals(setOf(4), sets.learning)
            assertEquals(setOf(0, 2), sets.learned)
            mode = TrainerMode.QUIZ
        }
        compose.onNodeWithTag("trainer_note_answer_0").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_answer_2").assertIsDisplayed().performClick()
        compose.onNodeWithTag("trainer_note_answer_4").assertDoesNotExist()
        compose.onNodeWithTag("trainer_note_next").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_answer_0").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, records) }
        compose.onNodeWithTag("trainer_note_next").performClick()
        compose.onNodeWithTag("trainer_note_feedback").assertDoesNotExist()
        compose.onNodeWithTag("trainer_bank_menu").performClick()
        compose.onNodeWithTag("trainer_bank_learning").performClick()
        compose.onNodeWithTag("trainer_note_play").assertIsNotEnabled()
    }

    @Test
    fun savedBanksSurvivePreferencesReloadIncludingEmptyBank() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = UserPreferences(context)
        val original = preferences.noteTrainingSets
        try {
            val sets = NoteTrainingSets().update(NoteBank.LEARNED, setOf(0, 2, 4))
            preferences.noteTrainingSets = sets
            assertEquals(sets, UserPreferences(context).noteTrainingSets)
            assertEquals(emptySet<Int>(), UserPreferences(context).noteTrainingSets.learning)
        } finally {
            preferences.noteTrainingSets = original
        }
    }
}
