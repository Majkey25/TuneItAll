# Alpha21 release evidence

Version `0.3.0-alpha.21`, version code `24`, package `com.tuneitall.tuner`.
The existing Alpha closed-testing track, testers, countries, and permissions
are retained. No new analytics, network dependency, or audio upload was added.

## Source and verification

- [PR #10](https://github.com/Majkey25/TuneItAll/pull/10) merged into main.
- Source/tag: `f7c0a8dfbc55dda77bd83bfaea72ca95ad6c0deb`.
- [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34226654172),
  [main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34227347837),
  [Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34227346872),
  [preview build](https://github.com/Majkey25/TuneItAll/actions/runs/34227412209),
  and [signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34227411902) passed.
- Local combined suite: 287/287 tests passed with the existing hash-verified
  corpus fixtures, zero skips. Lint and all APK/AAB build checks passed.
- Huawei YAL-L21 / Android 10: final 101/101 tests passed in 105.774 seconds,
  plus nine repeated audio checks. Native viewport and production app data
  were unchanged; only the isolated QA package was installed.
- The first stereo implementation passed correctness but exceeded the
  30-second private-song runtime limit. Paired-real FFT processing fixed that
  regression without relaxing the limit. Repeat analysis took 8.922 seconds.
- Ambiguous weak-D3 refinement P95: 2.45/2.73 ms; default guitar AUTO P95:
  2.53/2.50 ms, within the 42.7 ms hop budget in these controls.

The [quiet-note report](../quiet-current-evidence-2026-09-08.md) records the
104/144 to 122/144 near-noise improvement and the still-unmet hardest decay
target. The [stereo report](../stereo-song-analysis-2026-09-08.md) distinguishes
signal preservation, label coverage, and actual chord accuracy.

## Verified downloadable artifacts

The [GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.21)
is public with its APK and checksum. The downloaded bytes match both the
companion checksum and GitHub's asset digest.

- APK SHA-256: `c0eef194a72d3bd71b97c78e0d9057afac5f720ade831060355751231b90b5e1`.
- Signed AAB SHA-256: `85c2e6e824a31e74ff1d01e61a25eba15430c5ed5c3f9bed60ec9b0cc6a25938`.
- GitHub APK certificate: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Play upload certificate: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Apksigner verified the APK. Jarsigner verified the AAB, with the expected
self-signed-certificate warning and archive-order warnings from JarInputStream.
The signer was pinned separately. Bundletool validation and the actual bundle
manifest confirmed code 24, API 26+, target 36, protected gesture-only
Accessibility, and no Internet/advertising-ID permission. The test-only D3
resource is absent from the production APK.

The public APK is debug-signed for direct testing. It cannot replace a
Play-signed installation that uses a different certificate. The locally built
unsigned bundle was not used for Play.

## Google Play

The signed CI bundle was uploaded and submitted on 8 September 2026 as
`24 (0.3.0-alpha.21)`. At the 14:59 CEST verification, Publishing overview
displayed **Probíhá kontrola změn** for exactly one change: the Alpha release.
Quick checks were still running. Submission is confirmed; approval and tester
availability are not yet confirmed.

English and Czech release notes match the committed metadata. The rollout
uses 100% of the existing Alpha track, with managed publishing off. Testers,
177 countries/regions, prices, store artwork, and track type were not changed.
Play showed zero loss of supported devices. Its two warnings concern optional
deobfuscation files and native debug symbols, not a release-blocking error.

The previous alpha20 release was confirmed available to selected testers
before preparing this update.

## Remaining limits

This is a measured improvement, not a claim of perfect recognition for every
song or unplugged guitar. No real unplugged-guitar recording was available for
acoustic acceptance. Rejected adaptive/spectral/neural experiments and the
procedural model fine-tune are not included. Publisher legal identity remains
an owner-confirmation item in the earlier legal review; this release is not a
legal certification.
