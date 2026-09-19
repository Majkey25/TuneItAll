# Alpha27 verification

Version `0.3.0-alpha.27`, code `30`, package `com.tuneitall.tuner`.
[GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.27)
is public with a downloadable testing APK and checksum.
Google Play received the Alpha submission at 11:46 CEST on 19 September.
Quick checks and review remain pending. Alpha26 is the last confirmed
tester-available release, published 12 September at 19:07 CEST.

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
The post-fix native gate passes.

All checks were silent. No phone or Focusrite playback, production app data
changes, or system audio settings changes occurred. The first phone window
was released at 11:23 CEST with QA stopped and Home foreground.

Pre-optimization QA APK SHA-256:
`f1c96798270f8512a5f5a03d9416c5866f88e6be9aead96a7aaa67b8ac93582e`.
Instrumentation APK SHA-256:
`30c4f202d67ecd43c545e9e7b41b5405f69b03b63bd5a0fbef610bf50aad928d`.

Generated PCM checks and physical DSP timings do not establish acoustic
accuracy for an unplugged guitar recorded by the user's phone microphone.

## Final physical gate

All 11 tests pass in 15.123 seconds on the optimized QA build. The isolated
confirmation-echo repeat also passes in 1.792 seconds.

| Check | Before P95 | After P95 |
| --- | ---: | ---: |
| Confirmation rejection | 50.73 ms | 8.43 ms |
| Isolated confirmation repeat | 51.69 ms | 8.59 ms |
| E4 quiet retune | 54.11 ms | 9.67 ms |
| Ambiguous D3 refinement | 35.35 ms | 5.13 ms |

These are test wall-clock timings, not sampled CPU profiles. No runtime bound
or pitch-accuracy assertion was relaxed. Six-string hard decay retains 47/47
fresh correct windows on E2/A2/D3/G3 and 46/47 on B3/E4. Both quiet cold-start
cases have full scored coverage, 27/27 at 44.1 kHz and 30/30 at 48 kHz.
The 1/2/4 kHz chromatic checks remain below the audio hop, worst P95 15.50 ms.

Every mixed-chime window retains the correct string, maximum error 0.523 cents.
Microphone restart checks average 42.76 ms between callbacks with zero bursts.
Float capture still provides no additional sub-PCM16 resolution on this Huawei.

Optimized QA APK SHA-256:
`2c3cb4c24ee6aaeeefaa5d71fcc8693194543e6b9c5623f4c48b186204ea9033`.
Instrumentation hash is unchanged. Installed package metadata confirms
code 30 / `0.3.0-alpha.27-qa`. The phone was released at 11:40 CEST, QA stopped,
Home foreground, no queued ADB, playback, or shared settings changes.

## Source and Play artifact

[PR #17](https://github.com/Majkey25/TuneItAll/pull/17) merged head
`9309050be0b10f4e48c88e20aafa801d8d10322e` as
`6dc5c069a596d9cd8471be89a2a348200472a0c4`. Their trees are identical.
Tag `v0.3.0-alpha.27` points to the merge. The signed Play workflow built the
reviewed head; the preview workflow builds the tag.

- [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/35434860480): passed.
- [Signed bundle](https://github.com/Majkey25/TuneItAll/actions/runs/35434871662): passed.
- AAB SHA-256: `97ce972e47c265fdefb4e45aa7ad2a7f1facc3439b810bb41132145b570f21b6`.
- Upload signer: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- APK SHA-256: `ca8a5bd95da1a7f3e38b51310535089a8ddfc237fe199d18c87df8ec1e549787`.
- Preview signer: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

The downloaded GitHub APK matches the companion checksum and GitHub asset digest.
Apksigner verification passes and the packaged metadata confirms code 30 / alpha27.
[Preview release](https://github.com/Majkey25/TuneItAll/actions/runs/35435400036),
[main CI](https://github.com/Majkey25/TuneItAll/actions/runs/35435370719), and
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/35435370150) pass.

Bundletool 1.18.3 validation and jarsigner verification pass. Jarsigner reports
the existing self-signed certificate and JarInputStream-ordering warnings.
The actual bundle manifest confirms the unchanged package, code 30, API 26+,
target 36, and no Internet or advertising-ID permission. The archive contains
no experimental model, private recording, test PCM, or keystore.

## Google Play submission

Publishing overview confirms **Probíhá kontrola změn** for exactly one change,
Alpha code 30 / alpha27. This confirms submission, not approval or availability.
The release remains at 100% of the existing Alpha group. Testers, countries,
pricing, listing artwork, and managed-publishing settings were not changed.

Play reports no lost supported devices. Its two optional warnings concern
deobfuscation files and native debugging symbols. The release is not obfuscated;
no app-authored native code is added. No new terms or account prompts were accepted.
