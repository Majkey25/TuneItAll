Intoniva `v0.3.0-alpha.19` testing build.

Highlights:

- More precise quiet-note tuning, with a low-bass settling safeguard.
- Fixed plucked Amadd9 and rapid C–Cmaj7–C regression cases. Offline quality
  decoding preserves the existing root boundaries and does not invent higher confidence.
- Consistent song timing across sample rates, final-window analysis, safer stereo
  downmixing and cancellation throughout processing.
- Neutral out-of-tune indicator and readable large-font ruler labels.

- More accurate offline song transcription: common chord qualities,
  inversions, Classic/Power/Notes modes, instrument ranges, transposition, and
  playable acoustic arrangements.
- Exact linear chord-sequence decoding and compact harmonic features improve
  long-song speed and memory use without changing the decoded path.
- 49 built-in tunings across guitar, bass, ukulele, violin, viola, cello,
  mandolin, and banjo. Custom tunings can now be removed.
- Navigation and custom-tuning drafts survive activity recreation. Temporary
  microphone denial and audio failures now have an explicit retry action.
- Auto-scroll is system-bindable, scrolls on compact/large-font screens, has a
  notification Exit action, and safely stops if overlay permission changes.
- Current chord, trainer feedback, tempo results, and blocking errors expose
  screen-reader announcements.
- Release verification checks the release manifest and expected upload signer;
  CI also compiles the QA instrumentation APK.

The APK is debug-signed for direct testing and is not the Google Play bundle.
Android may ask for permission to install from your browser or file manager.
Intoniva has no ads, accounts, analytics, tracking, Internet permission, or
advertising ID. Audio and selected songs are analyzed locally on the device.

Use the attached `.sha256` file to verify the download.
