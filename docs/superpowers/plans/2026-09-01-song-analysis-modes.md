# Offline Song Analysis Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the unstable song chord matcher with offline Classic chords, Notes, and Power chords modes that produce synchronized, readable timelines.

**Architecture:** Keep `SongAudioDecoder` and playback unchanged at the Android boundary. Decode PCM into one clean-room harmonic feature extractor, then route the bounded feature frames to a mode-specific chord or predominant-note decoder with temporal sequence smoothing.

**Tech Stack:** Kotlin, Android `MediaExtractor` and `MediaCodec`, Compose Material 3, coroutines, JUnit, AndroidX Compose tests, and the existing pure-Kotlin FFT code.

**Spec:** `docs/superpowers/specs/2026-09-01-song-analysis-modes-design.md`

## Global Constraints

- Keep `minSdk = 26` and Android 8.0 support.
- Keep the application proprietary and fully offline.
- Do not add Internet permission, a neural model, or a GPL or AGPL dependency.
- Keep the 30-minute input limit and feature storage below 32 MiB.
- Keep `MediaPlayer`, the fixed current-event bar, and timeline auto-follow.
- Default to Classic chords.
- Write and run a failing test before each production behavior change.
- Do not commit or redistribute the reported MP3.

---

### Task 1: Typed analysis modes and events

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/music/SongAnalysisModels.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/music/SongAnalysisModelsTest.kt`

**Interfaces:**
- Produces: `SongAnalysisMode`, `NoteRange`, `SongEvent`, and `NoteEvent`.
- `ChordEvent` implements `SongEvent` without changing its constructor.

- [ ] **Step 1: Write the failing model tests**

```kotlin
@Test
fun `analysis modes expose exact default ranges`() {
    assertEquals(21..108, NoteRange.ANY.midiRange)
    assertEquals(40..88, NoteRange.GUITAR.midiRange)
    assertEquals(28..72, NoteRange.BASS.midiRange)
    assertEquals(55..100, NoteRange.VIOLIN.midiRange)
    assertEquals(21..108, NoteRange.PIANO.midiRange)
}

@Test
fun `note event validates time midi and confidence`() {
    val event: SongEvent = NoteEvent(100L, 500L, 69, 0.9)
    assertEquals(400L, event.durationMillis)
    assertFailsWith<IllegalArgumentException> { NoteEvent(100L, 100L, 69, 0.9) }
    assertFailsWith<IllegalArgumentException> { NoteEvent(0L, 100L, 128, 0.9) }
}
```

- [ ] **Step 2: Run the model tests and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.SongAnalysisModelsTest" --console=plain
```

Expected: compilation fails because the new types do not exist.

- [ ] **Step 3: Add the minimal typed models**

```kotlin
enum class SongAnalysisMode { CHORDS, NOTES, POWER }

enum class NoteRange(val midiRange: IntRange) {
    ANY(21..108), GUITAR(40..88), BASS(28..72), VIOLIN(55..100), PIANO(21..108),
}

sealed interface SongEvent {
    val startMillis: Long
    val endMillis: Long
    val confidence: Double
    val durationMillis: Long get() = endMillis - startMillis
}

data class NoteEvent(
    override val startMillis: Long,
    override val endMillis: Long,
    val midiNote: Int,
    override val confidence: Double,
) : SongEvent
```

Add constructor validation equivalent to `ChordEvent`. Make `ChordEvent`
implement `SongEvent` and remove its duplicate `durationMillis` property.

- [ ] **Step 4: Run the model tests and the existing chord model tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.music.SongAnalysisModelsTest" --tests "com.tuneitall.tuner.music.ChordModelsTest" --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit the typed model slice**

```text
feat(song): add typed analysis modes
```

### Task 2: Shared harmonic feature extractor

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/music/HarmonicFeatureExtractor.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/music/HarmonicFeatureExtractorTest.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/music/SongSignalFixtures.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordAnalyzer.kt`

**Interfaces:**
- Produces: `HarmonicFrame(startMillis, chroma, bassChroma, noteSalience, tonalStrength)`.
- `chroma` and `bassChroma` each contain 12 values. `noteSalience` contains
  88 values where index zero is MIDI 21 and index 87 is MIDI 108.
- Produces: `StreamingHarmonicFeatureExtractor(sampleRate).accept(samples)` and `.finish()`.
- Consumes mono normalized PCM from `SongAudioDecoder`.

- [ ] **Step 1: Write failing feature tests**

Test exact properties rather than private FFT details:

```kotlin
@Test
fun `extractor keeps a detuned A peak in A chroma`() {
    val extractor = StreamingHarmonicFeatureExtractor(48_000)
    extractor.accept(sine(48_000, seconds = 3, hertz = 445.0))
    val frames = extractor.finish()
    val frame = frames[frames.size / 2]
    assertEquals(9, frame.chroma.indices.maxBy(frame.chroma::get))
}

@Test
fun `two second aggregation suppresses isolated percussion`() {
    val frames = extract(noisyPowerRiff(rootHertz = 82.41, seconds = 5))
    assertTrue(frames.drop(12).dropLast(12).all { it.chroma[4] > it.chroma[2] })
}

@Test
fun `silence has no tonal salience`() {
    assertTrue(extract(FloatArray(48_000 * 3)).all { it.tonalStrength == 0.0 })
}
```

Add deterministic `sine`, `sineChord`, `noisyPowerRiff`, and `changingNoise`
fixtures to `SongSignalFixtures.kt`. Build the noisy riff from a clipped root
and fifth plus seeded short broadband impulses so the same samples run on every
machine.

- [ ] **Step 2: Run the extractor tests and verify RED**

Run the new test class. Expected: compilation fails because the extractor does
not exist.

- [ ] **Step 3: Implement the minimum shared extractor**

Move the FFT helper and STFT window ownership from `ChordAnalyzer.kt` into the
new file. Produce three-bins-per-semitone salience from 27.5 Hz through 4,200
Hz, clamp global tuning to plus or minus 50 cents, standardize each frequency
bin over six seconds, and aggregate frames over a centered two-second window.
Store feature arrays as `FloatArray` values. Reject non-finite PCM and keep the
existing duration bound.

- [ ] **Step 4: Run extractor tests and existing chord tests**

Expected: all selected tests pass and the synthetic C-to-G ordering remains
unchanged.

- [ ] **Step 5: Commit the shared feature slice**

```text
feat(song): extract stable harmonic features
```

### Task 3: Classic and Power chord sequence decoders

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/music/ChordAnalyzer.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordAnalyzerTest.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/music/ChordModelsTest.kt`

**Interfaces:**
- Produces: `analyzeChords(frames, SongAnalysisMode.CHORDS): List<ChordEvent>`.
- Produces: `analyzeChords(frames, SongAnalysisMode.POWER): List<ChordEvent>`.
- Consumes only `HarmonicFrame` values from Task 2.

- [ ] **Step 1: Write failing decoder tests**

```kotlin
@Test
fun `power mode keeps a distorted riff stable through drum transients`() {
    val events = analyzeSong(noisyPowerRiff(82.41, seconds = 6), SongAnalysisMode.POWER)
    val longest = events.maxBy(ChordEvent::durationMillis)
    assertEquals(Chord(4, ChordQuality.POWER), longest.chord)
    assertTrue(longest.durationMillis >= 4_000L)
    assertTrue(events.all { it.chord.quality == ChordQuality.POWER })
}

@Test
fun `classic mode does not turn an E power chord into E7`() {
    val events = analyzeSong(noisyPowerRiff(82.41, seconds = 6), SongAnalysisMode.CHORDS)
    assertTrue(events.none { it.chord == Chord(4, ChordQuality.DOMINANT_SEVENTH) })
}

@Test
fun `classic mode accepts a persistent genuine dominant seventh`() {
    val events = analyzeSong(sineChord(196.0, 246.94, 293.66, 349.23), SongAnalysisMode.CHORDS)
    assertEquals(Chord(7, ChordQuality.DOMINANT_SEVENTH), events.maxBy(ChordEvent::durationMillis).chord)
}
```

- [ ] **Step 2: Run the tests and verify RED**

Expected: the mode-specific entry point is missing or the existing matcher
emits unstable or dominant-seventh labels.

- [ ] **Step 3: Implement mode-specific emissions and Viterbi smoothing**

Classic states are `no chord`, 12 major, 12 minor, and 12 dominant seventh.
Power states are `no chord` plus 12 power chords. Score in-chord evidence minus
out-of-chord energy. Require 750 ms of flat-seventh evidence above twice the
median out-of-chord salience before allowing dominant seventh. Use one rolling
Viterbi cost row and compact backpointers. Prefer persistence, not a hardcoded
genre progression. Merge matching neighbors and matching sides around gaps
shorter than 350 ms.

- [ ] **Step 4: Run chord tests and the full unit suite**

Expected: the new tests and every existing unit test pass.

- [ ] **Step 5: Commit the chord decoder slice**

```text
fix(chords): decode stable song progressions
```

### Task 4: Predominant-note decoder

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/music/PredominantNoteAnalyzer.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/music/PredominantNoteAnalyzerTest.kt`

**Interfaces:**
- Produces: `analyzeNotes(frames, NoteRange): List<NoteEvent>`.
- Consumes `HarmonicFrame.noteSalience` from Task 2.

- [ ] **Step 1: Write failing melody tests**

```kotlin
@Test
fun `note mode follows an annotated A4 C5 E5 melody`() {
    val events = analyzeMelody(sequenceOf(69, 72, 76), NoteRange.VIOLIN)
    assertEquals(listOf(69, 72, 76), events.map(NoteEvent::midiNote))
    assertTrue(events.all { it.confidence >= 0.5 })
}

@Test
fun `violin mode excludes bass candidates`() {
    val events = analyzeNotes(extract(sine(48_000, 3, 82.41)), NoteRange.VIOLIN)
    assertTrue(events.isEmpty())
}

@Test
fun `changing broadband noise produces no notes`() {
    assertTrue(analyzeNotes(extract(changingNoise(48_000, 4)), NoteRange.ANY).isEmpty())
}
```

- [ ] **Step 2: Run the note tests and verify RED**

Expected: compilation fails because `analyzeNotes` does not exist.

- [ ] **Step 3: Implement harmonic-salience Viterbi decoding**

Evaluate MIDI candidates inside `NoteRange.midiRange` plus `no note`. Penalize
an upper-harmonic candidate when a stronger fundamental explains the same
peaks. Prefer small note transitions, but reduce the jump penalty at a detected
onset. Merge repeated notes across gaps shorter than 120 ms.

- [ ] **Step 4: Run note tests and the full unit suite**

Expected: all unit tests pass.

- [ ] **Step 5: Commit the note decoder slice**

```text
feat(song): add predominant note mode
```

### Task 5: Android orchestration and Compose UI

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/audio/SongAudioDecoder.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordViewModel.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordComponents.kt`
- Modify: `app/src/main/res/values*/strings.xml`
- Create: `app/src/androidTest/java/com/tuneitall/tuner/ChordViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/MusicToolsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/SongChordDecoderTest.kt`

**Interfaces:**
- `SongAudioDecoder.analyze(uri, mode, noteRange, isCancelled, onProgress)`.
- `ChordViewModel.setSongAnalysisMode(mode)` and `setNoteRange(range)`.
- `ChordUiState.events: List<SongEvent>`.

- [ ] **Step 1: Write failing ViewModel and Compose tests**

Use a generated local WAV with the real `SongAudioDecoder` for the ViewModel
test:

```kotlin
@Test
fun changingToNotesKeepsTheSongAndPublishesOnlyNoteEvents() = runBlocking {
    val application = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as Application
    val file = createMelodyWav(application.cacheDir, listOf(69, 72, 76))
    val viewModel = ChordViewModel(application)
    try {
        viewModel.loadSong(Uri.fromFile(file))
        viewModel.setSongAnalysisMode(SongAnalysisMode.NOTES)
        val state = withTimeout(15_000L) {
            viewModel.uiState.first {
                it.mode == SongAnalysisMode.NOTES && !it.analyzing && it.events.isNotEmpty()
            }
        }
        assertEquals(file.name, state.fileName)
        assertTrue(state.events.all { it is NoteEvent })
    } finally {
        viewModel.clearSong()
        file.delete()
    }
}
```

Add Compose tests with static typed state:

```kotlin
@Test
fun notesModeShowsRangesAndHidesChordDiagrams() {
    val state = songState(
        mode = SongAnalysisMode.NOTES,
        events = listOf(NoteEvent(0L, 1_000L, 69, 0.9)),
    )
    setChordsContent(state)
    compose.onNodeWithTag("song_mode_notes").assertIsSelected()
    compose.onNodeWithTag("song_note_range_violin").assertIsDisplayed()
    compose.onNodeWithTag("song_diagrams_toggle").assertDoesNotExist()
    compose.onNodeWithTag("current_song_note").assertTextEquals("A4")
}

@Test
fun powerModeHidesUnreviewedDiagrams() {
    setChordsContent(songState(mode = SongAnalysisMode.POWER))
    compose.onNodeWithTag("song_diagrams_toggle").assertDoesNotExist()
}
```

- [ ] **Step 2: Run selected tests and verify RED**

Run the ViewModel unit class and the affected Compose instrumentation methods.
Expected: missing mode actions and UI tags.

- [ ] **Step 3: Route PCM and UI state by mode**

Keep one decode implementation. Route finished features to the selected
decoder. On a mode or range change, increment `analysisGeneration`, clear only
events and analysis errors, retain `currentSongUri`, playback position, and the
current `MediaPlayer`, then restart analysis.

Add tags `song_mode_chords`, `song_mode_notes`, `song_mode_power`,
`song_note_range_any`, `song_note_range_guitar`, `song_note_range_bass`,
`song_note_range_violin`, and `song_note_range_piano`. Reuse the existing fixed
bar and timeline components.
Add localized EN, CS, DE, FR, and ES strings for each mode, each range, Current
note, and mode-specific empty results.

- [ ] **Step 4: Run unit, decoder, and connected UI tests**

Expected: selected tests pass on the Huawei QA package without replacing the
Play-installed production package.

- [ ] **Step 5: Commit the Android/UI slice**

```text
feat(song): expose chord note and power modes
```

### Task 6: Real-song acceptance and alpha.15 release

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Modify: `README.md`
- Modify: `.github/release-notes/preview.md`
- Modify: `docs/architecture.md`
- Modify: `docs/store/play-listing-cs.md`
- Modify: `docs/store/play-listing-en.md`
- Modify: `fastlane/metadata/android/*/full_description.txt`
- Create: `fastlane/metadata/android/cs-CZ/changelogs/18.txt`
- Create: `fastlane/metadata/android/en-US/changelogs/18.txt`

**Interfaces:**
- Produces version `0.3.0-alpha.15`, `versionCode 18`.

- [ ] **Step 1: Run the temporary real-song probe on Huawei**

Use an isolated `.qa` application ID and the exact reported MP3. Do not add the
MP3 to Git. Record duration, runtime, coverage, median event duration, event
count, and quality distribution for each mode.

Required results:

- All three modes decode the complete 210,576 ms input without a crash.
- Power covers at least 55% of the file, median duration is at least 750 ms,
  and every event is `POWER`.
- Classic does not return dominant seventh as the majority quality.
- Guitar Notes stays inside the selected MIDI range and covers at least 15% of
  the complete reported file.
- Each mode completes in at most 30 seconds.

- [ ] **Step 2: Run the full release gate**

Run:

```powershell
.\tools\build.ps1 -AllowUnsigned
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
```

Expected: unit tests, Lint, APK, AAB, and instrumentation compilation pass.

- [ ] **Step 3: Review the complete diff**

Run `git diff --check`, inspect every changed file, scan for secrets, confirm
that the application ID remains `com.tuneitall.tuner`, and verify that no QA
suffix or reported-song artifact is staged.

- [ ] **Step 4: Commit the release metadata**

```text
chore(release): prepare alpha.15
```

- [ ] **Step 5: Publish GitHub and Play Closed Alpha**

Push `main`, wait for Android CI, tag `v0.3.0-alpha.15`, verify the public APK
and checksum, run the signed Play bundle workflow, and verify the AAB hash.
Upload `versionCode 18` to the existing Alpha channel with EN and CS notes and
submit it to Google review for 100% of the current testers.
