# Quiet-note recovery after a short gap

## Cause

In two quiet D3/G3 retunes, the current fundamental disappeared for three
analysis frames. When it returned with zero acquisition weight, alpha22
rejected it because its predecessor was marked unobserved. The lower octave
took over in that same update and remained selected.

The fix permits fresh continuation near the last emitted pitch through a
short gap. A separate saturated counter expires this permission after eight
updates without a fresh estimate, including nonempty ambiguous frames. The
existing empty-frame expiry and early unvoiced-clear behavior remain intact.
Retained states still cannot be emitted as fresh measurements.

No gain, input source, analysis window, detector threshold, display smoothing,
confirmation timing, dependency, or user setting changed. The bound counts
tracker updates, not wall-clock time during a paused capture session.

## Fixed-input results

Using the same 48 retunes as the [alpha22 report](quiet-retune-evidence-2026-09-12.md),
correct estimates within 10 cents improve from **1013/1104 to 1065/1104**.
No case regressed. Five cases improved:

| Seed / starting note / retune | Alpha22 | Alpha23 |
| --- | ---: | ---: |
| 31 / D3 / +30 cents | 0/23 | 23/23 |
| 31 / G3 / -30 cents | 18/23 | 22/23 |
| 31 / G3 / +30 cents | 0/23 | 21/23 |
| 137 / G3 / -30 cents | 19/23 | 22/23 |
| 137 / E4 / -30 cents | 19/23 | 20/23 |

The remaining 43 cases are unchanged. The matrix is diagnostic, not a claim
that every frame or retune is correct. Both trackers received identical
detector frames, with the same seeds, amplitudes, frequency shifts, and
1.5–2.5 second scoring interval as before.

The existing near-noise control improves from 122/144 to 130/144 correct
fresh readings, with emitted readings increasing from 128 to 136 and zero
octave errors. Ordinary quiet strings remain 144/144, standalone hiss remains
0/164 voiced readings, and AUTO/CHROMATIC note switching remains 194.7 ms.

The separate hard-decay target still fails. D3 improves from 38/47 to 41/47;
the other five strings remain 44, 43, 46, 41, and 39 out of 47 respectively.
The research suite has the same three failures: two rejected spectral-policy
checks and this unmet decay target. No rejected fallback is shipped.

## Regression coverage

The recovery test failed on alpha22 before implementation. Boundary tests
cover seven, eight, and sixteen preceding null readings, including ambiguous
nonempty input. A separate control confirms that unvoiced evidence clears
continuation before the gap limit. Existing octave-acquisition, reset, and
obsolete-state tests remain in the release suite.

The release PCM test covers E4, D3, and G3 retunes through the detector, noise
gate, tracker, tuner engine, and confirmation tracker. It also rejects false
in-tune readings and confirmation during the detuned interval. Stopped-tone
and new-string controls exercise the same components plus display retention.
Both controls assert an initial in-tune reading before the dropout or note
change, so an inactive detector cannot pass them by returning only silence.

Local release checks passed 294 tests with zero failures/skips, lint, all
APK builds, and the bundle/manifest checks. Huawei YAL-L21 / Android 10 passed
102/102 instrumentation tests in 99.228 seconds, plus six focused audio tests.
Native E4/D3/G3 retunes match the local counts: 22/23, 23/23, and 21/23.
Their P95 processing times were 2.54, 2.04, and 1.98 ms against the 42.7 ms hop.

These are controlled PCM and component checks, with separate real microphone
cadence checks. They do not prove unplugged-guitar acoustic accuracy for an
unavailable recording, Android callback race freedom, or perfect song chords.

Run the focused permanent tests from the repository root:

```text
gradlew.bat :app:testDebugUnitTest --tests com.tuneitall.tuner.audio.PitchTracker* --tests com.tuneitall.tuner.audio.TunerContinuationSafetyTest --console=plain --max-workers=2
```
