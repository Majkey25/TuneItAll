# Quiet-note current evidence

## Cause and change

At default sensitivity 100, the RMS gate already accepts finite input frames.
The investigated failure was not a missing gain slider: a weak decaying D3
lost its current fundamental candidate when a nearly equivalent doubled period
won the detector search. The tracker could no longer choose the fundamental.

Retain near-best current YIN troughs using the existing periodicity floor and
margin. Additional alternatives carry zero acquisition probability. They can
continue an observed, viable pitch within one semitone, but cannot acquire a
new pitch. Near-best candidates use the existing frequency refinement.

An independent review found that an old latent state could become viable again
when competing scores fell. The tracker now distinguishes current observations
from unobserved retained states. Retained states cannot regain continuation
eligibility just by outlasting competitors, and cannot be emitted as fresh
measurements. Existing eight-frame gap expiry remains unchanged.

The [pYIN paper](https://webspace.eecs.qmul.ac.uk/s.e.dixon/pub/2014/MauchDixon-PYIN-ICASSP2014.pdf)
motivates retaining multiple observations for temporal decoding. The bounded
continuation rule here is independently implemented and tested; it is not a
claim to reproduce the paper's full probabilistic model.

No gain, input source, acquisition threshold, FFT fallback, window length,
needle smoothing, display hold, or confirmation duration changed.

## Frozen checks

Compared with released source `48168f8`, using identical inputs and settings:

| Default near-noise control | Released | Final change |
| --- | ---: | ---: |
| Fresh readings out of 144 | 118 | 128 |
| Within 10 cents out of 144 | 104 | 122 |
| Octave errors | 3 | 0 |

Ordinary quiet-string checks remain 144/144 within 10 cents. P95 errors remain
2.60 cents for quiet E2, 2.46 for buzzing E2, and 3.06 for B0. Deterministic
AUTO/CHROMATIC note switching remains 194.7 ms. Four hiss controls remain
0/164 voiced readings.

The final isolated audio/tuner suite passed 132/132 tests with no skips.
The repeated-mixture regression produced 12/12 stale readings before the
observation-state fix and none afterward. The immutable D3 resource checks
candidate identity and zero acquisition weight, not final tuning accuracy.
Its decoded PCM16LE SHA-256 is
`d27fc8f5fd3c04276591da234f9875600a50e58b320ecce28733765a95f29aea`.
It is generated test audio, not a user's microphone recording.

The hash-verified EGSet12 control remains 91/93 nominal-note matches at gains
1, 0.01, and 0.001, with 93/93 readings and one octave mismatch at each gain.
Those are amplified recordings and nominal labels, not measured-cent ground
truth or a physical unplugged-guitar test.

## Unmet stress target

The separate continuous-decay stress test still fails its requirement of at
least 90% fresh readings within 10 cents for every string. Late fundamentals
approach one PCM16 step under noise of about 13 steps. Correct final counts
are 44, 43, 38, 46, 41, and 39 out of 47 windows for E2 through E4.

An unsafe intermediate candidate reached 130/144 in the near-noise control
and 41/47 on hard D3 decay. Those are not the shipped candidate's results.
Closing stale-state resurrection reduced them to 122/144 and 38/47; released
hard D3 was 31/47. Display-held readings are not counted as fresh detection.

The failing stress harness and rejected adaptive/spectral experiments remain
preserved in the separate research worktree. They are not silently weakened
or included as production fallbacks. Physical unplugged-guitar acceptance
remains open; generated PCM and regular microphone callbacks cannot prove it.

## Combined build and phone checks

The combined release branch passed 287/287 local tests with no skips, Android
Lint, APK/test APK builds, and the unsigned release-bundle check.

Huawei YAL-L21 / Android 10 passed the 100-test app suite, nine repeated audio
checks, then the complete **101-test suite** including the new immutable D3
fixture. That final run completed in 105.774 seconds with no failures/skips.
The D3 check preserved its zero-acquisition fundamental and measured P95 times
of 2.45 and 2.73 ms over 50 calls after five warm-up calls. The default guitar
AUTO-range control measured 2.53 and 2.50 ms P95. Both use the existing 42.7 ms
audio-hop budget.

Real microphone callbacks averaged 42.77 ms without bursts across two restart
runs. Auto selected Voice Recognition (`COMPATIBLE`); raw input was not
advertised. Float capture returned no sub-PCM16 increments in 49,152 samples
on this phone, so merely switching the buffer type would not recover extra
input resolution in this check. No captured audio was saved.

These phone checks validate execution, input delivery, and generated-signal
precision. They do not close the separate unplugged-guitar acoustic target.

Tested combined QA APK SHA-256:
`fdc7d88721b4e6f4d0108151ced226759c2e9b5a866d58e5a54d29039827ea9e`.
Final 101-test APK SHA-256:
`f343c66645db8a61b77605ddaf72e145b0f697c142daf1e02177e5e57e90842b`.
