TuneItAll `v0.3.0-alpha.5` prerelease testing build.

Download the APK attached below and open it on an Android 8.0+ device. Android
may ask for permission to install apps from your browser or file manager because
this build is distributed outside Google Play.

This APK is debug-signed for testing. It is not the future Google Play build.
TuneItAll remains fully offline and requests only microphone permission.

Highlights:

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
- No ads, accounts, analytics, tracking, or network permission.

The app passed unit, Lint, API 35 Compose, audio-session, local-file decoding,
timeline, playback, and runtime UI gates.
The target Samsung `SM-S938B` was unavailable for this acceptance run. Quiet
and room-noise behavior, physical-device appearance, and acoustic metronome
quality still require Samsung listening before a broader release.

The attached `.sha256` file verifies the downloaded APK.
