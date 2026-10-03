# Alpha 35 release record

Version: `0.3.0-alpha.35` (38). Package: `com.tuneitall.tuner`.

## Source and checks

- [PR #25](https://github.com/Majkey25/TuneItAll/pull/25) merged at `c0e6b5fd9b138ace3ab6d16f89ea987732535ee9`.
- [Android CI](https://github.com/Majkey25/TuneItAll/actions/runs/37120106302) passed test, lint, and build gates.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/37120106262) passed 46 tests with no failures or skips.
- Local JVM suite: 345 tests, zero failures, errors, or skips.
- Huawei launcher checks and store captures: [branding record](2026-10-03-branding.md).
- `8e9062c` versions website artwork URLs after an existing browser tab retained the old cached icon.

## Signed Play bundle

[Bundle build](https://github.com/Majkey25/TuneItAll/actions/runs/37120106087) passed.
Bundletool validated the downloaded AAB. Its manifest reports version 38,
`0.3.0-alpha.35`, minimum SDK 26, target SDK 36, and the unchanged package ID.
The manifest adds no Internet or advertising ID permission.

SHA-256: `5d268ded45310c8339e2e1934df95df334d50a1b7176ea806a48fd83944627f4`.

Upload certificate SHA-256:
`DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

`jarsigner` reports `jar verified`. It also reports the existing self-signed
certificate, missing timestamp, and archive-order warnings. Bundletool accepts
the bundle. No signing key was downloaded or added to the repository.

## GitHub APK

[Release v0.3.0-alpha.35](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.35)
was published on 3 October 2026 at 11:49 UTC. The downloaded APK reports
version 38 and the expected package ID. `apksigner verify` passes with the
existing preview certificate:
`768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

APK SHA-256: `079a6c8399547b6eaa2e64a2e8842e8296916dd436399de4e2d38820588bfb37`.
The downloaded checksum file and GitHub asset digest match this value.
This remains a debug-signed GitHub testing APK, not a Google Play installation.

## Website

GitHub Pages serves the new artwork. The deployed icon matches the local file:
SHA-256 `351bf0b8ce9b91535caeb8e762cf82525a18e4a315d40e4d61d708f0f06df304`.
Desktop (1280px) and mobile (390px) browser checks show aligned logos and no
console errors. The live GitHub README banner also shows the new fork.

## Google Play status

Not uploaded or submitted in this session. Both connected-browser runtimes fail
before navigation with `failed to write kernel assets: The system cannot find
the path specified. (os error 3)`, including after a reset. A separate Playwright
browser reaches Google sign-in, not the authenticated Play Console.

The signed AAB and English/Czech listing assets are ready. Remaining work:
restore authenticated browser access, upload the bundle and both locales'
artwork, submit the production change, and verify the resulting Play status.
Build success is not evidence of Play publication.
