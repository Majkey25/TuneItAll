# Alpha22 release evidence

Version `0.3.0-alpha.22`, version code `25`, package `com.tuneitall.tuner`.
The update retains the existing Alpha closed-testing track, permissions,
testers, and countries. It adds no network dependency or audio upload.

## Change and local verification

The [quiet-retune report](../quiet-retune-evidence-2026-09-12.md) records the
octave-lock cause, fixed regression, 48-case diagnostic, and remaining limits.
Microphone input, detector thresholds, smoothing, confirmation timing, and
song analysis are unchanged.

- Reviewed source: `4424e5df8c2c7fac638a3a631935fe554cce88c4`.
- [PR #11](https://github.com/Majkey25/TuneItAll/pull/11) merged into main.
- Source/tag: `eb34eb5be230aed852221d9225d925e4f881e522` / `v0.3.0-alpha.22`.
  Production and test source match the reviewed commit.
- [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34684355697)
  passed its test, lint, build, and manifest checks in 6 minutes 14 seconds.
- Local suite: 289 tests passed, zero failures or skips, with existing
  hash-verified corpus fixtures. The separate matrix was diagnostic, not an
  all-pass accuracy test. Its temporary sources were removed after evaluation.
- Local debug, QA, instrumentation APKs, and release bundle built successfully.
  Package and permission checks passed. The local AAB is unsigned and must not
  be uploaded to Play.

## Huawei audio checks

The complete native suite passed **102/102 tests**, with zero failures or
skips, in 105.281 seconds. It includes UI, persistence, reference sound,
metronome, song decoding, and the new tuner regression. The private dense-song
fixture was restored from the existing local copy and hash-verified on-device
before the suite; its coverage and runtime checks passed. The temporary phone
copies were removed afterward, with the original local file preserved.

Huawei YAL-L21 / Android 10 passed all six focused audio tests on the isolated
QA package, version 25. The new retune check matched the JVM result: 22/23
correct settled windows, with 2.52 ms P95 detector/tracker time against the
42.7 ms hop budget. The default guitar-range check measured 2.27 ms P95, and
the ambiguous D3 check measured 2.64 ms P95.

In the full-suite repeat, retuning remained 22/23 with 2.74 ms P95 processing.
The guitar-range and D3 checks measured 3.78 and 2.60 ms P95 respectively.

Microphone callbacks averaged 42.77 and 42.76 ms across two restart runs, with
zero bursts. Auto selected Voice Recognition (`COMPATIBLE`); raw capture was
not advertised. Float capture showed zero sub-PCM16 increments in 49,152
samples. No microphone audio was saved. These are generated-signal and input
delivery checks, not unplugged-guitar acoustic acceptance.

Tested QA APK SHA-256:
`f202942c6054db99d55951679d9e1df0d77132c88c92415e398a83208b89beb2`.
Instrumentation APK SHA-256:
`cda71a164177d26db8e70c58045a21c92a68c3af181dd506b2e88582479c0ee6`.

The phone was released at 11:15 CEST, with QA stopped and Home foreground.
Production app data, system settings, and viewport were not changed.

## Public artifacts

The [GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.22)
is public. The downloaded APK matches both its companion checksum and GitHub's
asset digest. It retains the existing preview signing certificate.

- APK SHA-256: `bc3e3d393ba84b21a901d8ae587501b3343db47d9e9b1ebf5fb1b77c2f22567a`.
- Signed AAB SHA-256: `610c79f8807f0a6d90b8dd3b2782602237b3011ee0930775a299b6679da88578`.
- Preview certificate: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Play upload certificate: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

[Main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34685386102),
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34685385669),
[preview build](https://github.com/Majkey25/TuneItAll/actions/runs/34685432336),
and [signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34685432371) passed.
Apksigner verified the public APK. Jarsigner verified the AAB, with self-signed
certificate and JarInputStream archive-order warnings; its signer was checked
separately. Bundletool validation passed. The actual manifests confirm code 25,
API 26+, target 36, the unchanged package, and no Internet or advertising-ID
permission. The AAB contains neither the test-only D3 resource nor the
experimental ONNX model or upload keystore.

The GitHub APK is debug-signed for direct testing, not signed with the Play
app-signing key. It cannot replace a Play installation with a different signer.
Only the signed CI AAB is used for Play.

## Google Play

The previous alpha21 release was confirmed available to selected Play testers
on 12 September 2026. The console records publication on 8 September at 15:23
CEST.

Alpha22 was uploaded and submitted on 12 September at 11:27 CEST. Publishing
overview confirms **Probíhá kontrola změn** for exactly one change: code 25 on
the existing Alpha closed-testing track. Quick checks were still running.
Submission is confirmed; approval and tester availability are not yet confirmed.

English and Czech notes match the committed metadata. Rollout is 100% of the
existing Alpha track, with managed publishing off. Testers, 177 countries,
prices, and store artwork were unchanged. Play reports zero loss of supported
devices. Its two warnings concern optional deobfuscation files and native
debug symbols, not a blocking error.
