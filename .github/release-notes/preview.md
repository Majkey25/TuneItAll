Intoniva `v0.3.0-alpha.29` testing build.

- Detect low extended-range guitar and bass notes in imported songs.
- Retain A0 at 44.1 kHz and with common reference-pitch offsets.
- Reject reproduced false notes and chords caused by out-of-range PCM16 residue.
- Preserve quiet melody detection and the existing recorded-song predictions.
- Keep live tuning, settings, permissions, and offline operation unchanged.

The 48 frozen recording/gain cases retain exactly the same 1,665 predictions.
There are also 216 low-note controls, 240 negative controls and 288 quiet-melody
comparisons. See `docs/song-low-notes-2026-09-22.md` for scope and evidence.
All current song checks were silent. Coverage is not chord accuracy.

Google Play production access was requested on 22 September. Google's approval
is pending; this release does not imply a production rollout.

Recognition work remains open. Dense mixes, ambiguous voicings, and some
single-note harmonic stacks still produce errors. Experimental neural models
are not included. This build does not claim universally accurate transcription.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
