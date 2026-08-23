# Canonical chords, ear training, and graphics implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generated chord shapes and hand-built instrument silhouettes, then add single-note ear training.

**Architecture:** A small immutable catalog reads pinned `chords-db` resources. Chords and Trainer share that catalog. Downloaded licensed SVG geometry supplies the static headstock and metronome body while existing Compose layers keep labels, hit targets, selection, and audio-phase animation.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Android `org.json`, AndroidSVG 1.4, JUnit 4, Compose UI tests.

---

### Task 1: Replace generated shapes with canonical data

**Files:**
- Create: `app/src/main/res/raw/chords_db_guitar.json`
- Create: `app/src/main/res/raw/chords_db_ukulele.json`
- Create: `app/src/main/java/com/tuneitall/tuner/music/ChordShapeCatalog.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordModels.kt`
- Modify: `THIRD_PARTY_NOTICES.md`
- Test: `app/src/test/java/com/tuneitall/tuner/music/ChordShapeCatalogTest.kt`

- [x] **Step 1: Write the failing canonical-shape test**

```kotlin
@Test
fun `standard guitar uses canonical open and barre shapes`() {
    assertEquals(listOf(-1, 3, 2, 0, 1, 0), catalog.shape("guitar-6-standard", Chord(0, MAJOR))?.frets)
    assertEquals(listOf(3, 2, 0, 0, 0, 3), catalog.shape("guitar-6-standard", Chord(7, MAJOR))?.frets)
    assertEquals(listOf(1, 3, 3, 2, 1, 1), catalog.shape("guitar-6-standard", Chord(5, MAJOR))?.frets)
    assertEquals(listOf(-1, 2, 1, 2, 0, 2), catalog.shape("guitar-6-standard", Chord(11, DOMINANT_SEVENTH))?.frets)
    assertEquals(listOf(4, 6, 6, 4, 4, 4), catalog.shape("guitar-6-standard", Chord(8, MINOR))?.frets)
    assertEquals(listOf(0, 0, 0, 3), catalog.shape("ukulele-standard", Chord(0, MAJOR))?.frets)
}
```

- [x] **Step 2: Run the test and confirm the missing catalog failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.ChordShapeCatalogTest"`

Expected: FAIL because `ChordShapeCatalog` does not exist.

- [x] **Step 3: Vendor the pinned core data and implement the parser**

```kotlin
data class ChordVoicing(
    val frets: List<Int>,
    val fingers: List<Int>,
    val barres: List<Int>,
    val baseFret: Int,
)

class ChordShapeCatalog private constructor(
    private val shapes: Map<String, Map<Chord, ChordVoicing>>,
) {
    fun shape(tuningId: String, chord: Chord): ChordVoicing? = shapes[tuningId]?.get(chord)
}
```

The parser reads only Major, Minor, and Dominant 7 first positions from the two upstream files. It converts source-relative frets with `absolute = baseFret + relative - 1`. It keeps `-1` muted and `0` open. Only `guitar-6-standard` and `ukulele-standard` are valid catalog IDs.

- [x] **Step 4: Remove `findPlayableVoicing` and route audio through catalog frets**

Keep `voicingFrequencies(openNotes, voicing)`. Delete the backtracking search and its scoring constants.

- [x] **Step 5: Run catalog and music tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.*"`

Expected: PASS.

### Task 2: Render complete diagrams and repair Trainer

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordComponents.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TrainerScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordModels.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Test: `app/src/test/java/com/tuneitall/tuner/music/TrainerTest.kt`
- Test: `app/src/androidTest/java/com/tuneitall/tuner/MusicToolsScreenTest.kt`

- [x] **Step 1: Write failing tests for barre, fingers, unsupported tunings, and note questions**

```kotlin
@Test
fun `note question has one answer and four unique choices`() {
    val question = noteQuestion(seed = 5)
    assertEquals(4, question.choices.toSet().size)
    assertEquals(1, question.choices.count { it == question.answerPitchClass })
    assertEquals(60 + question.answerPitchClass, question.midiNote)
}
```

Compose tests must assert that the chord quiz has no `chord_diagram` before an answer and shows the canonical diagram after selection.

- [x] **Step 2: Run the focused tests and confirm behavior failures**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.TrainerTest"`

Expected: FAIL because `noteQuestion` does not exist.

- [x] **Step 3: Draw canonical details**

`ChordDiagram` draws a horizontal barre between the first and last matching string, writes finger numbers inside fretted dots, uses `baseFret`, and announces the exact fret sequence. If `catalog.shape()` returns null, show `chord_shape_standard_only` and draw no diagram.

- [x] **Step 4: Add Chords and Notes exercise selectors**

```kotlin
private enum class TrainerExercise { CHORDS, NOTES }

data class NoteQuestion(
    val answerPitchClass: Int,
    val midiNote: Int,
    val choices: List<Int>,
)
```

The Notes exercise plays `midiToHertz(question.midiNote)` through `ReferenceTonePlayer.play()`. It hides feedback until one of four choices is selected and keeps the same question for replay.

- [x] **Step 5: Run unit and connected Trainer tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.*"`

Run: `adb -s emulator-5584 shell am instrument -w -r com.tuneitall.tuner.test/androidx.test.runner.AndroidJUnitRunner`

Expected: PASS.

### Task 3: Use downloaded vector art

**Files:**
- Create: `app/src/main/res/raw/guitar_head_commons.svg`
- Create: `app/src/main/res/raw/metronome_body_cc0.svg`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/components/SvgAsset.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/components/Headstock.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/MetronomeScreen.kt`
- Modify: `THIRD_PARTY_NOTICES.md`
- Test: `app/src/androidTest/java/com/tuneitall/tuner/VectorAssetTest.kt`

- [x] **Step 1: Write a failing Android resource parse test**

```kotlin
@Test
fun bundledSvgAssetsParse() {
    assertTrue(SVG.getFromResource(context, R.raw.guitar_head_commons).documentWidth > 0f)
    assertTrue(SVG.getFromResource(context, R.raw.metronome_body_cc0).documentHeight > 0f)
}
```

- [x] **Step 2: Run the focused connected test and confirm missing resources**

Expected: FAIL because the raw SVG resources do not exist.

- [x] **Step 3: Add AndroidSVG and the pinned SVG files**

Add `implementation("com.caverock:androidsvg-aar:1.4")`. Keep the upstream metadata. Hide only the original metronome arm group `g3702` and its `path3717*` tick paths so TuneItAll can animate one arm.

- [x] **Step 4: Render the SVGs and preserve functional overlays**

`SvgAsset` parses once with `remember` and uses `drawIntoCanvas` to fit the `Picture`. Headstock keeps string lines, selected posts, and six labeled buttons. Metronome keeps the current `Choreographer` phase loop and rotates only the separate arm layer.

- [x] **Step 5: Run visual, bounds, and audio-phase tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.ui.*"`

Expected: PASS with stable component bounds and unchanged endpoint math.

### Task 4: Release verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Modify: `.github/release-notes/preview.md`
- Modify: `README.md`
- Modify: `fastlane/metadata/android/en-US/changelogs/5.txt`
- Modify: `fastlane/metadata/android/cs-CZ/changelogs/5.txt`
- Update: store screenshots

- [x] **Step 1: Set version `0.3.0-alpha.2` and version code `5`**

- [x] **Step 2: Run the repository gate**

Run: `./tools/build.ps1 -AllowUnsigned`

Expected: unit tests, lint, APK, AAB, and package verification pass.

- [x] **Step 3: Run the full API 35 connected suite and manual audio flows**

Verify canonical C, F, and B7 diagrams, chord replay, note replay, correct and incorrect note answers, metronome 40 and 400 BPM, settings navigation, and the tuner regression path. Check TuneItAll's active audio session for underruns.

- [ ] **Step 4: Commit, push `main`, tag, and watch both workflows**

Use Conventional Commits. Tag `v0.3.0-alpha.2`. Wait for Android CI and Preview APK release to finish.

- [ ] **Step 5: Verify the public APK**

Download the release APK and checksum. Verify SHA-256, preview certificate, package, version, permissions, install, resumed activity, live process, and empty fresh `AndroidRuntime` crash output.
