# TuneItAll Core implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the pYIN-derived tuner upgrade, Precision Panel Light and Dark UI, five-item app shell, and sample-accurate foreground metronome.

**Architecture:** Keep the single Android app module and typed screen state. Split pure detector, tracker, metronome schedule, and click synthesis from Android audio and Compose code so unit tests cover timing and pitch behavior. Use the existing `AudioRecord` path for tuning and one continuous native `AudioTrack` for metronome output.

**Tech stack:** Kotlin 2.3.21, Jetpack Compose Material 3, Android `AudioRecord`, Android `AudioTrack`, `SharedPreferences`, JUnit 4, Compose UI tests, SDK 36.

**Specs:** `docs/superpowers/specs/2026-08-12-pyin-audio-engine-design.md` and `docs/superpowers/specs/2026-08-20-musician-toolkit-design.md`

## Global constraints

- Keep `minSdk = 26`, `targetSdk = 36`, and Java 17.
- Add no dependency, Internet permission, account, analytics, or network call.
- Keep `RECORD_AUDIO` as the only manually declared permission.
- Tuner is the launch screen. Audio stops when the app leaves the foreground.
- Light uses `#FAF9F6`, `#111111`, and `#63D17A`.
- Dark uses `#101010`, `#F4F1EA`, and `#63D17A`.
- Keep all arrays, candidate sets, histories, preferences, and queues bounded.
- Preserve existing A4, tunings, custom tunings, notation, sensitivity, layout, and favorites.
- Write each behavior test first and observe the expected failure before production code.
- Do not commit, push, or open a PR without explicit user approval. Commit commands below are prepared checkpoints only.

## File structure

Create focused files:

```text
app/src/main/java/com/tuneitall/tuner/
  audio/
    AdaptiveNoiseFloor.kt      Bounded unvoiced RMS estimator
    AudioInputSource.kt        AUTO/RAW/COMPATIBLE capture selection
    PitchTracker.kt            Bounded online pYIN candidate tracking
    TunerAudioSettings.kt      Profiles and validated detector controls
    MetronomePlayer.kt         One continuous AudioTrack and audio thread
  metronome/
    MetronomeSettings.kt       Validated BPM, meter, accent, and sound state
    MetronomeSchedule.kt       Pure frame schedule and pendulum phase
    MetronomeSound.kt          Pure click-buffer generation
  ui/
    AppBottomBar.kt            Five fixed top-level destinations
    MetronomeScreen.kt         Precision Panel metronome and Canvas pendulum
    MetronomeViewModel.kt      Metronome lifecycle and UI state
  ui/theme/
    ThemeMode.kt               SYSTEM/LIGHT/DARK and pure resolution helper
```

Modify existing files only at their current responsibilities:

```text
audio/AudioInput.kt            Select and report active source
audio/YinPitchDetector.kt      Emit bounded multi-candidate PitchFrame
storage/UserPreferences.kt     Persist validated new settings
tuner/InTuneConfirmationTracker.kt  Configurable confirmation duration
tuner/TunerEngine.kt           Targeting and needle smoothing only
tuner/TunerReadingRetainer.kt  Configurable visual hold duration
ui/TunerViewModel.kt           Connect detector, tracker, settings, and lifecycle
ui/TunerScreen.kt              Precision Panel composition
ui/SettingsScreen.kt           Theme and bounded tuner controls
ui/theme/Theme.kt              Selected palettes
MainActivity.kt                Resolve theme outside the Material theme
TuneItAllApp.kt                Top-level destinations and audio ownership
values/strings.xml             English labels
values-cs/strings.xml          Czech labels
```

---

### Task 1: Validated tuner audio settings

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/AudioInputSource.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/audio/TunerAudioSettings.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/audio/TunerAudioSettingsTest.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt`

**Interfaces:**
- Produces: `TunerAudioSettings`, `TunerProfile`, `ResponseMode`, and `AudioInputSource`.
- Produces: `UserPreferences.tunerAudioSettings: TunerAudioSettings`.

- [ ] **Step 1: Write the failing settings tests**

```kotlin
@Test
fun `balanced settings match documented safe defaults`() {
    assertEquals(
        TunerAudioSettings(
            sensitivity = DetectionSensitivity(100),
            response = ResponseMode.BALANCED,
            needleStability = 65,
            noiseRejection = 30,
            harmonicProtection = 80,
            inTuneCents = 3,
            confirmationMillis = 250,
            readingHoldMillis = 250,
            inputSource = AudioInputSource.AUTO,
        ),
        TunerProfile.BALANCED.settings,
    )
}

@Test
fun `unsafe audio settings are rejected`() {
    assertFailsWith<IllegalArgumentException> { TunerAudioSettings(needleStability = 101) }
    assertFailsWith<IllegalArgumentException> { TunerAudioSettings(inTuneCents = 0) }
    assertFailsWith<IllegalArgumentException> { TunerAudioSettings(confirmationMillis = 50) }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.audio.TunerAudioSettingsTest" --no-daemon
```

Expected: compilation fails because `TunerAudioSettings` does not exist.

- [ ] **Step 3: Add the minimal typed settings**

```kotlin
enum class ResponseMode { FAST, BALANCED, STABLE }

data class TunerAudioSettings(
    val sensitivity: DetectionSensitivity = DetectionSensitivity.DEFAULT,
    val response: ResponseMode = ResponseMode.BALANCED,
    val needleStability: Int = 65,
    val noiseRejection: Int = 30,
    val harmonicProtection: Int = 80,
    val inTuneCents: Int = 3,
    val confirmationMillis: Long = 250,
    val readingHoldMillis: Long = 250,
    val inputSource: AudioInputSource = AudioInputSource.AUTO,
) {
    init {
        require(needleStability in 0..100)
        require(noiseRejection in 0..100)
        require(harmonicProtection in 0..100)
        require(inTuneCents in 1..10)
        require(confirmationMillis in 100L..1_000L && confirmationMillis % 50L == 0L)
        require(readingHoldMillis in 0L..1_000L && readingHoldMillis % 50L == 0L)
    }
}

enum class TunerProfile(val settings: TunerAudioSettings) {
    BALANCED(TunerAudioSettings()),
    QUIET_ROOM(TunerAudioSettings(noiseRejection = 15, harmonicProtection = 75)),
    NOISY_ROOM(TunerAudioSettings(sensitivity = DetectionSensitivity(70), noiseRejection = 70, harmonicProtection = 95)),
    FAST_RESPONSE(TunerAudioSettings(response = ResponseMode.FAST, needleStability = 35, harmonicProtection = 60)),
}
```

Persist every field under a stable key. Read each field through its constructor
or range check. Fall back to `TunerProfile.BALANCED.settings` per invalid field.

- [ ] **Step 4: Add and run the preference fallback test**

In `TuneItAllFlowTest`, write corrupt values directly, then assert that
`UserPreferences(context).tunerAudioSettings` returns bounded defaults without
clearing `favoriteIds` or `customTunings`.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon
```

Expected: settings tests pass and the existing flows remain green.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/audio/AudioInputSource.kt app/src/main/java/com/tuneitall/tuner/audio/TunerAudioSettings.kt app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt app/src/test/java/com/tuneitall/tuner/audio/TunerAudioSettingsTest.kt app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt
git commit -m "feat(tuner): add bounded audio settings"
```

Run this commit only after explicit approval.

### Task 2: Multi-candidate YIN frame analysis

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/audio/YinPitchDetector.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/audio/YinPitchDetectorTest.kt`

**Interfaces:**
- Produces: `PitchCandidate(hertz, probability, periodicity)`.
- Produces: `PitchFrame(candidates, rms, peak, unvoicedProbability)`.
- Produces: `YinPitchDetector.analyze(samples, sampleRate, minFrequency, maxFrequency): PitchFrame`.
- Keeps: existing `detect(...)` until Task 4 removes its callers.

- [ ] **Step 1: Add failing candidate tests**

```kotlin
@Test
fun `analysis retains fundamental and harmonic interpretations`() {
    val frame = detector.analyze(
        signal(110.0, 4096, listOf(1 to 0.20, 2 to 0.70, 3 to 0.10)),
        SAMPLE_RATE,
        70.0,
        1_000.0,
    )

    assertTrue(frame.candidates.size in 1..8)
    assertTrue(frame.candidates.any { abs(MusicMath.cents(it.hertz, 110.0)) <= 2.0 })
    assertTrue(frame.candidates.all { it.probability in 0.0..1.0 && it.periodicity in 0.0..1.0 })
}

@Test
fun `silence returns a bounded unvoiced frame`() {
    val frame = detector.analyze(ShortArray(4096), SAMPLE_RATE, 70.0, 1_000.0)
    assertTrue(frame.candidates.isEmpty())
    assertEquals(1.0, frame.unvoicedProbability, 0.0)
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run the targeted `YinPitchDetectorTest`. Expected: compilation fails because
`analyze` and frame types do not exist.

- [ ] **Step 3: Implement one CMNDF pass and bounded candidates**

Add exact validated data types:

```kotlin
data class PitchCandidate(val hertz: Double, val probability: Double, val periodicity: Double)
data class PitchFrame(
    val candidates: List<PitchCandidate>,
    val rms: Double,
    val peak: Double,
    val unvoicedProbability: Double,
)
```

Reuse the current `difference` and `cumulativeMean` arrays. Evaluate this fixed
threshold distribution after one CMNDF calculation:

```kotlin
private val THRESHOLDS = doubleArrayOf(0.05, 0.075, 0.10, 0.125, 0.15, 0.175, 0.20, 0.25, 0.30)
private val THRESHOLD_WEIGHTS = doubleArrayOf(0.02, 0.06, 0.12, 0.18, 0.20, 0.17, 0.12, 0.08, 0.05)
private const val MAX_CANDIDATES = 8
private const val MERGE_CENTS = 15.0
```

For each threshold, find its local minimum, refine its period, merge candidates
within 15 cents, and add the threshold weight. Sort by probability descending
and take eight. Calculate `unvoicedProbability` as
`(1.0 - candidates.sumOf { it.probability }).coerceIn(0.0, 1.0)`.

- [ ] **Step 4: Run detector accuracy, noise, boundary, and timing tests**

Run the complete `YinPitchDetectorTest`. Expected: all existing 30.87 Hz to
4,186.01 Hz, quiet tone, harmonic, silence, noise, invalid-input, and timing
cases pass.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/audio/YinPitchDetector.kt app/src/test/java/com/tuneitall/tuner/audio/YinPitchDetectorTest.kt
git commit -m "feat(tuner): emit bounded YIN candidates"
```

Run this commit only after explicit approval.

### Task 3: Adaptive noise floor and online pitch tracker

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/AdaptiveNoiseFloor.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/audio/PitchTracker.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/audio/AdaptiveNoiseFloorTest.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/audio/PitchTrackerTest.kt`

**Interfaces:**
- Consumes: `PitchFrame` and `TunerAudioSettings`.
- Produces: `AdaptiveNoiseFloor.accepts(rms, absoluteFloor, noiseRejection): Boolean`.
- Produces: `PitchTracker.update(frame, settings): PitchEstimate?` and `reset()`.

- [ ] **Step 1: Write failing noise and tracking tests**

Cover quiet voiced frames, changing unvoiced noise, one-frame octave glitches,
three consistent new-note frames, and an energy onset that switches in at most
three frames. Use real `PitchFrame` values, not mocks.

```kotlin
@Test
fun `one octave candidate cannot replace a stable fundamental`() {
    val tracker = PitchTracker()
    repeat(5) { tracker.update(frame(440.0, rms = 0.2), TunerProfile.BALANCED.settings) }
    val result = tracker.update(frame(880.0, rms = 0.2), TunerProfile.BALANCED.settings)
    assertEquals(440.0, requireNotNull(result).hertz, 1.0)
}

@Test
fun `noise floor rises slowly and falls quickly`() {
    val floor = AdaptiveNoiseFloor()
    repeat(100) { floor.observe(rms = 0.01, voiced = false) }
    val raised = floor.value
    repeat(10) { floor.observe(rms = 0.001, voiced = false) }
    assertTrue(floor.value < raised)
}
```

- [ ] **Step 2: Run both test classes and verify RED**

Expected: compilation fails because both classes are missing.

- [ ] **Step 3: Implement bounded state**

`AdaptiveNoiseFloor` stores one `Double`, starts at `0.00005`, uses `0.005`
for upward updates and `0.10` for downward updates, and observes only unvoiced
frames. Map noise rejection to a ratio with
`1.0 + noiseRejection * 0.05`.

`PitchTracker` keeps at most eight voiced states plus one unvoiced score. Score
each current candidate with `ln(probability)` plus the best previous score
minus cents-distance cost. Add an octave penalty scaled by
`harmonicProtection / 100.0`. Detect onset when current RMS is at least 1.8
times the prior RMS and reduce transition cost for that frame. Subtract the
best score each update to prevent values from growing without bound.

- [ ] **Step 4: Run all audio unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.audio.*" --no-daemon
```

Expected: new tracking tests and old signal tests pass.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/audio/AdaptiveNoiseFloor.kt app/src/main/java/com/tuneitall/tuner/audio/PitchTracker.kt app/src/test/java/com/tuneitall/tuner/audio/AdaptiveNoiseFloorTest.kt app/src/test/java/com/tuneitall/tuner/audio/PitchTrackerTest.kt
git commit -m "feat(tuner): track pitch candidates over time"
```

Run this commit only after explicit approval.

### Task 4: Integrate the pYIN-derived pipeline

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/tuner/TunerEngine.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/tuner/InTuneConfirmationTracker.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/tuner/TunerReadingRetainer.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt`
- Modify: corresponding unit tests.

**Interfaces:**
- Consumes: tracked `PitchEstimate` and `TunerAudioSettings`.
- Produces: `TunerEngine.update(..., settings: TunerAudioSettings)`.
- Produces: configurable `confirmationMillis` and `readingHoldMillis`.

- [ ] **Step 1: Replace heuristic tests with pipeline requirements**

Delete tests that assert the old three-frame `StablePitchFilter` behavior.
Add tests that assert `TunerEngine` only smooths within one detected note and
resets smoothing on a tracked note change. Add boundary tests for 1 and 10 cent
in-tune tolerances, 100 and 1,000 ms confirmation, and 0 and 1,000 ms hold.

- [ ] **Step 2: Run targeted tuner tests and verify RED**

Run `TunerEngineTest`, `InTuneConfirmationTrackerTest`, and
`TunerReadingRetainerTest`. Expected: new configurable signatures are missing.

- [ ] **Step 3: Remove duplicate harmonic selection**

Delete `StablePitchFilter` from `TunerEngine`. Add a private log-frequency
one-pole needle smoother whose factor is
`0.15 + (100 - needleStability) * 0.0065`. Reset it when the detected MIDI note,
mode, tuning, string, reference pitch, or settings change. Use
`settings.inTuneCents` for the green range. Remove RMS and confidence rejection
from `TunerEngine`; the detector pipeline now owns signal acceptance. Delete
the old `detect(...)` wrapper after its last caller moves to `analyze(...)`.

Change tracker helpers to accept the duration:

```kotlin
fun update(target: MidiNote?, inTune: Boolean, nowMillis: Long, confirmationMillis: Long): Boolean
fun update(reading: TunerReading?, nowMillis: Long, holdMillis: Long): TunerReading?
```

In `TunerViewModel`, replace `detector.detect` with:

```kotlin
val frame = detector.analyze(samples, sampleRate, range.minHertz, range.maxHertz)
noiseFloor.observe(frame.rms, frame.candidates.isNotEmpty())
val tracked = if (noiseFloor.accepts(frame.rms, settings.sensitivity.minimumRms, settings.noiseRejection)) {
    pitchTracker.update(frame, settings)
} else {
    null
}
val rawReading = tracked?.let { engine.update(it, mode, tuning, selectedString, referencePitch, settings) }
```

Reset detector state when context or profile changes. Keep the confirmation
tracker on `rawReading`, never on the retained display reading.

- [ ] **Step 4: Run the full unit suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: all tuner, audio, storage, and model tests pass.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/tuner app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt app/src/test
git commit -m "feat(tuner): integrate streaming pitch tracker"
```

Run this commit only after explicit approval.

### Task 5: Selectable raw microphone input and tuner settings UI

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/audio/AudioInputSource.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/audio/AudioInput.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt`
- Modify: English and Czech strings.
- Test: `TuneItAllFlowTest.kt` and `AudioPipelineTest.kt`.

**Interfaces:**
- Produces: `AudioInputCapabilities(rawSupported, activeSource)`.
- Changes: `AudioInput.start(windowSize, source, onWindow, onStarted, onError)`.

- [ ] **Step 1: Add failing source-selection and UI tests**

Test pure source resolution:

```kotlin
assertEquals(MediaRecorder.AudioSource.UNPROCESSED, resolveAudioSource(AudioInputSource.AUTO, rawSupported = true))
assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, resolveAudioSource(AudioInputSource.AUTO, rawSupported = false))
assertFailsWith<IllegalArgumentException> { resolveAudioSource(AudioInputSource.RAW, rawSupported = false) }
```

In Compose tests, assert that profiles and advanced values are visible,
changing one setting selects Custom, Raw is disabled when unsupported, and the
screen names the active source.

- [ ] **Step 2: Run tests and verify RED**

Expected: source resolver and Settings callbacks do not exist.

- [ ] **Step 3: Implement one orderly recorder restart path**

Move source resolution out of `createRecorder`. For Raw initialization failure,
release that recorder, try Compatible once, and report the active source through
`onStarted`. `TunerViewModel.setTunerAudioSettings` stops input, resets detector
state, saves settings, updates state, and calls `startAudioIfReady()` once.

Add profile chips plus the three primary controls. Put the remaining controls
under one `Advanced audio` expansion. Reuse Material sliders and filter chips.

- [ ] **Step 4: Run unit and connected Settings tests**

Expected: fallback is bounded, one worker remains active, and all controls have
English and Czech accessible labels.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/audio app/src/main/java/com/tuneitall/tuner/ui/SettingsScreen.kt app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt app/src/main/res app/src/test app/src/androidTest
git commit -m "feat(tuner): expose safe detector controls"
```

Run this commit only after explicit approval.

### Task 6: Precision Panel theme and app shell

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/ui/theme/ThemeMode.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/AppBottomBar.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/MainActivity.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`
- Modify: `UserPreferences.kt`, Settings, and strings.
- Test: `ThemeModeTest.kt` and `TuneItAllFlowTest.kt`.

**Interfaces:**
- Produces: `ThemeMode.SYSTEM`, `LIGHT`, `DARK` and `resolveDarkTheme(mode, systemDark)`.
- Produces: `PrimaryDestination(TUNER, METRONOME, CHORDS, LIBRARY, TRAINER)`.

- [ ] **Step 1: Write failing theme and navigation tests**

```kotlin
@Test fun `system theme follows Android`() {
    assertTrue(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
    assertFalse(resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
}
```

In Compose tests, assert five accessible destination labels, Tuner selected at
launch, and navigation to Metronome and back. Keep Chords and Trainer disabled
with an accessible `Not installed yet` description during Core development.
Their plans must enable them before a public release.

- [ ] **Step 2: Run tests and verify RED**

Expected: theme mode and bottom bar do not exist.

- [ ] **Step 3: Implement the smallest shell**

Resolve the selected theme before `MaterialTheme`. Keep `AppScreen` and add the
five primary destinations. Use `NavigationBar` and `NavigationBarItem`; do not
add Navigation Compose. Preserve `CustomTuning`, Settings, and About as
secondary screens without bottom-bar entries.

Update palettes to the exact spec colors. Use a single amber active color and
8 dp component shapes.

- [ ] **Step 4: Run theme, navigation, compact-height, and 1.3 font tests**

Expected: no clipped labels, every target is at least 48 dp, and legacy screens
remain reachable.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/MainActivity.kt app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt app/src/main/java/com/tuneitall/tuner/ui/AppBottomBar.kt app/src/main/java/com/tuneitall/tuner/ui/theme app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt app/src/main/res app/src/test app/src/androidTest
git commit -m "feat(ui): add Precision Panel app shell"
```

Run this commit only after explicit approval.

### Task 7: Precision Panel tuner composition

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/model/TuningModels.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/model/TuningCatalog.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/AppBottomBar.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/CustomTuningScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/UiLabels.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/components/CentsRail.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/components/Headstock.kt`
- Modify: `TuneItAllFlowTest.kt`.

**Interfaces:**
- Consumes: existing `TunerUiState`, typed tuning data, and `AppBottomBar`.
- Keeps: all existing callbacks and detection semantics.

**User visual addendum:**
- Replace placeholder navigation glyphs with custom Compose vector icons.
- Redraw the split headstock as a recognizable physical guitar head.
- Remove `INLINE_6` from production models, presets, UI, and tests.
- Decode stored custom `INLINE_6` as `SPLIT_3_3` to preserve user data.
- Replace the amber accent with launcher green `#63D17A` in both themes.
- Replace the launcher crosshair with a tuning-fork and cents-ruler vector.

- [ ] **Step 1: Add failing layout semantics tests**

Assert the status row order, fixed note slot, cents ruler labels, and physical
string descriptions `String 6 E2` through `String 1 E4`. Assert settings remains
above the mode selector and Chromatic omits the headstock.

- [ ] **Step 2: Run the targeted Compose tests and verify RED**

Expected: Precision Panel tags and layout bounds are missing.

- [ ] **Step 3: Recompose without replacing working components**

Reuse `CentsRail` and `Headstock`. Change spacing, typography, colors, and
button shape to match the saved Light and Dark references. Keep the display
note width fixed so accidentals do not move the rail. Do not use the raster
reference images in production.

- [ ] **Step 4: Run all tuner Compose tests**

Expected: dark, light, compact, large-font, chromatic, inline, split, reference
tone, and permission states pass.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt app/src/main/java/com/tuneitall/tuner/ui/components app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt
git commit -m "feat(ui): apply Precision Panel tuner"
```

Run this commit only after explicit approval.

### Task 8: Metronome model and frame schedule

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/metronome/MetronomeSettings.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/metronome/MetronomeSchedule.kt`
- Create: matching unit tests.

**Interfaces:**
- Produces: validated `Bpm`, `MetronomeSettings`, `PulseKind`, `ScheduledPulse`.
- Produces: `MetronomeSchedule.pulsesForBuffer(startFrame, frameCount): List<ScheduledPulse>`, `nextMainBeatFrame(): Long`, and `phaseAt(frame): Double`.

- [ ] **Step 1: Write failing boundary and timing tests**

```kotlin
@Test fun `accent five lands on every fifth main beat`() {
    val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(120), accentEvery = 5), 48_000)
    val pulses = buildList {
        repeat(500) { buffer -> addAll(schedule.pulsesForBuffer(buffer * 960L, 960)) }
    }
    val accents = pulses.filter { it.kind == PulseKind.ACCENT }
    assertEquals(listOf(0L, 120_000L, 240_000L, 360_000L), accents.take(4).map { it.frame })
}

@Test fun `fractional BPM does not drift over ten minutes`() {
    val schedule = MetronomeSchedule(MetronomeSettings(bpm = Bpm(137)), 48_000)
    var actual = 0L
    repeat(1_370) { actual = schedule.nextMainBeatFrame() }
    val expected = (1_369 * 48_000.0 * 60.0 / 137.0).roundToLong()
    assertTrue(abs(actual - expected) <= 1L)
}
```

- [ ] **Step 2: Run tests and verify RED**

Expected: metronome types do not exist.

- [ ] **Step 3: Implement a bounded incremental schedule**

Validate BPM 20 to 400, numerator 1 to 12, denominator 2, 4, 8, or 16,
subdivision 1 to 4, accent null or 2 to 12, volume 0 to 100, and count-in 0, 1,
2, or 4. Keep `nextMainFrame` and `mainBeatIndex`; do not materialize a whole
session. Require `frameCount in 1..8_192` and return only pulses inside that
buffer. Test long timing by advancing one main beat at a time.

Calculate pendulum phase from main-beat position. Return `-1.0` at one endpoint,
`0.0` halfway, and `1.0` at the next endpoint using cosine.

- [ ] **Step 4: Run 20, 137, and 400 BPM timing tests**

Expected: less than one-frame accumulated error and exact subdivision/accent
counts.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/metronome app/src/test/java/com/tuneitall/tuner/metronome
git commit -m "feat(metronome): add sample-frame schedule"
```

Run this commit only after explicit approval.

### Task 9: Click synthesis without cuts or DC offset

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/metronome/MetronomeSound.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/metronome/MetronomeSoundTest.kt`

**Interfaces:**
- Produces: `MetronomeSound.WOOD`, `CLICK`, `RIM`.
- Produces: `createClickBuffer(sound, kind, sampleRate): ShortArray`.
- Produces: `applyStopFade(buffer, fadeFrames): Unit`.

- [ ] **Step 1: Write failing waveform tests**

Assert first and last samples equal zero, peak stays below 30,000, mean absolute
DC is below 5, accent energy exceeds normal energy, subdivision energy is
lower, and a 10 ms stop fade ends at zero.

- [ ] **Step 2: Run tests and verify RED**

Expected: sound functions are missing.

- [ ] **Step 3: Generate short deterministic PCM**

Use sine partials and a 1 ms raised-cosine attack plus exponential decay.
Choose base frequencies 1,100 Hz for Wood, 1,800 Hz for Click, and 2,600 Hz for
Rim. Multiply accent frequency by 1.25 and subdivision amplitude by 0.55.
Subtract the buffer mean before PCM conversion, then force first and last
samples to zero.

- [ ] **Step 4: Run waveform and existing reference-tone tests**

Expected: all buffers meet the numerical limits and existing tone/chime tests
stay green.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/metronome/MetronomeSound.kt app/src/test/java/com/tuneitall/tuner/metronome/MetronomeSoundTest.kt
git commit -m "feat(metronome): synthesize clean click sounds"
```

Run this commit only after explicit approval.

### Task 10: Continuous AudioTrack playback and ViewModel

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/MetronomePlayer.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/MetronomeViewModel.kt`
- Create: `app/src/androidTest/java/com/tuneitall/tuner/MetronomePlaybackTest.kt`
- Modify: `TuneItAllApp.kt` and `TunerViewModel.kt` for audio ownership.

**Interfaces:**
- Produces: `MetronomePlayer.start(settings)`, `update(settings)`, `stop()`, `phase()`.
- Produces: immutable `MetronomeUiState` and validated ViewModel setters.

- [ ] **Step 1: Write failing lifecycle tests**

Add an Android test that starts, changes 120 to 137 BPM, changes accent 2 to 5,
mutes, unmutes, and stops. Assert one player session completes without exception
and the final state is stopped. Add a unit test for tap-tempo median and the
two-second reset.

- [ ] **Step 2: Run tests and verify RED**

Expected: player and ViewModel are missing.

- [ ] **Step 3: Implement one audio thread and one AudioTrack**

Build `AudioTrack` with mono PCM16, 48 kHz, `USAGE_MEDIA`, and
`CONTENT_TYPE_SONIFICATION`. Write fixed-size buffers on an audio-priority
thread. Mix scheduled click buffers into the stream with saturating addition.
Apply setting changes at the next main beat. Stop with a 10 ms fade, join the
thread within one second, release the track once, and expose phase from playback
frame position.

`MetronomeViewModel.onStop()` must always stop playback. Switching away from
Tuner calls `TunerViewModel.setTunerActive(false)` so the microphone closes.

- [ ] **Step 4: Run playback tests on emulator and Samsung**

Expected: rapid changes complete, one player owns output, and logcat contains no
`AudioTrack` underrun, `FATAL EXCEPTION`, or repeated initialization failure.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/audio/MetronomePlayer.kt app/src/main/java/com/tuneitall/tuner/ui/MetronomeViewModel.kt app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt app/src/androidTest/java/com/tuneitall/tuner/MetronomePlaybackTest.kt app/src/test
git commit -m "feat(metronome): stream scheduled clicks"
```

Run this commit only after explicit approval.

### Task 11: Mechanical metronome screen

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/ui/MetronomeScreen.kt`
- Modify: `TuneItAllApp.kt`, `AppBottomBar.kt`, UserPreferences, and strings.
- Modify: `TuneItAllFlowTest.kt`.

**Interfaces:**
- Consumes: `MetronomeUiState` and ViewModel callbacks.
- Produces: accessible Canvas pendulum and all required controls.

- [ ] **Step 1: Write failing Compose interaction tests**

Assert BPM input and steppers, meter, accent Off and 2/3/5, subdivision 1/2/3/4,
Tap, Start/Stop, settings sheet, theme contrast, 48 dp targets, and bottom nav.
Assert the pendulum test tag reports phase `-1`, `0`, and `1` without changing
layout bounds.

- [ ] **Step 2: Run targeted Compose tests and verify RED**

Expected: screen and semantics are missing.

- [ ] **Step 3: Draw and connect the screen**

Use `Canvas` for a filled pyramid case, side plane, plinth, vertical scale,
pivot, arm, slotted weight, and hub. Rotate the arm around the pivot by
`phase * 24` degrees. Keep only compact BPM controls, Tap, Start/Stop, and a
rhythm summary on the main screen. Reuse the bottom bar. Put meter,
subdivision, accent, sound, volume, mute, and count-in in one modal sheet.

Persist last settings after validation. Start always requires an explicit tap;
opening the screen never starts sound.

- [ ] **Step 4: Run all connected UI tests in Light, Dark, compact, and 1.3 font**

Expected: controls remain readable and no screen exceeds its safe bounds.

- [ ] **Step 5: Prepare the checkpoint commit**

```powershell
git add app/src/main/java/com/tuneitall/tuner/ui/MetronomeScreen.kt app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt app/src/main/java/com/tuneitall/tuner/ui/AppBottomBar.kt app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt app/src/main/res app/src/androidTest
git commit -m "feat(metronome): add Precision Panel controls"
```

Run this commit only after explicit approval.

### Task 12: Core acceptance verification

**Files:**
- Modify: `docs/architecture.md`
- Modify: `README.md` only after Core behavior passes live checks.

**Interfaces:**
- Verifies all interfaces from Tasks 1 through 11.

- [ ] **Step 1: Run the repository quality gate**

```powershell
.\tools\build.ps1 -AllowUnsigned
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
git diff --check
```

Expected: unit tests, lint, debug APK, release AAB, manifest checks, and connected
tests pass.

- [ ] **Step 2: Run Samsung live scenarios**

On `SM-S938B` verify:

1. Balanced tuner detects normal and quiet notes without a one-frame octave jump.
2. Silence and room noise do not hold a false note.
3. Light, Dark, Tuner, Metronome, Library, and secondary screens navigate.
   Chords and Trainer stay visibly disabled until their own slices land.
4. Metronome starts and stops cleanly at 20, 137, and 400 BPM.
5. Accent 2, 3, and 5 plus subdivisions 1 through 4 update without a pop.
6. The app leaves no audio session after backgrounding.

- [ ] **Step 3: Capture timing and performance evidence**

Record detector processing time for 500 frames and a five-minute metronome
frame trace. Require detector p95 below 42.7 ms, no growing backlog, metronome
schedule error below one frame, no `AudioTrack` underruns, and no fresh crash.

- [ ] **Step 4: Update documentation with measured facts**

Replace README references to the old median filter and stale alpha.2 download.
Describe the pYIN-derived online tracker accurately. State that metronome
playback is foreground-only, Chords and Trainer are not part of Core, and
acoustic sound quality still needs human listening. Do not tag or publish Core
as the complete musician toolkit.

- [ ] **Step 5: Review the entire diff and prepare one approved integration commit**

```powershell
git status --short
git diff --check
git diff --stat
git diff
```

After explicit user approval, either retain approved task commits or create the
requested Conventional Commit. Do not push, tag, release, or open a PR without
separate explicit approval.
