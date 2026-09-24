# Alpha30 verification

Version `0.3.0-alpha.30`, code `33`, package `com.tuneitall.tuner`.
[PR #20](https://github.com/Majkey25/TuneItAll/pull/20) merged at 15:17 CEST
as `1407ee6de726b093812aa4df933e281b6606d3f1`.
Its tree exactly matches verified source
`dbfd8bb1eb43648a99208e901911abdc0a457aa9`.
Tag `v0.3.0-alpha.30` points to the merge.

## Changes and checks

See [screen-on and tempo evidence](../tempo-awake-2026-09-24.md) for the root
causes, frozen comparisons and limitations. Live pitch and song-chord detection
are unchanged. There are no new permissions or dependencies.

- 339 local unit tests passed, with no failures, errors or skips.
- Android Lint, QA APK and instrumentation APK builds passed.
- [PR tests, lint and build](https://github.com/Majkey25/TuneItAll/actions/runs/36003919903): passed.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/36003919692): 21 tests passed, no failures or skips, 18.318 seconds.
- [Merge CI](https://github.com/Majkey25/TuneItAll/actions/runs/36004688900) and
  [Pages deployment](https://github.com/Majkey25/TuneItAll/actions/runs/36004688032): passed.
- The first CI run caught a test clicking an offscreen control after scrolling.
  Both affected test flows now scroll to and assert the control is visible before
  clicking. No application code or test assertion was weakened for that failure.

## Physical device

Huawei YAL-L21, Android 10, isolated QA package at code 33. All 22 selected
tests passed without skips in 40.802 seconds. They cover native file decoding,
tempo timbre changes and noise, screen-on request cleanup, metronome controls,
chord transposition and existing song regressions, including the private fixture.
The subsequent test-only scrolling correction passed on the CI emulator.

- QA APK SHA-256: `918a79a1e715a150c6cfb61bfb463523b53abb749b382ed4ad2604b9d190114e`.
- Huawei-run test APK SHA-256: `4e261f7d51d3e1da012f520b1c87492b1aa1450dbeff7be2d109efb36c737ded`.

The native View screen-on assertion failed before the fix and passed afterward
for Auto, Manual and Chromatic modes, including silence. It also verifies release
when stopped or removed. It is not an elapsed-timeout or Activity lifecycle test.
The private-song assertions do not establish that every chord is correct.

No audio was played through the phone or PC. Focusrite settings and production
app data were untouched. The phone returned to Home and was released at 14:58
CEST. The temporary transfer copy was removed; the private host recording and
QA-cache fixture remain. No private recording was uploaded.

## Signed Play bundle

[Signed build](https://github.com/Majkey25/TuneItAll/actions/runs/36003933006)
passed on the verified source.

- AAB SHA-256: `6529233c01efa70f63d8b24193e78c1efdd750ccf2f306149042c6f9fbc44cfc`.
- Upload signer SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Bundletool 1.18.3 validation and jarsigner verification passed. Existing warnings
remain for the self-signed certificate, missing timestamp, ZIP attributes and
JarInputStream entry ordering. The manifest confirms code 33 / alpha30, API 26+,
target 36, no debuggable flag, Internet, advertising-ID or wake-lock permission.
The 157-entry archive has no duplicate entries, audio recordings, experimental
models or signing keys.

## Delivery

At 15:20 CEST, Google Play publishing overview confirmed `Probíhá kontrola změn`
for Alpha code 33 / alpha30. Quick checks and review remain pending; tester
availability is not yet confirmed. Rollout is 100% of the existing Alpha group.
Testers, countries, pricing and managed publishing were not changed. Previously
submitted store-artwork changes had completed publication before this submission
and were left untouched.

No previously supported devices were lost. The only two Play warnings concern
optional deobfuscation data and native debugging symbols. This build is not
obfuscated and adds no app-authored native code.

Google Play production access is still awaiting approval. Publishing to Alpha
and GitHub does not establish production availability.

## Public GitHub APK

[Alpha30](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.30)
was published at 15:25 CEST by the
[preview workflow](https://github.com/Majkey25/TuneItAll/actions/runs/36004715864).

- APK SHA-256: `a77a51415f0e78abd4eb148f7ac6772491b23629ab84f74483d452a8626fe5ff`.
- Preview signer SHA-256: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

The downloaded APK matches both its companion checksum and GitHub asset digest.
Apksigner verification passed. Packaged metadata confirms code 33 / alpha30
and the unchanged application ID. This debug-signed direct-testing APK is
distinct from the signed, non-debuggable Play bundle.
