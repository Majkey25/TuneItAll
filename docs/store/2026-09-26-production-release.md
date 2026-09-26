# First production submission

On 26 September 2026, Play Console confirmed that Google had granted production
access to Intoniva, package `com.tuneitall.tuner`.

The production release form was completed through the browser UI:

- Release name: `34 - First public release`.
- Existing signed bundle: code `34`, version `0.3.0-alpha.31`.
- Availability: all 177 selectable regions, matching the previous test-track availability.
- Release notes: English and Czech, describing the app's existing features.
- Managed publishing: off, unchanged. Publication follows successful review.

Publishing overview confirmed `3 změny byly odeslány ke kontrole` and
`Probíhá kontrola změn`. The submitted changes are the production release,
176 named countries and regions, and the rest-of-world availability entry.
No unrelated store listing, pricing, tester, data-safety or permission changes
were submitted.

The release uses the existing bundle from the Play library, not the GitHub
debug APK. Its recorded AAB SHA-256 was rechecked locally:
`060275e52b66f1fb175edea323b45e434ee0c887bc264aa1b314921b1303402b`.
See [Alpha31 verification](2026-09-24-alpha31-release.md) for the build, signer,
unit, emulator and physical-device evidence. No new binary was built or tested
for this promotion.

Play reported no blocking validation errors. The two existing warnings concern
optional deobfuscation data and native debugging symbols.

Production review is pending. This record does not establish that the app is
already publicly downloadable from Google Play.
