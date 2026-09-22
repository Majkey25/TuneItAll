# Alpha29 verification

Version `0.3.0-alpha.29`, code `32`, package `com.tuneitall.tuner`.
Source `ddf7e551adcb8112de15a6aa41a804122009bbcf`,
[PR #19](https://github.com/Majkey25/TuneItAll/pull/19).
PR #19 merged as `497b0c44f1b79bd7ff1dbeb86a94131f907b36eb` at 23:05 CEST.
Its tree exactly matches the verified source. Tag `v0.3.0-alpha.29` points
to the merge. The GitHub prerelease is public; Google Play Alpha review and
production-access approval remain pending.

## Verified changes

See [low-note evidence](../song-low-notes-2026-09-22.md) for the root causes,
rejected candidates and frozen comparisons. Live microphone tuning, permissions,
settings and dependencies are unchanged.

- 330 local tests passed, with no failures or skips.
- Android Lint, QA APK and instrumentation APK builds passed.
- 216 low-note controls and 240 negative controls passed.
- 288 quiet-melody comparisons passed without any loss against alpha28.
- All 1,665 predictions across 48 frozen recording/gain cases exactly match alpha28.
- Independent review added a four-case regression for quiet Float audio with a DC offset.

- [PR tests, lint and build](https://github.com/Majkey25/TuneItAll/actions/runs/35783613608): passed.
- [Android 10 file-decoder CI](https://github.com/Majkey25/TuneItAll/actions/runs/35783613731): 14 tests, no failures or skips, 12.045 seconds.

## Signed Play bundle

[Signed build](https://github.com/Majkey25/TuneItAll/actions/runs/35783593544)
passed on the verified source.

- AAB SHA-256: `47d559994989a54b66cc2135937dabb1fe5de4923bea9489f31a02dee1350b3d`.
- Upload signer SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Bundletool 1.18.3 validation and jarsigner verification passed. The existing
self-signed certificate, missing timestamp and JarInputStream-ordering warnings
remain. The actual bundle manifest confirms code 32 / alpha29, API 26+,
target 36, the unchanged application ID, no Internet or advertising-ID permission,
and no debuggable flag. The archive has no duplicate entry, private recording,
test WAV, experimental model or signing key.

## Physical Android 10 device

Huawei YAL-L21, isolated QA package, installed code 32 / alpha29.
All 15 selected tests passed with no skips in 32.852 seconds on 22 September.
They cover real MediaCodec file decoding, low strings, reference offsets,
PCM16 false-note controls, silence, stereo cancellation, melody timing,
ninth chords, ambiguous voicings and the private problem recording.

The private recording met the existing 85% label-coverage and 30-second
runtime limits. Those assertions do not prove every chord is correct.

- QA APK SHA-256: `32cd93f8e9c6b1d24be6c9627a96b4d9882eae5b95d2e80f8a2e5dc21026d807`.
- Test APK SHA-256: `2385b380904dc7c50c47c156c3ce41ab0c1fc33d3cf72670159f7ab14a2fd7ae`.

The instrumentation process exited and the phone was released at 23:01 CEST.
The temporary public transfer copy was removed; the private host recording
and QA cache fixture remain. No audio was played, no Focusrite settings were
changed, and no production app data was modified. The recording was not uploaded.

## Production access

The [production-access application](2026-09-22-production-access.md) was
submitted at 22:50 CEST. Google approval is pending. An Alpha release and
a public GitHub APK are not a production rollout.

## Google Play Alpha submission

At 23:11 CEST, publishing overview confirmed `Probíhá kontrola změn` for
one submitted change, Alpha code 32 / alpha29. Quick checks and review are
pending, so tester availability is not yet confirmed. Rollout remains at
100% of the existing Alpha group; testers, countries, pricing and managed
publishing were not changed. No previously supported devices were lost.

The only two validation warnings concern optional deobfuscation data and native
debugging symbols. The build is not obfuscated and adds no app-authored native code.

## Public GitHub APK

[Alpha29](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.29)
was published at 23:12 CEST by the
[preview workflow](https://github.com/Majkey25/TuneItAll/actions/runs/35784464876).

- APK SHA-256: `8e69a06c8e297343ee12e105c9d4e955a373ee8e930bc7503c5b403a5fe15ed0`.
- Preview signer SHA-256: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

The downloaded APK matches its companion checksum and GitHub asset digest.
Apksigner verification passed. Packaged metadata confirms code 32 / alpha29
and the unchanged application ID. The testing APK is distinct from the
signed Play bundle.

[Main CI](https://github.com/Majkey25/TuneItAll/actions/runs/35784419058) and
[Pages deployment](https://github.com/Majkey25/TuneItAll/actions/runs/35784417954)
passed for the merge commit.
