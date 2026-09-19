# Alpha27 verification

Candidate `0.3.0-alpha.27`, code `30`, package `com.tuneitall.tuner`.
Not yet published. Alpha26 is confirmed available to the existing Alpha testers,
published 12 September at 19:07 CEST, checked in Play Console on 19 September.

## Change

The [quiet harmonic-fit record](../quiet-harmonic-fit-2026-09-12.md) describes
the detector and tracker correction, frozen comparisons, and remaining limits.
This release does not change song chord recognition, microphone capture, UI,
settings, permissions, or dependencies.

## Checks on 19 September

The first local build could not find the Android SDK. A minimal SDK was restored
at its original path using the retained accepted license. Gradle installed
platform 36, build-tools 35.0.0, and platform-tools. No emulator was installed.

The 311 JVM tests ran again with zero failures or skips. Android Lint,
QA/instrumentation assembly, debug assembly, and unsigned bundle verification
passed. The unsigned local bundle is not a Play upload artifact.

The first silent Huawei YAL-L21 / Android 10 run passed all pitch assertions
but failed two of 11 tests on runtime bounds. Chime rejection measured 50.73 ms
P95, then 51.69 ms in an isolated repeat. Quiet retuning measured 54.11 ms P95.
The audio hop is 42.67 ms. Those failures are not waived.

Candidate model orders repeatedly projected the same PCM onto the same lower
harmonics. Projection reuse removes those repeated scans without changing the
model, thresholds, search, or linear solver. A frozen pre-optimization comparison
passes with bit-identical frequencies and candidate weights across 36 generated
inputs spanning six frequencies, three sample rates, and two noise seeds.
The post-fix native gate remains pending.

All checks were silent. No phone or Focusrite playback, production app data
changes, or system audio settings changes occurred. The first phone window
was released at 11:23 CEST with QA stopped and Home foreground.

Pre-optimization QA APK SHA-256:
`f1c96798270f8512a5f5a03d9416c5866f88e6be9aead96a7aaa67b8ac93582e`.
Instrumentation APK SHA-256:
`30c4f202d67ecd43c545e9e7b41b5405f69b03b63bd5a0fbef610bf50aad928d`.

Generated PCM checks and physical DSP timings do not establish acoustic
accuracy for an unplugged guitar recorded by the user's phone microphone.
