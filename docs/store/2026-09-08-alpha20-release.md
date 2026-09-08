# Alpha20 release evidence

Version: `0.3.0-alpha.20`, version code `23`.
Package: `com.tuneitall.tuner`. Existing Alpha closed-testing track retained.

## Source and checks

- [PR #9](https://github.com/Majkey25/TuneItAll/pull/9) merged into main.
- Release source and tag: `339441415c92a232cd59a59a37b74c1671fa8f7b`.
- [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34199184146),
  [main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34199972757),
  [Pages deployment](https://github.com/Majkey25/TuneItAll/actions/runs/34199971732),
  [preview build](https://github.com/Majkey25/TuneItAll/actions/runs/34200014924),
  and [signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34200014862) passed.
- Local unit suite: 268 passed, four optional external-corpus checks skipped.
  Android Lint, debug and QA APKs, test APK, release bundle, and release
  package/permission verification passed.
- Huawei YAL-L21 / Android 10: 97/97 instrumentation tests passed in 103.357
  seconds, including the private-song fixture. No production app data or
  system viewport changed. The shared phone was released after the run.
- The final device run preceded a text-only private-support retention
  clarification and the version bump. It does not prove acoustic performance
  for every instrument or recording.

## Verified artifacts

The [GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.20)
is public and includes the testing APK and its checksum.

- APK SHA-256: `676ac28aebc7828bdb14ba39107c2390221742f5e1db4d55d6d5a85959fedf62`.
- Signed AAB SHA-256: `acfd1e4343e44220744452be4d25274da8c958198e80d99ad42a6661f2589c1e`.
- Upload certificate SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- Jarsigner verified the CI AAB. Bundletool validation and the actual bundle
  manifest confirmed code 23, API 26+, target 36, protected gesture-only
  Accessibility, and no Internet or advertising-ID permission.
- The downloaded APK checksum matches its companion file and GitHub asset
  digest. Its debug certificate was verified. It cannot replace a Play-signed
  installation with a different signing key.

## Google Play submission

On 8 September 2026, the signed CI AAB and updated English and Czech full
descriptions were submitted together. Release notes are present in both
languages. At 10:09 CEST, Publishing overview displayed **Probíhá kontrola změn**
for `23 (0.3.0-alpha.20)` and both descriptions. Automated quick checks were
still running. Submission is confirmed; Google approval and tester availability
are not yet confirmed.

The release uses 100% of the existing Alpha track. Testers, countries, prices,
permissions, artwork, and track type were not expanded. Managed publishing is
off. Play reported no loss of supported devices. Its two warnings concerned
optional deobfuscation and native debug-symbol files.

## Published policy and accessibility changes

The [policy hub](https://majkey25.github.io/TuneItAll/legal/) links to separate
English/Czech privacy, terms, refund, and cookie pages. App details and README
link to the same hub. Public HTTPS pages and language sections were checked
after deployment. Site source and browser checks found no forms, scripts,
third-party embeds, or optional cookies.

Light-theme foreground contrast improved from 1.825:1 to 6.772:1 while selected
controls retain bright green backgrounds and dark labels. Website keyboard
navigation, skip links, image alternatives, and responsive layouts were checked.
See the [review record](../legal-and-accessibility-review-2026-09-08.md).

Publisher legal identity and the actual private-support deletion procedure
still require owner confirmation. This release is not a legal or accessibility
certification. Experimental DSP and neural chord-recognition work is excluded;
the audio engine remains the alpha19 implementation.
