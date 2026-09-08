# Stereo song analysis

## Cause

The file decoder averaged channels before extracting pitch and rhythm features.
Its fallback selected one channel only when whole-buffer cancellation was severe.
A centered bass or drum could keep that average loud enough while out-of-phase
guitar notes disappeared. Selecting one channel also discarded instruments
present only in the other channel.

A local diagnostic of the private problem recording found 181 of 2,104 windows
with more than 10% of strong-band energy affected by cancellation. The diagnostic
used 4,096-sample Hann windows at 22,050 Hz, a 2,205-sample hop, and the 65–2,000 Hz
band. Strong bins were at least 10% of that window's peak power; cancellation
meant mono power below 10% of mean channel power. At 58.9 seconds, whole-window
mono power was 67.7% of the louder channel, while 68.0% of strong-band energy
met that cancellation criterion. The old whole-buffer fallback could miss this.

These are signal-loss measurements, not verified chord labels. The private
recording is not included in the repository or uploaded by the app.

## Change

- Preserve interleaved PCM channels through the Android decoder.
- Preserve channel spectra and combine mean power before extracting harmonic
  features. Pack each pair of real channels into one complex FFT, including
  a zero imaginary input for an odd final channel. Channel polarity cannot
  cancel a note at this step.
- Use mean channel energy for tempo analysis, not averaged waveforms.
- Keep the existing mono arithmetic, chord vocabulary, scoring, timing,
  cancellation, and 30-minute limit. Count duration in sample frames.
- Reject incomplete frames, nonfinite floats, unsupported PCM, and changing
  sample rates/channel counts. Accept one to eight channels.
- Reuse the Hann window and FFT buffers. Do not retain whole decoded songs.

No dependency, permission, network request, setting, or playback change was added.
This change does not modify live microphone tuning.

## Verification

Local command:

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleQa :app:assembleQaAndroidTest :app:bundleRelease --console=plain
```

Initial result: 280 unit tests, 276 passed and four optional corpus tests skipped
because their local fixtures were absent. A second full run with the existing
hash-verified local corpus fixtures completed with **280 passed, zero skips**.
Lint and all requested builds passed.
The local release bundle is unsigned and is not a Play upload artifact.

Focused checks cover phase cancellation beneath a centered bass, channel order
and polarity, dual-mono equivalence, complete frames, chunk boundaries, EOF,
duration limits, malformed floats, and PCM buffer offsets. Chord detection must
recover C major from the centered-bass/opposite-phase triad fixture; merely
producing nonempty output is not sufficient.

## Physical Android checks

Huawei YAL-L21, Android 10; isolated `com.tuneitall.tuner.qa`, existing private
fixture preserved in QA cache. Production app data and viewport were unchanged.

The same four Android decoder tests first ran against the released-source QA
build. The centered-bass fixture returned Fsus2/C instead of C major, and the
120 BPM fixture returned 200 BPM. The mono E5 and private-song controls passed.

The first corrected build passed 98/99 tests in the full phone suite. Both
stereo fixtures passed, but private-song analysis took 31,013 ms, exceeding the
unchanged 30,000 ms limit. That build was not accepted for release.

Packing the two real channels into one complex FFT fixed the measured cost.
All four decoder tests then passed on the phone in 11.707 seconds. Private-song
analysis took 10,681 ms for 210,535 ms of audio, with 286 events and 90.39%
coverage. The earlier baseline run took 21,378 ms with 266 events and 89.94%
coverage. These are individual runs, not a controlled speedup guarantee or
proof that the new chord labels are all correct. The runtime limit was not
relaxed.

Final optimized QA APK SHA-256:
`e23902c8c6717e4aada1f1433019faf470062d1722d47f3b99c525fbe95fac17`.
Shared test APK SHA-256:
`ad613e5d9825b434aa6cfa2d483e975a5a5199437959bdd513414123d46e9a0f`.
Baseline QA APK SHA-256:
`f220a30a92fecfcd342401fc94a0b4f197ffd9318f05a3f82f933109db633b2f`.

No new release has been published from this change yet. A full optimized
device rerun is still planned before the next release.

The unchanged mono benchmark on 12 performed GuitarSet recordings reports
64.99% root accuracy, 57.19% supported chord-quality accuracy, and 95.77% label
coverage. These measurements are not evidence of universal correctness.
The EGSet12 tuner control remains 91/93 nominal-note matches at gains 1, 0.01,
and 0.001; it is amplified recorded audio, not unplugged phone-microphone QA.

## Limits

Preserving input evidence removes one cause of wrong or missing results. It
does not establish perfect chord transcription for arbitrary recordings.
Dense mixes, distortion, incomplete voicings, and ambiguous harmony remain
separate recognition problems. Label coverage is not chord accuracy.
