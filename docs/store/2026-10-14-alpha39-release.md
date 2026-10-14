# Alpha 39 song-analysis release

Version `0.3.0-alpha.39` (42), package `com.tuneitall.tuner`.

## Changes

- Do not backdate a later song melody into an unsupported bass intro.
- Ignore seeks until the song player finishes preparation.
- Keep microphone sensitivity, pitch tracking, confirmation and reference sound unchanged.

## Verification

The clean local release gate passed 399 JVM tests, lint with zero errors and
24 warnings, APK/AAB builds, ten signature checks and 17 release-policy checks.
The added native regressions reproduced both old paths on the pinned alpha 38
QA APK, then passed on alpha 39. The final Huawei Android 10 recheck passed all
24 cases without skips in 14.108 seconds. Prepared seeking, reanalysis, cancellation,
melody timing, ninth chords and distorted power chords were included.

The first 24-case run had one bounded-import setup rejection; an isolated check
and complete repeat passed. The initial install-interrupted run is not counted.
The [reliability report](../reliability-2026-10-13.md) preserves these limitations.
No emulator, microphone recording or desktop/Focusrite playback was used.
The installed QA APK was read back on 14 October at 08:49 UTC; its SHA-256
matched `5a0618095894df112edbad8c52d23992b9bd041ab4d8c80f03a36a97456444fa`.
Only the own QA process was stopped, then all ADB access was released.

## Delivery

- [PR 35](https://github.com/Majkey25/TuneItAll/pull/35) passed the required
  [CI gate](https://github.com/Majkey25/TuneItAll/actions/runs/38536149788) and
  merged through protected main. The reviewed and merged trees are identical.
  Release source: `fe42de84da1a75c49a72f2db7960c693f3c0df92`.
- The immutable [hosted build](https://github.com/Majkey25/TuneItAll/actions/runs/38538003504)
  passed its credential-free build and [unsigned artifact verification](https://github.com/Majkey25/TuneItAll/actions/runs/38538003504/job/115768066402).
  Automatic signing stopped before key access because `GITHUB_TOKEN` could not
  inspect classic main protection. Owner-authenticated policy checks passed for
  signing and publication; existing owner-local signing was used without changing
  protection rules. The tag used the existing repository owner exemption.
- Both signing identities matched their pinned certificates. All 158 AAB and
  161 APK non-signature entries matched the hosted originals. Provenance states
  the owner-local route, not an Actions approval or cryptographic attestation.
- [GitHub alpha 39](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.39)
  was published at 09:00:10 UTC with the preview APK, checksum and provenance.
  All three uploaded asset digests matched the local verified files.
- Google Play accepted production code 42 at approximately 09:03 UTC for a
  100% rollout to the existing targeted countries. The Console showed **Changes
  in review**, with preliminary checks still running. Managed publishing is off;
  this does not establish approval or availability of code 42. Code 41 was active
  when checked earlier that morning. English and Czech release notes were supplied.
- Play reported no blocking release errors and no loss of supported devices.
  Two existing warnings remain: no deobfuscation mapping and no native debug symbols.

Signed Play bundle SHA-256:
`80c63d0f5c86c8ef4ffba9bc221bd22a9e19f1d21cb7f5a0755791a4f0b342d6`.
Preview APK SHA-256:
`5fae6fdfb97311469c4a6ad43740fabbc7b52577086f8611957dcba3874ef713`.

The 100-assignment Gemini audit remains incomplete: after preserving old results
and requeuing changed inputs, 69 accepted assignments apply to the current source
and 31 are pending at the 14 October checkpoint. This release does not claim perfect recognition of arbitrary
songs or address every later UI audit hypothesis.
