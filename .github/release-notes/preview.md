Intoniva `v0.3.0-alpha.22` testing build.

- Fix a quiet-retune error that could latch onto the octave below after a brief candidate dropout.
- Keep continuation-only evidence tied to the last emitted pitch, without blocking genuine note changes.
- Keep microphone input, detector thresholds, display smoothing, confirmation timing, and song analysis unchanged.

The hardest low-SNR decay test is still below target. Song recognition can still
misidentify dense or ambiguous music. Experimental neural models and rejected
adaptive/spectral fallbacks are not included. The new retune regression runs
the actual detector and tracker on deterministic quiet PCM; it is not a
recording of an unplugged guitar through a phone microphone.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
