# Canonical chords, ear training, and instrument graphics

Date: 2026-08-23
Status: Approved by standing user direction

## Goal

Replace generated chord shapes with established offline data. Replace the
hand-built instrument silhouettes with downloaded, licensed vector art. Add a
single-note ear exercise without changing the tuner or metronome audio clocks.

This change corrects the Chords and Trainer implementation against
[`2026-08-20-musician-toolkit-design.md`](2026-08-20-musician-toolkit-design.md).

## Canonical chord data

Pin `tombatossals/chords-db` at commit
`df06fa7b425cf5fd29485ff6591236b3557e3fac`. Vendor its MIT-licensed guitar
and ukulele JSON files as offline raw resources. Record the upstream commit,
file hashes, and license in `THIRD_PARTY_NOTICES.md`.

The app reads `frets`, `fingers`, `barres`, and `baseFret`. The first catalog
keeps the current Major, Minor, and Dominant 7 choices. Standard E guitar and
standard C ukulele use the upstream shapes. Other tunings show an explicit
unsupported message. The app must not substitute a generated shape.

The diagram places the lowest guitar string on the left. It shows muted and
open strings, finger numbers, barre positions, and the starting fret. Audio
uses the same absolute frets that the diagram shows.

## Trainer

Trainer has two exercise selectors:

- **Chords** keeps chord learning and chord recognition. Both use canonical
  shapes and the existing chord audio path.
- **Notes** plays one chromatic note from C4 through B4. The screen hides the
  answer until the user chooses one of four unique note names. The replay
  button repeats the same pitch. The existing bounded local score records the
  result.

Question generation is deterministic for tests and changes after each answer.
Leaving Trainer stops active output.

## Graphics

Use the CC0 `Guitar Head` SVG from SVG Repo as the six-string headstock shape.
Keep TuneItAll's string labels, touch targets, string lines, and selected-string
state above the downloaded path.

Use the CC0 `Metronome.svg` by J Alves from Wikimedia Commons as the static
mechanical body. Keep the existing audio-frame phase as the only animation
clock. Draw the moving arm and weight as a separate layer so that each audio
beat still lands at an endpoint.

Both sources remain local. The app gains no Internet permission.

## Verification

Tests must fail before the implementation and then prove:

- Known guitar shapes match `C x32010`, `G 320003`, `D xx0232`, `Am x02210`,
  `F 133211`, and `B7 x21202`.
- Known ukulele shapes match the pinned upstream data.
- Relative `baseFret` values convert to the correct absolute frets.
- Unsupported tunings never receive a generated diagram.
- Note questions contain four unique choices and exactly one answer.
- Note playback uses the requested MIDI pitch.
- Both SVG resources parse and render in an Android test.

Run unit tests, lint, the full connected test suite, an emulator flow for each
exercise, and audio-session checks. Publish the next alpha only after the
downloaded release APK passes checksum, signature, package, install, launch,
and crash-log checks.
