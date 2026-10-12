# Release process

## GitHub testing APK

1. Increment `versionCode` and set `versionName` to the matching pre-release
   version, for example `0.1.0-alpha.1`.
2. Update `CHANGELOG.md` and both Fastlane changelog files.
3. Run `./tools/build.ps1 -AllowUnsigned` and connected-device tests.
4. Push a matching `v*-alpha.*`, `v*-beta.*`, or `v*-rc.*` tag. The preview
   workflow builds without signing credentials. Separate `preview-signing`
   and `release-publication` jobs verify signing and publication of
   the verified debug-signed APK and SHA-256 checksum as a GitHub prerelease.

Preview APKs are for direct testing only. They do not use the private Play upload
key and must not be promoted to a stable release. Store
`TUNEITALL_PREVIEW_KEYSTORE_BASE64` only in the `preview-signing` environment
to keep preview signatures stable between GitHub Actions runs.

Before enabling signed workflows, protect `main` with strict `Test, lint, and build`
checks and restrict `preview-signing`, `play-signing`, and `release-publication`
deployment refs as described in the [security policy](../SECURITY.md).
Restrict `v*` tag creation, updates and deletion to the repository owner/admin.
The owner authorizes releases; no independent reviewer is required. Signing
credentials must not remain at repository or organization scope.

## Stable Play release

1. Complete `docs/store/release-checklist.md`.
2. Increment `versionCode` and `versionName`; update `CHANGELOG.md` and both
   Fastlane changelog files.
3. Run `./tools/build.ps1 -AllowUnsigned` and all connected-device tests.
4. Store these secrets only in the `play-signing` environment:
   `TUNEITALL_KEYSTORE_BASE64`, `TUNEITALL_KEYSTORE_PASSWORD`,
   `TUNEITALL_KEY_ALIAS`, and `TUNEITALL_KEY_PASSWORD`.
5. Create and push an annotated `vX.Y.Z` tag after the quality gate passes. The release
   workflow builds without signing credentials. A separate runner uses trusted
   `main` logic to verify and sign the AAB in `play-signing`.
   `release-publication` verifies provenance before creating the draft GitHub release.
6. Verify the AAB checksum and upload certificate, then upload the same AAB to
   Play internal testing. Review the Play pre-launch report before promotion.

Never store the upload keystore or credentials in Git. Google Play App Signing
should hold the app-signing key; retain the upload-key recovery plan offline.
