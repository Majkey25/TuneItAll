# Auto-scroll and Store Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ScrollIt's classic Accessibility auto-scroll to TuneItAll without Shizuku, prove it on the TuneItAll emulator, and replace raw Play screenshots with eight English phone-frame images.

**Architecture:** Keep five primary destinations. Chords opens a secondary Auto-scroll route. A private foreground overlay service controls a gesture-only Accessibility service; typed core classes own bounded speed and gesture timing. Store artwork embeds exact emulator PNGs without redrawing app UI.

**Tech Stack:** Kotlin 2.3, Android 10+, Jetpack Compose, Android AccessibilityService, foreground `specialUse` service, XML overlay views, JUnit 4, Compose UI tests, ADB, ImageMagick or the existing Java/ImageIO renderer.

---

### Task 1: Pure auto-scroll core

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollCore.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/autoscroll/AutoScrollCoreTest.kt`

- [ ] **Step 1: Write failing boundary and timing tests**

Cover speed `1`, `15`, `30`, out-of-range clamping, monotonic distance,
positive durations, and batch timestamps that remain inside Android's 2 s
gesture limit.

- [ ] **Step 2: Verify RED**

Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.tuneitall.tuner.autoscroll.AutoScrollCoreTest" --console=plain
```

Expected: compilation fails because `AutoScrollSpeed` and
`AutoScrollGestureProfile` do not exist.

- [ ] **Step 3: Implement the typed core**

Implement `AutoScrollSpeed`, `AutoScrollSettings`,
`AutoScrollGestureProfileFactory`, and `gestureStartTimes`. Reuse ScrollIt
v1.0.0's verified `1..30` interpolation and bounded defaults. Do not add a
mode enum because Shizuku is out of scope.

- [ ] **Step 4: Verify GREEN**

Run the Task 1 command. Expected: all core tests pass.

### Task 2: Storage, permission checks, and navigation

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollPreferences.kt`
- Create: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollPermissionState.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`
- Modify: `app/src/test/java/com/tuneitall/tuner/AppScreenTest.kt`
- Create: `app/src/test/java/com/tuneitall/tuner/autoscroll/AutoScrollPreferencesTest.kt`

- [ ] **Step 1: Add failing tests**

Assert `parentScreen(AppScreen.AutoScroll) == AppScreen.Chords`. Assert stored
speed values below 1 or above 30 normalize to the nearest endpoint.

- [ ] **Step 2: Verify RED**

Run the two named test classes. Expected: `AutoScroll` and preferences are
missing.

- [ ] **Step 3: Implement minimal state**

Store one integer in private SharedPreferences. Check overlay access with
`Settings.canDrawOverlays` and compare the enabled Accessibility component by
flattened component name. Add the secondary route and parent mapping.

- [ ] **Step 4: Verify GREEN**

Run the two test classes. Expected: pass.

### Task 3: Gesture-only Accessibility service

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollAccessibilityService.kt`
- Create: `app/src/main/res/xml/auto_scroll_accessibility_service.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-cs/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-es/strings.xml`

- [ ] **Step 1: Add a failing manifest test**

Add an instrumentation assertion that the service metadata declares
`canPerformGestures=true` and `canRetrieveWindowContent=false`.

- [ ] **Step 2: Verify RED**

Run the named instrumentation test. Expected: service metadata is absent.

- [ ] **Step 3: Implement gesture dispatch**

Batch upward swipes from the typed core. Stop on interrupt, destroy, cancelled
gesture, or explicit stop. Keep only a volatile in-process service reference;
do not inspect Accessibility events or windows.

- [ ] **Step 4: Verify GREEN**

Run the metadata test and Task 1 unit tests. Expected: pass.

### Task 4: Floating controls service

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/autoscroll/AutoScrollOverlayService.kt`
- Create: `app/src/main/res/layout/auto_scroll_overlay.xml`
- Create: `app/src/main/res/layout/auto_scroll_bubble.xml`
- Create: `app/src/main/res/drawable/bg_auto_scroll_panel.xml`
- Create: `app/src/main/res/drawable/bg_auto_scroll_bubble.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add failing lifecycle tests**

Test that service actions are package-private constants, speed updates clamp,
and Stop clears the engine's running state.

- [ ] **Step 2: Verify RED**

Run the new unit test class. Expected: overlay service API is missing.

- [ ] **Step 3: Implement the overlay**

Add the `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, and
`FOREGROUND_SERVICE_SPECIAL_USE` declarations. Render Start/Stop, minus,
speed, plus, Hide, and Close. Use 48 dp controls, draggable clamped placement,
a 48 dp edge bubble, low-importance notification, and no Material View
dependency.

- [ ] **Step 4: Verify GREEN**

Run unit tests plus `:app:lintDebug`. Expected: pass with no manifest error.

### Task 5: Compose setup screen and Chords entry

**Files:**
- Create: `app/src/main/java/com/tuneitall/tuner/ui/AutoScrollScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/ui/ChordsScreen.kt`
- Modify: `app/src/main/java/com/tuneitall/tuner/TuneItAllApp.kt`
- Modify: `app/src/androidTest/java/com/tuneitall/tuner/MusicToolsScreenTest.kt`
- Modify: all five `strings.xml` locale files

- [ ] **Step 1: Add failing Compose tests**

Assert Chords exposes `open_auto_scroll`; clicking it invokes the callback.
Assert the setup screen shows permission states, speed 15, and disables the
primary action while either permission is missing.

- [ ] **Step 2: Verify RED**

Run `MusicToolsScreenTest`. Expected: tags and screen are missing.

- [ ] **Step 3: Implement the route**

Add a compact Chords header action. The secondary screen refreshes permission
state on resume, opens Android settings, persists speed, and starts the private
foreground service only when both permissions are available.

- [ ] **Step 4: Verify GREEN**

Run `MusicToolsScreenTest`, `TuneItAllFlowTest`, and all unit tests. Expected:
pass.

### Task 6: Privacy and release metadata

**Files:**
- Modify: `docs/privacy/index.html`
- Modify: `docs/store/data-safety.md`
- Modify: `README.md`
- Modify: `fastlane/metadata/android/en-US/full_description.txt`
- Modify: `fastlane/metadata/android/cs-CZ/full_description.txt`

- [ ] **Step 1: Document exact behavior**

State that Accessibility performs gestures only, screen content is not read,
stored, or transmitted, and overlay controls require explicit user setup.

- [ ] **Step 2: Verify docs**

Run `./tools/build.ps1 -AllowUnsigned`, then request the local privacy page and
Fastlane descriptions with the repository's existing link checks. Expected:
no stale privacy claim.

### Task 7: Emulator acceptance

**Files:**
- Output only under `.reference/tmp/auto-scroll-qa/`

- [ ] **Step 1: Build and install**

Run the repository build gate, install on `TuneItAll_API_35`, and confirm the
package has no INTERNET or AD_ID permission.

- [ ] **Step 2: Verify four live scenarios**

Use UI-tree-derived coordinates only: missing permissions, overlay shown,
Chords movement while running then stable after Stop, and scrolling in a
second Android app. Check speed endpoints, Hide/restore/Close, crash buffer,
and ANR events.

### Task 8: Professional store images

**Files:**
- Replace: `fastlane/metadata/android/en-US/images/phoneScreenshots/1_tuner.png` through `8_inline.png`
- Create or modify: `tools/RenderStoreScreenshots.java`
- Modify: `README.md`

- [ ] **Step 1: Capture real screens**

Capture Tuner, Tunings, Chromatic/Settings, Metronome, Chords, Song Chords,
Trainer, and Auto-scroll from the verified English emulator build.

- [ ] **Step 2: Render deterministic artwork**

Produce eight 1080 x 1920 PNGs with exact captures inside a black rounded
phone frame, restrained dark/green or warm-white backgrounds, and the approved
English headlines. Do not synthesize or redraw app UI.

- [ ] **Step 3: Verify images**

Check dimensions, alpha, text clipping, phone-frame alignment, and visual
content. Open all eight outputs for human visual inspection. Run store asset
verification and update the README gallery.

### Task 9: Final gates and release handoff

**Files:**
- Modify version/changelog only after Tasks 1-8 pass

- [ ] **Step 1: Run full gates**

Run unit tests, lint, debug APK, signed release AAB, instrumentation, Android
10 compatibility, and fresh emulator smoke checks.

- [ ] **Step 2: Review the diff**

Check scope, permissions, accessibility privacy, no Shizuku references, no
new network permission, generated asset freshness, and no secrets.

- [ ] **Step 3: Prepare the next alpha**

Bump one versionCode and `0.3.0-alpha.8`, update changelogs, commit in atomic
slices, push/tag only under the user's existing release authorization, then
verify CI and public artifacts before any new Play upload.
