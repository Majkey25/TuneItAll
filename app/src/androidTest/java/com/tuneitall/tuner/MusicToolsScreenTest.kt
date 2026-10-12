package com.tuneitall.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.music.Chord
import com.tuneitall.tuner.music.ChordEvent
import com.tuneitall.tuner.music.ChordQuality
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.ArrangementMode
import com.tuneitall.tuner.music.arrangeForStandardE
import com.tuneitall.tuner.music.NoteEvent
import com.tuneitall.tuner.music.NoteRange
import com.tuneitall.tuner.music.SongAnalysisMode
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import com.tuneitall.tuner.ui.ChordTab
import com.tuneitall.tuner.ui.ChordUiState
import com.tuneitall.tuner.ui.ChordsScreen
import com.tuneitall.tuner.ui.AutoScrollScreen
import com.tuneitall.tuner.ui.TrainerScreen
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val ukulele = requireNotNull(TuningCatalog.byId("ukulele-standard"))
        var state by mutableStateOf(ChordUiState())
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning, ukulele),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = { state = state.copy(tab = it) },
                    onChordSelected = { state = state.copy(selectedChord = it) },
                    onTuningSelected = { state = state.copy(selectedTuningId = it) },
                    onTransposeChanged = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("chord_root_grid").assertIsDisplayed()
        compose.onNodeWithTag("chord_root_11").assertIsDisplayed()
        compose.onNodeWithTag("chord_root_9").performClick()
        compose.onNodeWithTag("chord_quality_minor").performScrollTo().performClick()

        compose.onNodeWithTag("selected_chord_label").assertTextEquals("Am")
        compose.onNodeWithTag("chord_diagram").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(Chord(9, ChordQuality.MINOR), state.selectedChord) }
        compose.onNodeWithTag("song_tuning").assertDoesNotExist()
        compose.onNodeWithTag("chord_tuning_selector").performScrollTo().performClick()
        compose.onNodeWithTag("chord_tuning_${ukulele.id}").performClick()
        compose.runOnIdle { assertEquals(ukulele.id, state.selectedTuningId) }
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
    fun accessibilitySettingsRequireDisclosureAcceptance() {
        var settingsOpened = false
        compose.setContent {
            TuneItAllTheme {
                AutoScrollScreen(
                    overlayAllowed = false,
                    accessibilityEnabled = false,
                    disclosureAccepted = false,
                    speed = 10,
                    onSpeedChanged = {},
                    onOpenOverlaySettings = {},
                    onDisclosureAccepted = {},
                    onOpenAccessibilitySettings = { settingsOpened = true },
                    onShowControls = {},
                )
            }
        }

        compose.onNodeWithTag("auto_scroll_accessibility_action").performClick()
        compose.onNodeWithTag("auto_scroll_disclosure").assertIsDisplayed()
        compose.runOnIdle { assertFalse(settingsOpened) }
        compose.onNodeWithTag("auto_scroll_disclosure_continue").performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun dismissingAccessibilityDisclosureDoesNotOpenSettings() {
        var settingsOpened = false
        compose.setContent {
            TuneItAllTheme {
                AutoScrollScreen(
                    overlayAllowed = false,
                    accessibilityEnabled = false,
                    disclosureAccepted = false,
                    speed = 10,
                    onSpeedChanged = {},
                    onOpenOverlaySettings = {},
                    onOpenAccessibilitySettings = { settingsOpened = true },
                    onShowControls = {},
                )
            }
        }

        compose.onNodeWithTag("auto_scroll_accessibility_action").performClick()
        compose.onNodeWithTag("auto_scroll_disclosure_cancel").performClick()

        compose.onNodeWithTag("auto_scroll_disclosure").assertDoesNotExist()
        compose.runOnIdle { assertFalse(settingsOpened) }
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
        compose.onNodeWithTag("choose_song").performScrollTo().assertTextEquals("Analyze another song")
        compose.onNodeWithTag("transpose_up").performScrollTo().assertIsDisplayed().performClick()

        compose.onNodeWithTag("current_song_chord").assertTextEquals("C♯")
        compose.runOnIdle { assertEquals(listOf(original), state.events) }
    }

    @Test
    fun songChordDiagramIsOptionalAndOffByDefault() {
        val ukulele = requireNotNull(TuningCatalog.byId("ukulele-standard"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val event = ChordEvent(0L, 4_000L, Chord(0, ChordQuality.MAJOR), 0.9)
        val state = ChordUiState(
            tab = ChordTab.SONG,
            selectedTuningId = ukulele.id,
            fileName = "test.wav",
            events = listOf(event),
            prepared = true,
            durationMillis = 4_000L,
            positionMillis = 1_000L,
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(ukulele),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = {},
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("chord_tuning_selector").assertDoesNotExist()
        compose.onNodeWithTag("song_tuning").assertTextEquals("${context.getString(R.string.instrument_guitar)} · ${tuning.name}")
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
        compose.onNode(
            hasTestTag("song_diagrams_toggle") and hasAnyAncestor(hasTestTag("current_song_chord_bar")),
        ).assertWidthIsEqualTo(48.dp).performClick()
        compose.onNode(
            hasTestTag("chord_diagram") and hasAnyAncestor(hasTestTag("current_song_chord_bar")),
        ).assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(context.getString(R.string.chord_diagram_description, "x, 3, 2, 0, 1, 0")),
            ),
        )
    }

    @Test
    fun songCurrentChordStaysInFixedBottomBar() {
        var shortLandscape by mutableStateOf(false)
        var normalPortrait by mutableStateOf(false)
        var state by mutableStateOf(ChordUiState(
            tab = ChordTab.SONG,
            fileName = "test.wav",
            events = listOf(ChordEvent(0L, 4_000L, Chord(1, ChordQuality.MAJOR_SEVENTH, 8), 0.9)),
            prepared = true,
            durationMillis = 4_000L,
            positionMillis = 1_000L,
        ))
        compose.setContent {
            TuneItAllTheme {
                CompositionLocalProvider(LocalDensity provides Density(
                    if (shortLandscape || normalPortrait) 1f else LocalDensity.current.density,
                    when { shortLandscape -> 2f; normalPortrait -> 1f; else -> 2.6f },
                )) {
                    Box(Modifier
                        .width(if (shortLandscape) 568.dp else 320.dp)
                        .height(when { shortLandscape -> 320.dp; normalPortrait -> 640.dp; else -> 568.dp })
                        .then(if (shortLandscape || normalPortrait) Modifier.consumeWindowInsets(WindowInsets.safeDrawing) else Modifier)
                        .testTag("song_test_viewport"),
                    ) {
                        ChordsScreen(
                            state = state,
                            tunings = listOf(tuning),
                            notation = NoteNotation.SHARPS,
                            catalog = catalog,
                            onTabSelected = {},
                            onChordSelected = {},
                            onTuningSelected = {},
                            onTransposeChanged = {},
                            onLoadSong = {},
                            onPlayPause = {},
                            onSeek = {},
                            onClearSong = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("current_song_chord_bar").assertIsDisplayed()
        compose.onNodeWithTag("current_song_chord").assertIsDisplayed().assertTextEquals("C♯maj7/G♯")
        compose.onNodeWithTag("song_diagrams_toggle").assertIsDisplayed()
        val labelBounds = compose.onNodeWithTag("current_song_chord").fetchSemanticsNode().boundsInRoot
        val toggleBounds = compose.onNodeWithTag("song_diagrams_toggle").fetchSemanticsNode().boundsInRoot
        assertFalse("Current chord $labelBounds overlaps diagram toggle $toggleBounds", labelBounds.overlaps(toggleBounds))

        compose.runOnIdle {
            state = state.copy(events = listOf(ChordEvent(0L, 4_000L, Chord(1, ChordQuality.MAJOR), 0.9)))
        }
        compose.onNodeWithTag("current_song_chord").assertTextEquals("C♯")
        compose.onNodeWithTag("song_diagrams_toggle").performClick()
        compose.onNode(hasTestTag("chord_diagram") and hasAnyAncestor(hasTestTag("current_song_chord_bar")))
            .assertIsDisplayed()
        assertCurrentChordBarFits(320f)
        compose.onNodeWithTag("chords_content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("current_song_chord_bar").assertIsDisplayed()
        compose.onNodeWithTag("song_diagrams_toggle").assertIsDisplayed()

        compose.runOnIdle { shortLandscape = true }
        val viewport = compose.onNodeWithTag("song_test_viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(568f, viewport.width, 1f)
        assertEquals(320f, viewport.height, 1f)
        assertCurrentChordBarFits(568f)
        compose.onNodeWithTag("current_song_chord").assertTextEquals("C♯")
        val contentBounds = compose.onNodeWithTag("chords_content").fetchSemanticsNode().boundsInRoot
        val fixedBarBounds = compose.onNodeWithTag("current_song_chord_bar").fetchSemanticsNode().boundsInRoot
        listOf("song_play_pause", "song_chord_0").forEach { tag ->
            if (tag == "song_chord_0") compose.onNodeWithTag("chord_timeline").performScrollTo()
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
            val bounds = node.boundsInRoot
            assertTrue("$tag is occluded by the fixed bar: $bounds", bounds.top >= contentBounds.top &&
                bounds.bottom <= contentBounds.bottom && !bounds.overlaps(fixedBarBounds))
            assertEquals("$tag is vertically clipped", node.size.height.toFloat(), bounds.height, 1f)
            assertEquals(fixedBarBounds, compose.onNodeWithTag("current_song_chord_bar").fetchSemanticsNode().boundsInRoot)
        }

        compose.runOnIdle {
            state = state.copy(events = listOf(ChordEvent(0L, 4_000L, Chord(1, ChordQuality.MAJOR_SEVENTH, 8), 0.9)))
        }
        compose.onNodeWithTag("song_diagrams_toggle").assertIsNotEnabled()
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
        assertCurrentChordBarFits(568f, diagramVisible = false)

        compose.runOnIdle {
            val events = listOf(ChordEvent(0L, 4_000L, Chord(6, ChordQuality.MINOR), 0.9))
            state = state.copy(events = events, arrangement = arrangeForStandardE(events.map { it.chord }, catalog, ArrangementMode.EXACT))
        }
        compose.onNodeWithTag("acoustic_shape").assertIsDisplayed()
        assertCurrentChordBarFits(568f, capoVisible = true)
        compose.runOnIdle { shortLandscape = false; normalPortrait = true }
        compose.onNodeWithTag("chord_diagram").assertHeightIsEqualTo(300.dp)
        assertCurrentChordBarFits(320f, capoVisible = true)

        compose.runOnIdle {
            state = state.copy(
                analysisMode = SongAnalysisMode.NOTES,
                events = listOf(NoteEvent(0L, 4_000L, 69, 0.9)),
                arrangement = null,
            )
        }
        compose.onNodeWithTag("current_song_note").assertIsDisplayed().assertTextEquals("A4")
        compose.onNodeWithTag("song_diagrams_toggle").assertDoesNotExist()
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
    }

    private fun assertCurrentChordBarFits(widthDp: Float, diagramVisible: Boolean = true, capoVisible: Boolean = false) {
        val viewport = compose.onNodeWithTag("song_test_viewport").fetchSemanticsNode().boundsInRoot
        val textTags = listOf("current_song_chord") + if (capoVisible) listOf("acoustic_shape") else emptyList()
        val tags = textTags + "song_diagrams_toggle" + if (diagramVisible) listOf("chord_diagram") else emptyList()
        tags.forEach { tag ->
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            val bounds = node.boundsInRoot
            assertTrue("$tag extends outside $viewport: $bounds", bounds.left >= viewport.left &&
                bounds.top >= viewport.top && bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
            assertEquals("$tag is horizontally clipped", node.size.width.toFloat(), bounds.width, 1f)
            assertEquals("$tag is vertically clipped", node.size.height.toFloat(), bounds.height, 1f)
        }
        textTags.forEach { tag ->
            compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(getResults(results))
                val layout = results.single()
                assertFalse("$tag text is clipped: size=${layout.size} constraints=${layout.layoutInput.constraints} " +
                    "lines=${layout.lineCount} heightOverflow=${layout.didOverflowHeight} widthOverflow=${layout.didOverflowWidth} " +
                    "fontSize=${layout.layoutInput.style.fontSize} lineHeight=${layout.layoutInput.style.lineHeight}", layout.hasVisualOverflow)
            }
        }
        val contentBounds = compose.onNodeWithTag("chords_content").fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded diagram leaves no usable scroll area: $contentBounds", contentBounds.height >= 48f * viewport.width / widthDp)
    }

    @Test
    fun songShowsStandardECapoAndShapeInFixedBar() {
        val ukulele = requireNotNull(TuningCatalog.byId("ukulele-standard"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val events = listOf(
            ChordEvent(0L, 1_000L, Chord(6, ChordQuality.MINOR), 0.9),
            ChordEvent(1_000L, 2_000L, Chord(11, ChordQuality.MAJOR), 0.9),
            ChordEvent(2_000L, 3_000L, Chord(2, ChordQuality.MAJOR), 0.9),
        )
        val state = ChordUiState(
            tab = ChordTab.SONG,
            selectedTuningId = ukulele.id,
            fileName = "test.wav",
            events = events,
            arrangementMode = ArrangementMode.EXACT,
            arrangement = arrangeForStandardE(events.map { it.chord }, catalog, ArrangementMode.EXACT),
            durationMillis = 3_000L,
            positionMillis = 500L,
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning, ukulele),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = {},
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("current_song_chord").assertTextEquals("F♯m")
        compose.onNodeWithTag("chord_tuning_selector").assertDoesNotExist()
        compose.onNodeWithTag("song_tuning").assertTextEquals("${context.getString(R.string.instrument_guitar)} · ${tuning.name}")
        compose.onNodeWithTag("acoustic_shape").assertTextEquals("Capo 2 · play Em")
        compose.onNodeWithTag("arrangement_exact").assertIsDisplayed()
    }

    @Test
    fun songTimelineFollowsTheActiveChord() {
        val events = List(20) { index ->
            ChordEvent(
                startMillis = index * 1_000L,
                endMillis = (index + 1) * 1_000L,
                chord = Chord(index % 12, ChordQuality.MAJOR),
                confidence = 0.9,
            )
        }
        val state = ChordUiState(
            tab = ChordTab.SONG,
            fileName = "test.wav",
            events = events,
            prepared = true,
            durationMillis = 20_000L,
            positionMillis = 18_500L,
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = {},
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        // Reveal the timeline vertically without moving its horizontal position.
        compose.onNodeWithTag("chord_timeline").performScrollTo()
        compose.onNodeWithTag("song_chord_18").assertIsDisplayed()
    }

    @Test
    fun notesModeShowsRangesAndHidesChordDiagrams() {
        var state by mutableStateOf(
            ChordUiState(
                tab = ChordTab.SONG,
                analysisMode = SongAnalysisMode.NOTES,
                noteRange = NoteRange.VIOLIN,
                fileName = "melody.wav",
                events = listOf(NoteEvent(0L, 2_000L, 69, 0.9)),
                prepared = true,
                durationMillis = 2_000L,
                positionMillis = 500L,
            ),
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = {},
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onAnalysisModeSelected = { state = state.copy(analysisMode = it) },
                    onNoteRangeSelected = { state = state.copy(noteRange = it) },
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("song_mode_notes").assertIsDisplayed()
        compose.onNodeWithTag("song_note_range_violin").assertIsDisplayed()
        compose.onNodeWithTag("song_diagrams_toggle").assertDoesNotExist()
        compose.onNodeWithTag("chord_tuning_selector").assertDoesNotExist()
        compose.onNodeWithTag("song_tuning").assertDoesNotExist()
        compose.onNodeWithTag("current_song_note").assertTextEquals("A4")
        compose.onNodeWithTag("song_mode_power").performClick()
        compose.runOnIdle { assertEquals(SongAnalysisMode.POWER, state.analysisMode) }
    }

    @Test
    fun powerModeHidesUnreviewedChordDiagrams() {
        val state = ChordUiState(
            tab = ChordTab.SONG,
            analysisMode = SongAnalysisMode.POWER,
            fileName = "riff.wav",
            events = listOf(ChordEvent(0L, 2_000L, Chord(4, ChordQuality.POWER), 0.9)),
            durationMillis = 2_000L,
            positionMillis = 500L,
        )
        compose.setContent {
            TuneItAllTheme {
                ChordsScreen(
                    state = state,
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onTabSelected = {},
                    onChordSelected = {},
                    onTuningSelected = {},
                    onTransposeChanged = {},
                    onAnalysisModeSelected = {},
                    onNoteRangeSelected = {},
                    onLoadSong = {},
                    onPlayPause = {},
                    onSeek = {},
                    onClearSong = {},
                )
            }
        }

        compose.onNodeWithTag("song_diagrams_toggle").assertDoesNotExist()
        compose.onNodeWithTag("chord_tuning_selector").assertDoesNotExist()
        compose.onNodeWithTag("song_tuning").assertDoesNotExist()
        compose.onNodeWithTag("current_song_chord").assertTextEquals("E5")
    }

    @Test
    fun trainerQuizRecordsOneAnswerAndKeepsTheAnswerHiddenUntilSelection() {
        var recorded: Boolean? = null
        var records = 0
        compose.setContent {
            TuneItAllTheme {
                TrainerScreen(
                    stats = TrainerStats(),
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onRecord = { recorded = it; records++ },
                    onReset = {},
                    noteSets = com.tuneitall.tuner.music.NoteTrainingSets(),
                    onNoteSetsChanged = {},
                )
            }
        }

        compose.onNodeWithTag("trainer_mode_quiz").performClick()
        compose.onNodeWithTag("chord_diagram").assertDoesNotExist()
        compose.onNodeWithTag("chord_diagram_label").assertDoesNotExist()
        val choices = compose.onAllNodes(SemanticsMatcher("Chord answer") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("trainer_answer_") == true
        })
        choices[0].performClick()

        compose.onNodeWithTag("trainer_feedback").assertIsDisplayed()
        choices[0].assertIsNotEnabled()
        compose.onNodeWithTag("trainer_play").assertIsDisplayed()
        compose.onNodeWithTag("trainer_next_question").assertIsDisplayed()
        val feedback = compose.onNodeWithTag("trainer_feedback").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        compose.runOnIdle {
            assertEquals(feedback.startsWith("Correct."), recorded)
            assertEquals(1, records)
        }
        compose.onNodeWithTag("trainer_next_question").performClick()
        compose.onNodeWithTag("trainer_feedback").assertDoesNotExist()
    }

    @Test
    fun noteTrainerRecordsOneAnswerWithoutRevealingItFirst() {
        var recorded: Boolean? = null
        compose.setContent {
            TuneItAllTheme {
                TrainerScreen(
                    stats = TrainerStats(),
                    tunings = listOf(tuning),
                    notation = NoteNotation.SHARPS,
                    catalog = catalog,
                    onRecord = { recorded = it },
                    onReset = {},
                    noteSets = com.tuneitall.tuner.music.NoteTrainingSets(),
                    onNoteSetsChanged = {},
                )
            }
        }

        compose.onNodeWithTag("trainer_exercise_notes").performClick()
        compose.onNodeWithTag("trainer_mode_quiz").performClick()
        compose.onNodeWithTag("trainer_note_feedback").assertDoesNotExist()
        compose.onNodeWithTag("trainer_note_play").assertIsDisplayed()
        compose.onNodeWithTag("trainer_note_answer_0").performClick()

        compose.onNodeWithTag("trainer_note_feedback").assertIsDisplayed()
        val feedback = compose.onNodeWithTag("trainer_note_feedback").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        compose.runOnIdle {
            assertEquals(feedback.startsWith("Correct."), recorded)
        }
    }
}
