# Audio accuracy and release blockers

Earlier checks on 2026-09-06 found two release-blocking chord failures. The final root-locked temporal decoder resolves both without changing the existing test expectations. Release candidate: 0.3.0-alpha.19, version code 22. Publication evidence belongs in the release report, not this measurement record.

## Retained tuner improvement

The detector acquires candidates over the full instrument/chromatic range, then refines the clearest candidate in a one-semitone neighborhood with a narrower low-pass filter. It preserves the candidate probability, periodicity and unvoiced probability. It does not force the estimate to the selected string's frequency.

Refinement is skipped when filter settling would consume more than the existing transient budget. This protects low bass notes. Extremely small invalid minimum frequencies are rejected before lag arithmetic can overflow.

The [YIN paper](https://www.ee.columbia.edu/~dpwe/papers/deChevK02-yin.pdf) discusses filtering and restricted local estimation. This implementation is an independently tested adaptation, not a claim to implement every step of that paper.

Controlled default-AUTO results:

- Quiet E2 P95 absolute error: 2.60 cents; buzzing E2: 2.46 cents.
- All six ordinary quiet-string cases: 144/144 evaluated frames within 10 cents, no octave errors.
- B0 P95: 3.06 cents, unchanged after the settling guard.
- Four hiss-only controls: 0/164 voiced readings.
- Near-noise-floor signals still have missing/incorrect readings. These results are not a universal sensitivity guarantee.

## Recorded electric-guitar check

[EGSet12](https://zenodo.org/records/11406378), CC-BY-4.0, tracks 01/03/09: 93 frozen windows from 28 single-note bouts. The loader verifies hashes and reads the first channel of PCM24LE stereo at 48 kHz. It removes 100 ms after annotated onset and 50 ms before offset, and only includes complete 8192-sample windows.

At gains 1, 0.01 and 0.001, each run produced 93/93 readings and 91/93 matches within 50 cents of the nominal MIDI annotation. One window disagrees by an octave. There is no measured-cent ground truth: the provided contours repeat nominal MIDI frequencies. These are amplified recordings and cannot prove unplugged-phone capture accuracy.

## Chord findings

Two new self-contained `ChordFeatureEvidenceTest` cases initially failed:

1. A plucked Amadd9 is labelled Am even though its ninth is audible.
2. A C-major / C-major-seventh / C-major sequence within one second collapses into C major.

Root-locked quality decoding, direct/derived feature consensus, linear-magnitude NNLS and frequency-whitened NNLS were compared with frozen recordings. They repaired some controlled signals but regressed recorded quality, coverage or existing timing checks. Those candidate changes were removed. No failing test was disabled or weakened.

The retained chord implementation remains at 64.99% root agreement and 56.96% exact supported-quality agreement on the twelve-recording GuitarSet comparison described in the [previous report](audio-accuracy-2026-09-05.md). Coverage is not accuracy.

## Verification and delivery gate

- `gradlew.bat :app:testDebugUnitTest :app:lintDebug --continue --console=plain`: 269 tests, 267 passed, two chord failures, zero skips. Lint completed successfully. The combined command correctly returned failure.
- QA APK and instrumentation APK built successfully.
- Huawei YAL-L21 / Android 10: 10/10 focused tuner/audio/UI instrumentation tests passed on 2026-09-06. The microphone averaged 42.77 ms per window without callback bursts.
- Native float capture produced 49,152 samples, with zero samples containing sub-PCM16 steps. Changing the whole capture path to float was therefore not justified by this device check. No captured audio was saved.
- The separate QA package was stopped and the shared phone released. Production app data was preserved.

The earlier draft PR preserved these failures. No test was disabled to release the candidate.

## Final chord correction

The first decoding pass retains the previous root and no-chord timeline. A second pass resolves qualities only inside accepted, contiguous same-root spans. An extension must have observed spectral support rather than only inferred subharmonics. Closely related qualities use pitch-set distance for transition cost. The existing ambiguity margin is charged once when deviating from the baseline quality, not on every frame: weak but consistent evidence can accumulate over time.

Short quality excursions merge into the neighboring quality with stronger evidence, without creating holes in an already accepted root span. Output confidence remains the duration-weighted confidence of the original root path. It is not presented as a calibrated probability.

The final focused run passes all 70 music tests, including isolated plucked Amadd9, rapid C–Cmaj7–C and the original recorded-rock segmentation checks. Frozen recorded quality changes from 56.9578% to 57.1889%; root agreement stays 64.9858% and coverage stays 95.7660%. These are bounded improvements, not a guarantee of perfect transcription for arbitrary mixes.
