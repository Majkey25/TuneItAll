# Alpha25 release evidence

Version `0.3.0-alpha.25`, code `28`, package `com.tuneitall.tuner`.

[PR #14](https://github.com/Majkey25/TuneItAll/pull/14) merged the silence
residue fix. Tag `v0.3.0-alpha.25` points to
`8c50a151515e41f116bbfe58542e3da1fe3bd183`, whose tree matches reviewed head
`69d2c5fe3ca4fec1b6884b5f1f1028663f769b39`.

## Verification

The [feature report](../song-feature-stability-2026-09-12.md) records the
mathematical cause and unchanged timelines across 48 recording/gain cases.
The old implementation invented a C2 from 1962 to 10000 ms during silence.
Both new regressions fail before the fix and pass afterward.

Local checks passed 304 tests, zero skips/failures, lint, all APK/AAB builds,
and manifest/version verification. Huawei YAL-L21 / Android 10 reproduced
the failure on installed alpha24, then passed 13 selected tests on alpha25
in 26.731 seconds. An isolated repeat completed in 149 ms with all 81 silent
feature frames zero and no note after 2133 ms.

All device checks were silent. They covered generated/file/stereo/tempo
analysis, the existing private-song fixture, and tuner audio regressions.
The complete playback/UI suite was not run because the user requested no
playback while using Focusrite. No PC or phone audio was played. Temporary
phone song copies were removed, original data preserved, and the phone
released at 17:08 CEST with QA stopped and Home foreground.

- QA APK SHA-256: `e59d0c495ba895bc42a79bb510155aedf5b080d9d40cfde3dadbbb08a5907979`.
- Test APK SHA-256: `147848cfe64db325ff8475f98dfbe2e91003ada7814a3b17674c4528385cfdb4`.

No tuner, UI, permission, dependency, or detector-threshold changes are included.
The rejected harmonic-weighting and multi-period experiments remain outside
production. Quiet-tuning limits and arbitrary-song chord accuracy are not solved.

## Public artifacts

[GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.25).
Downloaded APK bytes match the companion checksum and GitHub asset digest.

- APK SHA-256: `1b790bfd102fbb32eb1eb32791f93605e6660da2c9309d5cae5f0b44761d983a`.
- Signed AAB SHA-256: `6d3828ad56d4ff8b04f0e0b84c13c8911fcf0d9e4562d6ab6db928baa18afc83`.
- Preview signer: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Upload signer: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Apksigner, jarsigner and bundletool validation passed. The AAB has the existing
self-signed-certificate and JarInputStream ordering warnings; it was not altered.
Actual APK/AAB manifests confirm code 28, API 26+, target 36, the unchanged
package, and no Internet or advertising-ID permission. No experimental ONNX
model, private song, test-only D3 fixture, or upload keystore is in the AAB.
The debug-signed GitHub APK cannot replace a differently signed Play installation.

Passed: [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34700852087),
[main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34701412254),
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34701411455),
[preview build](https://github.com/Majkey25/TuneItAll/actions/runs/34701487225),
[signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34701487145).

## Google Play

Code 27 / alpha24 was available to selected testers before this submission.
At 17:36 CEST on 12 September, Publishing overview confirmed
**Probíhá kontrola změn** for exactly one Alpha change, code 28 / alpha25.
Quick checks were running. Submission is confirmed; approval is pending.

The rollout remains 100% of the existing Alpha test group. Testers, 177
countries/regions, prices, store artwork and managed-publishing settings
were unchanged. Play reported no lost supported devices. Only the usual
optional deobfuscation and native-symbol warnings appeared.

Follow-up on 12 September: the Alpha track confirmed code 28 / alpha25
**available to the selected testers**. Play displays publication at 17:49
CEST. This supersedes the earlier pending-review status.
