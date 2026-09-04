# Professional transcription, instruments, and privacy implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add measured fast chord transcription, Standard E acoustic instructions, expanded instrument tunings, and privacy disclosures that match the shipped app.

**Architecture:** Keep the existing offline Android decoder and pure-Kotlin DSP. Replace the two-second chord decision window with local chroma and onset-aware transitions, then add arrangement, catalog, UI, and privacy slices behind typed data. No network client or pretrained model is added.

**Tech Stack:** Kotlin 2.3.21, Android SDK 36 with min SDK 26, Jetpack Compose, Android `MediaExtractor` and `MediaCodec`, JUnit, AndroidX instrumentation tests, and the existing JSON chord catalogs.

**Spec:** `docs/superpowers/specs/2026-09-04-professional-transcription-instruments-privacy-design.md`

## Global constraints

- Keep application ID `com.tuneitall.tuner`.
- Keep the app offline, proprietary, ad-free, account-free, and bounded to 30-minute song files.
- Do not add a dependency, network permission, pretrained model, commercial audio fixture, or generated chord fingering.
- Use `BQLDU19927002646` for physical QA and an isolated `.qa` package.
- Write a failing test before each production change.
- Keep `main` buildable after every task.

---

### Task 1: Continuous chord evaluation

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/music/ChordEvaluation.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/music/ChordEvaluationTest.kt`

**Interfaces:**
- Consumes: `List<ChordEvent>` reference and estimate timelines.
- Produces: `evaluateChords(reference, estimate, songEndMillis): ChordEvaluation`.

- [ ] **Step 1: Write failing overlap tests**

```kotlin
@Test
fun `evaluation uses continuous overlap and reports boundary error`() {
    val reference = listOf(
        ChordEvent(0, 333, Chord(0, ChordQuality.MAJOR), 1.0),
        ChordEvent(333, 666, Chord(7, ChordQuality.MAJOR), 1.0),
        ChordEvent(666, 1_000, Chord(9, ChordQuality.MINOR), 1.0),
    )
    val result = evaluateChords(reference, reference, 1_000)
    assertEquals(1.0, result.rootWcsr, 0.0)
    assertEquals(1.0, result.qualityWcsr, 0.0)
    assertEquals(1.0, result.segmentationScore, 0.0)
    assertEquals(0.0, result.medianBoundaryErrorMillis, 0.0)
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.ChordEvaluationTest" --console=plain
```

Expected: compilation fails because `ChordEvaluation` and `evaluateChords` do not exist.

- [ ] **Step 3: Implement exact overlap metrics**

```kotlin
data class ChordEvaluation(
    val rootWcsr: Double,
    val majorMinorWcsr: Double,
    val qualityWcsr: Double,
    val segmentationScore: Double,
    val medianBoundaryErrorMillis: Double,
    val coverage: Double,
)

fun evaluateChords(
    reference: List<ChordEvent>,
    estimate: List<ChordEvent>,
    songEndMillis: Long,
): ChordEvaluation
```

Validate sorted, non-overlapping timelines and positive song length. Compute
overlap with two monotonic indices. Compute boundary errors against the nearest
estimated internal boundary. Use directional maximum-overlap loss for the two
segmentation directions and subtract the worse normalized loss from one.

- [ ] **Step 4: Verify GREEN and old model tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.ChordEvaluationTest" --tests "com.tuneitall.tuner.music.ChordModelsTest" --console=plain
```

- [ ] **Step 5: Commit the evaluation slice**

```text
test(chords): add continuous timeline metrics
```

### Task 2: Fast harmonic boundaries

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/music/HarmonicFeatureExtractor.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordAnalyzer.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordAnalyzerTest.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/SongSignalFixtures.kt`

**Interfaces:**
- `HarmonicFrame.chroma` becomes the local three-frame profile.
- Add `HarmonicFrame.contextChroma: FloatArray` for quality tie-breaking.
- `analyzeChords` keeps its current signature.
- Test fixtures add
  `sineChord(sampleRate: Int, durationSeconds: Double, vararg frequencies: Double): FloatArray`
  and `concatenate(vararg parts: FloatArray): FloatArray`.

- [ ] **Step 1: Write the failing 333 ms progression test**

```kotlin
@Test
fun `analyzer preserves three chords inside one second`() {
    val samples = concatenate(
        sineChord(48_000, 0.333, 261.63, 329.63, 392.0),
        sineChord(48_000, 0.333, 196.0, 246.94, 293.66),
        sineChord(48_000, 0.334, 220.0, 261.63, 329.63),
    )
    val analyzer = StreamingChordAnalyzer(48_000)
    analyzer.accept(samples)
    val events = analyzer.finish()
    val reference = listOf(
        ChordEvent(0, 333, Chord(0, ChordQuality.MAJOR), 1.0),
        ChordEvent(333, 666, Chord(7, ChordQuality.MAJOR), 1.0),
        ChordEvent(666, 1_000, Chord(9, ChordQuality.MINOR), 1.0),
    )
    assertEquals(listOf(0, 7, 9), events.map { it.chord.rootPitchClass })
    assertTrue(evaluateChords(reference, events, 1_000).medianBoundaryErrorMillis <= 100.0)
}
```

- [ ] **Step 2: Verify RED**

Run the new method. Expected: the two-second profile merges the progression or
moves a boundary beyond 100 ms.

- [ ] **Step 3: Produce local and context chroma**

Use standardized note frames directly for onset flux. Build local frames with
radius one and context frames with a 1.5-second window. Build bass chroma from
the local frame. Keep tuning correction and array bounds unchanged.

- [ ] **Step 4: Make transitions onset-aware**

Pass `frames` into `viterbi`. Use:

```kotlin
val transitionScale = 1.0 - 0.85 * frames[frameIndex].onsetStrength
val penalty = basePenalty * transitionScale.coerceIn(0.15, 1.0)
```

Set `MIN_EVENT_MILLIS = 170L`. Merge only equal chords separated by at most one
frame. Use context chroma only when two local quality scores differ by less
than `0.04`.

- [ ] **Step 5: Verify fast, noise, power, and GuitarSet tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.ChordAnalyzerTest" --console=plain
```

- [ ] **Step 6: Commit the boundary slice**

```text
fix(chords): preserve fast harmonic changes
```

### Task 3: Common chord qualities and inversions

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordModels.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordAnalyzer.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordShapeCatalog.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordComponents.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordModelsTest.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordAnalyzerTest.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordShapeCatalogTest.kt`

**Interfaces:**
- Add `bassPitchClass: Int? = null` to `Chord`.
- Keep `Chord(root, quality)` source compatibility.
- Add suffix mappings only for suffixes already present in bundled JSON.

- [ ] **Step 1: Write failing quality and inversion tests**

```kotlin
@Test
fun `common qualities expose exact pitch classes`() {
    assertEquals(setOf(0, 4, 7, 11), Chord(0, ChordQuality.MAJOR_SEVENTH).pitchClasses)
    assertEquals(setOf(0, 3, 7, 10), Chord(0, ChordQuality.MINOR_SEVENTH).pitchClasses)
    assertEquals(setOf(0, 3, 6, 10), Chord(0, ChordQuality.HALF_DIMINISHED_SEVENTH).pitchClasses)
}

@Test
fun `transposition moves root and inversion bass`() {
    assertEquals(
        Chord(2, ChordQuality.MAJOR, bassPitchClass = 6),
        Chord(0, ChordQuality.MAJOR, bassPitchClass = 4).transpose(2),
    )
}
```

- [ ] **Step 2: Verify RED**

Run `ChordModelsTest`. Expected: new enum members and constructor field are
missing.

- [ ] **Step 3: Add the bounded vocabulary**

Add `SUSPENDED_FOURTH`, `DIMINISHED`, `AUGMENTED`, `MAJOR_SIXTH`,
`MINOR_SIXTH`, `MAJOR_SEVENTH`, `MINOR_SEVENTH`,
`HALF_DIMINISHED_SEVENTH`, `ADD_NINTH`, and `MINOR_ADD_NINTH` with exact
interval sets. Validate the optional bass pitch class in `0..11`.

- [ ] **Step 4: Add extension evidence gates**

Score each candidate against its smallest required pitch set. Require every
defining extension to exceed both `0.18` salience and `1.6` times the median
outside salience in two consecutive local frames. Otherwise emit the contained
triad. Detect inversion bass only when bass salience exceeds the root by 20
percent in two consecutive frames.

- [ ] **Step 5: Reuse bundled voicings**

Map qualities to existing suffixes: `sus4`, `dim`, `aug`, `6`, `m6`, `maj7`,
`m7`, `m7b5`, `add9`, and `madd9`. Keep diagrams absent when a particular
instrument catalog has no reviewed entry.

- [ ] **Step 6: Run chord, catalog, and formatting tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.*" --console=plain
```

- [ ] **Step 7: Commit the vocabulary slice**

```text
feat(chords): add common qualities and inversions
```

### Task 4: Standard E acoustic arrangement

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/music/AcousticArrangement.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/music/AcousticArrangementTest.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordViewModel.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/MusicToolsScreenTest.kt`

**Interfaces:**
- Produces `AcousticArrangement(capo, instructions)`.
- `ChordUiState` gains `arrangementMode` and `arrangement`.
- Produces
  `arrangeForStandardE(chords: List<Chord>, catalog: ChordShapeCatalog, mode: ArrangementMode): AcousticArrangement`.

- [ ] **Step 1: Write failing capo and simplification tests**

```kotlin
@Test
fun `capo optimizer keeps one capo for the complete song`() {
    val chords = listOf(
        Chord(6, ChordQuality.MINOR),
        Chord(11, ChordQuality.MAJOR),
        Chord(2, ChordQuality.MAJOR),
    )
    val result = arrangeForStandardE(chords, testChordCatalog(), ArrangementMode.EXACT)
    assertEquals(2, result.capo)
    assertEquals(
        listOf(
            Chord(4, ChordQuality.MINOR),
            Chord(9, ChordQuality.MAJOR),
            Chord(0, ChordQuality.MAJOR),
        ),
        result.instructions.map { it.shapeChord },
    )
}

@Test
fun `simplified mode preserves root and major minor function`() {
    assertEquals(
        Chord(9, ChordQuality.MINOR),
        simplifyForAcoustic(Chord(9, ChordQuality.MINOR_SEVENTH)),
    )
}
```

`testChordCatalog()` reads the existing guitar and ukulele JSON files from
`app/src/main/res/raw` and calls `ChordShapeCatalog.parse`.

- [ ] **Step 2: Verify RED**

Run `AcousticArrangementTest`. Expected: arrangement types and functions do
not exist.

- [ ] **Step 3: Implement deterministic capo scoring**

```kotlin
enum class ArrangementMode { EXACT, SIMPLIFIED }

data class AcousticChordInstruction(
    val soundingChord: Chord,
    val shapeChord: Chord,
    val voicing: ChordVoicing?,
)

data class AcousticArrangement(
    val capo: Int,
    val instructions: List<AcousticChordInstruction>,
)
```

Evaluate capo `0..8`. Score missing voicing as unusable, then count barres,
base fret, fretted notes, and movement from the previous base fret. Resolve
ties toward the lower capo.

- [ ] **Step 4: Add exact and simplified controls**

Show sounding chord, shape chord, and capo in the fixed current-event panel.
Keep the diagram toggle in that panel. Recompute from immutable detected events
when mode changes.

- [ ] **Step 5: Run arrangement, ViewModel, and Compose tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.AcousticArrangementTest" --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
```

- [ ] **Step 6: Commit the arrangement slice**

```text
feat(chords): add Standard E arrangement
```

### Task 5: Bass and common instrument presets

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/model/TuningModels.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/model/TuningCatalog.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/UiLabels.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/CustomTuningScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/components/Headstock.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/model/TuningCatalogTest.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt`
- Modify: `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Add instrument enum members `VIOLIN`, `VIOLA`, `CELLO`, and `MANDOLIN`.
- Add layouts `INLINE_5` and `SPLIT_3_2` with `stringCount == 5`.
- Bass accepts 4, 5, and 6 strings.

- [ ] **Step 1: Write failing exact-preset tests**

```kotlin
@Test
fun `catalog includes five string bass and bowed standards`() {
    val expected = mapOf(
        "bass-5-standard" to "B0 E1 A1 D2 G2",
        "bass-5-drop-a" to "A0 E1 A1 D2 G2",
        "bass-6-standard" to "B0 E1 A1 D2 G2 C3",
        "violin-standard" to "G3 D4 A4 E5",
        "viola-standard" to "C3 G3 D4 A4",
        "cello-standard" to "C2 G2 D3 A3",
        "mandolin-standard" to "G3 D4 A4 E5",
    )
    expected.forEach { (id, spec) ->
        assertEquals(notes(spec), requireNotNull(TuningCatalog.byId(id)).notesLowToHigh)
    }
}
```

- [ ] **Step 2: Verify RED**

Run `TuningCatalogTest`. Expected: every new ID is absent.

- [ ] **Step 3: Extend typed model and catalog**

Add every preset from the spec. Use `INLINE_5` and `SPLIT_3_2` for five-string
bass, existing six-string layouts for six-string bass, and `SPLIT_2_2` for the
four-string instruments. Name the five-string standard preset `Standard B`.

- [ ] **Step 4: Extend custom-tuning validation and labels**

Return exact supported counts per instrument. Do not allow chromatic custom
presets. Add translated instrument labels to all five locale files.

- [ ] **Step 5: Render five strings and neutral four-string instruments**

Extend `sides()` and row mapping for five strings. Use no six-string guitar
bitmap when the active instrument is violin, viola, cello, or mandolin. Verify
string 5 is `B0` and string 1 is `G2` for standard five-string bass.

- [ ] **Step 6: Run catalog and Android UI tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.model.TuningCatalogTest" --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
```

- [ ] **Step 7: Commit the instrument slice**

```text
feat(tunings): add bass and string instruments
```

### Task 6: Obvious instrument discovery

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TuningLibraryScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt`

**Interfaces:**
- Instrument filtering remains local UI state.
- Favorites remain a separate first filter and first result group.

- [ ] **Step 1: Write failing filter-order tests**

```kotlin
@Test
fun `library shows favorites first and exposes five string bass`() {
    setLibrary(favoriteIds = setOf("bass-5-standard"))
    composeRule.onNodeWithTag("tuning_filter_favorites").assertIsDisplayed()
    composeRule.onNodeWithTag("tuning_filter_bass").performClick()
    composeRule.onNodeWithText("Standard B").assertIsDisplayed()
}
```

- [ ] **Step 2: Verify RED**

Run the new instrumentation method on the `.qa` package. Expected: the bass
filter or five-string preset is absent.

- [ ] **Step 3: Add instrument chips before results**

Use `Instrument.entries` except `CHROMATIC`. Selecting Favorites clears the
instrument selection. Selecting an instrument clears Favorites. Search applies
after either filter.

- [ ] **Step 4: Verify tuner selection end to end**

Select five-string bass, verify five targets, tap string 5, return to the
library, and verify the selection persists.

- [ ] **Step 5: Commit the discovery slice**

```text
feat(tunings): expose instrument filters
```

### Task 7: Privacy policy and Accessibility disclosure

**Files:**
- Modify: `docs/privacy/index.html`
- Modify: `docs/privacy/privacy-policy-en.md`
- Modify: `docs/privacy/privacy-policy-cs.md`
- Modify: `docs/store/data-safety.md`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/AboutScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/AutoScrollScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollPreferences.kt`
- Modify: `app/src/main/res/values*/strings.xml`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/MusicToolsScreenTest.kt`

**Interfaces:**
- `AutoScrollPreferences.disclosureAccepted: Boolean` defaults to false.
- Android settings open only after `I understand, continue`.

- [ ] **Step 1: Write a failing disclosure behavior test**

```kotlin
@Test
fun `accessibility settings require disclosure acceptance`() {
    var settingsOpened = false
    compose.setContent {
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
    compose.onNodeWithTag("auto_scroll_accessibility_action").performClick()
    compose.onNodeWithTag("auto_scroll_disclosure").assertIsDisplayed()
    assertFalse(settingsOpened)
    compose.onNodeWithTag("auto_scroll_disclosure_continue").performClick()
    assertTrue(settingsOpened)
}
```

Add a dismissal branch that leaves `settingsOpened == false`. Keep manifest
verification in `tools/verify-release.ps1`, which already inspects the built
artifact instead of source text.

- [ ] **Step 2: Verify RED**

Run the new `MusicToolsScreenTest` method. Expected: Android settings open
immediately because no disclosure gate exists.

- [ ] **Step 3: Replace public and in-app policy text**

Use the exact facts from the spec. State that app audio and song files never
leave the device. Separately disclose optional support correspondence, its
providers, purposes, legal bases, 12-month limit, and deletion contact.

- [ ] **Step 4: Gate Accessibility settings behind disclosure**

Show the disclosure before permission rows. If acceptance is false, the
Accessibility action opens a dedicated dialog. Only the affirmative button
stores acceptance and calls `onOpenAccessibilitySettings`. Dismissal changes
nothing.

- [ ] **Step 5: Run policy and Compose tests**

```powershell
.\tools\build.ps1 -AllowUnsigned
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
curl.exe -L --fail --head https://majkey25.github.io/TuneItAll/privacy/
```

- [ ] **Step 6: Commit the privacy slice**

```text
docs(privacy): align app and Play disclosures
```

### Task 8: Integrated acceptance and release decision

**Files:**
- Modify only after every gate passes: `CHANGELOG.md`, release notes, store
  descriptions, version metadata, and localized changelogs.

**Interfaces:**
- No new production API.

- [ ] **Step 1: Run all unit and build gates**

```powershell
.\tools\build.ps1 -AllowUnsigned
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
```

- [ ] **Step 2: Run licensed and synthetic accuracy gates**

Record root WCSR, major-minor WCSR, supported-vocabulary WCSR, segmentation,
boundary error, coverage, runtime, and memory. Do not release if a spec target
fails.

- [ ] **Step 3: Run Huawei end-to-end QA**

Install the isolated `.qa` APK and test:

1. A generated rapid progression through the real Android decoder.
2. Five-string bass manual and automatic screens.
3. Violin and cello preset selection.
4. Standard E acoustic arrangement playback follow.
5. Accessibility disclosure accept and dismiss paths.
6. Empty, unsupported, cancelled, and noise inputs.

Capture crash buffer, `AudioTrack` errors, runtime, and package metadata. Remove
the QA packages and restore device settings.

- [ ] **Step 4: Review the diff and artifact truth**

```powershell
git diff --check
git status --short
.\gradlew.bat :app:signingReport --console=plain
```

Verify no recording, signing key, `.qa` suffix, Internet permission, or
temporary artifact is staged.

- [ ] **Step 5: Prepare a release only if every target passes**

Use the next unused version code. Update public claims only with measured
results. Publishing to GitHub or Google Play remains a separate explicit
release action.
