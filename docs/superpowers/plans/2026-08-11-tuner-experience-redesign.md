# TuneItAll Tuner Experience Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved monochrome tuner UI, string-tap reference tone,
stable in-tune confirmation, bounded sensitivity, and ScanIt-grade repository
support without changing the accepted pitch mapping.

**Architecture:** Keep the current single-Activity Compose app and audio pipeline.
Add two small pure domain types (`DetectionSensitivity` and
`InTuneConfirmationTracker`), one native `AudioTrack` chime player, and route
their state through the existing ViewModel/preferences. Draw the headstock in
Compose; add no dependency.

**Tech Stack:** Kotlin 2.3, Jetpack Compose/Material 3, Android `AudioRecord` and
`AudioTrack`, SharedPreferences, JUnit 4, Compose UI tests, Gradle 8.13/JDK 17.

---

### Task 1: Bounded detector sensitivity

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/audio/DetectionSensitivity.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/audio/YinPitchDetector.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/tuner/TunerEngine.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/storage/UserPreferences.kt`
- Test: `app/src/test/java/com/tuneitall/tuner/audio/DetectionSensitivityTest.kt`

- [ ] Write failing tests proving values outside `0..100` fail, default 50 maps
  to RMS `0.003` and confidence `0.80`, and higher sensitivity lowers both gates.
- [ ] Run `:app:testDebugUnitTest --tests '*DetectionSensitivityTest'`; verify RED
  because `DetectionSensitivity` does not exist.
- [ ] Add `@JvmInline value class DetectionSensitivity(val value: Int)` with
  `DEFAULT = 50`, piecewise-linear bounded RMS/confidence mappings, and pass it
  into both detector and engine update paths.
- [ ] Persist the integer with validated fallback to 50.
- [ ] Re-run the focused test and existing detector/engine tests; verify GREEN.

### Task 2: Stable confirmation and native chime

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/tuner/InTuneConfirmationTracker.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/audio/ConfirmationChimePlayer.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/tuner/InTuneConfirmationTrackerTest.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/audio/ConfirmationChimeTest.kt`

- [ ] Write failing deterministic tracker tests: no fire before 250 ms, one fire
  at 250 ms, no repeat while locked, re-arm after 500 ms outside, immediate reset
  on target change.
- [ ] Write failing buffer tests: finite duration, non-silent energy, first/last
  samples near zero, and all samples inside `Short` range.
- [ ] Run both focused suites and verify RED from missing production types.
- [ ] Implement the tracker with elapsed-realtime inputs and implement a local
  decaying multi-partial bell buffer/player with `USAGE_NOTIFICATION` and
  `CONTENT_TYPE_SONIFICATION`.
- [ ] Re-run focused tests and verify GREEN.

### Task 3: ViewModel interaction flow

**Files:**
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerViewModel.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt`

- [ ] Add a failing Compose device test proving a string tap selects Manual and
  exposes the active reference-tone state.
- [ ] Run the single connected test and verify RED.
- [ ] Add `selectStringAndPlayReference(index)`, a one-shot `viewModelScope` stop
  job, detector suppression during reference playback, confirmation tracker
  updates, one chime per confirmed lock, and lifecycle cleanup.
- [ ] Add sensitivity and confirmation fields to `TunerUiState`; reset the tracker
  when mode/tuning/string/reference pitch/sensitivity changes.
- [ ] Re-run unit and connected focused tests; verify GREEN.

### Task 4: Conventional monochrome UI

**Files:**
- Replace: `app/src/main/java/com/tuneitall/tuner/ui/components/Headstock.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/TunerScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Test: `app/src/androidTest/java/com/tuneitall/tuner/TuneItAllFlowTest.kt`

- [ ] Add failing Compose assertions for dedicated Chromatic UI, sensitivity
  value/reset, physical string numbering, and accessible peg descriptions.
- [ ] Run connected tests and verify RED for the missing sensitivity controls.
- [ ] Draw a neutral headstock/strings/pegs with Compose Canvas and overlay 48-dp
  accessible peg targets for inline/split 4/6/7/8/9 layouts.
- [ ] Simplify spacing, dividers, surfaces, and buttons; move Settings to the top,
  remove the reference button, preserve the cents rail, and use green only for
  active/in-tune/confirmed state.
- [ ] Add the bounded sensitivity slider and reset/help copy in English and Czech.
- [ ] Run connected tests and inspect dark/light, compact, and large-font previews.

### Task 5: ScanIt-grade repository support

**Files:**
- Create: `SECURITY.md`
- Create: `CONTRIBUTING.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/config.yml`
- Create: `.github/dependabot.yml`
- Create: `tools/build.ps1`
- Create: `tools/verify-release.ps1`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] Add only TuneItAll-specific policies and deterministic commands; do not
  copy ScanIt claims, package IDs, licences, or feature code.
- [ ] Pin CI actions by full commit SHA, grant `contents: read`, add concurrency,
  timeout, and upload unsigned CI artifacts/reports.
- [ ] Verify APK package/version/SDK/no-INTERNET/alignment/signature and AAB
  structure/signing state/hash with Android/JDK tools.
- [ ] Run the build entry point and artifact verifier locally.

### Task 6: Full release and physical-device QA

**Files:**
- Update: `docs/store/release-checklist.md`
- Create: `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png`
- Create: `fastlane/metadata/android/cs-CZ/images/phoneScreenshots/*.png`

- [ ] Run unit tests, Lint, debug APK, release AAB, and `git diff --check`.
- [ ] Install on Samsung SM-S938B; test permission allow/deny, Auto/Manual/
  Chromatic, 6/7/8/9 strings, bass, ukulele, A4 444, sensitivity extremes/reset,
  auto reference tone, confirmation/re-arm, background/resume, and persistence.
- [ ] Confirm notification-channel chime routing with media muted while respecting
  silent/DND; inspect logcat for crashes/ANRs/audio errors.
- [ ] Capture and visually inspect real-device tuner/library/settings screenshots.
- [ ] Perform hostile diff review and report the unsigned upload-key gate.

No commit step is included because repository rules require separate explicit
approval before any commit, push, remote creation, PR, or release.
