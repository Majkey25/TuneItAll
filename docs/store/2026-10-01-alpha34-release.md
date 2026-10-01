# Alpha 34 release evidence

Version 37 / 0.3.0-alpha.34. Package remains `com.tuneitall.tuner`.

## Cause and correction

Alpha33 randomized Quiz but still opened in Learn, which began at C and advanced in catalogue order.
Next required a guess and did not start the next sound.
Answering a note question expanded the footer and reduced the visible question area.

The trainer now opens in Quiz. Learn starts and advances randomly.
Next skips without scoring and plays the next item.
Show answer is unscored. Note comparison opens explicitly, keeping quiz controls stationary.
Compact tabs, a note-bank menu, and correct-answer labels replace the previous stacked controls.

## Verification

- Two new regression tests failed on installed alpha33 for the expected default-mode and disabled-Next defects.
- Corrected Huawei YAL-L21 / Android 10: 25 tests passed.
  Coverage includes the whole screen with larger text, random Learn progression, playback callbacks,
  skip/reveal scoring, comparison, persistent banks, tuner-awake and metronome behavior.
- Manual screenshots inspected for chord quiz, note reveal and comparison.
- Next started a native AudioTrack. Media remained muted; no subjective listening or Focusrite playback is claimed.
- 345 JVM tests across 60 suites passed without failures/errors/skips. Lint and QA/test builds passed.
- [Android CI](https://github.com/Majkey25/TuneItAll/actions/runs/36840813400) passed.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/36840813437) passed all 44 tests without skips.
- Tested source: `7a933be80dcf530281402783eae69cfb7bc998c7`.
- [PR 24](https://github.com/Majkey25/TuneItAll/pull/24) merged as
  `be177a8842d3700244d07b5679c71aafeda61277`; the trees match.
- QA APK SHA-256: `dec9d3bca94309eee0f0f9b6822bbe9fd19a4d19e08eefbce4163c75c56d98ef`.

## Delivery

- [Signed bundle build](https://github.com/Majkey25/TuneItAll/actions/runs/36840812606) passed.
- AAB SHA-256: `bebd49e9dd8ae354cd85735f5f1c42ffad2d8d529fbfcdb2dcc7e47f7c1515f3`.
- Upload certificate SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- Bundletool, signature and manifest checks passed. Code 37, alpha.34, minSdk 26, targetSdk 36.
  The established self-signed-certificate, timestamp and ZIP-order warnings remain.
- Production release `37 - Trainer flow fixes` submitted on 1 October 2026, about 11:21 CEST.
  Console confirms review in progress, beginning with quick checks. Managed publishing is off.
  Submission does not mean version 37 is publicly available.
- Rollout: 100% of existing targeted countries, no supported devices lost.
  The existing optional deobfuscation/native-symbol warnings remain.
- GitHub tag: `v0.3.0-alpha.34`; [release workflow](https://github.com/Majkey25/TuneItAll/actions/runs/36841640469).
- [GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.34)
  published at 11:22:04 CEST with an APK and checksum.
- Downloaded APK SHA-256: `5c282e5e3b522f34c62f46f0c64a18a8ddc9d76cf501dee9c737c95b998b253f`.
  Matches the companion checksum and GitHub asset digest. Signature and code 37 metadata checks passed.
  This is the existing debug-signed preview, not the Google Play app-signing certificate.

No new permission, dependency, or pitch/song-recognition change.
