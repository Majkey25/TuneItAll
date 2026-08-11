# TuneItAll Tuner Experience Redesign

## Goal

Keep the accepted pitch detector and cents rail, then make the primary tuner
look conventional, calm, and professional while adding the missing sound and
sensitivity behavior.

## Main tuner

- Use a black or white neutral surface. Green is reserved for an in-tune state.
- Keep the existing horizontal cents rail, large detected note, frequency, and
  signed cents.
- Keep Auto, Manual, and Chromatic directly accessible. Chromatic remains the
  all-instrument tuner.
- Replace the rounded list-like string control with a crisp Compose-drawn
  headstock. It supports every existing inline and split layout without raster
  art. Peg labels are large tap targets and the selected/in-tune peg turns green.
- Keep tuning selection, favorite, and Settings at the top of preset modes, but
  reduce decoration and visual weight. Chromatic shows only its universal tuner
  controls and Settings.

## String and sound behavior

- Tapping a string selects Manual mode and immediately plays its one-shot
  reference tone. Pitch readings are ignored while that tone is playing so the app
  cannot tune against its own speaker.
- There is no separate reference-tone button. Rapid string changes fade the old
  track before releasing it so playback is not cut at an arbitrary waveform phase.
- A target must remain within the existing ±2-cent green band for 250 ms before
  one confirmation chime fires. It cannot fire again until the signal has been
  outside the green band for 500 ms or the target changes.
- The confirmation is a short locally generated bell chime using Android
  `USAGE_NOTIFICATION` / sonification attributes. It follows notification/ringer
  volume and Do Not Disturb instead of media volume. It posts no notification,
  shows no popup, and adds no network or account dependency.
- Microphone pitch analysis is gated for the bounded chime playback window so
  the confirmation cannot become a detected note.
- Reference pitches remain media audio because they are intentional musical
  playback. Both players are released on background/clear.

## Sensitivity

- Persist one bounded sensitivity value from 0 to 100, default 50.
- Value 50 exactly preserves the currently accepted RMS and confidence gates.
- Higher values accept quieter/lower-confidence signals; lower values reject
  more noise. The range stays finite and validated, and Settings has Reset.
- The detector and engine receive the same sensitivity object so their two
  existing gates cannot drift apart.

## Repository and release material

- Keep the existing proprietary licence, privacy policy, Play metadata, banner,
  feature graphic, icon, Fastlane metadata, and CI.
- Match the useful public-repository structure from ScanIt: SECURITY,
  CONTRIBUTING, issue templates, Dependabot, a deterministic build entry point,
  and artifact verification. Do not copy ScanIt product code.
- Do not commit, push, create a remote repository, or publish a release without
  separate explicit approval.

## Verification

- Unit tests cover sensitivity bounds/defaults, detector gating, 500-ms lock,
  re-arm behavior, and click-free bounded chime samples.
- Compose device tests cover sensitivity controls, chromatic access, string tap,
  and accessible string/favorite controls.
- Final gates: unit tests, Android Lint, debug APK, release AAB, connected device
  tests, clean-install permission flow, background/resume, logcat crash check,
  and visually inspected physical-device screenshots.
