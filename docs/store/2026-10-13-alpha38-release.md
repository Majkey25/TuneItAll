# Alpha 38 reliability release

Version `0.3.0-alpha.38` (41), package `com.tuneitall.tuner`.

## Changes and verification

The [reliability report](../reliability-2026-10-13.md) records the fixes,
review results, measured accuracy and remaining limits. The final local build
passed 398 JVM tests, lint with zero errors and 24 warnings, unsigned artifact
identity checks, ten bundle-signature checks and 17 release-policy checks.

The connected Huawei YAL-L21, Android 10, ran 191 instrumentation scenarios:
187 passed, no failures and four skips. Three gesture cases could not bind the
Accessibility service under instrumentation. Separate normal-Activity testing
verified scrolling, Stop, restart, long-press avoidance and permission revocation.
The clean QA APK and original QA preferences were restored afterward.

At 13:38–13:40 UTC, the missing exact private dense-song fixture was recovered
and tested on the same phone. The previously skipped case passed in 27.039 seconds
total, asserting at least 85% labelled coverage and analysis within 30 seconds.
This checks coverage and runtime, not correctness of every chord. The temporary
device copies were removed; the original host file was preserved. QA was stopped,
Home sent and all ADB released early at 13:40 UTC. No emulator, desktop playback,
production data, account or global-service changes were used.

## Delivery

- [PR 33](https://github.com/Majkey25/TuneItAll/pull/33) merged normally into protected main at `dab39257df0f63e4c0d68cca8aa99392e61b6be1`. [Final PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/38404847614) and [merged-source CI](https://github.com/Majkey25/TuneItAll/actions/runs/38406885520) passed. The release tag names that exact merged source.
- [GitHub v0.3.0-alpha.38](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.38) was published at 13:57:12 UTC with a downloadable preview APK, checksum and explicit owner-local provenance. The uploaded asset hashes match the verified local files.
- The [hosted build and unsigned verifier](https://github.com/Majkey25/TuneItAll/actions/runs/38407784758/job/115333613255) passed on artifact `11819441916`, from the release source. Both automatic signing routes stopped before key access because `GITHUB_TOKEN` cannot inspect classic main protection. No policy was weakened and no broader token was created.
- The owner-local route rechecked remote source/tag ancestry, clean source, all three release policies and hosted digests. Existing keys signed checked copies only. JDK verification checked every AAB payload entry against the pinned upload certificate; APK verification confirmed one pinned preview signer. Comparisons verified 158 AAB and 161 APK non-signature entries unchanged. Metadata assurance is inherited from the identical hosted, credential-free verified payloads; native metadata parsers did not run in the owner credential context. This is not an Actions approval or cryptographic attestation.
- Signed Play AAB SHA-256: `13530ea1e15ccbb08c4aaebef5091be374feb9dbacf4cd2a2ec70c5ff1ac4fec`.
- Published preview APK SHA-256: `bc257139d9ecbe1c9e817a5babeee0a0f91dd2fa3fb214fd4d55d06bca83ea04`.
- At 14:11 UTC, Google Play accepted version 41 for a 100% production rollout in all targeted countries, with no loss of supported devices. English and Czech release notes were saved. The owner submitted the update and confirmed the review restart replacing the previously pending version 40; the existing store-description changes stayed included. The publishing overview showed version 41 under “Changes in review”, with quick checks pending before full review. Managed publishing remains off, so approval will publish automatically. Version 41 is not yet confirmed live.

The optional emulator-only decoder workflow remains temporarily disabled until
the delivery documentation PR closes. Required CI and release protections remain
intact. No emulator is used for this release.

Play reported two nonblocking warnings: no deobfuscation mapping and no native
debug symbols. Neither file was fabricated. The upload browser briefly had a
zero-size hidden viewport; a temporary viewport restored the normal file-chooser
flow. It was reset after the proof screenshot. All browser control was released
early at 14:12 UTC, with no outstanding upload or UI action.

## Limits

Song recognition remains experimental. The performed GuitarSet benchmark measured
64.5% root agreement and 57.1% supported-quality agreement; this release does not
claim arbitrary-song transcription is solved. Quiet unplugged-electric tuning
on every phone and subjective speaker quality were not established. The requested
100-review Gemini queue was resumed at 14:12 UTC after authenticated model and
quota discovery. Source hashes matched the existing snapshot; 97 reviews were
still pending when the six-worker controller resumed. This record does not claim
that all 100 reviews completed.
Release code shrinking requires separate physical acceptance before Play's
February 2027 optimisation deadline; it was not enabled after final testing.
