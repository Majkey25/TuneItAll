# TuneItAll Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, package, document, and physically verify a proprietary offline Android tuner named TuneItAll for guitar, bass, ukulele, and chromatic tuning.

**Architecture:** One Kotlin/Compose application module. `AudioRecord` feeds a clean-room YIN detector; a small typed tuner engine maps pitch to preset or chromatic targets; one `ViewModel` owns lifecycle and immutable UI state. Android platform APIs provide persistence and reference-tone playback, avoiding copyleft DSP and unnecessary frameworks.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose BOM 2026.06.01, Material 3, Android API 26-36, AGP 8.13.2, Gradle 8.13, JDK 17, JUnit 4, AndroidX Compose UI tests.

---

## Execution rules

- Repository root: `C:\Users\mates\Documents\Codex\2026-08-09\pot-ebuju-ud-lat-aplikaci-ladi\outputs\tuneitall-android`.
- Apply code changes with patches. Keep scratch artifacts under `.reference/tmp`.
- Do not commit, push, create a PR, or publish a release without explicit user approval. The commit steps normally required by this skill are replaced by `git diff --check` checkpoints.
- Run the smallest relevant test after each implementation step, then run full gates before device installation.
- Treat the active spec at `docs/superpowers/specs/2026-08-09-tuneitall-design.md` as authoritative.

## File map

```text
app/src/main/java/com/tuneitall/tuner/
  MainActivity.kt                  Android entry and microphone permission request
  TuneItAllApp.kt                 Typed screen state and app-level composition
  audio/AudioInput.kt             AudioRecord lifecycle and sample delivery
  audio/ReferenceTonePlayer.kt    AudioTrack sine generator with fades
  audio/YinPitchDetector.kt       Clean-room YIN detector
  model/TuningModels.kt           Typed note, tuning, instrument, and headstock models
  model/TuningCatalog.kt          Immutable verified preset catalog
  storage/UserPreferences.kt      Bounded SharedPreferences persistence
  tuner/MusicMath.kt              Equal-temperament frequency/note/cents conversion
  tuner/TunerEngine.kt            Gating, smoothing, target selection, hysteresis
  ui/TunerViewModel.kt            Lifecycle and immutable UI state
  ui/TunerScreen.kt               Primary tuner surface
  ui/TuningLibraryScreen.kt       Search, instruments, favorites, preset selection
  ui/CustomTuningScreen.kt        Validated custom-tuning editor
  ui/SettingsScreen.kt            A4 and notation settings
  ui/AboutScreen.kt               Privacy, licence, app/version information
  ui/components/CentsRail.kt      Accessible horizontal cents indicator
  ui/components/Headstock.kt      Data-driven string/peg vector rendering
  ui/theme/Theme.kt               Restrained TuneItAll design tokens
app/src/test/java/com/tuneitall/tuner/
  audio/YinPitchDetectorTest.kt
  model/TuningCatalogTest.kt
  storage/CustomTuningCodecTest.kt
  tuner/MusicMathTest.kt
  tuner/TunerEngineTest.kt
app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt
docs/store/                       Store copy, data safety, release checklist
docs/privacy/                     Czech and English privacy policy
assets/                           Banner and source marketing assets
fastlane/metadata/android/        Play Store text and screenshots
```

### Task 1: Bootstrap verified Android toolchain

**Files:**
- Create: `.reference/tmp/gradle-8.13-bin.zip`

- [x] **Step 1: Install the verified JDK package**

Run:

```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
```

Expected: Temurin 17 installs successfully. Open a new non-login shell and run `java -version`; output contains `17.0.20` or a newer 17.x maintenance build.

- [x] **Step 2: Download and verify Gradle 8.13**

Run these as separate commands:

```powershell
New-Item -ItemType Directory -Force -Path '.reference\tmp'
Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile '.reference\tmp\gradle-8.13-bin.zip'
Get-FileHash -Algorithm SHA256 -LiteralPath '.reference\tmp\gradle-8.13-bin.zip'
```

Expected SHA-256: `20F1B1176237254A6FC204D8434196FA11A4CFB387567519C61556E8710AED78`.

- [x] **Step 3: Extract and verify the Gradle distribution**

Run:

```powershell
Expand-Archive -LiteralPath '.reference\tmp\gradle-8.13-bin.zip' -DestinationPath '.reference\tmp\gradle-8.13'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
& '.\.reference\tmp\gradle-8.13\gradle-8.13\bin\gradle.bat' --version
```

Expected: Gradle 8.13 and JVM 17. Wrapper generation occurs after Task 2 creates the settings file required by Gradle.

### Task 2: Create the minimal Android project

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/tuneitall/tuner/MainActivity.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-cs/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

- [x] **Step 1: Write Gradle settings and plugin versions**

Use:

```kotlin
// settings.gradle.kts
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TuneItAll"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
```

- [x] **Step 2: Configure the single application module**

Use `namespace = "com.tuneitall.tuner"`, `applicationId = "com.tuneitall.tuner"`, `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`, `versionCode = 1`, and `versionName = "0.1.0"`. Set Java/Kotlin target 17 and enable Compose.

Dependencies:

```kotlin
implementation("androidx.core:core-ktx:1.17.0")
implementation("androidx.activity:activity-compose:1.13.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
implementation(platform("androidx.compose:compose-bom:2026.06.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
testImplementation("junit:junit:4.13.2")
testImplementation(kotlin("test"))
testImplementation("org.json:json:20260719")
androidTestImplementation("androidx.test.ext:junit:1.3.0")
androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-tooling")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [x] **Step 3: Add the only runtime permission**

The manifest contains exactly:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Do not declare `INTERNET`, storage, advertising, or notification permissions.

- [x] **Step 4: Add a smoke activity and restrained theme**

`MainActivity` must call `setContent { TuneItAllTheme { Text(stringResource(R.string.app_name)) } }`. English and Czech `app_name` both equal `TuneItAll`. Theme colors are near-black `#101210`, warm off-white `#F3F1EA`, and accuracy green `#63D17A`.

- [x] **Step 5: Build the scaffold**

Run: `.\gradlew.bat :app:assembleDebug :app:lintDebug`

Before this build, run the verified extracted Gradle once to create the wrapper:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
& '.\.reference\tmp\gradle-8.13\gradle-8.13\bin\gradle.bat' wrapper --gradle-version 8.13 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL`; APK exists under `app/build/outputs/apk/debug/` and lint has no errors.

Implementation note: Core 1.19.0 and Lifecycle 2.11.0 require API 37 and AGP 9.1.0. The project pins Core 1.17.0 and Lifecycle 2.10.0, the compatible production line for compileSdk 36 and AGP 8.13.2.

### Task 3: Implement typed music and tuning models

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/model/TuningModels.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/tuner/MusicMath.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/tuner/MusicMathTest.kt`

- [x] **Step 1: Write failing conversion and validation tests**

Include assertions equivalent to:

```kotlin
assertEquals(440.0, MusicMath.frequency(69, 440.0), 1e-9)
assertEquals(444.0, MusicMath.frequency(69, 444.0), 1e-9)
assertEquals(0.0, MusicMath.cents(440.0, 440.0), 1e-9)
assertEquals(69, MusicMath.nearestMidi(440.0, 440.0))
assertFailsWith<IllegalArgumentException> { ReferencePitch(409.9) }
assertFailsWith<IllegalArgumentException> { ReferencePitch(Double.NaN) }
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.MusicMathTest"`

Expected: FAIL because the types do not exist.

- [x] **Step 2: Add precise domain types**

Define:

```kotlin
@JvmInline value class MidiNote(val value: Int)
@JvmInline value class ReferencePitch(val hertz: Double)
enum class Instrument { GUITAR, BASS, UKULELE, CHROMATIC }
enum class HeadstockLayout { INLINE_4, SPLIT_2_2, INLINE_6, SPLIT_3_3, INLINE_7, SPLIT_4_3, INLINE_8, SPLIT_4_4, INLINE_9, SPLIT_5_4 }
data class TuningPreset(
    val id: String,
    val name: String,
    val instrument: Instrument,
    val notesLowToHigh: List<MidiNote>,
    val layouts: Set<HeadstockLayout>,
)
```

`MidiNote` validates `0..127`. `ReferencePitch` validates finite `410.0..480.0`. `TuningPreset` validates non-blank ID/name and the exact instrument counts: guitar 6/7/8/9, bass 4, ukulele 4. Chromatic mode has no `TuningPreset`. Headstock capacity is an exhaustive `when`: inline/split 4 -> 4, inline/split 6 -> 6, inline/split 7 -> 7, inline/split 8 -> 8, inline/split 9 -> 9. Re-entrant ukulele means the model must not require ascending pitch.

- [x] **Step 3: Implement equal-temperament math**

Use:

```kotlin
fun frequency(midi: Int, a4: Double): Double = a4 * 2.0.pow((midi - 69) / 12.0)
fun nearestMidi(hertz: Double, a4: Double): Int = (69 + 12 * log2(hertz / a4)).roundToInt()
fun cents(hertz: Double, target: Double): Double = 1200 * log2(hertz / target)
```

Validate positive finite frequencies and reference pitch before calculation.

- [x] **Step 4: Run tests and diff check**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.MusicMathTest"
git diff --check
```

Expected: PASS; no whitespace errors.

### Task 4: Build and validate the preset catalog

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/model/TuningCatalog.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/model/TuningCatalogTest.kt`

- [x] **Step 1: Write failing catalog invariants**

Tests must require unique stable IDs, non-empty names, valid MIDI values, matching headstock string counts, no duplicate note sequence per instrument/string count, standard presets for 6/7/8/9 guitar, 4-string bass, ukulele, and named Drop D through Drop F coverage.

- [x] **Step 2: Implement immutable verified presets**

Use MIDI values and comments with scientific pitch notation. Minimum catalog:

```kotlin
TuningPreset("guitar-6-standard", "Standard E", GUITAR, notes("E2 A2 D3 G3 B3 E4"), setOf(INLINE_6, SPLIT_3_3))
TuningPreset("guitar-6-drop-d", "Drop D", GUITAR, notes("D2 A2 D3 G3 B3 E4"), setOf(INLINE_6, SPLIT_3_3))
TuningPreset("guitar-7-standard", "Standard B", GUITAR, notes("B1 E2 A2 D3 G3 B3 E4"), setOf(INLINE_7, SPLIT_4_3))
TuningPreset("guitar-8-standard", "Standard F♯", GUITAR, notes("F#1 B1 E2 A2 D3 G3 B3 E4"), setOf(INLINE_8, SPLIT_4_4))
TuningPreset("guitar-9-standard", "Standard C♯", GUITAR, notes("C#1 F#1 B1 E2 A2 D3 G3 B3 E4"), setOf(INLINE_9, SPLIT_5_4))
TuningPreset("bass-4-standard", "Standard E", BASS, notes("E1 A1 D2 G2"), setOf(INLINE_4, SPLIT_2_2))
TuningPreset("ukulele-standard", "Standard C", UKULELE, notes("G4 C4 E4 A4"), setOf(SPLIT_2_2))
```

Add verified lowered-standard, Drop D/Db/C/B/A/G/F, DADGAD, Open D, Open E, Open G, Open A, and relevant extended-range drop presets. Do not infer ambiguous names; each note sequence is explicit.

Define the mechanical scientific-note parser used above:

```kotlin
internal fun notes(spec: String): List<MidiNote>
```

Split on whitespace, parse `A..G` with an optional `#` or `b` accidental and a scientific octave, and calculate MIDI as `(octave + 1) * 12 + pitchClass`. Reject empty specifications, malformed notes, missing octaves, and out-of-range results.

- [x] **Step 3: Run focused tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.TuningCatalogTest"`

Expected: PASS with every catalog invariant checked.

### Task 5: Implement clean-room YIN with synthetic fixtures

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/YinPitchDetector.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/audio/YinPitchDetectorTest.kt`

- [x] **Step 1: Write failing signal tests**

Generate deterministic PCM16 sine and harmonic-rich signals in test code. Cover `30.87`, `34.65`, `41.20`, `46.25`, `82.41`, `110.0`, `440.0`, and C8 `4186.01`. Require ±1 cent on stable signals, no result for silence, and no octave jump when the second harmonic is stronger than the fundamental. Chromatic detection accepts only A0 through C8 (`27.5..4186.01 Hz`).

- [x] **Step 2: Implement the detector contract**

```kotlin
data class PitchEstimate(val hertz: Double, val confidence: Double, val rms: Double)

class YinPitchDetector(private val threshold: Double = 0.15) {
    fun detect(
        samples: ShortArray,
        sampleRate: Int,
        minFrequency: Double,
        maxFrequency: Double,
    ): PitchEstimate?
}
```

Implementation rules: validate arguments; preallocate/reuse difference and cumulative-mean arrays; calculate RMS; search `tau` only between `sampleRate / maxFrequency` and `sampleRate / minFrequency`; use YIN cumulative mean normalized difference; select the first threshold crossing followed to its local minimum; refine with the two neighboring values; return `sampleRate / refinedTau`; return null for weak RMS or confidence.

- [x] **Step 3: Prove low-note and negative paths**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.YinPitchDetectorTest"`

Expected: all frequency, octave, silence, noise, and validation cases pass.

- [x] **Step 4: Profile the pure detector locally**

Add one JUnit timing assertion that processes 100 pre-generated 4096-sample frames without allocation setup inside the measured loop. Keep the threshold generous enough for CI variance and use physical-device tracing as the real performance gate.

### Task 6: Add target selection, gating, smoothing, and hysteresis

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/tuner/TunerEngine.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/tuner/TunerEngineTest.kt`

- [x] **Step 1: Write failing behavior tests**

Cover Auto closest-string selection, Manual fixed target, Chromatic nearest note, ± cents direction, no state for rejected confidence/RMS, three-value median, and target hysteresis around adjacent strings.

- [x] **Step 2: Implement typed state**

```kotlin
enum class TunerMode { AUTO, MANUAL, CHROMATIC }
data class TunerReading(
    val detected: MidiNote,
    val target: MidiNote,
    val hertz: Double,
    val cents: Double,
    val inTune: Boolean,
)
```

`TunerEngine.update(estimate, mode, tuning, selectedString, referencePitch)` returns `TunerReading?`. In-tune is `abs(cents) <= 2.0`. Clamp only the visual rail, never the stored cents value. Reset median/hysteresis when mode, tuning, selected string, or reference pitch changes.

- [x] **Step 3: Run focused and neighboring tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.TunerEngineTest" --tests "*.MusicMathTest"`

Expected: PASS.

### Task 7: Implement microphone capture and reference tone

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/AudioInput.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/audio/ReferenceTonePlayer.kt`

- [x] **Step 1: Implement lifecycle-safe `AudioRecord`**

`AudioInput.start(windowSize: Int, onWindow: (ShortArray, Int) -> Unit, onError: (AudioInputError) -> Unit)` requests mono PCM16 at 48 kHz, reads the initialized recorder's actual sample rate, uses `UNPROCESSED` only when `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED == "true"`, and otherwise uses `VOICE_RECOGNITION`. One audio-priority thread fills a reusable ring buffer and invokes the detector synchronously with a reusable complete analysis window at a bounded cadence; the callback must not retain the buffer. `stop()` interrupts, joins, stops, and releases exactly once. `AudioInputError` is a sealed interface with `PermissionMissing`, `InitializationFailed`, and `ReadFailed` values.

- [x] **Step 2: Implement click-free `AudioTrack` tone playback**

`ReferenceTonePlayer.play(hertz)` validates the requested frequency, generates one second of harmonic-rich mono PCM16 at 48 kHz, applies a plucked decay and silent tail, plays once in `MODE_STATIC`, and exposes idempotent `stop()`/`close()`.

- [x] **Step 3: Compile and inspect platform error paths**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:lintDebug`

Expected: PASS; no missing permission annotation, leaked recorder, or unsupported API lint error.

### Task 8: Add bounded preferences and custom tuning codec

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/storage/CustomTuningCodecTest.kt`

- [x] **Step 1: Write failing codec tests**

Cover round-trip, corrupt item skipping, duplicate ID rejection, invalid MIDI rejection, 100-item limit, and serialize-once JSON shape.

- [x] **Step 2: Implement platform-only persistence**

Use `SharedPreferences` for `mode`, `last_tuning_id`, `headstock_layout`, `a4_hertz`, notation, and a copied `Set<String>` of favorite IDs. Store custom tunings as one JSON array with fields `id`, `name`, `instrument`, `notes`, and `layout`. Validate every decoded object before constructing `TuningPreset`. Persist with `apply()` only after validation.

- [x] **Step 3: Run persistence tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*.CustomTuningCodecTest"`

Expected: PASS; malformed entries do not discard valid siblings.

### Task 9: Build `TunerViewModel` and permission/lifecycle flow

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/MainActivity.kt`

- [ ] **Step 1: Define one immutable UI state**

```kotlin
data class TunerUiState(
    val mode: TunerMode,
    val tuning: TuningPreset,
    val selectedString: Int,
    val referencePitch: ReferencePitch,
    val reading: TunerReading?,
    val microphoneGranted: Boolean,
    val listening: Boolean,
    val referenceTonePlaying: Boolean,
    val error: String?,
)
```

- [ ] **Step 2: Connect audio without blocking the main thread**

The `ViewModel` owns `AudioInput`, `YinPitchDetector`, `TunerEngine`, preferences, and tone player. Audio callbacks run detection on the audio worker and post only the typed result to immutable state. `onStart()` begins capture only with permission; `onStop()` stops capture and tone playback; `onCleared()` releases both.

- [ ] **Step 3: Request microphone exactly once in context**

`MainActivity` uses `rememberLauncherForActivityResult(RequestPermission())`. On first composition it automatically launches the single Android permission request while the tuner surface and one-line microphone explanation are already rendered. Denial keeps the surface usable. Permanent denial offers an explicit application-settings intent after user tap.

- [ ] **Step 4: Compile and run lifecycle lint**

Run: `.\gradlew.bat :app:compileDebugKotlin :app:lintDebug`

Expected: PASS.

### Task 10: Build the primary tuner UI and headstocks

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/components/CentsRail.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/components/Headstock.kt`

- [ ] **Step 1: Implement the cents rail**

`CentsRail(cents, inTune, modifier)` draws a -50..+50 horizontal rail with 10-cent ticks, a centered zero mark, a clamped moving marker, text direction, and semantic state such as `"12 cents flat"`. Color supplements shape/text and is not the only signal.

- [ ] **Step 2: Implement data-driven peg placement**

`Headstock(layout, notes, selectedIndex, onStringSelected)` derives peg count and side from `HeadstockLayout`; it never receives a separate peg count. Tests/preview assertions require `notes.size` to match layout capacity. Inline and split variants use the same note order as the tuning model.

- [ ] **Step 3: Compose the immediate tuner surface**

Use one vertically responsive screen: top tuning/favorite/settings header, three-mode segmented control, large note, cents rail, frequency/cents details, and headstock/string controls. String taps play one-shot reference tones; no separate reference action is shown. Chromatic mode omits all preset and headstock controls. No loading screen, dialog, bottom-sheet onboarding, ad slot, or unused navigation bar.

- [ ] **Step 4: Add phone/tablet and font-scale previews**

Preview 1080x2340-equivalent portrait, compact height, dark/light theme, and font scale 1.3. Fix clipping before device install.

### Task 11: Implement library, custom tuning, settings, and About

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/ui/TuningLibraryScreen.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/CustomTuningScreen.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/ui/AboutScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`

- [ ] **Step 1: Add typed screen state without a navigation dependency**

```kotlin
sealed interface AppScreen {
    data object Tuner : AppScreen
    data object Library : AppScreen
    data object CustomTuning : AppScreen
    data object Settings : AppScreen
    data object About : AppScreen
}
```

Handle Android back by returning to `Tuner` from secondary screens.

- [ ] **Step 2: Implement library and favorites**

Filter by instrument/string count/search query. Favorites remain a stable-ID set. Selecting a tuning persists it and returns directly to active tuning.

- [ ] **Step 3: Implement validated custom tuning editor**

Provide name, instrument, string count, note, octave, and layout inputs. Allowed combinations are guitar 6/7/8/9, bass 4, and ukulele 4 strings. Validation is inline. Save is disabled until valid. Custom IDs use a locally generated UUID and remain bounded to 100 entries.

- [ ] **Step 4: Implement A4 settings without modal UI**

Provide `-0.1`, slider, direct decimal input, `+0.1`, and reset to `440.0`. Reject non-finite/out-of-range input. Show an inline caution outside `430.0..450.0`; applying that range requires a second explicit button tap on the same screen, not a popup. Also provide sharps/flats notation and only the headstock layouts valid for the active tuning, including the required 6-inline/3+3 choice.

- [ ] **Step 5: Implement About**

Show version, proprietary copyright, offline/no-data statement, microphone explanation, and links to bundled privacy/licence text without requiring internet.

### Task 12: Complete localization, accessibility, and UI tests

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Create: `app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt`

- [ ] **Step 1: Move all user-visible prose to resources**

English and Czech resources have identical key sets. Czech strings use UTF-8 diacritics. Note names, frequencies, and preset proper names remain shared structured data.

- [ ] **Step 2: Add stable semantics and content descriptions**

Every icon-only action has a localized content description. Controls meet 48 dp touch targets. Dynamic tuner state announces changes conservatively so TalkBack is not flooded by every audio frame.

- [ ] **Step 3: Write instrumented flows**

Tests cover mode selection, tuning selection, favorite toggle, A4 444.0 validation, invalid A4 rejection, custom nine-string tuning, permission-denied UI, About, and back navigation.

- [ ] **Step 4: Run on connected device**

Run:

```powershell
adb devices -l
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: SM-S938B is `device`; all instrumentation tests pass.

### Task 13: Create original graphics and inspect them

**Files:**
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `assets/tuneitall-banner.png`
- Create: `fastlane/metadata/android/en-US/images/featureGraphic.png`

- [ ] **Step 1: Activate the image-generation skill**

Read and follow the installed `imagegen` skill before generating brand artwork. Prompt for an original minimal note-and-cent-line motif matching the black/off-white/green UI. Do not include guitars with impossible strings, pegs, or fret geometry. Read and follow the installed `screenshot` skill before capturing final device screenshots.

- [ ] **Step 2: Keep functional headstocks as Compose vectors**

The generated raster art is marketing-only. App headstocks remain `Headstock.kt` output driven by model data.

- [ ] **Step 3: Inspect every generated image**

Open source and exported images at original resolution. Reject malformed text, incorrect note symbols, wrong string counts, clipping, low contrast, or features absent from the app. Confirm feature graphic dimensions match current Play requirements before export.

### Task 14: Add proprietary repository and Play documentation

**Files:**
- Create: `LICENSE`
- Create: `THIRD_PARTY_NOTICES.md`
- Create: `README.md`
- Create: `CHANGELOG.md`
- Create: `docs/privacy/privacy-policy-en.md`
- Create: `docs/privacy/privacy-policy-cs.md`
- Create: `docs/store/data-safety.md`
- Create: `docs/store/play-listing-en.md`
- Create: `docs/store/play-listing-cs.md`
- Create: `docs/store/release-checklist.md`
- Create: `.github/workflows/android.yml`

- [ ] **Step 1: Write the proprietary licence**

Use `Copyright © 2026 TuneItAll. All rights reserved.` and explicitly prohibit copying, modification, redistribution, sublicensing, and commercial use without written permission. Do not apply that claim to third-party dependencies.

- [ ] **Step 2: Write privacy and Data Safety truthfully**

State: microphone samples are processed transiently on-device; no recording, retention, transmission, sharing, account, analytics, or internet permission; capture stops outside the active tuner. Document that the Play Data Safety answer is no data collected/shared only while the dependency set remains network-free.

- [ ] **Step 3: Build evidence-backed README**

Include banner, real screenshots, features, supported instruments/layouts, A4 calibration, architecture, privacy, exact JDK/Gradle build commands, wireless ADB install command, licence status, and release artifact locations. Do not claim Play availability before publication.

- [ ] **Step 4: Add one CI workflow**

CI runs wrapper validation, `testDebugUnitTest`, `lintDebug`, and `assembleDebug` on JDK 17. It does not publish, sign, or create releases.

### Task 15: Run hostile review and complete automated gates

**Files:**
- Modify only files implicated by findings.

- [ ] **Step 1: Inspect the entire diff**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Review for duplicate definitions, `Any`, bare collections, undefined state, blocking main-thread work, per-frame allocation, audio leaks, unbounded state, wrong tuning notes, double JSON serialization, hidden internet dependencies, and unused abstractions.

- [ ] **Step 2: Run full quality gates**

Run:

```powershell
.\gradlew.bat clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

Expected: every task reports success; unit XML contains zero failures; lint contains zero errors; debug APK and release AAB exist.

- [ ] **Step 3: Inspect dependencies and manifest**

Run dependency reports and inspect the merged release manifest. Confirm no copyleft DSP dependency and no `INTERNET`, storage, advertising, or tracking permission.

### Task 16: Physically verify on SM-S938B and capture real screenshots

**Files:**
- Create: `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png`
- Create: `fastlane/metadata/android/cs-CZ/images/phoneScreenshots/*.png`
- Create: `.reference/device-verification.md`

- [ ] **Step 1: Install the exact debug build**

Run:

```powershell
adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
adb shell pm clear com.tuneitall.tuner
adb shell monkey -p com.tuneitall.tuner -c android.intent.category.LAUNCHER 1
```

Expected: first-run tuner surface appears and Android requests microphone once.

- [ ] **Step 2: Run four live scenarios**

Verify and record evidence for:

1. Happy: grant microphone, Standard E, relaunch, immediate restored active tuner.
2. Edge: B0/C#1/F#1 synthetic reference signals, A4 444.0, custom nine-string tuning, favorite persistence.
3. Negative: permission denial/revocation, silence, noise, background/foreground, audio interruption.
4. Regression: Manual string reference tone, return from Settings, resume microphone without feedback or stale state.

- [ ] **Step 3: Measure behavior**

Capture logcat around startup and audio errors, inspect crashes/ANRs, use Android profiling output for audio-thread CPU/allocation, and record detector acquisition/settling observations. Do not call ±1-cent physical accuracy proven solely from synthetic unit signals.

- [ ] **Step 4: Capture screenshots from the verified build**

Use the installed release UI for Auto Standard E, centered in-tune, tuning library/favorites, extended guitar headstock, A4 444 settings, and chromatic mode. Inspect every image for correct note, string/peg count, clipping, system overlays, and consistent language.

### Task 17: Final release-readiness audit

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/store/release-checklist.md`
- Modify: `README.md`

- [ ] **Step 1: Verify artifact hashes**

Calculate SHA-256 for debug APK and release AAB and record them in local release notes. Confirm the AAB is unsigned or locally debug-signed unless a user-supplied upload key is provided outside Git.

- [ ] **Step 2: Verify repository truth**

README screenshots must match files; documented commands must run; version text must equal Gradle metadata; privacy claims must match manifest/dependencies; changelog must describe only implemented behavior.

- [ ] **Step 3: Report the external publication gate**

Report exact checks and live scenarios. State that package/title clearance, Play developer identity/contact, public privacy-policy URL, upload key, commit, push, remote repository, and GitHub/Play release remain external gates unless the user explicitly supplies/authorizes them.

- [ ] **Step 4: Preserve the no-publish boundary**

Do not commit, push, open a PR, create a GitHub release, upload an AAB, or expose signing material without explicit approval.
