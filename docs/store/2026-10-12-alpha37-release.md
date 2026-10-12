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
Legacy repository credentials remain until environment signing is verified.

## Delivery

GitHub CI, merge, signed release artifacts and Google Play submission are pending.
This document records local evidence, not publication.

## Accuracy limits

Song recognition remains experimental. Existing real full-mix chord errors are
not resolved by UI or performance changes. Universal arbitrary-song transcription
and quiet unplugged-electric tuning on every phone have not been established.
Native playback tests do not prove subjective loudspeaker quality or real-guitar accuracy.
