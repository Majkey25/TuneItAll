# Chord input evidence and recording level

Development candidate after alpha19. The changes address false chords from inferred subharmonics and inconsistent results when only file gain changes. They do not establish better transcription for every recording. Publication remains separate from these measurements.

## Reproduced failures

`SingleNoteChordTest` sends PCM through the actual feature extractor and chord decoder. The original decoder labels a single A tone as D minor or Dsus2. Its harmonic sum supplies virtual D and F evidence, and the chord scorer counts those inferred notes as if they were observed.

The scorer now requires two observed pitch classes belonging to its candidate chord. Evidence uses the same local window as the chord features. A first implementation used the 1.5-second context and incorrectly labelled Dm from 324 to 2646 ms when the actual chord occupied 1000 to 2000 ms. A regression test caught this. The local-window implementation passes that test and 216 single-tone/octave-only cases across all pitch classes, three octaves, and three gains.

The old `ln1p(magnitude)` weighting also changes relative pitch strengths with recording gain. The candidate uses `sqrt(magnitude / frequency)` before the existing normalization. Uniform gain then supplies a common factor that normalization removes. Maximum feature deviation in the plucked-chord control falls from 0.1154 to 0.000051. This is a gain-consistency result, not a claim that a square-root feature is universally the best classifier input.

[Logarithmic compression](https://www.audiolabs-erlangen.de/resources/MIR/FMP/C3/C3S1_LogCompression.html) and [feature normalization](https://www.audiolabs-erlangen.de/resources/MIR/FMP/C3/C3S1_FeatureNormalization.html) describe the underlying operations and their noise tradeoffs. The implementation and tests here are independent.

## Recorded comparison

The original twelve GuitarSet recordings remain unchanged. Twelve additional full microphone recordings were selected before candidate evaluation, across bossa nova, funk, jazz, and rock. The new selection uses performers 00, 02, and 04 on BN1-129-Eb, Funk1-114-Ab, Jazz1-130-D, and Rock1-130-A. The previously tested 00_Rock1 recording was replaced with 00_Rock3-117-Bb before evaluation.

Fixtures are hash-pinned under `.reference/tmp/chord-benchmark` and `.reference/tmp/chord-heldout`. The new manifest SHA-256 is `14d41127dcc7b2bb68b4da1f6d0d062f3abc66a25ebba653c037996cababdf4a`. These are additional recordings from [GuitarSet](https://zenodo.org/records/3371780), not independent mixed-band or metal validation. Supported exact-quality duration is 216.353 seconds in the original selection and 171.237 seconds in the new selection. Annotation limitations from the earlier reports still apply.

| Recording set | Gain | Root before | Root candidate | Exact quality before | Exact quality candidate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original twelve | 1 | 64.99% | 64.64% | 57.19% | 57.81% |
| Original twelve | 0.01 | 62.42% | 64.64% | 55.49% | 57.81% |
| New twelve | 1 | 64.12% | 63.07% | 45.23% | 43.95% |
| New twelve | 0.01 | 55.09% | 63.07% | 39.79% | 43.95% |

The new-set quiet baseline includes the observed-note guard; at normal gain that guard leaves both recorded sets unchanged. The gain-consistent candidate improves quiet recordings but regresses normal-gain root/quality on the new selection. Averaging the two gain levels must not be presented as a general accuracy improvement.

Normalized logarithmic peaks and whole-file RMS normalization were also evaluated. Both broke existing recorded regressions and were removed. No existing test expectation was reduced.

## Verification

`gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleQa :app:assembleQaAndroidTest --console=plain` passed with 277 unit tests, zero failures, and zero skips on this workspace. Optional corpus tests require the local fixtures and skip when absent. Existing rapid C-Cmaj7-C, plucked Amadd9, noise, distorted power-chord, NOTES, tuner, and cancellation tests pass.

The Android instrumentation suite also checks decoded PCM16 octave-only notes and a Cmaj7 at two recording levels. Huawei YAL-L21, Android 10: all 97 instrumentation tests passed in 72.03 seconds on 2026-09-06. This includes the private dense-song fixture, tuner capture, playback, and UI workflows. The private song check measures runtime and coverage, not reference chord accuracy. The separate QA package was stopped and the phone released at 13:33 CEST. Production app data was preserved.

## Remaining accuracy work

The observed-note guard does not distinguish every rich single-note overtone series from a chord. Full mixes still need better fundamental evidence and independent chord/timing references. The normal-gain regression above prevents calling this a completed general-accuracy upgrade or publishing it as one.
