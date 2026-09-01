# Changelog

## 0.3.0-alpha.17 — 2026-09-01

- Make low guitar reference tones easier to hear on phone speakers by keeping
  the second and third harmonics present without clipping the PCM output.
- Keep all six standard-guitar reference tones within a bounded phone-speaker
  loudness range while preserving the existing fade and rapid-switch cleanup.

## 0.3.0-alpha.16 — 2026-09-01

- Keep low-contrast harmonic evidence from dense rock and metal mixes instead
  of discarding every frame below the old fixed tonal-strength threshold.
- Reject short or low-confidence whole events so increased coverage does not
  turn changing broadband noise into chords.
- Add suspended-second recognition and reduce bass-root bias so inverted
  chords keep their annotated root.
- Improve the reported 210.6-second metal track from 59.3% to 88.1% Classic
  chord coverage while the broadband-noise regression remains empty.
- Move the optional chord diagram into the fixed current-chord panel and
  replace the oversized text control with a compact accessible fretboard icon.
- Replace the dark promotional screenshots with fresh English and Czech
  SeliaScan-style 1080×1920 artwork based on Huawei device captures.

## 0.3.0-alpha.15 — 2026-09-01

- Add separate Classic chords, Notes, and Power chords modes to offline song
  analysis.
- Replace short-frame matching with tuning-corrected, temporally standardized
  harmonic features and persistence-biased sequence decoding.
- Add Guitar, Bass, Violin, Piano, and Any melody ranges to predominant-note
  analysis with octave-aware note names.
- Keep Notes and Power timelines free of unreviewed chord diagrams while
  preserving fixed current-event display and timeline auto-follow.
- Improve the reported 210.6-second metal track from 16% unstable chord
  coverage to 61% Classic/Power coverage with a 1.45-second median segment;
  Guitar Notes reaches 28% coverage.

## 0.3.0-alpha.14 — 2026-08-31

- Detect power chords in distorted guitar audio and label them with the standard
  `5` suffix, such as E5 or F♯5.
- Weight low-frequency spectral evidence more strongly so fundamentals and
  fifths survive dense upper harmonics.
- Hold the last stable chord through gaps up to 500 ms and reject isolated
  single-frame matches to stop the current-chord label from flickering.
- Hide song chord diagrams by default and add an explicit Show chord diagrams
  toggle; the chord name and timeline remain visible.
- Keep the current chord in a fixed bottom bar and automatically reveal the
  active event in the timeline during playback.

## 0.3.0-alpha.13 — 2026-08-26

- Preserve pending in-tune confirmation through detector gaps up to 200 ms,
  while still requiring a fresh in-tune reading before confirming.
- Restore clear confirmation feedback with a green fade-and-scale glow around
  the detected note.
- Keep the 90 ms confirmation chime isolated from instrument pitch tracking and
  preserve Android Silent/DND behavior.

## 0.3.0-alpha.12 — 2026-08-25

- Add offline tempo estimation for a user-selected song, with progress,
  confidence, explicit BPM application, and a clear no-stable-beat result.
- Rename Song Chords to Get Chords for Song in every supported language.
- Add Deep and Bright metronome sounds alongside Wood, Click, and Rim.
- Keep every synthesized click DC-balanced, headroom-bounded, and faded to
  exact silence at both buffer edges.

## 0.3.0-alpha.11 — 2026-08-25

- Make Auto prefer unprocessed capture when supported, then Android's clean
  voice-recognition source, with processed `MIC` as the last fallback.
- Bypass Huawei's active voice preprocessing and noise gate on the tested
  YAL-L21 while retaining 48 kHz mono PCM input.
- Analyze the full decaying guitar window when its recent tail weakens, while
  preserving the faster short window for steady notes.
- Retain pitch state across brief eight-frame capture gaps and hold the last
  reliable reading for one second.
- Open the tuning picker in Favorites mode whenever favorite tunings exist.

## 0.3.0-alpha.10 — 2026-08-25

- Make Universal detect quiet instruments without requiring the unplugged
  electric profile by removing the maximum-sensitivity amplitude gate.
- Correct the temporal voiced/unvoiced model so a weak stable pitch can build
  confidence while changing broadband noise remains unvoiced.
- Preserve low-clarity string buzz through a bounded pYIN no-trough candidate
  instead of discarding the entire frame at the fixed threshold.
- Use multi-resolution 8192-sample analysis for bass, guitar, ukulele, and
  chromatic ranges while keeping a 2048-sample update hop.
- Enable capture AGC when available for Auto input and disable speech-oriented
  noise suppression and echo cancellation without changing Raw or Compatible.
- Require 900 ms of continuous in-tune input before confirmation, then play a
  short 90 ms chime without pausing instrument-mode analysis. Chromatic mode
  discards only the 300 ms overlapping acoustic tail.
- Put Favorites first in the tuning picker and replace both Auto-scroll arrow
  glyphs with one clear bidirectional-scroll icon.

## 0.3.0-alpha.9 — 2026-08-24

- Rename the user-facing product to Intoniva while retaining the stable
  `com.tuneitall.tuner` application ID and repository history.
- Make Auto microphone input use Android's processed `MIC` path instead of
  choosing low-level unprocessed capture on supported devices.
- Let maximum sensitivity use the safe absolute floor instead of remaining
  blocked by a learned room-noise floor.
- Add a stable Unplugged electric profile and make Universal the clearer,
  high-sensitivity default with independent needle smoothing.
- Replace the duplicate Library bottom destination with Auto-scroll while
  keeping tunings available from the more prominent current-tuning control.
- Fix the Auto-scroll Hide crash by collapsing the actual overlay panel rather
  than passing the clicked button to `WindowManager.removeView`.
- Refresh the adaptive icon, feature graphic, website, Play copy, and all eight
  professional store screenshots for Intoniva and the expanded music toolkit.

## 0.3.0-alpha.8 — 2026-08-24

- Add classic ScrollIt-style hands-free scrolling through a user-enabled,
  gesture-only Accessibility service and compact floating controls. No Shizuku
  or screen-content retrieval is used.
- Expose Auto-scroll from Chords with permission status, speed 1 through 30,
  Start/Stop, Hide, Close, and a movable edge bubble.
- Make microphone sensitivity affect both the absolute input floor and the
  adaptive noise gate. Maximum sensitivity now retains near-silent electric
  guitar tones just above amplifier hiss instead of requiring a hard attack.
- Add exact privacy and Data Safety disclosures for overlay and Accessibility
  use while retaining no ads, analytics, tracking, Internet, or advertising ID.
- Replace every raw Play screenshot with eight deterministic English phone-frame
  graphics captured from the verified emulator build.

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
