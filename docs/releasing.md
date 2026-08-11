# Release process

## GitHub testing APK

1. Increment `versionCode` and set `versionName` to the matching pre-release
   version, for example `0.1.0-alpha.1`.
2. Update `CHANGELOG.md` and both Fastlane changelog files.
3. Run `./tools/build.ps1 -AllowUnsigned` and connected-device tests.
4. Push a matching `v*-alpha.*`, `v*-beta.*`, or `v*-rc.*` tag. The preview
   workflow publishes a verified debug-signed APK and SHA-256 checksum as a
   GitHub prerelease.

Preview APKs are for direct testing only. They do not use the private Play upload
key and must not be promoted to a stable release. The repository secret
`TUNEITALL_PREVIEW_KEYSTORE_BASE64` keeps preview signatures stable between
GitHub Actions runs.

## Stable Play release

1. Complete `docs/store/release-checklist.md`.
2. Increment `versionCode` and `versionName`; update `CHANGELOG.md` and both
   Fastlane changelog files.
3. Run `./tools/build.ps1 -AllowUnsigned` and all connected-device tests.
4. Configure GitHub Actions secrets:
   `TUNEITALL_KEYSTORE_BASE64`, `TUNEITALL_KEYSTORE_PASSWORD`,
   `TUNEITALL_KEY_ALIAS`, and `TUNEITALL_KEY_PASSWORD`.
5. Create and push an annotated `vX.Y.Z` tag only after review. The release
   workflow builds a signed AAB, verifies it, and creates a draft GitHub release.
6. Verify the AAB checksum and upload certificate, then upload the same AAB to
   Play internal testing. Review the Play pre-launch report before promotion.

Never store the upload keystore or credentials in Git. Google Play App Signing
should hold the app-signing key; retain the upload-key recovery plan offline.
