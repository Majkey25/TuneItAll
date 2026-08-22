# TuneItAll musician toolkit design

Date: 2026-08-20
Status: Approved direction, written specification awaiting review

## Goal

Turn TuneItAll into a small offline musician toolkit without weakening the
tuner. Keep startup direct, controls readable, and every audio feature free of
ads, accounts, analytics, and network access.

The selected visual direction is Precision Panel:

- [Light reference](../../design/precision-panel-light.png)
- [Dark reference](../../design/precision-panel-dark.png)

The images define hierarchy and style. Production code must correct any
generated-image mistakes, including string position and metronome scale text.

User visual review adds these binding corrections:

- Replace every bottom-navigation text glyph with a purpose-drawn Compose
  vector icon for Tuner, Metronome, Chords, Library, and Trainer.
- Draw the six-string headstock as a recognizable guitar head with three pegs
  on each side, visible tuning posts, a nut, a truss-rod cover, and strings that
  terminate at the correct posts.
- Remove `INLINE_6`. Six-string guitar presets and custom tunings use only
  `SPLIT_3_3`. Migrate stored custom `INLINE_6` data to `SPLIT_3_3` while
  decoding so no saved tuning disappears.
- Keep inline layouts for bass and extended-range guitars until their own
  physical layouts receive separate review.
- Use the launcher's exact green `#63D17A` as the only accent in both themes.
  On Light surfaces, use green as a filled indicator or container with
  near-black content, not as small text on warm white.
- Replace the launcher crosshair motif with a clear tuning-fork silhouette
  above a cents ruler. Use the same shape for the adaptive monochrome icon.

## Delivery order

Build the product in three working slices. Each slice must pass the full build
and device checks before the next slice starts.

1. Core: pYIN-derived tuner engine, Light and Dark Precision Panel UI, bottom
   navigation, and metronome.
2. Reference: Chords and Library.
3. Practice: Trainer.

The pYIN work follows
[`2026-08-12-pyin-audio-engine-design.md`](2026-08-12-pyin-audio-engine-design.md).
This document does not duplicate its detector rules.

## Navigation

The bottom bar has five destinations:

- **Tuner** opens on app launch.
- **Metronome** opens the timing tool.
- **Chords** finds playable chord shapes.
- **Library** finds tunings, scales, progressions, and favorites.
- **Trainer** runs short practice exercises.

Keep Settings in the Tuner header. Do not add a drawer, home dashboard, or
sixth primary destination.

## Light and Dark themes

Settings offers `System`, `Light`, and `Dark`. `System` is the default. Store
the choice locally.

Light uses warm white `#FAF9F6`, near-black `#111111`, and icon green
`#63D17A`. Dark uses near-black `#101010`, warm white `#F4F1EA`, and the same
icon green `#63D17A`. Green marks active navigation, the in-tune state, the
current beat, and the primary action. Do not use gradients, glow, glass
effects, or shadows.

Controls use 8 dp corners and a minimum 48 dp touch target. Note names, cents,
and BPM use tabular figures. All screens support Czech and English text, font
scaling, and screen-reader labels.

## Tuner screen

Keep the existing tuner behavior and cents rail. Recompose the screen in the
Precision Panel hierarchy:

1. Show the tuning, mode, and A4 reference in one status row.
2. Show the detected note and signed cents as the main readout.
3. Show the precise `-50..+50` cents ruler.
4. Show the selected instrument headstock and string buttons.
5. Show the bottom navigation.

For six-string Standard E, display the physical numbering exactly:

| String | Note |
| --- | --- |
| 6 | E2 |
| 5 | A2 |
| 4 | D3 |
| 3 | G3 |
| 2 | B3 |
| 1 | E4 |

Reuse the current typed tuning and headstock data. Do not encode note labels in
the screen.

## Metronome controls

The first metronome release includes:

- Integer BPM from 20 to 400.
- Direct BPM entry, `-1`, `+1`, hold-to-repeat, and tap tempo.
- Meter numerator from 1 to 12 and denominator 2, 4, 8, or 16.
- Subdivision 1, 2, 3, or 4 clicks per beat.
- Accent cycle `Off` or every 2nd through 12th main beat.
- Sound style `Wood`, `Click`, or `Rim`.
- Volume from 0 to 100 and mute.
- Count-in of 0, 1, 2, or 4 bars.
- Start and stop.

The main screen shows only the compact BPM controls, the mechanical pendulum,
Tap, Start/Stop, and one rhythm-summary button. The summary opens a single
scroll-safe sheet for meter, subdivision, accent, sound style, volume, mute,
and count-in. Do not show idle status text or decorative beat dots.

`Off` disables the stronger accent sound. The engine still plays normal beat
and subdivision clicks. An accent cycle of `N` plays the stronger sound on
every Nth main beat. Subdivision clicks are quieter than main beats.

Tap tempo uses the median of the last five valid tap intervals. Accept 30 to
400 BPM and reset tap history after two seconds. Direct BPM entry still supports
20 to 400 BPM.

## Metronome clock, sound, and animation

Use one continuously running mono PCM16 `AudioTrack` at 48 kHz. A dedicated
audio-priority thread writes the stream ahead of playback. Do not use
`MediaPlayer` or start one player per click.

Generate three click buffers in Kotlin. Each buffer must:

- Start and end at zero.
- Use a short attack and a smooth decay.
- Stay below clipping when clicks overlap.
- Have negligible DC offset.
- Use a different pitch and envelope for normal, subdivision, and accent
  clicks.

Schedule beats by audio frame number. Keep the fractional frame remainder when
BPM does not divide the sample rate. This prevents long-session drift. Apply
BPM, meter, subdivision, and accent changes at the next main beat boundary.

The audio frame position is the authoritative clock. Compose reads the same
beat phase to draw the pendulum. The pendulum follows a smooth sinusoidal path
and reaches each endpoint on a main beat. Draw a filled mechanical pyramid,
plinth, vertical scale, arm, slotted weight, and hub with `Canvas`; do not use a
radial gauge or ship the generated raster mockup inside the app.

Fade the stream over 10 ms on stop. Keep one `AudioTrack` while running. These
rules prevent cuts, pops, buzzing, and timing changes caused by player startup.

In the first release, stop playback when the app leaves the foreground. This
avoids a foreground service and a permanent notification. Add locked-screen
playback only after an explicit request.

## Chords

Chords is an offline chord finder. The user chooses an instrument, root, and
quality. Show the chord notes and curated playable shapes.

The first catalog covers guitar and ukulele:

- Major and minor.
- Power chord.
- Dominant 7, major 7, and minor 7.
- Sus2 and sus4.
- Diminished and augmented.

Each diagram shows fret numbers, muted strings, open strings, and finger
numbers. Support right-handed and left-handed display. Store favorites locally.
Do not generate arbitrary chord shapes at runtime in the first release.

## Library

Library is reference material, not a lesson screen. It contains:

- The existing tuning catalog and custom tunings.
- Scale and mode formulas with fretboard views.
- Chord progressions.
- Favorites from Tunings, Chords, Scales, and Progressions.

Filter progressions by `Classical`, `Jazz`, `Blues`, `Rock`, and `Metal`.
Ship typed offline data. Search matches names and musical symbols. No web
content or downloads are required.

## Trainer

Trainer is practice, not a chatbot. It contains four exercises:

- Fretboard note identification for guitar, bass, and ukulele.
- Note and interval recognition by ear.
- Chord-quality recognition by ear.
- Rhythm imitation with the metronome clock.

Generate exercise audio with the existing tone synthesis and the metronome
engine. Store only local session score, streak, difficulty, and last exercise.
Do not add accounts, leaderboards, AI chat, or cloud progress.

## State and lifecycle

- Keep the current single activity and typed `AppScreen` state.
- Stop tuner microphone input while Metronome or Trainer owns audio output.
- Stop all audio when the app leaves the foreground.
- Persist theme, metronome defaults, favorites, and trainer progress with
  bounded validated values.
- Preserve existing tunings, favorites, A4, notation, sensitivity, and custom
  tuning data during migration.
- Keep `RECORD_AUDIO` as the only manually declared permission.

## Verification

Write failing tests before each behavior change.

Metronome unit checks must prove:

- BPM and option boundaries reject invalid values.
- Beat scheduling accumulates less than one audio-frame error over ten minutes
  at 20, 137, and 400 BPM.
- Accent cycles 2, 3, 5, and 12 land on the expected main beats.
- Subdivision counts are correct.
- Click buffers start and end at zero, do not clip, and have bounded DC offset.
- Stop fade ends at zero.
- Pendulum endpoints match main-beat boundaries.

UI and storage checks must prove:

- Light, Dark, and System modes select the correct theme.
- Every bottom destination is reachable and Tuner remains the launch screen.
- Six-string labels and numbers are correct.
- Metronome controls expose accessible labels and exact values.
- Corrupt or legacy stored values fall back without clearing user data.

Run the repository build gate, lint, unit tests, and all connected Compose
tests. Then install on the Samsung SM-S938B and verify navigation, both themes,
audio start and stop, rapid BPM changes, mute, accent 2, 3, and 5, and fresh
crash logs. Record a five-minute metronome timing trace. Report that synthetic
and system traces do not replace an acoustic microphone measurement.

## Out of scope

- Background or locked-screen playback.
- Song tablature, sheet music, or copyrighted song libraries.
- Polyphonic tuning.
- Cloud sync, accounts, social features, or AI chat.
- Downloadable sound packs.
