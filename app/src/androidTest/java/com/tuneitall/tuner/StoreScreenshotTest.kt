package com.tuneitall.tuner

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.tuneitall.tuner.audio.PitchEstimate
import com.tuneitall.tuner.audio.TunerProfile
import com.tuneitall.tuner.audio.YinPitchDetector
import com.tuneitall.tuner.model.HeadstockLayout
import com.tuneitall.tuner.model.ReferencePitch
import com.tuneitall.tuner.model.TuningCatalog
import com.tuneitall.tuner.music.ChordShapeCatalog
import com.tuneitall.tuner.music.NoteTrainingSets
import com.tuneitall.tuner.music.StreamingChordAnalyzer
import com.tuneitall.tuner.storage.NoteNotation
import com.tuneitall.tuner.storage.TrainerStats
import com.tuneitall.tuner.tuner.TunerEngine
import com.tuneitall.tuner.tuner.TunerMode
import com.tuneitall.tuner.ui.AppBottomBar
import com.tuneitall.tuner.ui.AutoScrollScreen
import com.tuneitall.tuner.ui.ChordTab
import com.tuneitall.tuner.ui.ChordUiState
import com.tuneitall.tuner.ui.ChordsScreen
import com.tuneitall.tuner.ui.MetronomeScreen
import com.tuneitall.tuner.ui.MetronomeUiState
import com.tuneitall.tuner.ui.PrimaryDestination
import com.tuneitall.tuner.ui.TrainerScreen
import com.tuneitall.tuner.ui.TunerScreen
import com.tuneitall.tuner.ui.TunerUiState
import com.tuneitall.tuner.ui.TuningLibraryScreen
import com.tuneitall.tuner.ui.theme.TuneItAllTheme
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in store captures. Production composables, synthetic signals, no microphone or saved user data. */
class StoreScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val tuning = requireNotNull(TuningCatalog.byId("guitar-6-standard"))
    private val names = listOf(
        "1_tuner", "2_chromatic", "3_tunings", "4_metronome",
        "5_chords", "6_song_chords", "7_trainer", "8_auto_scroll",
    )

    @Test
    fun captureStoreScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureStore") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".qa")) { "Store captures require the isolated QA package" }
        val catalog = ChordShapeCatalog.fromResources(context.resources)
        val guitar = tunerState(TunerMode.AUTO, 110.05)
        val chromatic = tunerState(TunerMode.CHROMATIC, 440.13)
        val analyzer = StreamingChordAnalyzer(22_050)
        listOf(listOf(261.63, 329.63, 392.0), listOf(220.0, 261.63, 329.63),
            listOf(174.61, 220.0, 261.63), listOf(196.0, 246.94, 293.66)).forEach { notes ->
            analyzer.accept(FloatArray(22_050 * 3) { frame ->
                (notes.sumOf { sin(2.0 * PI * it * frame / 22_050) } * 0.2).toFloat()
            })
        }
        val events = analyzer.finish()
        assertTrue("Synthetic progression must produce actual analyzer events", events.isNotEmpty())
        val song = ChordUiState(
            tab = ChordTab.SONG, fileName = "Practice progression.wav", events = events,
            prepared = true, durationMillis = 12_000L, positionMillis = 1_000L,
        )
        var screen by mutableIntStateOf(0)
        var language by mutableStateOf("en-US")
        compose.setContent {
            val registryOwner = LocalActivityResultRegistryOwner.current
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalActivityResultRegistryOwner provides requireNotNull(registryOwner),
            ) {
                key(language, screen) {
                    TuneItAllTheme(darkTheme = screen == 1) {
                        StoreScreen(screen, if (screen == 1) chromatic else guitar,
                            song.copy(fileName = if (language == "cs-CZ") "Cvičná skladba.wav" else song.fileName), catalog)
                    }
                }
            }
        }
        for (locale in listOf("en-US", "cs-CZ")) {
            for (index in names.indices) {
                compose.runOnIdle { language = locale; screen = index }
                if (index == 6) {
                    compose.onNodeWithTag("trainer_exercise_notes").performSemanticsAction(SemanticsActions.OnClick) { it() }
                }
                compose.mainClock.advanceTimeBy(1_000L)
                compose.waitForIdle()
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                assertTrue("Capture must be portrait", bitmap.height > bitmap.width)
                val directory = File(context.getExternalFilesDir(null), "store-captures/$locale")
                check(directory.mkdirs() || directory.isDirectory)
                File(directory, "${names[index]}.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
    }

    private fun tunerState(mode: TunerMode, frequency: Double): TunerUiState {
        val samples = ShortArray(8_192) { (sin(2.0 * PI * frequency * it / 48_000) * 12_000).toInt().toShort() }
        val frame = YinPitchDetector().analyze(samples, 48_000, 60.0, 900.0)
        val pitch = requireNotNull(frame.candidates.maxByOrNull { it.probability })
        val reading = TunerEngine().update(
            PitchEstimate(pitch.hertz, pitch.probability, frame.rms), mode, tuning, 1,
            ReferencePitch(440.0), TunerProfile.BALANCED.settings,
        )
        assertTrue("Synthetic reference tone should be in tune", reading.inTune)
        return TunerUiState(
            mode = mode, tuning = tuning, selectedString = 1, headstockLayout = HeadstockLayout.SPLIT_3_3,
            referencePitch = ReferencePitch(440.0), notation = NoteNotation.SHARPS,
            favoriteIds = setOf(tuning.id), customTunings = emptyList(), reading = reading,
            microphoneGranted = true, microphonePermanentlyDenied = false, listening = true,
            referenceTonePlaying = false, error = null, tuningConfirmed = true,
        )
    }

    @Composable
    private fun StoreScreen(index: Int, tuner: TunerUiState, song: ChordUiState, catalog: ChordShapeCatalog) {
        val destination = when (index) {
            3 -> PrimaryDestination.METRONOME
            4, 5 -> PrimaryDestination.CHORDS
            6 -> PrimaryDestination.TRAINER
            7 -> PrimaryDestination.AUTO_SCROLL
            else -> PrimaryDestination.TUNER
        }
        Scaffold(contentWindowInsets = WindowInsets(0), bottomBar = {
            if (index != 2) AppBottomBar(selected = destination, onSelect = {})
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (index) {
                    0, 1 -> TunerScreen(tuner, {}, {}, {}, {}, {}, {})
                    2 -> TuningLibraryScreen(TuningCatalog.presets, setOf(tuning.id), NoteNotation.SHARPS, {}, {}, {}, {})
                    3 -> MetronomeScreen(
                        state = MetronomeUiState(), phaseProvider = { 0.0 }, onBpmChange = {},
                        onTap = {}, onStart = {}, onStop = {}, onOpenSettings = {},
                    )
                    4, 5 -> ChordsScreen(
                        state = if (index == 5) song else ChordUiState(), tunings = TuningCatalog.presets,
                        notation = NoteNotation.SHARPS, catalog = catalog, onTabSelected = {},
                        onChordSelected = {}, onTuningSelected = {}, onTransposeChanged = {},
                        onLoadSong = {}, onPlayPause = {}, onSeek = {}, onClearSong = {},
                    )
                    6 -> TrainerScreen(TrainerStats(), TuningCatalog.presets, NoteNotation.SHARPS, catalog, {}, {}, NoteTrainingSets(), {})
                    7 -> AutoScrollScreen(
                        overlayAllowed = false, accessibilityEnabled = false, speed = 15,
                        onSpeedChanged = {}, onOpenOverlaySettings = {}, onOpenAccessibilitySettings = {},
                        onShowControls = {},
                    )
                }
            }
        }
    }
}
