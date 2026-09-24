Intoniva `v0.3.0-alpha.31` testing build.

- Hide song-tempo analysis by default under **Get tempo from a song**.
- Expand the section to select a file, view progress and apply detected BPM.
- Collapse it without clearing the analysis result. Expansion survives configuration changes.
- Use translated labels and accessible expanded/collapsed state in all five languages.

This is a focused UI update. Audio analysis, metronome playback, permissions
and dependencies are unchanged. The screen-on and tempo improvements from
alpha30 remain included.

Google Play production access was requested on 22 September. Google's approval
is pending; this release does not imply a production rollout.

Half/double-time interpretations can remain ambiguous even with a strong rhythm
match. Song chord recognition remains experimental; this is not a claim of
universally accurate transcription.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Use the attached SHA-256 file to verify the download. The app has no ads,
accounts, analytics, tracking, Internet permission, or advertising ID.

Policies: https://majkey25.github.io/TuneItAll/legal/
Review and remaining publisher-identification checks: `docs/legal-and-accessibility-review-2026-09-08.md`.
