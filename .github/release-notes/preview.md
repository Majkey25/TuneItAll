Intoniva `v0.3.0-alpha.9` prerelease testing build.

Download the APK attached below and open it on an Android 8.0+ device. Android
may ask for permission to install apps from your browser or file manager because
this build is distributed outside Google Play.

This APK is debug-signed for testing. It is not the Google Play App Bundle.
Intoniva remains fully offline. The tuner uses microphone permission;
optional Auto-scroll uses Android overlay, foreground-service, and
Accessibility gesture permissions. The app has no Internet or advertising ID
permission.

Highlights:

- New user-facing name: Intoniva. The Android application ID remains
  `com.tuneitall.tuner`, so this installs as an update rather than a new app.
- Auto capture now uses Android's processed microphone path, and maximum
  sensitivity no longer remains pinned behind the learned room-noise floor.
- Universal and Unplugged electric profiles keep microphone sensitivity high
  while smoothing the visible needle independently.
- Auto-scroll replaces the duplicate Library bottom destination; tunings remain
  one tap away through the outlined current-tuning control.
- Hide now collapses the floating panel without crashing the application.

- A clean-room, pYIN-derived streaming tuner for quieter notes and steadier
  pitch tracking.
- Complete tuner controls for sensitivity, response, needle stability, noise
  rejection, harmonic protection, tolerance, confirmation, reading hold, and
  Auto/Raw/Compatible input.
- A foreground-only mechanical metronome from 20 to 400 BPM with meter,
  subdivision, accent, sound, volume, mute, and count-in controls.
- Canonical major, minor, and dominant-seventh diagrams for Standard E guitar
  and Standard C ukulele, with finger numbers, starting frets, and barres.
- Experimental offline chord detection for a local audio file, with synchronized
  playback, timeline navigation, canonical standard-instrument diagrams, and
  transposition.
- Chord learning, hidden-answer chord quiz, and a 12-note ear trainer with
  generated audio and local scoring.
- Stable confirmed BPM editing, audio-synchronized mechanical animation, a
  shared global Settings destination, and a restored quick rhythm panel.
- Exact attributed 3+3 and 6-inline headstock artwork with correct E4 string 1
  through E2 string 6 ordering.
- The supplied CC0 metronome SVG rebuilt as a static body with exactly one
  audio-synchronized moving arm.
- Narrower, taller chord diagrams in both Chords and Trainer.
- Six-inline string names and numbers now sit on the left side.
- Public privacy and support pages for Play closed testing.
- Complete System-default, English, Czech, German, French, and Spanish
  interfaces, including Android 8 through Android 12 fallback handling.
- An optional Buy Me a Coffee button in App details. It opens an external
  browser and does not unlock any feature.
- Classic hands-free Auto-scroll with a gesture-only Accessibility service,
  speed 1 through 30, movable floating controls, and no Shizuku or access to
  screen content.
- Maximum microphone sensitivity now uses the safe absolute floor instead of
  remaining blocked by a learned room-noise floor.
- Eight professional English Play images built from exact emulator captures in
  a deterministic phone-frame renderer.
- No ads, accounts, analytics, tracking, or network permission.

The app passed unit, Lint, API 35 Compose, audio-session, local-file decoding,
timeline, playback, and runtime UI gates.
Auto-scroll Hide and reopen passed on a clean Android 15 emulator without a
process restart or runtime exception. Quiet and room-noise behavior and acoustic
metronome quality still require physical-device listening before a broader release.

The attached `.sha256` file verifies the downloaded APK.
