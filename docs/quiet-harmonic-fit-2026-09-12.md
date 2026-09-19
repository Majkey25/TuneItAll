# Quiet pitch refinement and acquisition

## Reproduced failures

The previous frequency refinement minimized a local raw difference function.
At low signal-to-noise ratios this could retain an inaccurate period. YIN also
discarded valid fundamental troughs more than 0.05 above the deepest trough.
Finally, the tracker ranked each candidate using the larger of its probability
and periodicity. A multi-period candidate could therefore defeat the actual
fundamental and persist one octave below it, including on a quiet first strike.

The frozen released detector/tracker returned 743 correct fresh measurements
out of 846 hard-decay windows. In a separate 48-case cold-start comparison it
returned 1088/1368 correct measurements; nine cases latched the octave below.

## Change

- Fit frequency to the current narrow-filtered PCM using real-valued least
  squares: up to three low harmonics, independent phases and a fitted DC offset.
  Keep the existing search bounds and low-bass filter-settling guard.
- Retain local YIN troughs meeting the existing periodicity floor, still capped
  at eight candidates. The global-depth-margin condition is removed.
- Compare refined candidates on one common coarse-filtered window. Penalized
  residual fits select harmonic order through the filter passband, capped at 16.
  The bandwidth-adjusted sample count and resulting weights are approximations,
  not calibrated probabilities or guarantees about instrument identity.
- Allocate the existing tracker-level voiced mass across those weights. The
  tracker uses that allocation instead of overriding it with periodicity.

Acoustic model evidence can promote a formerly zero-YIN-weight proposal into
acquisition. This also changes detector-voiced input to adaptive noise-floor
training. Microphone source, gain, settings, window length, display smoothing,
feedback rejection and confirmation timing are unchanged. No history is fitted
as current audio and no target-string frequency is forced into a result.

The implementation uses Kotlin and the existing signal path; no dependency,
learned model, permission, upload or stored microphone audio is added. Ordinary
bounded least squares is used, not the fast recursive algorithm described in
the [NLS frequency-estimation paper](https://www.sqrt-1.dk/aboutme/publications/20170123_esp17.pdf).

On 19 September, physical regression tests exposed repeated projection work
that exceeded the audio-hop budget. All candidate model orders now reuse one
set of PCM projections per candidate. The frequency grid and linear solver
remain unchanged. See the [release checks](store/2026-09-19-alpha27-release.md).

## Local evidence

The final source passes 311 existing/new JVM tests and Android Lint, with no
failures or skips. Checks include the detector, adaptive floor and tracker,
not just isolated candidate values.

| Frozen comparison | Released | New |
| --- | ---: | ---: |
| Hard decay, correct fresh windows within 10 cents | 743/846 | 837/846 |
| Quiet cold start, correct windows within 10 cents | 1088/1368 | 1368/1368 |

Hard decay spans three noise seeds and six strings. All 18 new cases exceed
90% correct fresh coverage; none returns a fresh value outside 10 cents in
the scored interval. Cold start spans six strings, ±30-cent offsets, two seeds
and both 44.1/48 kHz. These are deterministic, generated three-harmonic PCM
signals mixed with noise, not recordings of an unplugged electric guitar.

Whole-clip white/colored noise, level changes, isolated clicks, stopped tones,
new strings, tuning glides, fret-buzz models, low bass and confirmation-echo
regressions remain passing. Model order initially limited to three failed an
existing strong-eighth-partial fixture; covering the coarse passband fixes it
without a tone-specific exception.

Three real EGSet recordings provide 93 eligible windows per gain. Nominal-note
identity remains 91/93 at each gain of 1, 0.01 and 0.001, with one octave error
at each gain. All nine per-recording/gain totals are unchanged. These labels
do not provide cent-accurate acoustic ground truth. The retained octave error
is not presented as resolved.

## Physical checks and limits

Silent instrumentation runs on Huawei YAL-L21 / Android 10 exercise the actual
installed detector/tracker and microphone lifecycle. No test plays sound through
the phone or Focusrite. Physical DSP execution is not an acoustic guitar test.
Exact final native results, artifact hashes and delivery status belong in the
versioned release record.

The new checks meet the previously failing generated quiet-decay target. They
do not establish performance for every phone, room, string, buzz pattern or
recording. Actual low-level guitar capture remains a separate acceptance input.
Song chord recognition is unchanged by this tuner patch.
