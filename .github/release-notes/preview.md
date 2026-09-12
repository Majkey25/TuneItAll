Intoniva `v0.3.0-alpha.23` testing build.

- Recover the current quiet note after short fundamental-candidate gaps without returning retained pitches as fresh measurements.
- Bound weak continuation to seven missed readings, including ambiguous nonempty frames, and clear it when unvoiced evidence wins.
- Keep microphone input, detector thresholds, display smoothing, confirmation timing, and song analysis unchanged.

The hardest low-SNR decay test is still below target. Song recognition can still
misidentify dense or ambiguous music. Experimental neural models and rejected
adaptive/spectral fallbacks are not included. Retune and stale-confirmation
regressions run the actual detector, tracker, and tuner engine on deterministic
PCM. They are not recordings of an unplugged guitar through a phone microphone.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
