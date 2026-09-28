# Alpha 33 release evidence

Version 36 / 0.3.0-alpha.33. Package remains `com.tuneitall.tuner`.

## Changes

- Native segmented trainer controls and pinned playback/Next buttons.
- Local Learning/Learned note banks, selection limits, and A/B comparison.
- A/B uses one looping AudioTrack with exact reference-tone buffers and 150 ms silence between notes.
- Quiz selection uses random questions. The old chord step visited only 24 of 168 combinations.
- Two-note sets allow repeats, preventing predictable alternation. Larger sets avoid immediate repeats.
- Chord roots and qualities use grids; full selected quality names remain visible below the grid.
- Five languages. No new dependency, permission, telemetry, or network path.

## Verification

- Tested source: `2a9101bef5051ab77a9125c3b34c63cab7d19f02`.
- [PR 23](https://github.com/Majkey25/TuneItAll/pull/23) merged as
  `0ca4bbaa9d949141eaa13d2da430551b83e3c313`. The trees match.
- Local: 345 JVM tests, 60 suites, zero failures/errors/skips; lint and QA/test APK builds passed.
- Huawei YAL-L21, Android 10: 20 trainer, music-tool, tuner-awake and metronome UI tests passed.
- Small-screen checks cover pinned A/B controls, bank editing, bank persistence, restricted quiz choices, hidden answers and single scoring.
- Device AudioTrack remained active across repeated cycles, then stopped/released on Home.
  Media stayed muted. No subjective listening assessment or PC/Focusrite playback is claimed.
- PCM regression verifies both exact generated tones, the silent gaps and zero-valued loop edges.
- [Android CI](https://github.com/Majkey25/TuneItAll/actions/runs/36396761972) passed.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/36396761899) passed all 39 tests without skips.
  Its first run exposed an older timeline test that assumed the entire song form fitted vertically.
  The test now reveals the timeline vertically before asserting automatic horizontal positioning.
- QA APK SHA-256: `9f16082ab91c1ad5f47f618656041c06df05fc27675967b34d1afa777c9d4c7f`.

## Google Play

- [Signed build](https://github.com/Majkey25/TuneItAll/actions/runs/36396758045) passed.
- AAB SHA-256: `b4aad8b3e3dafc2ad47c02c12bed989b31c5168b37086b6e56085b7bdc5f2ce5`.
- Upload certificate SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- Bundletool validation, manifest inspection and signature verification passed.
  The usual self-signed upload-certificate, timestamp and ZIP-entry-order warnings remain.
- Manifest: code 36, alpha.33, minSdk 26, targetSdk 36. No Internet or advertising-ID permission.
- Production release `36 - Focused ear training` submitted on 28 September 2026, about 10:32 CEST.
- 100% rollout to existing targeted countries. No supported devices lost.
- Console confirms review in progress, starting with quick checks. Managed publishing is off.
  Submission is not proof that version 36 is publicly available.
- Only the existing optional deobfuscation/native-symbol warnings appeared during validation.
  The separate DEX optimization recommendation remains outside this trainer update.

## GitHub

- Release tag: `v0.3.0-alpha.33`.
- [Release workflow](https://github.com/Majkey25/TuneItAll/actions/runs/36397566918).
- [Public release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.33)
  published at 10:33:59 CEST with downloadable APK and SHA-256 file.
- Downloaded APK SHA-256: `14d8079041484848e61fbc4ce16613a19d01da45fe8c7e252e084488e34903f7`.
  It matches both the companion checksum and GitHub asset digest.
- APK signature and package/version inspection passed.
- The downloadable GitHub APK uses the existing preview certificate, not the Play app-signing certificate.

Pitch/song recognition algorithms and metronome scheduling are unchanged.
