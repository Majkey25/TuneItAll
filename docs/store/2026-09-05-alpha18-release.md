# Alpha18 release evidence

Version: `0.3.0-alpha.18` / versionCode `21`.
Package: `com.tuneitall.tuner`. Existing Alpha closed-testing track retained.

## Source and artifacts

- App changes: `d2c2302bd3429f1dc7239fb8f56565eb30be66d9`.
- Play build: `5a1fec9cf19a9d24e4745ed47901f558ace537e0`; the only change
  after the app tag fixes verification of self-signed Android upload certificates.
- [GitHub APK release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.18)
  is public, marked prerelease, and includes its SHA-256 checksum.
- [Signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/33960499581)
  passed, as did [Android CI](https://github.com/Majkey25/TuneItAll/actions/runs/33960471748).
- Signed AAB SHA-256:
  `BFD5AC4384BB26D22121E4BE5D95357621932D334BD4A539E6C65DCF6C567B40`.
- Upload certificate SHA-256:
  `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.
- Bundletool validation and the actual AAB manifest confirmed code 21, API 26+
  and target 36, the protected Accessibility service, and no Internet/AD_ID permission.

## Verification

The clean build ran unit tests, Lint, debug/QA assembly, QA instrumentation
assembly, release bundle assembly, and manifest/signature checks.

Huawei YAL-L21 / Android 10 covered all 88 ordinary instrumentation scenarios
across the full run and focused rerun. One long-suite Compose test lost its
test Activity hierarchy; its isolated rerun passed. The private 210.6-second
song regression also passed its coverage and runtime thresholds. Auto-scroll
system binding, Show/Hide/reopen/Close were checked on the device. Temporary
QA packages and Accessibility settings were removed/restored afterward.

An independent final source review found no blocking regression in chord
decoding, lifecycle, microphone retry, custom deletion, overlay cleanup, or
the release signer gate. This does not establish exact transcription of every
commercial mix or replace physical acoustic testing with every instrument.

## Play submission

On 5 September 2026, Play Console accepted all five requested changes:

- Alpha `21 (0.3.0-alpha.18)`, 100% of the existing closed-testing track.
- English full description and eight phone screenshots.
- Czech full description and eight phone screenshots.

Both screenshot sets use the checked-in phone-frame artwork, ordered Tuner,
Chromatic, Tunings, Metronome, Chords, Song Chords, Trainer, Auto-scroll.
Descriptions cover five-string bass and the expanded instrument/song features,
and accurately describe song transcription as an estimate.

Observed state: **Changes in review**, with quick checks still running.
Managed publishing remains off. The update will become available after Google
approves it; submission is not approval or confirmed tester availability.
The only bundle warnings were missing optional deobfuscation/native symbols.
