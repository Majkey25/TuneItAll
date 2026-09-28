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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.music.NoteBank
import com.tuneitall.tuner.music.NoteTrainingSets
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.ui.NoteTrainer
import com.tuneitall.tuner.ui.TrainerMode
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainerPracticeScreenTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithTag("trainer_play_a").assertIsDisplayed().performClick()
        compose.onNodeWithTag("trainer_play_b").assertIsDisplayed().performClick()
        compose.onNodeWithTag("trainer_repeat").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(0, 1), played)
            assertEquals(0 to 1, repeated)
        }
        compose.onNodeWithTag("trainer_repeat").assertTextEquals("Stop repeating").performClick()
        compose.runOnIdle { assertTrue(!repeating) }
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
