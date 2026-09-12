Intoniva `v0.3.0-alpha.24` testing build.

- Prevent the confirmation chime from becoming a false guitar note in low-range tuning.
- Keep low-range capture running, with double-precision rejection in both pitch-analysis passes only when chime energy is present.
- Preserve feedback protection across mode, tuning, and settings changes.
- Keep a bounded 400 ms input gate for wider instrument and chromatic ranges.
- Keep microphone input, display smoothing, confirmation dwell, and song analysis unchanged.

Checks: 302 local tests; a 104-test Huawei integration run followed by eight
focused audio checks on the final build. The six-string echo mixture keeps
all seven fresh readings per string within three cents. Full DSP P95 is
3.47 ms against a 42.7 ms capture hop. See `docs/confirmation-feedback-2026-09-12.md`
for the test scope and limitations.

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
