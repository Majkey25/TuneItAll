# Alpha28 verification

Candidate version `0.3.0-alpha.28`, code `31`, package `com.tuneitall.tuner`.
Publication is pending. Recognition work remains open.

## Verified song changes

[PR #18](https://github.com/Majkey25/TuneItAll/pull/18) fixes the failures documented
in [song evidence](../song-evidence-2026-09-19.md). Source
`fbf98a32a8e570ab807b70a5428e195bb1699619` passes 323 local tests with zero skips,
Android Lint, QA assembly, and instrumentation assembly.

- [CI tests, lint, and build](https://github.com/Majkey25/TuneItAll/actions/runs/35443238550): passed.
- [Android 10 decoder](https://github.com/Majkey25/TuneItAll/actions/runs/35443238549): 10 tests passed, zero skips.
- Independent PCM controls: 64 bass/violin cases and three melody octave cases passed.
- Recorded-song comparison: no root or supported-quality loss in any of 48 cases.

## Physical device

Huawei YAL-L21, Android 10, serial `BQLDU19927002646`, isolated QA package.
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
