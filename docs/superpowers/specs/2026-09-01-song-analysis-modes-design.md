# Offline song analysis modes

Date: 2026-09-01
Status: Approved direction, written specification awaiting review

## Goal

Replace the current short-frame song chord matcher with a clean-room offline
analysis pipeline that produces readable musical timelines from full mixes.
The user selects one analysis mode before or after loading a local audio file:

- **Classic chords** identifies major, minor, and strongly supported dominant
  seventh chords. This is the default.
- **Notes** follows one predominant melody and preserves octave information.
- **Power chords** identifies root-and-fifth power chords without forcing a
  major or minor third.

The application stays proprietary, fully offline, and compatible with Android
8.0 and newer. It does not add Internet permission, an account, cloud analysis,
or a license-incompatible dependency.

## Evidence from the reported song

The existing `0.3.0-alpha.14` analyzer decoded the full 210,576 ms MP3 on the
Huawei YAL-L21 in 19.089 seconds. The generated timeline covered only 16% of
the file. Its median event duration was 342 ms. Of 69 events, 39 were labeled
as dominant seventh chords and only two were labeled as power chords.

This result isolates the failure to feature extraction and chord sequence
decoding. `MediaExtractor`, `MediaCodec`, and the file picker completed the
full input without an error.

## Selected approach

Use a clean-room tonal feature extractor and three small mode-specific
decoders. Reuse the existing PCM decoder, playback synchronization, fixed
current-event bar, and auto-follow timeline.

Published chord-recognition systems commonly use a log-frequency spectrum,
tuning correction, spectral whitening, chroma aggregation, and a temporal
decoder. Essentia documents a two-second sliding window over HPCP frames.
NNLS Chroma documents log-frequency mapping, tuning correction, running
standardization, and HMM or Viterbi smoothing. The implementation can use
these published concepts but must not copy GPL source code.

Do not ship Chordino, Essentia, or another GPL or AGPL implementation. Do not
add a neural model until its training data, model license, accuracy, APK size,
and runtime cost are known.

## User flow

Add a three-option segmented control to **Get Chords for Song**:

1. **Classic chords**
2. **Notes**
3. **Power chords**

Loading a file starts analysis in the selected mode. Changing the mode keeps
the selected file and playback position, cancels the old analysis generation,
clears stale events, and analyzes the file again.

The Notes mode also shows an instrument-range selector:

- Any melody: MIDI 21 through 108
- Guitar: MIDI 40 through 88
- Bass: MIDI 28 through 72
- Violin: MIDI 55 through 100
- Piano: MIDI 21 through 108

The selector limits candidate pitches. It does not claim to separate stems or
identify every simultaneous piano note. Notes mode follows the strongest
stable melody in the selected range.

Classic mode keeps the optional chord-diagram toggle. Notes and Power modes
hide chord diagrams because the bundled catalog has no reviewed power-chord
voicings. Transposition applies to both chord roots and note MIDI numbers. The
fixed bottom bar says **Current chord** or **Current note** based on the
selected mode.

## Data model

Add `SongAnalysisMode` with `CHORDS`, `NOTES`, and `POWER` values.

Add a sealed `SongEvent` contract with `startMillis`, `endMillis`, and
`confidence` values:

- `ChordEvent` contains a `Chord`.
- `NoteEvent` contains an integer MIDI note.

`SongAnalysisResult` returns a bounded `List<SongEvent>`. `ChordUiState` stores
the selected mode, the selected note range, and the current event list. The
default state uses `CHORDS` and `Any melody`.

The state does not store decoded PCM or a spectrogram. A 30-minute input remains
the hard limit.

## Shared harmonic features

Keep the existing 8,192-sample Hann-window STFT and 4,096-sample hop. Replace
the direct 12-bin peak fold with a shared `StreamingHarmonicFeatureExtractor`:

1. Interpolate spectral peaks between FFT bins.
2. Map 27.5 Hz through 4,200 Hz to a log-frequency representation with three
   bins per semitone across the complete range.
3. Estimate one global tuning offset from stable spectral peaks and clamp the
   result to minus or plus 50 cents.
4. Apply running spectral standardization over a six-second window to reduce
   fixed timbre and broad distortion harmonics.
5. Create separate full-range and bass-weighted 12-bin chroma vectors.
6. Aggregate chroma over a centered two-second window before classification.

The extractor keeps only bounded feature frames. It does not retain source PCM.
For a 30-minute file, the implementation must keep feature storage below
32 MiB.

## Classic chord decoder

Classic mode evaluates `no chord`, 12 major, 12 minor, and 12 dominant seventh
states.

Use mode-specific templates with an out-of-chord energy penalty. A dominant
seventh state must beat its matching major state and retain flat-seventh
evidence for at least 750 ms. The flat seventh must exceed twice the median
salience of the out-of-chord pitch classes. A weak incidental flat seventh must
not convert a major chord or a power chord into a dominant seventh.

Decode the frame sequence with a persistence-biased Viterbi pass. The
transition model enforces continuity but does not prefer a genre-specific chord
progression. Merge identical neighbors and absorb short low-confidence gaps
into the surrounding chord only when both sides agree.

## Power chord decoder

Power mode evaluates `no chord` and 12 power-chord states. It combines the
bass-weighted root evidence with full-range root-and-fifth evidence. A stable
major or minor third does not change the output label in this explicit mode.

The output quality is always `ChordQuality.POWER`. This rule prevents a full
metal mix from producing a stream of unsupported dominant seventh labels.

## Predominant-note decoder

Notes mode uses the shared log-frequency feature before pitch-class folding.
For each frame, calculate harmonic salience for MIDI candidates inside the
selected instrument range. Penalize candidates that explain only an upper
harmonic while a stronger fundamental candidate exists.

Run a Viterbi pass over pitch candidates plus a `no note` state. The transition
cost favors small melodic motion while still allowing large jumps at detected
onsets. Convert the decoded sequence to `NoteEvent` objects and merge repeated
notes across short gaps.

The note timeline shows localized sharp or flat names with octave numbers. It
does not display chord shapes.

## Lifecycle and errors

`SongAudioDecoder` continues to own `MediaExtractor` and `MediaCodec`.
`ChordViewModel` continues to use an analysis generation number so a cancelled
mode cannot publish stale results.

Changing a mode or instrument range restarts only analysis. It does not reopen
the document picker or replace the `MediaPlayer` session.

Return a mode-specific empty-result message when the decoder finds no stable
chords or notes. Do not fill silence, percussion-only sections, or broadband
noise with guessed labels.

## Verification

Write each production change through a red-green TDD cycle.

Unit tests must cover these cases:

- A clean major chord, a clean minor chord, and a genuine dominant seventh.
- A distorted root-and-fifth riff with drum transients.
- A power chord with incidental upper harmonics does not become dominant seven.
- A C-major to G-major change remains in order and has a boundary error below
  500 ms.
- Silence and changing broadband noise produce `no chord` or `no note`.
- A synthetic melody returns the correct MIDI notes within 50 cents.
- Instrument ranges exclude out-of-range candidates.
- A cancelled analysis generation cannot update the UI.

Connected Huawei tests must use the real Android decoder and verify:

- All three modes analyze the complete reported MP3 without a crash.
- Power mode covers at least 55% of the complete reported file, has a median
  event duration of at least 750 ms, and returns only power-chord events.
- Classic mode no longer produces dominant seventh as the majority label for
  the reported metal track.
- Notes mode returns only MIDI notes inside the selected range. Its exact-note
  accuracy is accepted against synthetic annotated melodies, not guessed from
  the unannotated reported song.
- Each mode completes the 210,576 ms file in at most 30 seconds on the Huawei
  YAL-L21.
- The current-event bar stays fixed and the timeline follows playback.

The reported song has no trusted manual chord annotation. Runtime and stability
metrics do not prove exact musical correctness. Final acceptance therefore
includes listening to the synchronized timeline on the physical phone.

Run the full repository build gate, Android Lint, all unit tests, connected UI
tests, synthetic decoder tests, and the temporary real-song acceptance probe.
Do not commit or redistribute the reported MP3.

## Release

Ship the three modes together as `0.3.0-alpha.15` only after all gates pass.
Publish a GitHub prerelease first. Submit the same signed `versionCode 18` AAB
to the existing Google Play Closed Alpha channel after the GitHub workflow and
artifact checks pass.

## Deferred work

- Source separation and stem analysis
- Polyphonic piano transcription
- A learned TFLite chord model
- Extended chord vocabularies beyond strongly supported dominant seventh
- YouTube or remote URL import

## References

- [Essentia chord estimation](https://essentia.upf.edu/tutorial_tonal_chords.html)
- [Essentia HPCP](https://essentia.upf.edu/reference/streaming_HPCP.html)
- [NNLS Chroma and Chordino](https://isophonics.net/nnls-chroma)
- [Chord recognition using duration-explicit hidden Markov models](https://ismir2012.ismir.net/event/papers/445_ISMIR_2012.pdf)
- [Feature Learning for Chord Recognition](https://arxiv.org/abs/1612.05065)
