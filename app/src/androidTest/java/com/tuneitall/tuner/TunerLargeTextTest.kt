package com.tuneitall.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.TunerReading
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.theme.AppearanceSettings
import com.tuneitall.tuner.ui.theme.ThemeFont
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TunerLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun combinedSystemAndAppTextScaleKeepsEveryStatusLabelVisible() {
        compose.setContent { CompactLargeTextTuner(state(), ThemeFont.SYSTEM) }

        compose.onNodeWithTag("tuner_status_tuning").assertTextEquals("Standard E")
        compose.onNodeWithTag("tuner_status_mode").assertTextEquals(context.getString(R.string.mode_manual))
        compose.onNodeWithTag("tuner_status_reference").assertTextEquals(
            context.getString(R.string.reference_pitch_value, 440.0),
        )
        listOf("tuner_status_tuning", "tuner_status_mode", "tuner_status_reference").forEach { tag ->
            val result = textLayout(tag)
            assertEquals(2.6f, result.layoutInput.density.fontScale, 0.001f)
            assertFullyLaidOut(tag, result)
        }
        assertTrue(textLayout("tuner_status_reference").lineCount > 1)
    }

    @Test
    fun combinedSystemAndAppTextScaleKeepsModeChipLabelsVisible() {
        compose.setContent { CompactLargeTextTuner(state(), ThemeFont.SYSTEM) }

        listOf(R.string.mode_auto, R.string.mode_manual, R.string.mode_chromatic).forEach { resource ->
            val label = context.getString(resource)
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(
                hasText(label) and hasAnyAncestor(hasTestTag("mode_selector")),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                assertTrue(getResults(results))
            }
            assertFullyLaidOut("mode_selector $label", results.single())
        }
    }

    @Test
    fun combinedLargeTextKeepsNaturalSharpAndFlatReadoutUnclippedAndAnchored() {
        var note by mutableStateOf(MidiNote(60))
        var notation by mutableStateOf(NoteNotation.SHARPS)
        var font by mutableStateOf(ThemeFont.SYSTEM)
        compose.setContent {
            CompactLargeTextTuner(
                state().copy(
                    reading = TunerReading(note, note, 261.63, 0.0, inTune = true),
                    notation = notation,
                ),
                font,
            )
        }

        ThemeFont.entries.forEach { family ->
            compose.runOnIdle { font = family; note = MidiNote(60); notation = NoteNotation.SHARPS }
            compose.onNodeWithTag("detected_note").assertContentDescriptionEquals("C4")
            val natural = readoutAnchors()
            compose.runOnIdle { note = MidiNote(61) }
            compose.onNodeWithTag("detected_note").assertContentDescriptionEquals("C♯4")
            val sharp = readoutAnchors()
            compose.runOnIdle { notation = NoteNotation.FLATS }
            compose.onNodeWithTag("detected_note").assertContentDescriptionEquals("D♭4")
            val flat = readoutAnchors()

            natural.indices.forEach { index ->
                assertEquals(natural[index].left, sharp[index].left, 0.5f)
                assertEquals(natural[index].width, sharp[index].width, 0.5f)
                assertEquals(natural[index].left, flat[index].left, 0.5f)
                assertEquals(natural[index].width, flat[index].width, 0.5f)
            }
            assertTrue(natural[0].right <= natural[1].left)
            assertTrue(natural[1].right <= natural[2].left)
        }
    }

    private fun readoutAnchors(): List<Rect> = listOf(
        "detected_note_letter", "detected_note_accidental", "detected_note_octave",
    ).map { tag ->
        assertFullyLaidOut(tag, textLayout(tag))
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                assertTrue(getResults(results))
            }
        return results.single()
    }

    private fun assertFullyLaidOut(tag: String, result: TextLayoutResult) {
        assertFalse("$tag omitted or clipped text: ${result.layoutInput.text}", result.hasVisualOverflow)
        repeat(result.lineCount) { line ->
            val lineWidth = result.getLineRight(line) - result.getLineLeft(line)
            assertTrue("$tag line $line is wider than its layout", lineWidth <= result.size.width + 1f)
        }
    }

    @Composable
    private fun CompactLargeTextTuner(state: TunerUiState, font: ThemeFont) {
        CompositionLocalProvider(
            LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 2f),
        ) {
            TuneItAllTheme(appearance = AppearanceSettings(textScale = 130, font = font)) {
                Box(Modifier.requiredSize(width = 360.dp, height = 720.dp)) {
                    TunerScreen(
                        state = state,
                        onModeSelected = {},
                        onStringSelected = {},
                        onToggleFavorite = {},
                        onOpenLibrary = {},
                        onOpenSettings = {},
                        onOpenApplicationSettings = {},
                    )
                }
            }
        }
    }

    private fun state(): TunerUiState = TunerUiState(
        mode = TunerMode.MANUAL,
        tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard")),
        selectedString = 0,
        headstockLayout = HeadstockLayout.SPLIT_3_3,
        referencePitch = ReferencePitch(440.0),
        notation = NoteNotation.SHARPS,
        favoriteIds = emptySet(),
        customTunings = emptyList(),
        reading = null,
        microphoneGranted = true,
        microphonePermanentlyDenied = false,
        listening = false,
        referenceTonePlaying = false,
        error = null,
    )
}
