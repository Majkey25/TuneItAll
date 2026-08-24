package com.tuneitall.tuner

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import com.tuneitall.tuner.audio.DetectionSensitivity
import com.tuneitall.tuner.audio.AudioInputCapabilities
import com.tuneitall.tuner.audio.AudioInputSource
import com.tuneitall.tuner.audio.MetronomePlayer
import com.tuneitall.tuner.audio.ResponseMode
import com.tuneitall.tuner.audio.TunerAudioSettings
import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.MidiNote
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.model.TuningPreset
import com.tuneitall.tuner.metronome.Bpm
import com.tuneitall.tuner.metronome.MetronomeSettings
import com.tuneitall.tuner.metronome.MetronomeSound
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.UserPreferences
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.tuner.TunerReading
import com.tuneitall.tuner.ui.AboutScreen
import com.tuneitall.tuner.ui.AppBottomBar
import com.tuneitall.tuner.ui.CustomTuningScreen
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeError
import com.tuneitall.tuner.ui.MetronomeUiState
import com.tuneitall.tuner.ui.MetronomeViewModel
import com.tuneitall.tuner.ui.PrimaryDestination
import com.tuneitall.tuner.ui.ResolvedChipColors
import com.tuneitall.tuner.ui.ResolvedNavigationColors
import com.tuneitall.tuner.ui.SettingsScreen
import com.tuneitall.tuner.ui.SettingsSection
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TunerViewModel
import com.tuneitall.tuner.ui.TuningLibraryScreen
import com.tuneitall.tuner.ui.bottomNavigationSelectedColors
import com.tuneitall.tuner.ui.modeSelectorSelectedColors
import com.tuneitall.tuner.ui.metronomeErrorResource
import com.tuneitall.tuner.ui.secondaryHeaderContentColor
import com.tuneitall.tuner.ui.components.CentsRail
import com.tuneitall.tuner.ui.components.Headstock
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import com.tuneitall.tuner.ui.theme.ThemeMode
import com.tuneitall.tuner.ui.theme.resolveDarkTheme
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
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
    fun themePreferenceDefaultsRoundTripsAndRejectsCorruptValues() {
        val preferences = UserPreferences(context)
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)

        preferences.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, UserPreferences(context).themeMode)

        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", "SEPIA")
            .commit()
        assertEquals(ThemeMode.SYSTEM, UserPreferences(context).themeMode)

        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putInt("theme_mode", 1)
            .commit()
        assertEquals(ThemeMode.SYSTEM, UserPreferences(context).themeMode)
    }

    @Test
    fun metronomePreferencesRoundTripAndRejectCorruptValues() {
        val expected = MetronomeSettings(
            bpm = Bpm(137),
            numerator = 5,
            denominator = 8,
            subdivision = 3,
            accentEvery = 5,
            volume = 42,
            countIn = 2,
            sound = MetronomeSound.RIM,
        )
        UserPreferences(context).apply {
            metronomeSettings = expected
            metronomeMuted = true
        }

        assertEquals(expected, UserPreferences(context).metronomeSettings)
        assertTrue(UserPreferences(context).metronomeMuted)

        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putString("metronome_bpm", "fast")
            .putInt("metronome_numerator", 13)
            .putInt("metronome_denominator", 3)
            .putInt("metronome_subdivision", 5)
            .putInt("metronome_accent_every", 1)
            .putInt("metronome_volume", 101)
            .putInt("metronome_count_in", 3)
            .putString("metronome_sound", "BELL")
            .putString("metronome_muted", "yes")
            .commit()

        assertEquals(MetronomeSettings(), UserPreferences(context).metronomeSettings)
        assertFalse(UserPreferences(context).metronomeMuted)
    }

    @Test
    fun metronomeViewModelRestoresValidatedSettingsWithoutStarting() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        MetronomeViewModel(application).apply {
            setBpm(211)
            setNumerator(7)
            setDenominator(16)
            setSubdivision(3)
            setAccentEvery(5)
            setSound(MetronomeSound.CLICK)
            setVolume(64)
            setCountIn(4)
            setMuted(true)
        }

        val restored = MetronomeViewModel(application).uiState.value
        assertEquals(211, restored.settings.bpm.value)
        assertEquals(7, restored.settings.numerator)
        assertEquals(16, restored.settings.denominator)
        assertEquals(3, restored.settings.subdivision)
        assertEquals(5, restored.settings.accentEvery)
        assertEquals(MetronomeSound.CLICK, restored.settings.sound)
        assertEquals(64, restored.settings.volume)
        assertEquals(4, restored.settings.countIn)
        assertTrue(restored.muted)
        assertFalse(restored.starting)
        assertFalse(restored.playing)
        assertFalse(restored.stopping)
    }

    @Test
    fun metronomePrecisionPanelExposesAndConnectsEveryControl() {
        var state by mutableStateOf(MetronomeUiState())
        var tapCount = 0
        var startCount = 0
        composeRule.setContent {
            TuneItAllTheme {
                MetronomeScreen(
                    state = state,
                    phaseProvider = { 0.0 },
                    onBpmChange = { state = state.copy(settings = state.settings.copy(bpm = Bpm(it))) },
                    onNumeratorChange = { state = state.copy(settings = state.settings.copy(numerator = it)) },
                    onDenominatorChange = { state = state.copy(settings = state.settings.copy(denominator = it)) },
                    onSubdivisionChange = { state = state.copy(settings = state.settings.copy(subdivision = it)) },
                    onAccentEveryChange = { state = state.copy(settings = state.settings.copy(accentEvery = it)) },
                    onTap = { tapCount++ },
                    onStart = { startCount++ },
                    onStop = {},
                    onSoundChange = { state = state.copy(settings = state.settings.copy(sound = it)) },
                    onVolumeChange = { state = state.copy(settings = state.settings.copy(volume = it)) },
                    onMutedChange = { state = state.copy(muted = it) },
                    onCountInChange = { state = state.copy(settings = state.settings.copy(countIn = it)) },
                    onOpenSettings = {},
                )
            }
        }

        val bpmInput = composeRule.onNodeWithTag("metronome_bpm_input")
        bpmInput.performTextClearance()
        bpmInput.performTextInput("137")
        bpmInput.performImeAction()
        assertEquals(137, state.settings.bpm.value)
        composeRule.onNodeWithTag("metronome_bpm_decrease").performClick()
        composeRule.onNodeWithTag("metronome_bpm_increase").performClick()
        assertEquals(137, state.settings.bpm.value)
        composeRule.onNodeWithTag("metronome_bpm_increase").performTouchInput {
            down(center)
            advanceEventTime(900L)
            up()
        }
        assertTrue(state.settings.bpm.value >= 140)

        composeRule.onNodeWithText("Precision Panel").assertDoesNotExist()
        composeRule.onNodeWithText("Tempo").assertDoesNotExist()
        composeRule.onNodeWithText("Ready").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_beat_dots").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_numerator_strip").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_denominator_strip").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_subdivision_strip").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_accent_strip").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_rhythm_summary")
            .assertTextEquals("4/4 · 1× · Accent off")
        composeRule.onNodeWithTag("metronome_tap").performClick()
        composeRule.onNodeWithTag("metronome_start_stop").performClick()
        assertEquals(1, tapCount)
        assertEquals(1, startCount)

        composeRule.onNodeWithTag("metronome_rhythm_summary").performClick()

        listOf(1, 12).forEach { value ->
            composeRule.onNodeWithTag("metronome_numerator_strip").performScrollTo()
            composeRule.onNodeWithTag("metronome_numerator_$value").performScrollTo().performClick()
            assertEquals(value, state.settings.numerator)
        }
        listOf(2, 4, 8, 16).forEach { value ->
            composeRule.onNodeWithTag("metronome_denominator_strip").performScrollTo()
            composeRule.onNodeWithTag("metronome_denominator_$value").performScrollTo().performClick()
            assertEquals(value, state.settings.denominator)
        }
        listOf(1, 2, 3, 4).forEach { value ->
            composeRule.onNodeWithTag("metronome_subdivision_strip").performScrollTo()
            composeRule.onNodeWithTag("metronome_subdivision_$value").performScrollTo().performClick()
            assertEquals(value, state.settings.subdivision)
        }

        listOf("off" to null, "2" to 2, "3" to 3, "5" to 5).forEach { (tag, value) ->
            composeRule.onNodeWithTag("metronome_accent_strip").performScrollTo()
            composeRule.onNodeWithTag("metronome_accent_$tag").performScrollTo().performClick()
            assertEquals(value, state.settings.accentEvery)
        }
        MetronomeSound.entries.forEach { sound ->
            composeRule.onNodeWithTag("metronome_sound_${sound.name.lowercase(Locale.ROOT)}").performClick()
            assertEquals(sound, state.settings.sound)
        }
        listOf(0f, 42f, 100f).forEach { volume ->
            composeRule.onNodeWithTag("metronome_volume")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(volume) }
            assertEquals(volume.toInt(), state.settings.volume)
        }
        composeRule.onNodeWithTag("metronome_mute").performClick()
        assertTrue(state.muted)
        composeRule.onNodeWithTag("metronome_mute").performClick()
        assertFalse(state.muted)
        listOf(0, 1, 2, 4).forEach { countIn ->
            composeRule.onNodeWithTag("metronome_count_in_strip").performScrollTo()
            composeRule.onNodeWithTag("metronome_count_in_$countIn").performScrollTo().performClick()
            assertEquals(countIn, state.settings.countIn)
        }
        composeRule.onNodeWithTag("metronome_settings_done").performScrollTo().performClick()
        composeRule.onNodeWithTag("metronome_rhythm_summary")
            .assertTextEquals("12/16 · 4× · Accent every 5")
    }

    @Test
    fun metronomeStatusAndTypedErrorsUseLocalizedResources() {
        var state by mutableStateOf(MetronomeUiState())
        composeRule.setContent {
            TuneItAllTheme {
                MetronomeScreen(
                    state = state,
                    phaseProvider = { 0.0 },
                    onBpmChange = {},
                    onTap = {},
                    onStart = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }

        listOf(
            MetronomeUiState(starting = true) to ("Starting…" to "Starting…"),
            MetronomeUiState(playing = true) to ("Playing" to "Stop"),
            MetronomeUiState(stopping = true) to ("Stopping…" to "Stopping…"),
            MetronomeUiState(error = MetronomeError.OUTPUT_UNAVAILABLE) to
                ("Metronome audio is unavailable." to "Start"),
        ).forEach { (next, labels) ->
            composeRule.runOnIdle { state = next }
            composeRule.onNodeWithTag("metronome_status").assertTextEquals(labels.first)
            composeRule.onNodeWithTag("metronome_start_stop_label", useUnmergedTree = true)
                .assertTextEquals(labels.second)
        }
        composeRule.runOnIdle { state = MetronomeUiState() }
        composeRule.onNodeWithTag("metronome_status").assertDoesNotExist()
        composeRule.onNodeWithTag("metronome_start_stop_label", useUnmergedTree = true)
            .assertTextEquals("Start")

        val czechConfiguration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("cs"))
        }
        val czech = context.createConfigurationContext(czechConfiguration)
        listOf(
            MetronomeError.OUTPUT_UNAVAILABLE to ("Metronome audio is unavailable." to "Zvuk metronomu není dostupný."),
            MetronomeError.PLAYBACK_STOPPED to
                ("Metronome audio stopped unexpectedly." to "Zvuk metronomu se neočekávaně zastavil."),
            MetronomeError.STOP_FAILED to
                ("Metronome audio could not stop cleanly." to "Zvuk metronomu se nepodařilo bezpečně zastavit."),
        ).forEach { (error, labels) ->
            assertEquals(labels.first, context.getString(metronomeErrorResource(error)))
            assertEquals(labels.second, czech.getString(metronomeErrorResource(error)))
        }
    }

    @Test
    fun metronomePendulumUsesAudioPhaseWithoutChangingBounds() {
        var phase by mutableStateOf(-1.0)
        val phaseReads = AtomicInteger()
        composeRule.setContent {
            TuneItAllTheme {
                Box(Modifier.requiredSize(width = 360.dp, height = 320.dp)) {
                    MetronomeScreen(
                        state = MetronomeUiState(playing = true),
                        phaseProvider = {
                            phaseReads.incrementAndGet()
                            phase
                        },
                        onBpmChange = {},
                        onTap = {},
                        onStart = {},
                        onStop = {},
                        onOpenSettings = {},
                    )
                }
            }
        }

        fun pendulum() = composeRule.onNodeWithTag("metronome_pendulum").fetchSemanticsNode()
        composeRule.waitUntil(2_000L) { phaseReads.get() >= 2 }
        val initial = pendulum()
        val firstReadCount = phaseReads.get()
        composeRule.runOnIdle { phase = 0.0 }
        composeRule.waitUntil(2_000L) { phaseReads.get() > firstReadCount }
        assertEquals(initial.boundsInRoot, pendulum().boundsInRoot)
        val secondReadCount = phaseReads.get()
        composeRule.runOnIdle { phase = 1.0 }
        composeRule.waitUntil(2_000L) { phaseReads.get() > secondReadCount }
        assertEquals(initial.boundsInRoot, pendulum().boundsInRoot)
    }

    @Test
    fun metronomePanelFitsCompactLargeFontWithReadableLightAndDarkColors() {
        var darkTheme by mutableStateOf(false)
        val screenState = MetronomeUiState(playing = true)
        var background = Color.Unspecified
        var primary = Color.Unspecified
        var safeDrawingTop = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 1.3f),
            ) {
                TuneItAllTheme(darkTheme = darkTheme) {
                    val colors = MaterialTheme.colorScheme
                    val density = LocalDensity.current
                    val safeTop = WindowInsets.safeDrawing.getTop(density)
                    SideEffect {
                        background = colors.background
                        primary = colors.primary
                        safeDrawingTop = safeTop
                    }
                    Box(Modifier.requiredSize(width = 360.dp, height = 720.dp)) {
                        MetronomeScreen(
                            state = screenState,
                            phaseProvider = { 0.0 },
                            onBpmChange = {},
                            onTap = {},
                            onStart = {},
                            onStop = {},
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("metronome_header").fetchSemanticsNode().boundsInRoot
        assertTrue("safe drawing top inset is zero", safeDrawingTop > 0)
        assertTrue("metronome header overlaps system bar", header.top >= safeDrawingTop)
        listOf(
            "metronome_settings" to null,
            "metronome_bpm_decrease" to null,
            "metronome_bpm_increase" to null,
            "metronome_tap" to null,
            "metronome_start_stop" to null,
            "metronome_rhythm_summary" to null,
        ).forEach { (tag, verticalTarget) ->
            verticalTarget?.let { composeRule.onNodeWithTag(it).performScrollTo() }
            val bounds = composeRule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag narrower than 48 dp", bounds.width / context.resources.displayMetrics.density >= 48f)
            assertTrue(
                "$tag shorter than 48 dp: ${bounds.height / context.resources.displayMetrics.density}, $bounds",
                bounds.height / context.resources.displayMetrics.density >= 48f,
            )
            assertTrue("$tag clipped left", bounds.left >= root.left)
            assertTrue("$tag clipped right", bounds.right <= root.right)
            assertTrue("$tag clipped top", bounds.top >= root.top)
            assertTrue("$tag clipped bottom", bounds.bottom <= root.bottom)
        }

        fun textColor(tag: String): Color {
            var color = Color.Unspecified
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                    val results = mutableListOf<TextLayoutResult>()
                    assertTrue(getResults(results))
                    color = results.single().layoutInput.style.color
                }
            return color
        }

        fun assertThemeContrast() {
            val statusColor = textColor("metronome_status")
            val actionColor = textColor("metronome_start_stop_label")
            composeRule.runOnIdle {
                assertTrue(contrastRatio(statusColor, background) >= 4.5f)
                assertTrue(contrastRatio(actionColor, primary) >= 4.5f)
                assertFalse(statusColor == primary)
            }
        }

        assertThemeContrast()
        composeRule.runOnIdle { darkTheme = true }
        assertThemeContrast()
    }

    @Test
    fun metronomeSettingsIconIsCodeNativeBoundedAndTextFree() {
        composeRule.setContent {
            TuneItAllTheme {
                MetronomeScreen(
                    state = MetronomeUiState(),
                    phaseProvider = { 0.0 },
                    onBpmChange = {},
                    onTap = {},
                    onStart = {},
                    onStop = {},
                    onOpenSettings = {},
                )
            }
        }

        val button = composeRule.onNodeWithTag("metronome_settings").fetchSemanticsNode().boundsInRoot
        val icon = composeRule.onNodeWithTag("metronome_settings_icon", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(icon.left >= button.left)
        assertTrue(icon.right <= button.right)
        assertTrue(icon.top >= button.top)
        assertTrue(icon.bottom <= button.bottom)
        assertEquals(24f, icon.width / context.resources.displayMetrics.density, 0.5f)
        assertEquals(24f, icon.height / context.resources.displayMetrics.density, 0.5f)
        composeRule.onNodeWithText("Sound").assertDoesNotExist()
    }

    @Test
    fun metronomeNavigationStopReturnsBeforeBlockedWorkerExits() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val beforeCreate = CountDownLatch(1)
        val allowCreate = CountDownLatch(1)
        val player = MetronomePlayer(
            outputFactory = { error("stopped worker must not create output") },
            setAudioThreadPriority = {},
            startupTimeoutMillis = 500L,
            stopTimeoutMillis = 500L,
            fadeDrainTimeoutMillis = 10L,
            beforeCreate = {
                beforeCreate.countDown()
                allowCreate.await()
            },
        )
        val metronomeViewModel = MetronomeViewModel(application, player)
        val tunerViewModel = TunerViewModel(application)
        composeRule.setContent {
            TuneItAllTheme {
                TuneItAllApp(
                    state = state(),
                    viewModel = tunerViewModel,
                    openApplicationSettings = {},
                    metronomeViewModel = metronomeViewModel,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Metronome").performClick()
        val navigateToTuner = requireNotNull(
            composeRule.onNodeWithContentDescription("Tuner")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action,
        )
        composeRule.onNodeWithTag("metronome_start_stop").performClick()
        assertTrue(beforeCreate.await(1, TimeUnit.SECONDS))

        val callbackElapsedMillis = AtomicLong(Long.MAX_VALUE)
        val callbackReturned = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            val startedAt = SystemClock.elapsedRealtime()
            navigateToTuner()
            callbackElapsedMillis.set(SystemClock.elapsedRealtime() - startedAt)
            callbackReturned.countDown()
        }

        assertTrue(callbackReturned.await(1L, TimeUnit.SECONDS))
        assertTrue(
            "navigation blocked for ${callbackElapsedMillis.get()} ms",
            callbackElapsedMillis.get() < 150L,
        )
        val stoppingDeadline = SystemClock.elapsedRealtime() + 1_000L
        while (!metronomeViewModel.uiState.value.stopping && SystemClock.elapsedRealtime() < stoppingDeadline) {
            Thread.sleep(10L)
        }
        assertTrue(metronomeViewModel.uiState.value.stopping)
        assertTrue(player.status.running)
        assertTrue(player.status.stopping)

        allowCreate.countDown()
        val stoppedDeadline = SystemClock.elapsedRealtime() + 1_500L
        while (metronomeViewModel.uiState.value.stopping && SystemClock.elapsedRealtime() < stoppedDeadline) {
            Thread.sleep(10L)
        }
        assertFalse(metronomeViewModel.uiState.value.stopping)
        assertFalse(player.status.running)
    }

    @Test
    fun appShellNavigatesAllDestinationsAndReturnsSettingsToItsSource() {
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            TuneItAllTheme {
                TuneItAllApp(
                    state = state(),
                    viewModel = viewModel,
                    openApplicationSettings = {},
                )
            }
        }

        listOf("Tuner", "Metronome", "Chords", "Library", "Trainer").forEach { label ->
            composeRule.onNodeWithContentDescription(label, substring = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Tuner").assertIsSelected()
        composeRule.onNodeWithContentDescription("Chords").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("chords_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Trainer").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("trainer_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Metronome").performClick()
        composeRule.onNodeWithTag("metronome_screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Metronome").assertIsSelected()
        composeRule.onNodeWithTag("metronome_settings").performClick()
        composeRule.onNodeWithTag("settings_section_metronome").assertIsSelected()
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.onNodeWithTag("metronome_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tuner").performClick()
        composeRule.onNodeWithContentDescription("Tuner").assertIsSelected()
        composeRule.onNodeWithTag("tuner_settings").performClick()
        composeRule.onNodeWithTag("settings_section_tuner").assertIsSelected()
        composeRule.onNodeWithTag("back_button").performClick()
        composeRule.onNodeWithContentDescription("Tuner").assertIsSelected()
    }

    @Test
    fun bottomDestinationsUseBoundedVectorIconsWithoutTextGlyphs() {
        composeRule.setContent {
            TuneItAllTheme {
                AppBottomBar(selected = PrimaryDestination.TUNER, onSelect = {})
            }
        }

        listOf("tuner", "metronome", "chords", "library", "trainer").forEach { destination ->
            val item = composeRule.onNodeWithTag("bottom_item_$destination", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val icon = composeRule.onNodeWithTag("bottom_icon_$destination", useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(icon.left >= item.left)
            assertTrue(icon.right <= item.right)
            assertTrue(icon.top >= item.top)
            assertTrue(icon.bottom <= item.bottom)
            assertEquals(24f, icon.width / context.resources.displayMetrics.density, 0.5f)
            assertEquals(24f, icon.height / context.resources.displayMetrics.density, 0.5f)
        }
        listOf("◉", "♩", "♬", "≡", "✓").forEach { glyph ->
            composeRule.onNodeWithText(glyph, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun bottomDestinationsFitCompactWidthAtLargeFontWithMinimumTargets() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 1.3f),
            ) {
                TuneItAllTheme {
                    Box(Modifier.requiredSize(width = 360.dp, height = 96.dp)) {
                        AppBottomBar(
                            selected = PrimaryDestination.TUNER,
                            onSelect = {},
                        )
                    }
                }
            }
        }

        val destinations = listOf("tuner", "metronome", "chords", "library", "trainer")
        val labelBounds = destinations.map { destination ->
            val item = composeRule.onNodeWithTag("bottom_item_$destination", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val labelNode = composeRule.onNodeWithTag("bottom_label_$destination", useUnmergedTree = true)
                .assertIsDisplayed()
            val label = labelNode.fetchSemanticsNode().boundsInRoot
            assertTrue(item.width / context.resources.displayMetrics.density >= 48f)
            assertTrue(item.height / context.resources.displayMetrics.density >= 48f)
            assertTrue(label.left >= item.left)
            assertTrue(label.right <= item.right)
            assertTrue(label.top >= item.top)
            assertTrue(label.bottom <= item.bottom)
            labelNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(getResults(results))
                val result = results.single()
                assertFalse("$destination label overflowed vertically", result.didOverflowHeight)
                val lineWidth = result.getLineRight(0) - result.getLineLeft(0)
                assertTrue(
                    "$destination label overflowed horizontally: text=$lineWidth, layout=${result.size.width}",
                    lineWidth <= result.size.width + 1f,
                )
            }
            label
        }
        labelBounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        composeRule.onNodeWithContentDescription("Metronome").assertIsEnabled()
    }

    @Test
    fun settingsPersistsThemeAndRecomposesExactMaterialColors() {
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext())
        var background = Color.Unspecified
        var accent = Color.Unspecified
        var selectedContainer = Color.Unspecified
        var primaryFixed = Color.Unspecified
        composeRule.setContent {
            val currentState by viewModel.uiState.collectAsState()
            TuneItAllTheme(darkTheme = resolveDarkTheme(currentState.themeMode, systemDark = false)) {
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    background = colorScheme.background
                    accent = colorScheme.primary
                    selectedContainer = colorScheme.secondaryContainer
                    primaryFixed = colorScheme.primaryFixed
                }
                SettingsScreen(
                    state = currentState,
                    onThemeModeChanged = viewModel::setThemeMode,
                    onReferencePitchChanged = {},
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onAudioSettingsChanged = {},
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color(0xFFFAF9F6), background)
            assertEquals(Color(0xFF63D17A), accent)
            assertEquals(Color(0xFF63D17A), selectedContainer)
            assertEquals(Color(0xFF63D17A), primaryFixed)
        }
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithText("Dark").assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(ThemeMode.DARK, UserPreferences(context).themeMode)
            assertEquals(Color(0xFF101010), background)
            assertEquals(Color(0xFF63D17A), accent)
            assertEquals(Color(0xFF63D17A), selectedContainer)
            assertEquals(Color(0xFF63D17A), primaryFixed)
        }
    }

    @Test
    fun selectedComponentColorsUseGreenAndMeetContrastInBothThemes() {
        var darkTheme by mutableStateOf(false)
        var modeColors: ResolvedChipColors? = null
        var navigationColors: ResolvedNavigationColors? = null
        var primary = Color.Unspecified
        var onPrimary = Color.Unspecified
        var background = Color.Unspecified
        var onBackground = Color.Unspecified
        composeRule.setContent {
            TuneItAllTheme(darkTheme = darkTheme) {
                val resolvedModeColors = modeSelectorSelectedColors()
                val resolvedNavigationColors = bottomNavigationSelectedColors()
                val colorScheme = MaterialTheme.colorScheme
                SideEffect {
                    modeColors = resolvedModeColors
                    navigationColors = resolvedNavigationColors
                    primary = colorScheme.primary
                    onPrimary = colorScheme.onPrimary
                    background = colorScheme.background
                    onBackground = colorScheme.onBackground
                }
            }
        }

        fun assertResolvedColors() {
            val mode = requireNotNull(modeColors)
            val navigation = requireNotNull(navigationColors)
            assertEquals(primary, mode.container)
            assertEquals(onPrimary, mode.content)
            assertEquals(primary, navigation.indicator)
            assertEquals(onPrimary, navigation.icon)
            assertEquals(onBackground, navigation.text)
            assertTrue(contrastRatio(navigation.text, background) >= 4.5f)
            assertTrue(contrastRatio(navigation.icon, navigation.indicator) >= 4.5f)
            assertTrue(contrastRatio(mode.content, mode.container) >= 4.5f)
        }

        composeRule.runOnIdle {
            assertResolvedColors()
            darkTheme = true
        }
        composeRule.runOnIdle { assertResolvedColors() }
    }

    @Test
    fun secondaryHeaderUsesReadableNeutralTextInLightTheme() {
        var backColor = Color.Unspecified
        var primary = Color.Unspecified
        var onBackground = Color.Unspecified
        composeRule.setContent {
            TuneItAllTheme(darkTheme = false) {
                val resolvedBackColor = secondaryHeaderContentColor()
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    backColor = resolvedBackColor
                    primary = colors.primary
                    onBackground = colors.onBackground
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(onBackground, backColor)
            assertFalse(backColor == primary)
        }
    }

    @Test
    fun lightInTuneReadoutKeepsTextNeutralAndGreenForComponents() {
        val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard-c-sharp"))
        val target = tuning.notesLowToHigh.first()
        var background = Color.Unspecified
        var onBackground = Color.Unspecified
        var primary = Color.Unspecified
        var onPrimary = Color.Unspecified
        composeRule.setContent {
            TuneItAllTheme(darkTheme = false) {
                val colors = MaterialTheme.colorScheme
                SideEffect {
                    background = colors.background
                    onBackground = colors.onBackground
                    primary = colors.primary
                    onPrimary = colors.onPrimary
                }
                TunerScreen(
                    state = state().copy(
                        tuning = tuning,
                        reading = TunerReading(target, target, 69.30, 0.0, inTune = true),
                        tuningConfirmed = true,
                    ),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        fun textColor(tag: String): Color {
            var color = Color.Unspecified
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                    val results = mutableListOf<TextLayoutResult>()
                    assertTrue(getResults(results))
                    color = results.single().layoutInput.style.color
                }
            return color
        }

        val textColors = listOf(
            textColor("detected_note_letter"),
            textColor("detected_note_accidental"),
            textColor("detected_note_octave"),
            textColor("signed_cents"),
            textColor("tuner_status_tuning"),
            textColor("tuner_status_mode"),
            textColor("tuner_status_reference"),
        ).toMutableList()
        var instructionColor = Color.Unspecified
        composeRule.onNodeWithText("Tap a string to hear its reference tone", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                val results = mutableListOf<TextLayoutResult>()
                assertTrue(getResults(results))
                instructionColor = results.single().layoutInput.style.color
            }
        textColors += instructionColor

        composeRule.runOnIdle {
            textColors.forEach { color ->
                assertEquals(onBackground, color)
                assertTrue(contrastRatio(color, background) >= 4.5f)
            }
            assertEquals(Color(0xFF63D17A), primary)
            assertTrue(contrastRatio(onPrimary, primary) >= 4.5f)
        }
    }

    @Test
    fun resolvedThemeUpdatesSystemBarIconAppearance() {
        var darkTheme by mutableStateOf(false)
        lateinit var activity: Activity
        composeRule.setContent {
            activity = requireNotNull(LocalActivity.current)
            UpdateSystemBarAppearance(darkTheme)
        }

        composeRule.runOnIdle {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertTrue(controller.isAppearanceLightStatusBars)
            assertTrue(controller.isAppearanceLightNavigationBars)
            darkTheme = true
        }
        composeRule.runOnIdle {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertFalse(controller.isAppearanceLightStatusBars)
            assertFalse(controller.isAppearanceLightNavigationBars)
        }
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
    fun corruptAudioSettingsFallBackWithoutClearingSavedTunings() {
        val tuning = standardTuning()
        UserPreferences(context).favoriteIds = setOf(tuning.id)
        UserPreferences(context).customTunings = listOf(
            TuningPreset(
                id = "custom-test",
                name = "Custom test",
                instrument = tuning.instrument,
                notesLowToHigh = tuning.notesLowToHigh,
                layouts = setOf(tuning.layouts.first()),
            ),
        )
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putInt("detection_sensitivity", 101)
            .putInt("tuner_needle_stability", 101)
            .putInt("tuner_noise_rejection", 101)
            .putInt("tuner_harmonic_protection", 101)
            .putInt("tuner_in_tune_cents", 0)
            .putLong("tuner_confirmation_millis", 50)
            .putLong("tuner_reading_hold_millis", 125)
            .putString("tuner_response_mode", "UNKNOWN")
            .putString("tuner_input_source", "UNKNOWN")
            .commit()

        val preferences = UserPreferences(context)

        assertEquals(TunerProfile.BALANCED.settings, preferences.tunerAudioSettings)
        assertEquals(setOf(tuning.id), preferences.favoriteIds)
        assertEquals(listOf("custom-test"), preferences.customTunings.map(TuningPreset::id))
    }

    @Test
    fun inlineSixPreferenceRoundTrips() {
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putString("headstock_layout", "INLINE_6")
            .commit()

        assertEquals(HeadstockLayout.INLINE_6, UserPreferences(context).headstockLayout)
    }

    @Test
    fun wrongTypedAudioSettingsFallBackWithoutClearingSavedTunings() {
        val tuning = standardTuning()
        UserPreferences(context).favoriteIds = setOf(tuning.id)
        UserPreferences(context).customTunings = listOf(
            TuningPreset(
                id = "custom-test",
                name = "Custom test",
                instrument = tuning.instrument,
                notesLowToHigh = tuning.notesLowToHigh,
                layouts = setOf(tuning.layouts.first()),
            ),
        )
        context.getSharedPreferences("tuneitall_preferences", Context.MODE_PRIVATE).edit()
            .putString("detection_sensitivity", "invalid")
            .putInt("tuner_response_mode", 1)
            .putString("tuner_needle_stability", "invalid")
            .putString("tuner_noise_rejection", "invalid")
            .putString("tuner_harmonic_protection", "invalid")
            .putString("tuner_in_tune_cents", "invalid")
            .putString("tuner_confirmation_millis", "invalid")
            .putInt("tuner_reading_hold_millis", 1)
            .putInt("tuner_input_source", 1)
            .commit()

        val preferences = UserPreferences(context)

        assertEquals(DetectionSensitivity.DEFAULT, preferences.sensitivity)
        assertEquals(TunerProfile.BALANCED.settings, preferences.tunerAudioSettings)
        assertEquals(setOf(tuning.id), preferences.favoriteIds)
        assertEquals(listOf("custom-test"), preferences.customTunings.map(TuningPreset::id))
    }

    @Test
    fun tunerAudioSettingsRoundTripAllFields() {
        val expected = TunerAudioSettings(
            sensitivity = DetectionSensitivity(70),
            response = ResponseMode.STABLE,
            needleStability = 35,
            noiseRejection = 70,
            harmonicProtection = 95,
            inTuneCents = 7,
            confirmationMillis = 500,
            readingHoldMillis = 750,
            inputSource = AudioInputSource.COMPATIBLE,
        )

        UserPreferences(context).tunerAudioSettings = expected

        assertEquals(expected, UserPreferences(context).tunerAudioSettings)
    }

    @Test
    fun settingsAccepts444RejectsInvalidAndRequiresExtremeConfirmation() {
        var applied: ReferencePitch? = null
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = state(),
                    onThemeModeChanged = {},
                    onReferencePitchChanged = { applied = it },
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onAudioSettingsChanged = {},
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
    fun audioSettingsShowProfilesAdvancedValuesAndDeriveCustom() {
        var currentState by mutableStateOf(
            state().copy(
                audioSettings = TunerProfile.BALANCED.settings,
                audioInputCapabilities = AudioInputCapabilities(
                    rawSupported = false,
                    activeSource = null,
                ),
            ),
        )
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = currentState,
                    initialSection = SettingsSection.TUNER,
                    onThemeModeChanged = {},
                    onReferencePitchChanged = {},
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onAudioSettingsChanged = { currentState = currentState.copy(audioSettings = it) },
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Balanced")[0].assertIsSelected()
        composeRule.onNodeWithText("Quiet room").assertIsDisplayed()
        composeRule.onNodeWithText("Noisy room").assertIsDisplayed()
        composeRule.onNodeWithText("Fast response").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Microphone sensitivity")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(37f) }
        composeRule.onNodeWithText("Custom").assertIsSelected()

        composeRule.onNodeWithText("Advanced audio").performScrollTo().performClick()
        composeRule.onNodeWithText("Noise rejection: 30%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Harmonic protection: 80%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("In-tune tolerance: 3 cents").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Confirmation time: 250 ms").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reading hold: 250 ms").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Raw").assertIsNotEnabled()
        composeRule.onNodeWithText("Raw input is unavailable on this device.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Active input: Not listening").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Reset audio settings").performScrollTo().performClick()
        assertEquals(TunerProfile.BALANCED.settings, currentState.audioSettings)
    }

    @Test
    fun rawInputCanBeSelectedWhenSupported() {
        var applied: TunerAudioSettings? = null
        var currentState by mutableStateOf(
            state().copy(
                audioInputCapabilities = AudioInputCapabilities(
                    rawSupported = true,
                    activeSource = AudioInputSource.COMPATIBLE,
                ),
            ),
        )
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = currentState,
                    initialSection = SettingsSection.TUNER,
                    onThemeModeChanged = {},
                    onReferencePitchChanged = {},
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onAudioSettingsChanged = { applied = it },
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Advanced audio").performScrollTo().performClick()
        composeRule.onNodeWithText("Raw").performScrollTo().performClick()

        assertEquals(AudioInputSource.RAW, applied?.inputSource)
        composeRule.onNodeWithText("Active input: Compatible").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            currentState = currentState.copy(
                audioInputCapabilities = currentState.audioInputCapabilities.copy(
                    activeSource = AudioInputSource.RAW,
                ),
            )
        }
        composeRule.onNodeWithText("Active input: Raw").performScrollTo().assertIsDisplayed()
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
    fun sixStringCustomTuningOffersBothLayoutsAndSavesInline() {
        var saved: TuningPreset? = null
        composeRule.setContent {
            TuneItAllTheme {
                CustomTuningScreen(existing = emptyList(), onSave = { saved = it }, onBack = {})
            }
        }

        composeRule.onNodeWithText("Headstock layout").assertIsDisplayed()
        composeRule.onNodeWithText("6 inline").performClick()
        composeRule.onNode(hasText("Tuning name") and hasSetTextAction()).performTextInput("Six Inline")
        composeRule.onNodeWithText("Save and start tuning").performScrollTo().performClick()

        assertEquals(setOf(HeadstockLayout.INLINE_6), saved?.layouts)
    }

    @Test
    fun settingsOffersSplitAndInlineSixLayouts() {
        composeRule.setContent {
            TuneItAllTheme {
                SettingsScreen(
                    state = state(),
                    onThemeModeChanged = {},
                    onReferencePitchChanged = {},
                    onNotationChanged = {},
                    onLayoutChanged = {},
                    onAudioSettingsChanged = {},
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Headstock").assertIsDisplayed()
        composeRule.onNodeWithText("3 + 3").assertIsDisplayed()
        composeRule.onNodeWithText("6 inline").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("Back").performClick()
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
    fun precisionPanelOrdersStatusNoteCentsRulerAndHeadstock() {
        val note = MidiNote(40)
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(
                        reading = TunerReading(note, note, 82.41, 2.0, inTune = false),
                    ),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        val tuning = composeRule.onNodeWithTag("tuner_status_tuning")
            .assertTextEquals("Standard E")
            .fetchSemanticsNode().boundsInRoot
        val mode = composeRule.onNodeWithTag("tuner_status_mode")
            .assertTextEquals("Auto")
            .fetchSemanticsNode().boundsInRoot
        val reference = composeRule.onNodeWithTag("tuner_status_reference")
            .assertTextEquals("A4 = 440.0 Hz")
            .fetchSemanticsNode().boundsInRoot
        val noteBounds = composeRule.onNodeWithTag("detected_note").fetchSemanticsNode().boundsInRoot
        val cents = composeRule.onNodeWithTag("signed_cents").fetchSemanticsNode().boundsInRoot
        val ruler = composeRule.onNodeWithTag("cents_ruler").fetchSemanticsNode().boundsInRoot
        val headstock = composeRule.onNodeWithTag("headstock").fetchSemanticsNode().boundsInRoot

        assertTrue(tuning.right <= mode.left)
        assertTrue(mode.right <= reference.left)
        assertTrue(tuning.top < noteBounds.top)
        assertTrue(noteBounds.bottom <= cents.top)
        assertTrue(cents.bottom <= ruler.top)
        assertTrue(ruler.bottom <= headstock.top)
    }

    @Test
    fun centsRulerShowsEverySignedTenCentLabel() {
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

        (-50..50 step 10).forEach { cents ->
            composeRule.onNodeWithTag("cents_ruler_label_$cents", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertTextEquals(if (cents > 0) "+$cents" else "$cents")
        }
    }

    @Test
    fun centsRulerKeepsNegativeLeftAndPositiveRightInRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TuneItAllTheme {
                    Box(Modifier.requiredSize(width = 360.dp, height = 120.dp)) {
                        CentsRail(cents = 12.0, inTune = false, idleText = "Idle")
                    }
                }
            }
        }

        val negative = composeRule.onNodeWithTag("cents_ruler_label_-50", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val center = composeRule.onNodeWithTag("cents_ruler_label_0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val positive = composeRule.onNodeWithTag("cents_ruler_label_50", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(negative.center.x < center.center.x)
        assertTrue(center.center.x < positive.center.x)
    }

    @Test
    fun standardSplitHeadstockUsesPhysicalStringOrderAndMinimumTargets() {
        composeRule.setContent {
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(mode = TunerMode.MANUAL),
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
        val strings = listOf("E2", "A2", "D3", "G3", "B3", "E4").mapIndexed { index, note ->
            composeRule.onNodeWithContentDescription("String ${6 - index} $note")
                .fetchSemanticsNode().boundsInRoot
        }

        strings.forEach { bounds ->
            assertTrue(bounds.width / context.resources.displayMetrics.density >= 48f)
            assertTrue(bounds.height / context.resources.displayMetrics.density >= 48f)
        }
        strings.take(3).forEach { assertTrue(it.center.x < root.center.x) }
        strings.takeLast(3).forEach { assertTrue(it.center.x > root.center.x) }
        assertTrue(strings[2].top < strings[1].top)
        assertTrue(strings[1].top < strings[0].top)
        assertTrue(strings[3].top < strings[4].top)
        assertTrue(strings[4].top < strings[5].top)
    }

    @Test
    fun splitHeadstockKeepsPhysicalSidesInRtl() {
        val tuning = standardTuning()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TuneItAllTheme {
                    Box(Modifier.requiredSize(width = 360.dp, height = 260.dp)) {
                        Headstock(
                            layout = HeadstockLayout.SPLIT_3_3,
                            notes = tuning.notesLowToHigh,
                            selectedIndex = null,
                            confirmed = false,
                            notation = NoteNotation.SHARPS,
                            onStringSelected = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("String 6 E2", "String 5 A2", "String 4 D3").forEach { description ->
            assertTrue(composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center.x < root.center.x)
        }
        listOf("String 3 G3", "String 2 B3", "String 1 E4").forEach { description ->
            assertTrue(composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.center.x > root.center.x)
        }
    }

    @Test
    fun precisionPanelFitsCompactWidthAtLargeFont() {
        val viewModel = TunerViewModel(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale = 1.3f),
            ) {
                TuneItAllTheme {
                    Box(Modifier.requiredSize(width = 360.dp, height = 720.dp)) {
                        TuneItAllApp(
                            state = state().copy(
                                mode = TunerMode.MANUAL,
                            ),
                            viewModel = viewModel,
                            openApplicationSettings = {},
                        )
                    }
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        listOf("tuner_status", "detected_note", "signed_cents", "cents_ruler").forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag clipped left", bounds.left >= root.left)
            assertTrue("$tag clipped right", bounds.right <= root.right)
        }
        composeRule.onNodeWithTag("detected_note").assertIsDisplayed()
        composeRule.onNodeWithTag("cents_ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_item_tuner", useUnmergedTree = true).assertIsDisplayed()
        listOf("tuner_status_tuning", "tuner_status_mode", "tuner_status_reference", "signed_cents")
            .forEach { tag ->
                composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                        val results = mutableListOf<TextLayoutResult>()
                        assertTrue(getResults(results))
                        val result = results.single()
                        assertFalse("$tag text overflowed vertically", result.didOverflowHeight)
                        val lineWidth = result.getLineRight(0) - result.getLineLeft(0)
                        assertTrue(
                            "$tag text overflowed horizontally: text=$lineWidth, layout=${result.size.width}",
                            lineWidth <= result.size.width + 1f,
                        )
                    }
            }
        listOf("E2", "A2", "D3", "G3", "B3", "E4").forEachIndexed { index, note ->
            val stringNumber = 6 - index
            val button = composeRule.onNodeWithContentDescription("String $stringNumber $note")
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(button.width / context.resources.displayMetrics.density >= 48f)
            assertTrue(button.height / context.resources.displayMetrics.density >= 48f)
            listOf("headstock_note_$stringNumber", "headstock_number_$stringNumber").forEach { tag ->
                composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults ->
                        val results = mutableListOf<TextLayoutResult>()
                        assertTrue(getResults(results))
                        val result = results.single()
                        assertFalse("$tag overflowed vertically", result.didOverflowHeight)
                        val lineWidth = result.getLineRight(0) - result.getLineLeft(0)
                        assertTrue(lineWidth <= result.size.width + 1f)
                    }
            }
            composeRule.onNodeWithTag("bottom_item_tuner", useUnmergedTree = true).assertIsDisplayed()
        }
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
    fun detectedNoteKeepsNaturalSharpAndFlatInnerAnchors() {
        var note by mutableStateOf(MidiNote(60))
        var notation by mutableStateOf(NoteNotation.SHARPS)
        composeRule.setContent {
            val reading = TunerReading(note, note, 261.63, 0.0, inTune = true)
            TuneItAllTheme {
                TunerScreen(
                    state = state().copy(mode = TunerMode.CHROMATIC, reading = reading, notation = notation),
                    onModeSelected = {},
                    onStringSelected = {},
                    onToggleFavorite = {},
                    onOpenLibrary = {},
                    onOpenSettings = {},
                    onOpenApplicationSettings = {},
                )
            }
        }

        fun anchors() = listOf("detected_note_letter", "detected_note_accidental", "detected_note_octave")
            .map { tag -> composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }

        val natural = anchors()
        composeRule.runOnIdle { note = MidiNote(61) }
        val sharp = anchors()
        composeRule.runOnIdle { notation = NoteNotation.FLATS }
        val flat = anchors()

        natural.indices.forEach { index ->
            assertEquals(natural[index].left, sharp[index].left, 0.5f)
            assertEquals(natural[index].width, sharp[index].width, 0.5f)
            assertEquals(natural[index].left, flat[index].left, 0.5f)
            assertEquals(natural[index].width, flat[index].width, 0.5f)
        }
        assertTrue(natural[0].right <= natural[1].left)
        assertTrue(natural[1].right <= natural[2].left)
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

        val settings = composeRule.onNodeWithTag("tuner_settings").fetchSemanticsNode().boundsInRoot
        val modeSelector = composeRule.onNodeWithTag("mode_selector").fetchSemanticsNode().boundsInRoot

        assertTrue(settings.top < modeSelector.top)
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
        audioSettings = TunerProfile.BALANCED.settings,
    )

    private fun standardTuning(): TuningPreset = requireNotNull(TuningCatalog.byId("guitar-6-standard"))

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
