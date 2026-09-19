# Song chord and note evidence

## Fixed paths

- Chord entry now requires multiple observed pitch classes. The harmonic
  projection contains alternative fundamental hypotheses, not necessarily
  simultaneous notes. Previously, a single A4 could become a D-minor chord.
  Centered chord evidence remains available for sustained chords, but cannot
  start a chord before the unsmoothed observation supports one.
- Quality refinement preserves an exact voicing supported by both observed
  chroma and independent spectral notes in adjacent frames. Evidence is checked
  before temporal whitening removes the relative strength of stationary notes.
  This prevents clear ninths from losing to a three-note subset. An independent
  voicing alone is insufficient: additional observed pitch classes disqualify
  this stronger evidence path.
- Inversion bass uses observed low notes, not virtual subharmonics. Its duration
  vote uses the same window-center boundaries as the displayed chord segment.
- Notes use window-center boundaries too. Violin-range suppression now applies
  to a related lower harmonic source, not arbitrary bass accompaniment. High
  missing-fundamental melody notes remain eligible with unrelated weak bass:
  support checks do not require harmonics above the extractor's frequency range.
  Accompanied candidates still need direct or independently observed harmonic
  support, preventing weak virtual pitches from becoming melody notes.

No new runtime dependency, permission, network service, playback, or tuner change.

## Recorded comparison

Frozen GuitarSet subsets: 24 performed clips, each at gains 1 and 0.01.
The reference and scoring rules are unchanged. Values below are milliseconds
of correctly labeled reference audio, before -> after. Chord-quality scoring
excludes reference qualities outside the supported comparison vocabulary.

| Subset / gain | Correct root | Correct supported quality | Labeled duration |
| --- | ---: | ---: | ---: |
| Original / 1 | 205255 -> 205255 | 123730 -> 123730 | 302473 -> 302473 |
| Original / 0.01 | 197148 -> 197271 | 120048 -> 120875 | 294858 -> 294301 |
| Additional / 1 | 183574 -> 183574 | 77448 -> 77448 | 270522 -> 270522 |
| Additional / 0.01 | 157641 -> 157641 | 68043 -> 68043 | 264413 -> 263856 |

No individual case lost root or supported-quality agreement. Two quiet cases
each lost 557 ms of incorrect labels. Less coverage is not automatically better
or worse; it is separate from correctness. The original subset contains
315846 ms of reference / 216353 ms of supported qualities per gain; the
additional subset contains 286297 / 171237 ms. These scores still leave
substantial recognition errors.

## Regression checks

- 216 single-note / octave-doubling controls no longer emit phantom chords.
- Twelve sustained major/minor added-ninth cases span three roots and two gains.
- Existing plucked Amadd9 and rapid C-Cmaj7-C fixtures retain their labels,
  including three chords in one second.
- A4 / Dm / A4 emits only the middle Dm rather than a three-second chord.
- Note changes tested at 44.1, 48, and 96 kHz stay within 50 ms of the fixture
  changes; no blank gaps are introduced between its consecutive notes.
- Silence, stereo/chunking, missing fundamentals, transposed bass rejection,
  and passing-bass inversion duration have focused regression tests.

The Android decoder workflow generates its own WAV files and checks actual
MediaCodec decoding on Android 10 without playback. It does not upload the
private problem song. Physical-device and CI outcomes are recorded separately
when they complete; adding a workflow is not a successful device test.

## Remaining limits

A missing-fundamental monophonic harmonic stack can still be classified as a
chord. Dense mixes and ambiguous voicings remain imperfect. This change fixes
specific demonstrated failures, not arbitrary-song transcription. Earlier
quality bonuses improved synthetic fixtures while worsening recorded music;
those variants were rejected.
