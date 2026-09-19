Intoniva `v0.3.0-alpha.27` testing build.

- Refine quiet frequencies from the current PCM with a bounded harmonic model.
- Retain weak fundamental candidates previously hidden by deeper multi-period troughs.
- Correct candidate ranking that could latch onto the octave below a quiet first strike.
- Preserve microphone capture, settings, feedback rejection, UI and offline operation.

The frozen hard-decay comparison improves from 743/846 to 837/846 correct fresh
windows within 10 cents. The separate 48-case quiet-start comparison improves
from 1088/1368 to 1368/1368. These are generated noisy signals, not recordings
of an unplugged guitar through a phone. Real-recording nominal-note results
remain unchanged, including an unresolved octave error.

Checks cover quiet acquisition, decay, stopped tones, new strings, noise, bass,
upper partials and confirmation echoes. See `docs/quiet-harmonic-fit-2026-09-12.md`
and `docs/store/2026-09-19-alpha27-release.md` for evidence and delivery status.
No playback tests ran while the user used Focusrite.

Song chord recognition is unchanged. Rejected chord experiments and neural
models are not included; arbitrary-song transcription is not guaranteed.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
