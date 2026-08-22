# TuneItAll pYIN Audio Engine Design

Date: 2026-08-12
Status: Approved direction; written specification awaiting user review

## Goal

Make TuneItAll reliably detect quiet guitar, bass, ukulele, and other
monophonic notes without making the displayed pitch jump between harmonics.
Keep the app offline, fast to open, proprietary-compatible, and simple by
default. Advanced controls must be bounded, validated, resettable, and unable
to leave the tuner in an unusable state.

## Evidence and algorithm choice

The current input path already follows Android's recommended raw-capture
strategy: request `UNPROCESSED` when the device reports support and otherwise
use `VOICE_RECOGNITION`. Android documents `VOICE_RECOGNITION` as the fallback
that avoids AGC and noise suppression. The main limitation is therefore the
single-candidate, single-threshold YIN decision and the separate large-jump
heuristic, not the lack of an audio library.

The new detector will be a clean-room Kotlin, pYIN-derived streaming detector:

1. Calculate the YIN difference and cumulative mean normalized difference
   functions once per overlapping PCM window.
2. Evaluate a bounded distribution of YIN thresholds and retain multiple local
   pitch candidates with observation probabilities.
3. Track those candidates over time with a bounded online dynamic-programming
   state, including explicit pitch-distance and octave-jump costs.
4. Emit the highest-scoring live pitch without batch look-ahead.

This preserves YIN's fine pitch accuracy while addressing the information loss
that pYIN identifies in single-candidate frame processing. It is described as
"pYIN-derived" rather than full offline pYIN because the app uses an online
tracker with no future frames.

CREPE is not selected for this release. It benchmarks well, but an embedded
neural model would add model assets, an inference runtime, startup and battery
cost, and substantially more Android integration. It would also make the
detector harder to inspect and tune for the app's small set of monophonic
real-time use cases. No third-party pitch library or model weights will be
added.

Primary references:

- YIN: https://doi.org/10.1121/1.1458024
- pYIN: https://qmro.qmul.ac.uk/xmlui/bitstream/handle/123456789/6040/MAUCHpYINFundamental2014Accepted.pdf?sequence=2
- Android raw audio guidance: https://developer.android.com/media/platform/mediarecorder
- CREPE comparison: https://arxiv.org/abs/1802.06182

## Architecture

The bounded real-time pipeline will be:

`AudioRecord` -> overlapping PCM window -> signal statistics -> multi-candidate
YIN -> online pitch tracker -> note target -> needle smoother -> retained UI
reading.

### Audio input

`AudioInput` will continue to capture mono PCM16 at 48 kHz on a dedicated audio
priority thread. The default window remains 4,096 samples with a 2,048-sample
hop. This gives a new observation about every 42.7 ms and enough periods for
the supported low guitar and bass range.

The input source becomes an explicit bounded setting:

- `AUTO` (default): use `UNPROCESSED` when Android reports support, otherwise
  `VOICE_RECOGNITION`.
- `RAW`: use `UNPROCESSED`; the UI disables this option when unsupported.
- `COMPATIBLE`: use `VOICE_RECOGNITION`.

Changing the source performs an orderly recorder restart. Unsupported or failed
raw initialization falls back once to `VOICE_RECOGNITION` and exposes the
active source in Settings. It must not leave the microphone loop stopped.

### Signal statistics and adaptive noise floor

Each window produces normalized RMS and peak statistics before pitch
selection. A bounded noise-floor tracker learns only from frames that the pitch
detector classifies as unvoiced. Its upward adaptation is deliberately slower
than its downward adaptation so a sustained quiet note cannot quickly become
the learned noise floor.

The acceptance gate is the greater of:

- a bounded absolute RMS floor derived from microphone sensitivity; and
- the learned noise floor multiplied by a bounded ratio derived from noise
  rejection.

The noise floor resets when the audio source changes. It is retained across
normal note changes and never persisted as a user preference.

### Multi-candidate YIN

The detector reuses preallocated arrays. It computes the difference and
cumulative mean normalized difference functions once per window. It then:

- finds local minima inside the mode-specific pitch search range;
- evaluates a fixed, bounded threshold distribution;
- refines each accepted period with parabolic interpolation;
- combines threshold mass, periodicity, and an optional onset indication into
  an observation probability;
- returns at most eight candidates plus an unvoiced probability.

Candidates must contain finite positive frequency, probability in `0..1`, and
periodicity in `0..1`. Results outside the requested frequency range are
discarded. Silence, clipped input, invalid arguments, and non-periodic noise
must not produce a voiced result.

### Online temporal tracker

The tracker keeps only the previous bounded candidate state. For every frame it
scores observation probability plus transition cost. Transition cost increases
with cents distance and adds a separate octave penalty. A detected energy onset
temporarily reduces the normal note-change cost so a newly plucked string is
accepted quickly.

The tracker has no unbounded history and no delayed batch/Viterbi pass. At most
eight voiced states and one unvoiced state survive each frame. Switching
profiles or detector-affecting settings resets this state.

The existing `StablePitchFilter` large-jump/pending-frame logic will be removed.
It must not be layered on top of the new tracker. Harmonic selection belongs in
the candidate tracker; visual damping belongs in the needle smoother.

### Target selection and needle display

Preset Auto and Manual modes retain their current bounded frequency ranges.
Chromatic mode retains the full supported range. Existing target hysteresis
remains responsible only for mapping a stable detected pitch to a displayed
target note.

A separate log-frequency one-pole smoother controls the needle within the
tracked pitch. It never chooses a note or rejects a candidate. Its strength is
derived from the needle stability setting. A confirmed onset or accepted note
change resets the smoother to avoid slowly sliding from the previous string.

The reading retainer keeps the last reliable visual result for the configured
hold time. The confirmation tracker continues to receive only a live reading,
never a retained stale reading, so a missing signal cannot trigger the chime.

## User settings

Settings will show safe profiles first and advanced controls below them. The
tuner screen remains unchanged.

### Profiles

- `Balanced` (default): sensitive and stable general-purpose setup.
- `Quiet room`: maximum quiet-note pickup with moderate noise rejection.
- `Noisy room`: stronger signal/noise and harmonic rejection.
- `Fast response`: faster note changes and lighter needle damping.
- `Custom`: selected automatically after any individual audio control changes.

Applying a profile changes only audio/detection controls. It never changes the
A4 reference, notation, tuning, favorites, or headstock layout.

### Bounded controls and defaults

| Control | Range | Default | Responsibility |
| --- | --- | --- | --- |
| Microphone sensitivity | 0 to 100 | 100 | Absolute quiet-signal floor only |
| Response | Fast / Balanced / Stable | Balanced | Temporal note-change cost |
| Needle stability | 0 to 100 | 65 | Visual pitch smoothing only |
| Noise rejection | 0 to 100 | 30 | Required signal-to-noise ratio |
| Harmonic protection | 0 to 100 | 80 | Octave/subharmonic transition penalty |
| In-tune tolerance | 1 to 10 cents | 3 cents | Green range and confirmation eligibility |
| Confirmation time | 100 to 1,000 ms, 50 ms steps | 250 ms | Required live in-tune duration |
| Reading hold | 0 to 1,000 ms, 50 ms steps | 250 ms | Visual retention after signal loss |
| Audio input | Auto / Raw / Compatible | Auto | Android capture source |

The internal threshold distribution, candidate count, transition matrix shape,
noise-floor time constants, and numerical detector limits are not exposed.
They are implementation safety parameters, not useful musical controls.

`Reset audio settings` restores the Balanced profile. Stored values are parsed
through typed value objects/enums. Missing, obsolete, non-finite, or out-of-range
values fall back to Balanced defaults. Existing installations migrate their
current sensitivity and use defaults for new settings; no user tunings or
favorites are cleared.

## UI behavior

Settings keeps the current monochrome style and scrolling layout. Profiles are
compact chips. Sensitivity, response, and needle stability appear directly
below them. `Advanced audio` reveals noise rejection,
harmonic protection, green tolerance, confirmation time, reading hold, and
audio source. This keeps normal use simple while allowing full adjustment.

Every slider shows its exact current value. Every control has an accessible
label and Czech/English help text describing what it changes. Raw input is
disabled with a short explanation when unavailable. The active input source is
shown for diagnostics.

## Error handling and concurrency

- Audio source changes stop and join the old recorder before starting another.
- Only one `AudioRecord` session and one worker may exist at a time.
- Detector and tracker state stay on the audio callback path; UI publication
  continues through the existing bounded coroutine/state flow.
- A stale frame captured under previous settings is discarded using the
  existing detection-context check.
- Failed raw initialization falls back once to Compatible and reports the
  fallback; repeated failures become the existing explicit audio error.
- All arrays and candidate collections are fixed-size or bounded. No frame
  history, cache, or preference collection grows without limit.
- No microphone samples are written to disk or transmitted. The manifest keeps
  no Internet permission.

## Test and acceptance plan

Implementation follows test-first red/green cycles.

### Deterministic detector tests

Procedurally generated PCM cases cover:

- E1 bass, E2/E4 guitar, ukulele, and high chromatic notes;
- very quiet decaying plucked tones;
- detuning on both sides of the target;
- missing fundamental and dominant second/third harmonics;
- one-frame octave and subharmonic glitches;
- rapid real string changes with a new energy onset;
- stationary background noise, changing noise floor, silence, and clipped PCM;
- invalid and out-of-range input.

Acceptance targets:

- stable clean/quiet tones are within 2 cents after acquisition;
- a single harmonic glitch never changes the tracked note;
- Balanced accepts a genuine new note within 150 ms after onset;
- Fast accepts it within 100 ms after onset;
- alternating ±8-cent input jitter produces at most ±3-cent displayed jitter
  after settling with default needle stability;
- deterministic noise and silence remain unvoiced at default settings;
- detector p95 processing time on the Samsung stays below the 42.7 ms hop and
  no processing backlog grows.

### Settings and regression tests

- Every typed setting validates its boundaries and defaults.
- Profiles produce exact documented values; any manual change becomes Custom.
- Corrupt and legacy preferences migrate without a crash.
- Changing audio source performs one restart and fallback is bounded.
- Retained green readings cannot confirm tuning.
- Existing A4, notation, tunings, favorites, custom tunings, reference tones,
  confirmation exclusion, string numbering, and fixed chromatic layout remain
  covered.

### Android verification

Run the repository build gate, all unit tests, Android lint, and all connected
Compose tests. Then install on the Samsung SM-S938B through Wireless debugging
and verify:

1. Happy path: default Balanced profile starts listening and tracks a normal
   instrument/reference note.
2. Edge path: Quiet room profile retains a decaying quiet note without octave
   jumping.
3. Negative path: ambient noise/silence does not display a stable false note.
4. Nearby regression: reference-tone tap, confirmation chime exclusion, A4
   adjustment, and Chromatic layout still work.

Capture active audio source, detector timing, foreground activity, version,
permission, and crash logs. If an actual instrument cannot be played during
automated verification, report that limitation separately instead of treating
procedural audio or UI tests as proof of acoustic performance.

## Out of scope

- Polyphonic chord detection.
- Recording or saving microphone audio.
- Cloud processing, analytics, or telemetry.
- Neural pitch models or TensorFlow/ONNX runtimes.
- Bluetooth-specific calibration.
- Per-device hidden presets without measured evidence.
