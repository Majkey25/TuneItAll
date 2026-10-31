# Intoniva 0.3.0-alpha.45 (48)

## Changes

* Enable R8 code optimization, obfuscation and resource shrinking in Play builds.
* Keep reference-pitch, custom-tuning and metronome controls above the keyboard.
* Make the complete tuning chooser scrollable, including landscape search results.
* Keep the custom color dialog's Apply button visible and touchable above the keyboard.
* Reject release bundles without optimization metadata or below the 25% thresholds.

## Verification

* Debug and release JVM suites each passed 397 tests, with four optional corpus evaluations skipped. Both lint variants reported no errors and 21 existing warnings.
* Eight physical Huawei YAL-L21 Android 10 regressions passed in 13.456 seconds. They cover five keyboard cases, favorites, tuning selection and keep-awake behavior. No emulator, desktop audio or production-data changes.
* An isolated optimized build exercised the tuner, chords, trainer, metronome and settings. The corrected color dialog accepted a real touch above the visible keyboard.
* R8 metadata reports 1.83% not obfuscated, 2.94% not optimized and 1.95% not shrunk. DEX data fell from 23,791,052 to 2,768,488 bytes. These are compiler measurements, not a new Play Console score.
* [PR50](https://github.com/Majkey25/TuneItAll/pull/50) and [main CI](https://github.com/Majkey25/TuneItAll/actions/runs/41191407614) passed. Release source is `fea0bc1681789769d2165b6b62c694b0e007b6ba`.

## Delivery

The hosted build and unsigned verification passed in [run41191485304](https://github.com/Majkey25/TuneItAll/actions/runs/41191485304). Automated signing stopped before key access because its token cannot inspect main-branch protection. The existing owner-local recovery route checked the same three release policies with existing owner authentication and signed the exact hosted files with unchanged identities. No protections were weakened or new credentials stored in Actions.

The downloaded CI archive matched GitHub digest `b0b02f346d3b383a85c3db19d66cd7eb5ee8a363a2ce4d3be72e030398deafcc`. Both certificate pins matched. Every non-signature payload entry remained identical: 133 in the AAB and 176 in the APK. Provenance explicitly records owner-local signing, not an Actions approval or cryptographic attestation.

[GitHub alpha45](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.45) was published on 31 October 2026 at 10:52:43 UTC. The server-computed digests of all three public assets match the checked local files. Optional support links remain in the GitHub APK and are absent from the Play DEX.

* Signed Play AAB SHA256: `09e49baddbb3886c08178054eb867ea8dc19e98de1e960520689a1bc1058db97`.
* Published preview APK SHA256: `50c917080bb7b030b27b3d1a288ee190e022b82fa9a93d23caad80309016620e`.

The signed Play48 bundle is prepared but has not been uploaded, submitted, approved or made live. The browser tool still returns `Local browser use is disabled in Work settings` despite coordinated retries and no conflicting browser lease. Donations enforcement was resolved in the last available Console check on 30 October; this does not prove the new DEX warning has disappeared.

This release does not change audio-detection algorithms or establish new live-guitar accuracy. Play memory metrics had insufficient data. Point-in-time PSS samples are not the new 28-day anonymous-RSS-plus-swap metric. No universal policy-compliance claim is made.
