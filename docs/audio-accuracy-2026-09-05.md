# Audio accuracy checks — 2026-09-05

These measurements describe the working branch, not a published release or a guarantee for every song.

## Retained chord changes

- Resolve the likely root before relaxing a quality prior using the observed chord-note strengths. A fixed complexity penalty previously made an ideal balanced add9 or maj7 lose to its major-triad subset.
- Require evidence for at least two chord pitch classes. An ideal isolated pitch class must not establish a major/minor chord.
- Place transitions between the centers of adjacent half-overlapping analysis windows, rather than at the start of the next window.
- Keep analysis windows between approximately 171 and 186 ms across supported input rates. Include the final partial window.
- Require a majority of the segment's duration before attaching an inversion label. Two passing bass frames must not rename a ten-second chord.
- Preserve stereo material when channel averaging causes severe phase cancellation. The bounded fallback uses one stable channel; it can omit parts unique to the other channel during cancellation.
- Cancel feature extraction and sequence decoding, not only file decoding. Reject changing PCM rates and measure duration from decoded samples.

## Frozen recording comparison

Twelve complete microphone recordings from [GuitarSet](https://zenodo.org/records/3371780), six performers each playing Rock2 and Jazz3. Total reference duration: 315.846 s. Exact-quality scoring uses 216.353 s of directly supported performed labels. Unsupported alterations/omissions are excluded explicitly, not relabelled as simpler chords. Files and annotations are hash-pinned in the optional corpus manifest.

| Metric | Alpha18 baseline | Retained scorer + timing |
| --- | ---: | ---: |
| Correct root, duration weighted | 64.28% | 64.99% |
| Correct supported root + quality, duration weighted | 54.52% | 56.96% |
| Labelled duration | 96.62% | 95.77% |

Coverage is not accuracy. More non-empty labels can mean more wrong labels. These recordings are isolated guitars, not fully mixed metal recordings. The performed GuitarSet annotations are semi-automatically derived and manually checked; they are not infallible ground truth.

## Independent controlled signals

The same deterministic PCM is used for each comparison. Plucked harmonic stacks, low levels, fret-buzz-like modulation, clipped triads/power riffs, sevenths, added ninths and three changes per second are tested alongside silence, noise and percussive transients.

| Controlled case | Retained scorer + timing: exact root + quality |
| --- | ---: |
| Clean / weak / buzzing / clipped triads | 99.15% |
| Clipped power progression, explicit power mode | 99.15% |
| Seventh progression | 74.34% |
| Added-ninth progression | 49.95% |
| Three changes per second | 94.13% |

Silence, broadband noise and the percussion-only probe produced no chord events. A real harmonic single-note probe still produces a chord label: the ideal-pitch-class regression does not prove rejection of actual monophonic audio. This remains an accuracy limitation, as do extended voicings and dense mixes.

## Rejected experiments

- Plain cosine scoring improved ideal inputs but regressed real recordings and existing tests.
- Globally reducing complexity penalties changed roots to relative chords. Removing harmonic features improved synthetic extensions but regressed GuitarSet.
- Rejecting signals explained by one harmonic series removed genuine clipped power chords. Distortion can create a common difference frequency; periodicity alone does not establish monophony.
- Basic Pitch frontends and ChordMini hybrids were benchmarked offline, not added to the app. Some improved selected quality scores but lost quiet passages or produced noise labels. Basic Pitch trained on GuitarSet, so that corpus cannot establish independent generalization for it.

A subsequent independent ChordMini probe did reject silence, noise, percussion and the harmonic single note, and scored 97.84% exact quality on the seventh progression. However, its vocabulary cannot express power chords or add9: both scored 0% exact quality. Its fast setting scored 88.05% on the rapid progression, below the retained DSP's 94.13%. It is therefore not a drop-in replacement for all three app analysis modes or all chord families. Model integration remains separate work, requiring frontend parity, device performance and explicit vocabulary handling.

No new model/runtime dependency, uploaded user audio or downloaded proprietary code was added to the application.

## Verification

- Final working tree: 264 unit tests, zero failures/skips; Android lint passed.
- Huawei YAL-L21 / Android 10: 94 instrumentation tests passed in 102.056 s, including the private problem-song decode, timeline/navigation, microphone restart, text contrast and 200% font-scale ruler checks.
- Microphone callback averages: 42.77 / 42.76 ms across two sessions, zero sub-10-ms bursts. Low-note DSP P95: 1.46 ms against a 42.7-ms hop budget. This measures processing speed and capture cadence, not acoustic accuracy on every instrument.
- Only the separate `com.tuneitall.tuner.qa` build was installed. Production data was preserved. Temporary song copies were removed and the shared phone released at 18:26 local time.
- The first instrumentation attempt was interrupted while the target APK installation was still completing. Both installers completed successfully before the complete rerun above.

No release was published by these checks.

## Reproduction

Run `gradlew.bat :app:testDebugUnitTest :app:lintDebug` for the standard checks. `ChordEmissionRegressionTest` covers balanced extensions, singleton pitch-class evidence, nearby major triads and rapid timeline alignment. `ChordAnalyzerTest` includes recorded GuitarSet audio, distorted riffs, noise and seven input rates.

`GuitarSetCorpusTest` and `BasicPitchCorpusTest` are optional measurement harnesses. Their corpus files live under `.reference/tmp/chord-benchmark`; absent fixtures are reported as skips. Passing a measurement harness alone is not an accuracy acceptance gate. Raw A/B reports are retained there; synthetic files and model experiments are not APK assets.
