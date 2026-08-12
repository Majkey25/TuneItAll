# Changelog

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
