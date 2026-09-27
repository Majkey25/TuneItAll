# Settings support update

Version `0.3.0-alpha.32`, code `35`, package `com.tuneitall.tuner`.
[PR #22](https://github.com/Majkey25/TuneItAll/pull/22).
Verified source: `2b17b2e4f34baf296178c51bef13ffc0e454c2ff`.
Merged as `2e69b60ec98a9911ffde1a1f4e745563dabd3a0e` at 21:27 CEST.
The merge tree matches the verified source and is tagged `v0.3.0-alpha.32`.

General settings now has a Support me section below About Intoniva. Both screens
reuse one Buy Me a Coffee button and the existing external-browser callback.
The style matches the inspected ScanIt and Selia Weather implementations:
yellow `#FFDD00`, dark `#111111`, a 56 dp minimum target and a 24 dp coffee icon.
The heading and description are translated into all five app languages.
Support is optional and unlocks no features. No new permissions, dependencies,
payment SDK or audio changes were introduced.

## Verification

- 339 local unit tests passed with no failures, errors or skips.
- Android Lint and both QA APK builds passed.
- Huawei YAL-L21, Android 10: eight silent tests passed in 10.752 seconds.
- The new settings-support assertion failed before implementation, then passed
  for light/dark settings and the existing About entry point.
- About accessibility, screen-on cleanup and metronome UI regressions passed.
- Manual UI inspection confirmed the new section. Its button opened
  `buymeacoffee.com/majkey` in Chrome and displayed the Majkey support profile.
  No payment was initiated.
- The phone returned to Home at 21:19 CEST. No active peer project was using it.
  Production app data and Focusrite settings were untouched.

Initial phone runs were blocked by the non-secure lock screen. After normal
keyguard dismissal, the regression failed for the expected missing section.
No screen-lock or timeout setting was changed.

QA APK SHA-256: `402e301a31a1d13d258e4a94312b4a8f72815f343629c819d17ba017a008fdbb`.
Test APK SHA-256: `a8dfd027fb404d3c06e8bfeac31184572cbe8d45bd0cc6730a535f00b5025570`.

## CI and artifacts

- [PR quality gate](https://github.com/Majkey25/TuneItAll/actions/runs/36344013093): passed.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/36344013109): 24 tests passed, no failures or skips, 27.499 seconds.
- [Signed bundle](https://github.com/Majkey25/TuneItAll/actions/runs/36344012836): passed.
- [Merge CI](https://github.com/Majkey25/TuneItAll/actions/runs/36344444308) and [Pages](https://github.com/Majkey25/TuneItAll/actions/runs/36344444040): passed.
- AAB SHA-256: `3740cbb8110b159de763ac021e28de2017af48d5c423e5b97b8ce44a9546973f`.
- Upload signer SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Bundletool validation and jarsigner verification passed. Existing warnings for
the self-signed certificate, absent timestamp, ZIP attributes and JarInputStream
ordering remain. The manifest confirms code 35, API 26+, target 36, unchanged
permissions and no debuggable flag.

[GitHub Alpha32](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.32)
was published at 21:33 CEST by the [preview workflow](https://github.com/Majkey25/TuneItAll/actions/runs/36344474139).
The downloaded APK matches its checksum file and GitHub digest. Apksigner
verification and packaged version checks passed.

- APK SHA-256: `7a700dd33eececbd4752da69c53b358da4eea5f7a587ea7782cb8cd76818af91`.
- Preview signer SHA-256: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

## Google Play

The public store listing was verified with an Install action and the first
production release notes. The new code 35 update, named `35 - Support in Settings`,
was submitted to Production on 27 September. Publishing overview confirmed
`1 změna byla odeslána ke kontrole` and `Probíhá kontrola změn`.

Rollout is 100% of the existing targeted regions. Countries, pricing and tester
access were not changed. No previously supported devices were lost. The two
validation warnings concern optional deobfuscation data and native debug symbols.
Managed publishing remains off, so publication follows successful review.
Code 35 is not yet confirmed available on Play; the public listing is a separate fact.

The production dashboard also reported a DEX-obfuscation optimization warning
with a February 2027 deadline. This non-blocking, pre-existing build setting was
not changed as part of the support UI release.
