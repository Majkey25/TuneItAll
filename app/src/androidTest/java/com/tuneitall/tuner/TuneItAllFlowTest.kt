package com.tuneitall.tuner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.TunerReading
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.CustomTuningScreen
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TuningLibraryScreen
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TuneItAllFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun modeSelectionAndPermissionDeniedSurfaceRemainUsable() {
        var selectedMode: TunerMode? = null
        composeRule.setContent {
            TuneItAllTheme(darkTheme = true) {
                TunerScreen(
                    state = state().copy(microphoneGranted = false, listening = false),
                    onModeSelected = { selectedMode = it },
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Microphone access is required to hear your instrument. Audio stays on this device.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Manual").performClick()
        assertEquals(TunerMode.MANUAL, selectedMode)
    }

    @Test
    fun librarySelectsTuningAndFavoritePersists() {
        val tuning = standardTuning()
        var selected: TuningPreset? = null
        var favoriteId: String? = null
        composeRule.setContent {
            TuneItAllTheme {
                TuningLibraryScreen(
                    presets = listOf(tuning),
                    favoriteIds = emptySet(),
                    notation = NoteNotation.SHARPS,
                    onSelect = { selected = it },
                    onToggleFavorite = { favoriteId = it },
                    onCreateCustom = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("☆").performClick()
        assertEquals(tuning.id, favoriteId)
        composeRule.onNodeWithText("Standard E").performClick()
        assertEquals(tuning, selected)

        UserPreferences(context).favoriteIds = setOf(tuning.id)
        assertEquals(setOf(tuning.id), UserPreferences(context).favoriteIds)
    }

    @Test
    fun settingsAccepts444RejectsInvalidAndRequiresExtremeConfirmation() {
        var applied: ReferencePitch? = null
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = state(),
                    onReferencePitchChanged = { applied = it },
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onSensitivityChanged = {},
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        val input = composeRule.onNode(hasSetTextAction())
        input.performTextClearance()
        input.performTextInput("444.0")
        composeRule.onNodeWithText("Apply").performClick()
        assertEquals(444.0, applied?.hertz ?: 0.0, 0.0)

        input.performTextClearance()
        input.performTextInput("400.0")
        composeRule.onNodeWithText("Apply").assertIsNotEnabled()

        input.performTextClearance()
        input.performTextInput("420.0")
        applied = null
        composeRule.onNodeWithText("Review value").performClick()
        assertTrue(applied == null)
        composeRule.onNodeWithText("Confirm value").performClick()
        assertEquals(420.0, applied?.hertz ?: 0.0, 0.0)
    }

    @Test
    fun sensitivityCanChangeAndResetToSafeDefault() {
        var applied: DetectionSensitivity? = null
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = state(),
                    onReferencePitchChanged = {},
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onSensitivityChanged = { applied = it },
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Microphone sensitivity")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(37f) }
        assertEquals(37, applied?.value)
        composeRule.onNodeWithText("Reset sensitivity").performScrollTo().performClick()
        assertEquals(DetectionSensitivity.DEFAULT, applied)
    }

    @Test
    fun customNineStringTuningSavesValidatedNotes() {
        var saved: TuningPreset? = null
        composeRule.setContent {
            TuneItAllTheme {
                CustomTuningScreen(existing = emptyList(), onSave = { saved = it }, onBack = {})
            }
        }

        composeRule.onNodeWithText("9").performClick()
        composeRule.onNode(hasText("Tuning name") and hasSetTextAction()).performTextInput("Nine Low")
        composeRule.onNodeWithText("Save and start tuning").performScrollTo().performClick()

        assertEquals(9, saved?.notesLowToHigh?.size)
        assertTrue(saved?.id?.startsWith("custom-") == true)
        assertFalse(saved?.name.isNullOrBlank())
    }

    @Test
    fun aboutAndBackAreAvailableWithoutNetwork() {
        var back = false
        composeRule.setContent {
            TuneItAllTheme { AboutScreen(onBack = { back = true }) }
        }

        composeRule.onNodeWithText("Offline. No ads, accounts, analytics, tracking, or network access.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").performClick()
        composeRule.onNodeWithText("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun tunerFavoriteActionHasAccessibleDescription() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state(),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add to favorites").assertIsDisplayed()
    }

    @Test
    fun headstockExposesEveryStringAndTapTarget() {
        var selectedString: Int? = null
        composeRule.setContent {
            TuneItAllTheme(darkTheme = true) {
                TunerScreen(
                    state = state().copy(mode = TunerMode.MANUAL),
                    onModeSelected = {},
                    onStringSelected = { selectedString = it },
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("String 6 E2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("String 1 E4").performScrollTo().performClick()
        assertEquals(5, selectedString)
    }

    @Test
    fun sixInlineHeadstockIsCompactAndKeepsPegsOnTheLeft() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(headstockLayout = HeadstockLayout.INLINE_6),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val highString = composeRule.onNodeWithContentDescription("String 1 E4").fetchSemanticsNode().boundsInRoot
        val lowString = composeRule.onNodeWithContentDescription("String 6 E2").fetchSemanticsNode().boundsInRoot
        val spanDp = (lowString.bottom - highString.top) / context.resources.displayMetrics.density

        assertTrue(highString.center.x < root.center.x)
        assertTrue(lowString.center.x < root.center.x)
        assertTrue(spanDp <= 300f)
    }

    @Test
    fun chromaticModeUsesAUniversalTunerWithoutInstrumentControls() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(mode = TunerMode.CHROMATIC),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Chromatic tuner").assertIsDisplayed()
        composeRule.onNodeWithText("Play any note").assertIsDisplayed()
        composeRule.onNodeWithText("Standard E").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("String 6 E2").assertDoesNotExist()
        composeRule.onNodeWithText("Tap a string to hear its reference tone").assertDoesNotExist()
        composeRule.onNodeWithText("Play reference tone").assertDoesNotExist()
    }

    @Test
    fun chromaticNoteSlotDoesNotMoveWhenAccidentalAppears() {
        var note by mutableStateOf(MidiNote(60))
        composeRule.setContent {
            val reading = TunerReading(note, note, 261.63, 0.0, inTune = true)
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(mode = TunerMode.CHROMATIC, reading = reading),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        val natural = composeRule.onNodeWithTag("detected_note").fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle { note = MidiNote(61) }
        val accidental = composeRule.onNodeWithTag("detected_note").fetchSemanticsNode().boundsInRoot

        assertEquals(natural.left, accidental.left, 0.5f)
        assertEquals(natural.width, accidental.width, 0.5f)
    }

    @Test
    fun referenceToneHasNoStandaloneButton() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state(),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("Play reference tone").assertDoesNotExist()
    }

    @Test
    fun settingsActionIsAboveTheModeSelector() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state(),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        val settings = composeRule.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        val auto = composeRule.onNodeWithText("Auto").fetchSemanticsNode().boundsInRoot

        assertTrue(settings.top < auto.top)
    }

    private fun state(): TunerUiState = TunerUiState(
        mode = TunerMode.AUTO,
        tuning = standardTuning(),
        selectedString = 0,
        headstockLayout = HeadstockLayout.SPLIT_3_3,
        referencePitch = ReferencePitch(440.0),
        notation = NoteNotation.SHARPS,
        favoriteIds = emptySet(),
        customTunings = emptyList(),
        reading = null,
        microphoneGranted = true,
        microphonePermanentlyDenied = false,
        listening = true,
        referenceTonePlaying = false,
        error = null,
    )

    private fun standardTuning(): TuningPreset = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
}
