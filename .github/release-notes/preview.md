Intoniva `v0.3.0-alpha.26` testing build.

- Fix a Notes-mode decoding shortcut that could choose the wrong preceding note.
- Search every prior pitch using the unchanged acoustic scores and transition costs.
- Preserve the alpha25 fix for invented notes during silent song passages.
- Leave chord recognition, tuner capture, feedback rejection, UI, permissions, and dependencies unchanged.

The transition regression fails on the preceding build and passes with this fix.
Checks cover 305 local tests and 16 silent Huawei checks, including an imported
WAV melody followed by silence and a 30-minute note-timeline workload.
Exact decoding takes about 2.9 seconds for that workload on the Android 10
test phone. This measures the decoder only, not total song analysis.
See `docs/store/2026-09-12-alpha26-release.md` for evidence and delivery status.
No playback tests ran while the user used Focusrite.

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
