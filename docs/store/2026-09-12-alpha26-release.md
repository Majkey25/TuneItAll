# Alpha26 release evidence

Version `0.3.0-alpha.26`, code `29`, package `com.tuneitall.tuner`.
Release preparation is in progress. Alpha25 remains the last confirmed Play release.

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
