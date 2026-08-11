# TuneItAll release checklist

## Product and legal

- [ ] Complete trademark/store-name clearance for `TuneItAll` in target markets.
- [ ] Replace placeholder publisher contact details and host the privacy policy
      at a stable public HTTPS URL.
- [ ] Confirm proprietary copyright owner wording.
- [ ] Regenerate and review third-party licence notices from the release bundle.

## Android and Play

- [ ] Increment `versionCode` and confirm `versionName`.
- [ ] Build with JDK 17 and run unit tests, Lint, debug assembly, and device tests.
- [ ] Verify the merged release manifest has no `INTERNET` permission.
- [ ] Test microphone allow, deny, permanent deny, background, and resume flows.
- [ ] Test clean signals, low extended-range notes, silence/noise, and A4 444.0 Hz.
- [ ] Inspect 6/7/8/9-string, bass, and ukulele headstocks on a real device.
- [ ] Supply the private upload key outside Git and build a signed release AAB.
- [ ] Enrol/configure Play App Signing and retain the upload-key recovery plan.
- [ ] Upload 512×512 icon, 1024×500 feature graphic, and real device screenshots.
- [ ] Complete Content Rating, Target Audience, App Access, Ads, and Data Safety.
- [ ] Confirm the Data Safety answers against the final dependency graph.
- [ ] Run internal testing before production rollout.

## Store copy and evidence

- [ ] Check English and Czech titles, short descriptions, and full descriptions.
- [ ] Ensure screenshots show only behavior present in the submitted build.
- [ ] Do not claim Google Play availability before publication.
- [ ] Archive test reports, final AAB checksum, signing certificate fingerprint,
      mapping file, and Play pre-launch report for the release.
