# Alpha 37 reliability release

Version `0.3.0-alpha.37` (40), package `com.tuneitall.tuner`.

## Changes

- Reduce pitch and song-processing work with reference-equivalent FFT and harmonic projections.
- Preserve tuner capture ownership, stable confirmations and reference-tone state across navigation and rapid setting changes.
- Keep metronome Stop responsive during native startup; reject stale stops and avoid reading the playback head before playback starts.
- Anchor song chord/note labels and adapt diagrams for short screens and enlarged text. Do not show unverified fingerings.
- Use Material as the default layout; add Glass and improve navigation/control contrast across themes.
- Keep trainer controls reachable and retain practice/comparison state through configuration changes.
- Correct slow auto-scroll gestures and prevent accidental long presses.
- Bound selected-song imports, provider cancellation and analysis work. Use and disclose private cache copies for consistent playback and reanalysis.
- Separate unsigned build, unprivileged parsing, signing and publication. Pin artifact identity, immutable source, digests and complete bundle signatures.

## Local verification

12 October 2026:

- 377 JVM tests passed; no failures, errors or skips.
- Debug APK, QA APK, instrumentation APK and unsigned release AAB built.
- Android lint: zero errors, 24 warnings.
- Exact release verifier passed on Windows PowerShell 5 and PowerShell 7. Rejected wrong versions/digests, substituted QA APK, malformed AAB, SDK-23 INTERNET/AD_ID permissions, window-content retrieval, missing Accessibility metadata, a second Accessibility service and an extra capability.
- 10 bundle signature/module checks and 17 release-policy checks passed.
- Huawei YAL-L21, Android 10: 13 music-tool tests, 17 import/security/keep-awake/contrast/large-text tests and 8 appearance tests passed. These preceded the final metronome-only startup fix.
- Final candidate installed separately as `com.tuneitall.tuner.qa`; all 11 native metronome lifecycle/playback tests passed, including every sound at 400 BPM/four subdivisions and rapid setting changes.
- Phone window 08:40:00–08:43:10 UTC was released early, after instrumentation exited. Home sent; only the QA package stopped. Production data, accounts and global settings untouched. No desktop/Focusrite audio.
- Focused read-only Claude Code `claude-opus-5-5`, high-effort reviews identified the startup playback-head lock and additional release isolation/metadata gaps. Fixes were independently checked against code and adversarial fixtures. A separate app review found no blocking regression.

The owner explicitly chose release authorization without a second human reviewer.
Main protection, restricted signing/publication environments and an owner/admin-only
release-tag ruleset were configured. Existing local upload and preview identities
were verified before copying credentials into the corresponding environments.
The five legacy repository signing secrets were removed after the environment
copies were stored and the retained local identities signed this release
successfully. Original local credentials remain available for recovery. Hosted
environment signing has not completed: its policy check stops before key access.

## Delivery

- [PR 29](https://github.com/Majkey25/TuneItAll/pull/29) merged to protected main at `f521cc0c62f094f414c674cb30ca7f91f6e6fa9f` after [quality CI](https://github.com/Majkey25/TuneItAll/actions/runs/38228989156) and [all 68 Android 10 instrumentation tests](https://github.com/Majkey25/TuneItAll/actions/runs/38228989173) passed. Main-push CI also passed.
- [GitHub release v0.3.0-alpha.37](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.37) is published as a prerelease with a downloadable preview APK, SHA-256 checksum and explicit owner-local provenance. The uploaded asset digest matches the verified APK.
- Unsigned build and credential-free native metadata verification passed in [run 38230182022](https://github.com/Majkey25/TuneItAll/actions/runs/38230182022). Both automated release routes then failed before signing because `GITHUB_TOKEN` cannot inspect classic main-branch protection. The gate remains fail-closed; no broader token was created and no protection was weakened.
- The owner-local route checked fresh remote tag/main ancestry, clean source, immutable artifact identity and digests, hosted verifier success, all three release policies and pinned certificates. It signed only checked copies, verified complete bundle signatures and checked content equality for every non-signature ZIP entry in both APK and AAB. Native metadata parsers did not run in the owner credential context. This is explicit owner signing, not an Actions environment approval or cryptographic attestation.
- Signed AAB SHA-256: `5e12d2427eca8f78416fbd77b6376c981649da4544ce209464004c4191c7a734`.
- Published preview APK SHA-256: `224f517fd256db551a729d780f9b4862ada269e005b0b3b0a518f5a37b42fb5e`.
- At 10:02 UTC, Google Play had accepted version 40 for a 100% production rollout with no loss of supported devices. The owner submitted the production update and both store descriptions. The console showed “Changes in review”; quick checks precede Google's full review. Managed publishing is off, so approval will publish the update automatically. Version 39 remained live pending approval; version 40 was not yet public on Play.
- English and Czech store descriptions now disclose the private song-cache copy. GitHub Pages deployed the matching privacy policy from the release source.

Google Play reports two nonblocking warnings: missing deobfuscation mapping and
native debug symbols. No source mapping or native symbols were fabricated.
The automated signing routes still need a repository-scoped policy-read credential
or equivalent platform permission; this owner-local release does not prove those
routes operational.

## Accuracy limits

Song recognition remains experimental. Existing real full-mix chord errors are
not resolved by UI or performance changes. Universal arbitrary-song transcription
and quiet unplugged-electric tuning on every phone have not been established.
Native playback tests do not prove subjective loudspeaker quality or real-guitar accuracy.
