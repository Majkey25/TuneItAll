# Alpha28 verification

Version `0.3.0-alpha.28`, code `31`, package `com.tuneitall.tuner`.
The [GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.28)
is public with a downloadable testing APK. Google Play received the Alpha
submission at 15:55 CEST on 19 September. The Alpha track subsequently confirmed
code 31 available to selected testers, published at 16:29 CEST that day.
Recognition work remains open.

## Initial verified song changes

[PR #18](https://github.com/Majkey25/TuneItAll/pull/18) fixes the failures documented
in [song evidence](../song-evidence-2026-09-19.md). Source
`fbf98a32a8e570ab807b70a5428e195bb1699619` passes 323 local tests with zero skips,
Android Lint, QA assembly, and instrumentation assembly.

- [CI tests, lint, and build](https://github.com/Majkey25/TuneItAll/actions/runs/35443238550): passed.
- [Android 10 decoder](https://github.com/Majkey25/TuneItAll/actions/runs/35443238549): 10 tests passed, zero skips.
- Independent PCM controls: 64 bass/violin cases and three melody octave cases passed.
- Recorded-song comparison: no root or supported-quality loss in any of 48 cases.

## Physical device

Huawei YAL-L21, Android 10, isolated QA package.
USB had failed earlier but was stable for the 14:52 CEST test window.
All 11 selected tests passed in 23.451 seconds, including actual MediaCodec
decoding, ninth chords, melody octaves, silence, stereo cancellation, and the
private problem song. No test was skipped.

The private 210535 ms recording produced 286 chord events, with 90.3892% label
coverage, in 16638 ms. The unchanged limit is 30000 ms. These results establish
processing and coverage, not correct chord labels throughout that recording.

The tested QA build still carried alpha27/code30 metadata. Only the release
version changes afterward; the source hash above identifies its analysis code.

- QA APK SHA-256: `e4c5977fe47b9214173cc5034fbcbd212bfc7a97b9580468c025f385bae7bc5d`.
- Test APK SHA-256: `cb44bb19eb91aad084e6ba2ebd44dec87e37aabfaa2baedef6bcbcd3c67a44db`.

Instrumentation exited and the phone was released at 14:55 CEST. The temporary
public transfer file was removed; the QA fixture and original host file remain.
No audio playback, Focusrite changes, production app data changes, or shared
ADB server reset occurred. The private recording was not uploaded.

## Final root-ranking correction

Further controls found 20 wrong roots in 24 spread-major voicings. The final
change makes persistent observed evidence available before root locking, using
the existing relative note-presence rule. Ambiguous pitch sets retain the
original bass/context ranking. Global score changes that regressed recordings
were rejected. See [song evidence](../song-evidence-2026-09-19.md).

Final head `378302f4109db47bf35eb547a780a4c2e399f02f` passes 325 local tests,
zero failures or skips, Android Lint, QA assembly, and test APK assembly.
All 24 spread-major cases pass. Independent review checks 102 cases, including
72 strict controls and 30 ambiguous/rootless comparisons, plus weak aliases.
The 48 frozen recording/gain cases have no per-case root or supported-quality
loss compared with the released baseline.

- [Final PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/35446279120): passed.
- [Final Android 10 decoder run](https://github.com/Majkey25/TuneItAll/actions/runs/35446279216): 12 tests, zero failures or skips, 8.823 seconds.
- Final physical Huawei run: 13 tests, zero failures or skips, 25.714 seconds.
- Installed QA metadata: code 31 / `0.3.0-alpha.28-qa`.
- Final QA APK SHA-256: `c170a6ec5e4007437e7feec73031f94a66ec4afa1cb91668c0747a17b19f5ac2`.
- Final test APK SHA-256: `16f991d80af4705412349e120e7d4ad1861ae6f8eada40873bc2544707a9e10e`.

The final phone run includes the private recording's unchanged coverage and
30000 ms runtime assertions. Instrumentation exited and the phone was released
at 15:44 CEST. All checks remained silent. The intermediate root build also
passed 12 device tests in 26.686 seconds before the weak-alias safeguard was added.

## Source and signed bundle

PR #18 merged at 15:46 CEST as `6126d0e0250fa90d0b6538aebf4236c22c12a262`.
Its tree matches the verified head. Tag `v0.3.0-alpha.28` points to that merge.

- [Signed bundle workflow](https://github.com/Majkey25/TuneItAll/actions/runs/35446277064): passed, built the final head.
- AAB SHA-256: `eacede24da0a992ee0a3d3bcd68ac5ee360ec3ff22b399c0c063bd4ef2420029`.
- Upload signer SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Bundletool 1.18.3 validation and jarsigner verification pass. The existing
self-signed-certificate and JarInputStream-ordering warnings remain. Manifest
inspection confirms the unchanged package, code 31, API 26+, target 36, and no
Internet or advertising-ID permission. The archive contains no private recording,
experimental model, test WAV, or keystore.

## Public APK

- [Preview release workflow](https://github.com/Majkey25/TuneItAll/actions/runs/35446798115): passed.
- APK SHA-256: `785d9adb0d5028f59a45a8e50c4b5ccb76f7a6c48262befde2abae3196b0d60c`.
- Preview signer SHA-256: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

The downloaded APK matches its companion checksum and the GitHub asset digest.
Apksigner verification passes. Packaged metadata confirms code 31 / alpha28 and
the unchanged application ID. This debug-signed APK is separate from the signed
Google Play bundle. [Main CI](https://github.com/Majkey25/TuneItAll/actions/runs/35446760742)
and the [Pages build](https://github.com/Majkey25/TuneItAll/actions/runs/35446760456) pass.

## Google Play submission

Publishing overview confirms `Probíhá kontrola změn` for exactly one change,
Alpha code 31 / alpha28. The rollout remains at 100% of the existing Alpha
group. Testers, countries, pricing, and managed-publishing settings were not changed.
There are no lost supported devices.

The two optional warnings concern a deobfuscation file and native debugging
symbols. The build is not obfuscated and adds no app-authored native code.
No new account terms were accepted. Google review, not the upload or this
verification record, determines when testers receive the update.

At 16:54 CEST, the Alpha track showed `K dispozici pro vybrané testery` for
code 31 / alpha28, with publication time 19 September 16:29. Publishing overview
showed no unpublished changes.
