Intoniva `v0.3.0-alpha.28` testing build.

- Remove reproduced phantom chords around single-note passages.
- Retain clear added ninths and avoid virtual bass inversion labels.
- Correct note-change timing and melody suppression by bass accompaniment.
- Keep live tuning, settings, permissions, and offline operation unchanged.

Checks pass 323 local tests, 10 Android 10 emulator tests, and 11 physical Huawei
song-decoder tests. The Huawei processed the private 210535 ms problem recording
in 16638 ms, within the unchanged runtime limit. Coverage is not chord accuracy.

The 48 frozen recording/gain cases show no root or supported-quality regression.
See `docs/song-evidence-2026-09-19.md` and `docs/store/2026-09-19-alpha28-release.md`
for evidence and delivery status. All current song checks were silent.

Recognition work remains open. Dense mixes, ambiguous voicings, and some
single-note harmonic stacks still produce errors. Experimental neural models
are not included. This build does not claim universally accurate transcription.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
