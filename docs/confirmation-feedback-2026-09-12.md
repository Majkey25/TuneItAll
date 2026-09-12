# Confirmation-sound interference

## Reproduced cause

The old tracker test supplied an 880 Hz candidate directly. Actual audio
behaves differently: guitar AUTO searches up to about 392 Hz, so the 90 ms
chime can produce a roughly 293 Hz subharmonic candidate. Mixing the app's
generated chime with a quiet E2, A2, D3, G3, B3, or E4 reproduced incorrect
fresh measurements for several overlapping frames. Returning to the guitar
after the chime was not enough to make the intervening readings valid.

Mode/settings changes also cleared the feedback deadline while playback or
its acoustic tail could remain. A new Huawei regression failed on that old
reset path and passed after removing the context-only deadline reset.

## Change

For searches below 440 Hz, the existing feedback deadline enables rejection
of the known chime partials at 880, 1764.4, and 3511.2 Hz. Two Q=1 biquads per
partial run before both YIN analysis and refinement. Samples stay in Double
precision; there is no second PCM16 quantization step. Coefficients follow
the [RBJ biquad equations](https://www.w3.org/TR/audio-eq-cookbook/).

A short-window Goertzel check requires evidence of at least one known
partial before filtering. An always-on filter regressed a near-noise G3
control; this activation check preserves clean windows. A fundamental-only
check missed an attenuated-fundamental chime, so all three partials are
checked. No candidate frequency is blacklisted.

The deadline starts before playback and survives detection-context changes.
It expires after 400 ms. Wider searches, including chromatic and some
ukulele/high-instrument presets, keep a brief gate instead of removing
legitimate notes in the chime band. This is not universal echo cancellation.
No dependency, permission, microphone source, UI smoothing, confirmation
dwell, or song-analysis algorithm changes.

## Verification

- 302 local tests passed, no skips; lint, debug/QA/test APKs, release bundle,
  and package/version/manifest checks passed. The local bundle is unsigned.
- 51 controlled mixture cases: six guitar strings with ordinary and
  fundamental-attenuated chimes, plus five bass strings; gains 0, 0.01,
  and 0.1, seeded hiss, a 20 ms direct delay and 60 ms echo.
- Each mixture retains all 7/7 fresh observations during feedback, within
  3 cents. Quiet no-chime decay, strong instrument partials near 880 Hz,
  stopped tones, eight retune/new-string cases, and high-range rejection
  controls also pass.
- Huawei YAL-L21 / Android 10: the final alpha24/code27 build passed all
  104/104 integration tests in 108.321 seconds, zero skipped/failed statuses,
  plus eight focused native audio/lifecycle checks. This final run followed
  earlier 103- and 104-test candidate runs. The installed APK hash matched
  the local build below; all three chime partials were enabled.
- Final native echo mixture: 7/7 fresh readings per string, maximum error
  2.6863 cents; complete detector/refinement/tracker P95 3.46875 ms against
  the 42.7 ms hop. Normal guitar DSP P95 2.244792 ms.
- Physical microphone restart/cadence: 42.7473 and 42.7533 ms average,
  zero callback bursts. AUTO selected the device's compatible capture path.

Final QA APK SHA-256:
`04249ec49a25ca49ae40c2ef82d0b1e8c57329450d44325f4b24f474e4420105`.
Instrumentation APK SHA-256:
`bef757b486b13388a1d85e2e753593ff3071df96392fb3324745e44d136872f7`.

Device windows ended at 14:34 and 14:46 CEST. Only QA was updated. Temporary private
song copies were removed; the original recording and production app remain.

These acoustic mixtures are synthetic PCM, not recordings of the user's
unplugged guitar through the Huawei speaker/microphone path. Device tests
establish execution, cadence and numeric agreement, not every room or
speaker response. Clipping, very long echo tails, and extremely weak notes
remain limits. The separate hardest quiet-decay target is still unmet.
Song chord recognition is unchanged; rejected research is not shipped.
