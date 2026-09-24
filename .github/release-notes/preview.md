Intoniva `v0.3.0-alpha.30` testing build.

- Keep the screen on while the tuner listens, including pauses between notes. Normal screen timeout returns when tuning stops or you leave the tuner.
- Detect tempo from changes across frequency bands, with filtering that prevents steady and fading tones from masquerading as beats.
- Refine BPM between analysis frames instead of rounding to coarse lag steps.
- Show the score as rhythm match, not a probability that the BPM is correct.
- Use clearer song-analysis actions and descriptions in all five app languages.

The change uses native Android screen-on behavior without a wake-lock permission,
global display changes or a new dependency. Pitch and chord recognition are
unchanged. Tempo checks include clear rhythms, timbre changes, noise, fading
tones, syncopation, drift and half-time ambiguity. All checks were silent.

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
