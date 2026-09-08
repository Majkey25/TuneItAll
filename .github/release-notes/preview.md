Intoniva `v0.3.0-alpha.21` testing build.

- Retain weak decaying note candidates without raising new-note acquisition probabilities.
- Prevent stale pitch states from being returned as fresh measurements.
- Preserve stereo guitar notes and beats that were lost when channels cancelled each other.
- Use one FFT per channel pair and reuse the analysis window to reduce processing work.

The hardest low-SNR decay test is still below target. Song recognition can still
misidentify dense or ambiguous music. Experimental neural models and rejected
adaptive/spectral fallbacks are not included. Detailed evidence:
`docs/quiet-current-evidence-2026-09-08.md` and
`docs/stereo-song-analysis-2026-09-08.md`.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
