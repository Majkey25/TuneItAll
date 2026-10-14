# Alpha 39 song-analysis release

Candidate `0.3.0-alpha.39` (42), package `com.tuneitall.tuner`.

## Changes

- Do not backdate a later song melody into an unsupported bass intro.
- Ignore seeks until the song player finishes preparation.
- Keep microphone sensitivity, pitch tracking, confirmation and reference sound unchanged.

## Verification

The clean local release gate passed 399 JVM tests, lint with zero errors and
24 warnings, APK/AAB builds, ten signature checks and 17 release-policy checks.
The added native regressions reproduced both old paths on the pinned alpha 38
QA APK, then passed on alpha 39. The final Huawei Android 10 recheck passed all
24 cases without skips in 14.108 seconds. Prepared seeking, reanalysis, cancellation,
melody timing, ninth chords and distorted power chords were included.

The first 24-case run had one bounded-import setup rejection; an isolated check
and complete repeat passed. The initial install-interrupted run is not counted.
The [reliability report](../reliability-2026-10-13.md) preserves these limitations.
No emulator, microphone recording or desktop/Focusrite playback was used.

## Delivery

Publication is pending at this candidate checkpoint. The existing Play version 41
was still in review at the last Console check. GitHub and Play delivery will be
verified separately; submitting to Google does not mean the version is live.

The 100-assignment Gemini audit remains incomplete: after preserving old results
and requeuing changed inputs, 66 accepted assignments apply to the current source
and 34 are pending. This release does not claim perfect recognition of arbitrary
songs or address every later UI audit hypothesis.
