# Alpha26 release evidence

Version `0.3.0-alpha.26`, code `29`, package `com.tuneitall.tuner`.
[GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.26)
is public. Google Play accepted the Alpha submission at 18:48 CEST on
12 September. Review remains pending; alpha25 is the last confirmed
tester-available Play release.

## Cause and change

The Notes decoder previously considered only the current state, no-note,
the globally strongest prior state, and pitches one/two/twelve semitones away.
Transition cost also depends on distance. A weaker prior state outside that
shortlist can therefore win after its smaller transition penalty is included.

With normalized prior evidence at MIDI 61, 63, and 100 followed by MIDI 60,
the old decoder returns `[61, 60]`. The existing scoring model prefers
`[63, 60]`. This reproduces on both JVM and installed alpha25 on Huawei.

The fix searches all previous states, at most 89. Staying wins ties as before.
Acoustic features, emission thresholds, frame timestamps, gap merging, and transition
penalties are unchanged. The only production logic change is in
`PredominantNoteAnalyzer.kt`. SongAudioDecoder invokes it only for Notes mode;
ChordViewModel runs analysis off the UI thread and does not persist old results.

## Verification

- 305 JVM tests passed, zero failures or skips. This includes 24 transposed
  ascending/descending transition counterexamples and the existing melody,
  harmonic-fundamental, noise, range, cancellation, and recorded-corpus checks.
- Sixteen selected native tests passed in 31.917 seconds on Huawei YAL-L21 /
  Android 10. The new transition test fails on installed alpha25 and passes
  after the source update. The silence regression remains green.
- A 21,177-frame workload represents a 30-minute Notes timeline. The old
  decoder took 536 and 529 ms; the exact decoder took 2852 ms, debug QA.
  This workload uses uniform synthetic note evidence, not recorded music.
  This is decoder wall time, not total song analysis or a CPU profile.
  The native regression has a 10-second bound.
- An imported mono PCM WAV follows A4, C5, E5 and then silence in default
  Notes mode. MediaExtractor/MediaCodec and the feature/decoder pipeline run
  without creating a player or rendering audio.
- The selected suite also covers stereo chords/tempo, distorted power chords,
  the private-song fixture, and eight tuner audio regressions.

Pre-version-bump QA APK SHA-256:
`1bb50e6a63d3535d2a0e5bd1f687bec20bb5382eecd823cfc937945417558686`.
Instrumentation APK SHA-256:
`054c49b297d273dff0503f01047aace3141c9cb1ed5ec1d4b18a76cbe68fa054`.

No Focusrite or phone playback was used. Temporary phone song copies were
removed, original data preserved, and the phone released at 18:31 CEST with
QA stopped and Home foreground. The complete playback/UI suite was not run.
This corrects the decoder; it does not establish perfect acoustic transcription
or improved quiet-tuner capture.

## Final versioned device check

The final QA APK reports code 29 / `0.3.0-alpha.26-qa`. Its SHA-256 is
`d94955e78dc9a4516109c1f6001a049e4bed33f89d28b67b1911be8c6b0af728`.
The instrumentation APK hash is unchanged. All four focused checks passed
again in 3.287 seconds, including actual WAV Notes analysis and silence.
The full-length decoder measured 2852 ms again. These are repeats of four
tests from the 16-test suite, not four additional independent scenarios.
The Huawei was released at 18:50 CEST, QA stopped, Home foreground, no audio
or shared settings changes, and no ADB work left running.

## Artifacts and gates

[PR #15](https://github.com/Majkey25/TuneItAll/pull/15) merged reviewed head
`e247b647a78f15b29b61ac97e9c34cc1ee5db0e9` as
`4793595a97711c5268bc1895d41fb114e1e98c51`. Their trees are identical.
Tag `v0.3.0-alpha.26` points to the merge. The signed Play build used the
reviewed head; the GitHub APK build used the tag.

- APK SHA-256: `bab2c0bb2690368962f51646d667a5366cf30fdc62d94cc396157bc2b41e97c2`.
- AAB SHA-256: `ca1da6918dfe5e434692ec491147018a1e3687f7e4245bf1092faae192a2612b`.
- Preview signer: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Upload signer: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Downloaded APK bytes match both the companion checksum and GitHub asset
digest. Apksigner, jarsigner and bundletool validation passed. Jarsigner
reported the existing self-signed-certificate and JarInputStream ordering
warnings. Actual APK/AAB manifests confirm code 29, API 26+, target 36,
unchanged package, and no Internet/advertising-ID permission. The AAB contains
no experimental model, private song, test audio or keystore.

Local 305-test execution, lint, all APK/AAB builds and package verification
passed. One initial lint invocation crashed inside UElementAsPsiDetector on
unchanged Theme.kt; the unchanged single-worker retry passed. No lint rules
were disabled. The local unsigned AAB was not uploaded.

Passed: [PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34705750652),
[main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34706116824),
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34706115866),
[preview release](https://github.com/Majkey25/TuneItAll/actions/runs/34706133781),
[signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34705823684).

## Google Play submission

Publishing overview confirmed **Probíhá kontrola změn** for exactly one
change, Alpha code 29 / alpha26. Quick checks were still running. Submission
is confirmed, not approval or tester availability.

The rollout remains 100% of the existing Alpha group. Testers, countries,
prices, artwork and managed-publishing settings were not changed. Play
reported zero lost devices and only the optional deobfuscation/native-symbol
warnings. No terms or unrelated account prompts were accepted.
