# Alpha19 release evidence

Version: `0.3.0-alpha.19`, version code `22`.
Package: `com.tuneitall.tuner`. Existing Alpha closed-testing track retained.

## Source and builds

- [PR #7](https://github.com/Majkey25/TuneItAll/pull/7) merged into main.
- Release source/tag: `c0a9a5419639aefcc1f56a23300f228817fccaa5`.
- [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34025955306),
  [main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34026297143),
  [preview release build](https://github.com/Majkey25/TuneItAll/actions/runs/34026332173),
  and [signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34026332273) all passed.
- Local verification: 269 unit tests passed, zero failures/skips, plus lint,
  debug/QA/test APK builds and release-bundle assembly.
- Final Huawei YAL-L21 / Android 10 run: 95/95 instrumentation tests passed
  in 102.892 seconds, including the private problem-song decode and audio/UI checks.
  Temporary song copies were removed and the shared phone was released.

## Verified artifacts

[GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.19)
is public and contains the installable testing APK plus its checksum.

- APK SHA-256: `d57d1f8ed387cd4e941f451e660dd69c82dae2a4e32bba957f2258f6f7aa7238`.
- Signed AAB SHA-256: `bfaf683bfd83d878c385788aada588ff29feb184751a059b5e85e2b2e1e705f2`.
- Upload certificate SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- Jarsigner verified the AAB; the expected self-signed upload certificate matched.
  Bundletool validation and the actual AAB manifest confirmed code 22, API 26+,
  target 36, the existing protected Accessibility service, and no Internet/AD_ID permission.
- The public APK's companion checksum matches GitHub's asset digest.

## Google Play submission

On 6 September 2026, the signed AAB was uploaded, reviewed and submitted for
100% of the existing Alpha track. English and Czech release notes were supplied.
No countries, testers, prices, permissions or release tracks were expanded.

Observed at 12:15 CEST: **Probíhá kontrola změn** for `22 (0.3.0-alpha.19)`.
Automated quick checks were still running. Managed publishing remains off.
Submission is confirmed; Google approval and availability of this update to
testers are not yet confirmed.

The only bundle warnings were missing optional deobfuscation and native debug
symbol files. Play reported no loss of supported devices.

## Accuracy scope

The Amadd9 and rapid C-major / C-major-seventh / C-major regressions pass without
changing their expected results. The final decoder preserves accepted roots,
no-chord spans and baseline confidence, while accumulating quality evidence.
See the [measurement report](../audio-accuracy-2026-09-06.md).

This does not guarantee exact transcription of every song, dense mix, ambiguous
voicing or single-note passage. The application and release notes continue to
describe song chords as estimates. Audio remains processed locally.
