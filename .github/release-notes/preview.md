Intoniva `v0.3.0-alpha.12` prerelease testing build.

Download the APK attached below and open it on an Android 8.0+ device. Android
may ask for permission to install apps from your browser or file manager because
this build is distributed outside Google Play.

This APK is debug-signed for testing. It is not the Google Play App Bundle.
Intoniva remains fully offline. The tuner uses microphone permission;
optional Auto-scroll uses Android overlay, foreground-service, and
Accessibility gesture permissions. The app has no Internet or advertising ID
permission.

Highlights:

- Import a local song in Metronome to estimate its BPM fully offline, review
  confidence, and apply the result in one tap.
- Song Chords is now named Get Chords for Song in every supported language.
- Deep and Bright join Wood, Click, and Rim as clean synthesized metronome
  sounds. Every profile is DC-balanced, headroom-bounded, and faded to silence.
- Physical Huawei YAL-L21 diagnostics found that processed `MIC` capture kept
  Huawei's voice preprocessing enabled. Auto now selects Raw when supported,
  otherwise effect-free Voice Recognition, with processed `MIC` last.
- Adaptive decay analysis uses the full 8192-sample guitar window only when the
  recent tail weakens; steady notes retain the faster short-window path.
- Brief capture gaps preserve pitch state for eight frames and the last reliable
  reading remains visible for one second.
- Opening Tunings now activates the first-position Favorites filter whenever
  favorite tunings exist.
- Universal now uses the same maximum-sensitivity engine for quiet guitar,
  bass, ukulele, and chromatic tuning without requiring a special profile.
- Stable low-probability pYIN candidates can build confidence over time instead
  of losing permanently to one global unvoiced score.
- Strong string buzz retains a bounded no-trough candidate; changing broadband
  noise remains rejected by periodicity and temporal continuity checks.
- Multi-resolution 8192-sample analysis improves low notes while a 2048-sample
  hop retains frequent updates and bounded detector work.
- Auto input avoids voice effects before falling back to processed capture.
- Confirmation now requires 900 ms continuously in tune. Its 90 ms chime keeps
  instrument-mode analysis live; Chromatic ignores only its 300 ms overlap.
- Favorite tunings sort to the top and the Favorites filter appears first.
- Auto-scroll now uses one clear bidirectional icon in the bottom bar and the
  collapsed floating control.
- New user-facing name: Intoniva. The Android application ID remains
  `com.tuneitall.tuner`, so this installs as an update rather than a new app.
- Maximum sensitivity is independent from visible needle stability and the
  learned room-noise floor.
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
- Maximum microphone sensitivity is no longer blocked by an amplitude or
  learned room-noise floor; the periodic detector rejects silence and noise.
- Eight professional English Play images built from exact emulator captures in
  a deterministic phone-frame renderer.
- No ads, accounts, analytics, tracking, or network permission.

The app passed unit, Lint, API 29 Huawei runtime UI, audio-session, local-file decoding,
timeline, playback, and runtime UI gates.
Generated 60, 90, 120, and 180 BPM tracks, missing beats, silence, and all five
metronome sound profiles pass deterministic regression checks. A 120 BPM WAV
was detected and applied on the physical Huawei; silence returned no stable beat.
Auto-scroll Hide and reopen passed on Android 15. Synthetic quiet, decaying,
buzz, bass, guitar, ukulele, silence, and broadband-noise regressions pass.
Huawei YAL-L21 physical QA verified 48 kHz Voice Recognition capture with no
active preprocessing effects; final acoustic judgment still requires a guitar.

The attached `.sha256` file verifies the downloaded APK.
