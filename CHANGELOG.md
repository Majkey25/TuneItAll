# Changelog

## 0.3.0-alpha.7 — 2026-08-24

- Add a complete in-app language selector with System default, English, Czech,
  German, French, and Spanish. All languages ship in the offline bundle.
- Use Android per-app languages on Android 13 and newer, with a local
  configuration fallback for Android 8 through Android 12.
- Add the ScanIt-style optional Buy Me a Coffee button to App details. It opens
  the published support page in the external browser and unlocks no features.

## 0.3.0-alpha.6 — 2026-08-24

- Move all 6-inline string names and physical numbers to the left of the
  headstock while retaining E4 string 1 through E2 string 6 order.
- Add the public TuneItAll website, privacy policy, and support page for Google
  Play distribution.
- Add a manual GitHub workflow that builds and verifies a signed Play bundle.

## 0.3.0-alpha.5 — 2026-08-24

- Use the exact attributed Les Paul and Explorer headstock artwork requested
  for the selectable 3+3 and 6-inline layouts.
- Enlarge both images while retaining 48 dp tone controls and correct physical
  string numbering.
- Rebuild the supplied CC0 metronome SVG as a static body with exactly one
  audio-synchronized moving arm.

## 0.3.0-alpha.4 — 2026-08-24

- Replace photographic instrument graphics with crisp theme-aware native
  vectors in Light and Dark mode.
- Draw a recognizable 3+3 headstock with six posts, strings, nut, and neck.
- Restore a selectable, pointed 6-inline headstock with string controls on the
  opposite side and correct physical numbering.
- Animate the separate CC0-derived metronome arm from the audio playback phase.

## 0.3.0-alpha.3 — 2026-08-24

- Replace the six-string headstock and mechanical metronome body with real,
  licensed photographs while retaining functional controls and the
  audio-synchronized pendulum.
- Make canonical chord diagrams narrower and taller in both Chords and Trainer.
- Remove the AndroidSVG runtime dependency and obsolete vector assets.

## 0.3.0-alpha.2 — 2026-08-23

- Replace generated chord fingerings with pinned canonical Standard E guitar
  and Standard C ukulele shapes from the MIT-licensed `chords-db` catalog.
- Show finger numbers, muted and open strings, starting frets, and full barre
  positions. Unsupported tunings no longer receive a guessed diagram.
- Hide the chord answer until the ear-training quiz is answered.
- Add a single-note ear trainer for all 12 chromatic notes from C4 through B4.
- Replace the six-string headstock and metronome body with downloaded licensed
  SVG assets while keeping strings, controls, and the audio-synchronized arm.

## 0.3.0-alpha.1 — 2026-08-23

- Browse major, minor, and dominant-seventh chord shapes generated for the
  selected guitar, bass, ukulele, or custom tuning.
- Import a local audio file, detect a smoothed offline chord timeline, play it
  in sync, transpose the detected harmony, and view a playable voicing.
- Learn chord shapes and sounds or run a local quiz with persistent scoring.
- Keep metronome audio stable while typing tempo values. BPM updates now commit
  only after confirmation, and 400 BPM playback keeps one output session.
- Move the pendulum on a dedicated frame callback tied to the audio playback
  head, without recomposing the full Compose screen.
- Use one global Settings destination from every gear, plus the restored quick
  rhythm panel and a standard back arrow.
- Replace the tuner and metronome illustrations with restrained mechanical
  vectors based on CC0 references.

## 0.2.0-alpha.1 — 2026-08-22

- Tune quieter instruments with a pYIN-derived streaming tracker, adaptive
  noise floor, steadier needle, and configurable response, stability, noise,
  harmonic, tolerance, timing, and input-source controls.
- Practice with a foreground-only mechanical metronome from 20 to 400 BPM,
  including meter, subdivision, accent, sound, volume, mute, count-in, and tap
  tempo.
- Use the simplified Light/Dark interface with a recognizable mechanical
  metronome and a physically mapped 3+3 six-string guitar headstock.
- Keep tuning fully offline and ad-free. Chords and Trainer remain unavailable
  in this Core prerelease.

## 0.1.0-alpha.3 — 2026-08-12

- Detect quieter instrument notes with a higher default microphone sensitivity.
- Stabilize the tuner needle while preserving accurate cent measurements.
- Reject short harmonic jumps before changing the detected note.
- Keep the chromatic note display fixed when sharps or flats appear.
- Retain the last reliable reading across brief signal gaps.

## 0.1.0-alpha.2 — 2026-08-11

- Use an explicit stable preview signing key and verify its certificate before
  publishing the downloadable APK.

## 0.1.0-alpha.1 — 2026-08-11

- Initial native Android tuner.
- Guitar 6/7/8/9, bass 4, ukulele, and chromatic support.
- Auto and manual targeting, favorites, custom tunings, and headstock layouts.
- One-shot reference tone on string tap with click-free switching.
- One-shot confirmation chime after 250 ms of stable in-tune detection, excluded
  from microphone pitch analysis.
- Compact left-side inline headstocks and harmonic-rich plucked reference tones.
- Correct physical string numbering: highest string is 1 and lowest string is the
  instrument string count.
- Dedicated all-instrument Chromatic screen without preset headstock controls.
- Bounded 0–100 microphone sensitivity with the tested behavior at 50.
- Simplified monochrome tuner and data-driven inline/split headstock drawing.
- Adjustable 410.0–480.0 Hz A4 reference with a 440.0 Hz default.
- Offline-only operation with no ads, analytics, accounts, or network access.
- Pinned CI, signed tag-release workflow, release verification, and Play metadata.
