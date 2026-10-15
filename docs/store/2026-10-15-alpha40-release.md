# Alpha 40 settings release

Version `0.3.0-alpha.40` (43), package `com.tuneitall.tuner`.

## Changes

- Add Google Material outlined icons to General, Tuner, Metronome and Appearance settings.
- Wrap category chips on narrow screens instead of requiring horizontal scrolling.
- Verify unselected chip icon contrast with custom-theme colors.
- Preserve setting callbacks, saved preferences, audio detection and playback.

The work is isolated from the older unfinished tuner changes. No project migration,
dependency update, emulator or desktop/Focusrite playback is included.

## Verification

The local APK/AAB build and lint passed: zero lint errors, 24 existing warnings.
JVM results: 395 passed, four optional corpus tests skipped, zero failures.
Ten bundle-signature checks and 17 release-policy checks passed, including
rejection of substituted identity, digest, permissions and accessibility metadata.

The first Huawei Android 10 run passed 69 of 70 native cases. The custom-tuning
return test failed because `tuning_picker` was not displayed; an isolated repeat
failed at the same assertion. The pinned published alpha 39 QA APK also failed
this test, but earlier, because `Tunings` was not displayed. This does not establish
the same root cause. That unrelated workflow remains an explicit validation gap;
the test was not changed or disabled.

The new large-text category regression failed before the icon/layout changes and
passed after them. The version 43 Huawei run passed all 24 targeted native
cases, with no skips, in 51.366 seconds. After bundling the full icon licence,
the same 24 cases passed again in 50.271 seconds. These cover categories at 200%
text size, callbacks, custom-color contrast, appearance persistence, language,
reference-pitch validation, headstock choice, metronome controls and support.
An optional chip-color adjustment was removed because the device test showed
the existing fallback already preserved contrast.

The final installed QA APK was read back at 07:59 UTC and matched the tested build:
`d266c38a4f90bbe2d2d240f4c494fd4f2e20d3750e87250793087ade30eba1dd`.
Only QA preferences were restored and the own QA process stopped. All ADB access
was released at 07:59:52 UTC. No production user data was changed.

## Delivery

- [PR 37](https://github.com/Majkey25/TuneItAll/pull/37) passed its required
  [CI gate](https://github.com/Majkey25/TuneItAll/actions/runs/38702202192) and
  merged through protected main at 08:06:02 UTC. Reviewed and merged trees match.
  Release source: `9b79ec814ec4a2fdf9a97227b30d5beb674a957a`.
  The separate [main CI run](https://github.com/Majkey25/TuneItAll/actions/runs/38703271568)
  also passed.
- The immutable [tagged build](https://github.com/Majkey25/TuneItAll/actions/runs/38703321395)
  and its unsigned identity/digest verification passed. The signing job then
  stopped before key access because its token could not inspect classic main
  protection. Fresh owner-authenticated checks passed for all three release
  environments. Existing owner-local keys were used; no protection or credential
  settings changed. Tag creation used the existing repository-owner exemption.
- Both pinned signing certificates matched. All 181 AAB and 183 APK non-signature
  entries matched the hosted originals. The provenance explicitly records
  owner-local signing and is not a GitHub Actions cryptographic attestation.
- [GitHub alpha 40](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.40)
  was published at 08:16:42 UTC with the preview APK, checksum and provenance.
  All three uploaded asset digests matched the verified local files.
- At approximately 08:20 UTC, Google Play accepted the production code 43
  submission for a 100% rollout in the existing targeted countries. English and
  Czech release notes were supplied. The Console showed **Changes in review**,
  with quick checks still running. Managed publishing is off. This is not
  confirmation of approval or availability; code 42 was active when checked
  at 07:32 UTC.
- Play showed no blocking release errors or loss of supported devices.
  Two existing warnings remain: no deobfuscation mapping and no native debug symbols.
  The reported download increase is 19.1 kB.

Signed Play bundle SHA-256:
`a6afefa3a848f155ea35bc8b0d9b62a499202883b954f2813e3be70410cd9fcc`.
Preview APK SHA-256:
`630e9506753f75796fac6d261a859422834a1693e20bf0d955f3155d167ba266`.
