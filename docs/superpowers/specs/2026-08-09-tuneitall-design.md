# TuneItAll Android Design

## Product

TuneItAll is a proprietary, offline Android tuner for guitar, bass, ukulele, and chromatic use. Its product promise is zero ads, zero account, zero network use, zero onboarding, and no custom loading screen. After the required Android microphone permission is granted once, every launch opens directly into active tuning.

The repository name is `tuneitall-android`. The application ID is `com.tuneitall.tuner`. The display name is `TuneItAll`. User-visible text ships in Czech and English through Android resources; the system locale selects the language. An existing iOS tuner currently uses the spaced name `Tune It All`; name and trademark clearance remains a release gate, while the user has explicitly selected `TuneItAll` for this Android implementation.

## Scope

The first production release includes:

- Automatic tuning against the selected preset.
- Manual string selection with optional reference-tone playback.
- Chromatic tuning independent of a preset across A0 through C8 (27.5 through 4186.01 Hz).
- Six-, seven-, eight-, and nine-string guitar presets.
- Four-string bass and four-string ukulele presets.
- Guitar headstocks in 6-inline, 3+3, 7-inline, 4+3, 8-inline, 4+4, 9-inline, and 5+4 layouts.
- Bass headstocks in 4-inline and 2+2 layouts.
- Ukulele headstock in 2+2 layout.
- Standard, lowered-standard, drop, open, and common alternate tunings.
- Verified drop presets from Drop D down through Drop F where musically applicable.
- Searchable presets, favorites, last-used tuning, and user-created custom tunings.
- Adjustable A4 reference pitch from 410.0 Hz to 480.0 Hz in 0.1 Hz steps.
- Original app icon, vector instrument graphics, repository banner, real-device screenshots, README, changelog, proprietary license, privacy policy, release notes, and Play-ready Android App Bundle.

An exhaustive static list of every possible tuning is not finite. Curated presets plus a validated custom-tuning editor provide complete coverage without an unmaintainable catalog.

## Excluded

- Ads, subscriptions, purchases, accounts, telemetry, analytics, cloud sync, or network access.
- Lessons, chord libraries, games, metronomes, social features, or other practice-suite features.
- Background microphone capture.
- Bluetooth-specific audio processing.
- Temperament systems beyond equal temperament in the first release.
- Oboe, JNI, CMake, and native DSP unless physical-device profiling proves `AudioRecord` cannot meet the acceptance targets.

## User experience

### Launch

Android's system splash is the only splash. TuneItAll does not render a second splash or loading screen.

On first launch, the tuner screen is already visible behind the required system microphone permission request. Granting permission starts capture immediately. Denial leaves the same screen usable for manual reference tones and shows one stable action for enabling microphone access. The app never loops or repeatedly nags for permission.

On later launches, the app restores the last tuning, mode, headstock layout, favorites, and A4 reference, then starts microphone capture as soon as the activity is resumed.

### Tuner screen

The tuner screen contains, from top to bottom:

1. Current tuning name, favorite toggle, tuning-library action, and Settings.
2. `Auto`, `Manual`, and `Chromatic` mode control.
3. Large detected or target note.
4. Horizontal cents rail from -50 to +50 with a moving indicator, flat/sharp direction, and a green centered lock state.
5. Secondary detected frequency and cents values.
6. Data-driven headstock or string row. Tapping a string selects it in Manual mode and plays its one-shot reference tone. Physical numbers run from the lowest string count down to string 1 at the highest pitch.

Chromatic mode replaces preset-specific controls and the headstock with one
universal detected-note surface.

The screen uses no modal marketing, onboarding, rating prompt, tooltip tour, or interstitial. Tuning-library, custom-tuning, settings, and about content use normal full-screen destinations instead of dialogs.

### Modes

- **Auto:** chooses the closest valid string in the selected tuning. Hysteresis prevents rapid switching between adjacent targets.
- **Manual:** evaluates only the selected string target. Reference-tone playback pauses microphone evaluation to prevent feedback and resumes it when playback stops.
- **Chromatic:** reports the nearest equal-tempered note across the supported range without requiring a tuning preset.

The display shows `♭` or `♯` direction and signed cents. Enharmonic note naming follows the selected notation preference. The default notation uses sharps.

### Tuning library

The library groups items by instrument, string count, and category. It supports text search, favorites, and custom tunings. Presets are immutable typed data with stable IDs. A custom tuning requires a non-blank unique name, an allowed string count, and one valid note with octave per string. Invalid or duplicate definitions are rejected with inline text.

The catalog is verified against independent musical references before release. Automated validation checks string count, MIDI-note range, unique stable ID, non-empty display name, correct low-to-high ordering, and absence of duplicate note sequences within the same instrument category.

### A4 calibration

The default and reset value is 440.0 Hz. The settings screen provides minus/plus controls, a slider, and direct numeric input with a 0.1 Hz resolution. Only finite values from 410.0 through 480.0 Hz are persisted. Values outside the range are rejected. Values outside 430.0 through 450.0 Hz show an inline caution before they can be applied. A non-default value remains visible on the tuner screen as `A4 = nnn.n Hz`.

## Visual system

The interface uses near-black and warm off-white surfaces with one restrained green accuracy accent. Typography is large, high-contrast, and readable at arm's length. The cents rail, text direction, and centered lock state do not rely on color alone. Touch targets meet Android accessibility sizing.

Headstocks are original scalable vectors driven from the same typed string-layout data used by tuning logic. Peg count, side, order, selected string, and displayed pitch cannot drift between image and model. Raster headstock screenshots are not used inside the app.

The app icon and repository/store artwork use the same note-and-cent-line motif. Marketing screenshots come from the release UI installed on the physical Samsung SM-S938B. Generated artwork is visually inspected at source size and final export size. Store screenshots never show features that the release build lacks.

## Architecture

TuneItAll is a single-module, single-activity Kotlin application using Jetpack Compose and Material 3. It does not add a navigation framework: a small typed screen state handles Tuner, Tuning Library, Custom Tuning, Settings, and About.

The minimum supported version is Android 8.0 / API 26. The app compiles and targets API 36. The initial toolchain is AGP 8.13.2, Gradle 8.13, JDK 17, Kotlin 2.3.21, and Compose BOM 2026.06.01.

Production components have narrow responsibilities:

- `AudioInput`: owns `AudioRecord`, recorder lifecycle, source fallback, audio thread, and reusable sample buffers.
- `YinPitchDetector`: clean-room Kotlin implementation from the published YIN algorithm. It accepts samples and the actual sample rate and returns a typed result containing frequency, confidence, and signal level.
- `TunerEngine`: applies noise/confidence gates, short median smoothing, target selection, hysteresis, note conversion, and cents calculation.
- `ReferenceTonePlayer`: generates a click-free sine reference with short fades through `AudioTrack`.
- `TunerViewModel`: coordinates lifecycle-safe audio state and exposes immutable UI state.
- Compose screens: render state and send typed user actions. They do not perform audio or persistence work.

This is not split into repositories, services, factories, or interfaces with one implementation.

## Audio and pitch detection

Capture uses mono PCM16. It requests 48 kHz and supports 44.1 kHz, always calculating from the actual initialized recorder sample rate. `UNPROCESSED` is used only when the device advertises support; otherwise capture uses `VOICE_RECOGNITION`. Unsupported or failed recorder configurations produce an explicit UI error and do not crash.

Audio runs on one dedicated audio-priority worker. Hot-path arrays and ring buffers are preallocated. Capture stops on pause/background and is released on disposal.

The detector uses YIN thresholding and parabolic refinement. Six-string guitar, seven-string guitar, bass E1, and ukulele use a 4096-sample analysis window. Extended-low guitar, low chromatic notes, and any target below E1 use an 8192-sample window. Preset modes constrain the searched period range around valid strings. Chromatic mode uses the full A0-through-C8 range. Analysis cadence is profiled on the target phone and capped so UI updates remain smooth without allocating per frame.

Weak input and low-confidence estimates show a listening state instead of a false note. A three-result median and short target hysteresis suppress jitter and octave hopping without making the display sluggish.

Equal-tempered note frequency is calculated from the validated A4 reference. JSON is serialized once at persistence boundaries; internal audio and tuning state remains typed.

## Persistence

The data set is small and bounded. Android `SharedPreferences` stores primitive settings, last-used stable IDs, and a bounded favorite-ID set. Custom tunings are typed in memory and serialized once into one validated JSON array using the platform JSON API. At most 100 custom tunings are retained. Malformed persisted entries are skipped individually and never crash launch.

No audio samples, detected notes, usage history, device identifiers, or personal data are stored.

## Error handling

- Microphone denied: tuner remains open; manual tones work; one permission action is available.
- Permission permanently denied: action opens the application settings page only after user tap.
- Microphone unavailable or interrupted: capture stops cleanly and presents a retry action.
- Unsupported audio configuration: try the documented fallback once, then show an explicit error.
- Silence/noise/low confidence: show listening state; never invent a note.
- Invalid A4 or custom tuning: reject at the UI boundary and persist nothing.
- Corrupt local preference entry: ignore only that entry and preserve other valid data.

## Privacy and Play readiness

The manifest declares only `android.permission.RECORD_AUDIO`; it omits `INTERNET`, storage, advertising, and tracking permissions. Audio is processed in memory on-device, is not recorded, retained, transmitted, or shared, and capture is active only while the tuner screen is in the foreground.

The repository includes a matching privacy policy in English and Czech, Play Data Safety answers, app-category metadata, short and full store descriptions, release notes, and feature-graphic/screenshot assets. Publishing uses an `.aab` and Play App Signing. Upload/app-signing keys remain outside the repository.

The proprietary `LICENSE` grants no reuse, modification, redistribution, or sublicensing rights and uses `Copyright © 2026 TuneItAll. All rights reserved.` Third-party Android/Kotlin dependencies retain their own licenses and are documented separately.

## Verification

### Automated checks

- Gradle build and dependency resolution.
- Android lint.
- Kotlin compilation with warnings treated seriously.
- Unit tests for note/frequency conversion, cents at multiple A4 references, A4 validation, preset validation, custom-tuning parsing, and YIN output.
- Detector fixtures covering clean sine, harmonic-rich synthetic plucks, amplitude variation, silence, noise, and octave-confusing harmonics.
- Instrumented Compose checks for first-run permission states, mode switching, favorite persistence, custom tuning, settings validation, and lifecycle restart.

### Detector acceptance set

Known signals include B0 30.87 Hz, C#1 34.65 Hz, E1 41.20 Hz, F#1 46.25 Hz, E2 82.41 Hz, A2 110.00 Hz, A4 440.00 Hz, A4 444.00 Hz calibration, and a high chromatic note. Stable clean signals must settle within ±1 cent. Low notes must not settle on their octave. Silence and rejected noise must produce no note.

### Physical SM-S938B checks

1. Happy path: grant microphone, tune standard six-string targets, relaunch, and verify immediate restored tuning.
2. Edge path: low extended-range signals plus A4 444.0 Hz, favorites, and custom nine-string tuning.
3. Negative path: deny/revoke microphone, silence, noise, background/foreground transition, and audio interruption.
4. Nearby regression: manual target selection and reference-tone playback after returning from Settings.

The app is installed through wireless ADB on the connected Android 16 / API 36 SM-S938B. Screenshots are captured from that verified build. Startup timing, detector latency, crashes, ANRs, and audio-thread CPU are measured on the device. Claims are not made from unit tests alone.

## Repository deliverables

- Buildable Android source.
- Proprietary `LICENSE` and third-party notices.
- `README.md` with real screenshots, banner, features, architecture, privacy, build/run instructions, and release installation steps.
- `CHANGELOG.md`, release notes, versioning, and unsigned local debug APK.
- Signed release AAB only after an upload key is supplied outside Git.
- One focused CI workflow for build, lint, and tests.
- Play Store copy, privacy policy, icon, feature graphic, phone screenshots, and release checklist.

No commit, push, remote repository, pull request, or GitHub release is created without separate explicit approval.
