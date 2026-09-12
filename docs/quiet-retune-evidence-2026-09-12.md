# Quiet-retune octave lock

## Cause and change

The detector supplied the correct current fundamental in 34 of 37 windows
between 0.9 and 2.5 seconds, but alpha21's tracker emitted it correctly in only
16. An unselected half-frequency hypothesis accumulated continuity from
zero-acquisition-weight alternatives. One missing fundamental candidate let
that hypothesis take over.

Continuation-only candidates now need to stay within the existing one-semitone
range of the last emitted pitch, as well as an observed, viable predecessor.
Positive-weight candidates can still acquire a new note. The reference clears
on reset, the existing eight-empty-frame expiry, or an unvoiced winner. It is
never returned as a measurement by itself.

No microphone input, gain, detector threshold, analysis window, display hold,
smoothing, confirmation timing, or song-analysis code changed.

## Frozen comparisons

The permanent [regression](../app/src/test/java/com/tuneitall/tuner/audio/PitchTrackerRetuneTest.kt)
uses the production YIN detector and tracker with the default guitar AUTO
search range. At 48 kHz, E4 starts at amplitude 0.01, then shifts 30 cents after
0.5 seconds and drops to 0.0001. A second harmonic has amplitude 0.45 relative
to the fundamental; seeded uniform hiss has amplitude 0.0004. PCM is rounded
to signed 16-bit samples. Analysis uses 8192 samples and a 2048-sample hop.

In the settled 1.5–2.5 second interval, fresh estimates within 10 cents improve
from **5/23 to 22/23**. The wider 0.9–2.5 second diagnostic improves from
16/37 to 33/37; these are different scoring intervals, not competing results.

A separate predeclared matrix used seeds 7, 31, 81, and 137; all six standard
guitar frequencies; and shifts of -30 and +30 cents. Both trackers received
identical detector frames. Across 48 cases, correct settled windows improved
from **958/1104 to 1013/1104**, with no case regressing. Three cases improved:

| Seed / initial note / shift | Alpha21 | Alpha22 |
| --- | ---: | ---: |
| 31 / D3 / -30 cents | 0/23 | 23/23 |
| 81 / E4 / -30 cents | 5/23 | 20/23 |
| 81 / E4 / +30 cents | 5/23 | 22/23 |

Seed 31 D3 and G3 at +30 cents still have zero correct settled windows. The
other 43 cases are unchanged. This matrix measures these generated inputs,
not arbitrary recordings or microphone sensitivity.

In those two failures, correct current candidates remain present in 23/23 and
21/23 windows respectively; positive-acquisition candidates are present in
23/23 and 20/23. The remaining fault is selection, not a missing RMS gain.

The [pYIN paper](https://webspace.eecs.qmul.ac.uk/s.e.dixon/pub/2014/MauchDixon-PYIN-ICASSP2014.pdf)
uses candidate probabilities as tracking observations. A separate diagnostic
therefore tested relative positive-candidate weights alongside the current
periodicity score. It reduced this matrix from 1013 to 968 correct windows
and regressed two cases without solving the remaining failures. It was
rejected, with no production change or parameter search.

The existing hard-decay control remains below its per-string 90% target;
the [earlier report](quiet-current-evidence-2026-09-08.md) records that limit.
The rejected NSDF replacement was not shipped: its raw frequency estimates
were worse on most frozen decay controls and it supplied no validated voicing
policy. No neural or spectral fallback was added.

## Verification scope

Local checks passed: 289 unit tests, zero failures/skips, Android lint, debug
and QA APKs, instrumentation APK, release bundle, and manifest/privacy checks.
The additional positive-octave test verifies that a genuinely acquired octave
becomes the continuation reference. Existing tests cover short dropouts,
state expiry, reset, weak retuning, and obsolete-state resurrection.

These PCM regressions test detector/tracker behavior, not the ViewModel's
noise gate, note assignment, UI smoothing, or tuning-confirmation feedback.
Physical-device results are recorded in the [release evidence](store/2026-09-12-alpha22-release.md).
Real unplugged-guitar acoustic acceptance remains open.

Run the permanent regression from the repository root:

```text
gradlew.bat :app:testDebugUnitTest --tests com.tuneitall.tuner.audio.PitchTrackerRetuneTest --console=plain --max-workers=2
```
