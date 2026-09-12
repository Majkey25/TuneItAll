Intoniva `v0.3.0-alpha.25` testing build.

- Stop numerical averaging residue from producing sustained notes after song audio becomes silent.
- Sum each short feature window directly in Double precision, with no new input threshold.
- Preserve recorded chord labels and timing across all 48 frozen recording/gain cases.
- Keep the tuner, feedback rejection, UI, microphone input, permissions, and dependencies unchanged.

Checks: 304 local tests, lint, APK/bundle builds, and package/manifest verification.
Huawei passed 13 silent analysis/audio checks, plus an isolated repeat of the
new regression. No playback tests ran while the user was using Focusrite.
The new regressions fail on the preceding implementation, which invents an
eight-second C2 note at confidence 0.82 during digital silence.
See `docs/song-feature-stability-2026-09-12.md` for the cause and evidence.

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
