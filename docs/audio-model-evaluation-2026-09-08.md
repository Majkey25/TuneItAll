# Offline chord model evaluation

Research only: these models are not integrated into alpha21. The separately
verified stereo and weak-note fixes are documented in the
[release record](store/2026-09-08-alpha21-release.md).

## Frozen desktop checks

The Music X Lab ISMIR 2019 s0 checkpoint was evaluated with its original
dictionary and decoder. The two fixed GuitarSet subsets contain 12 recordings
each. These are isolated guitar performances, not an independent full-band
metal benchmark. Exact-quality scores exclude qualities outside the existing
benchmark's supported set; root scores cover all annotated chord spans.

The first portable frontend removed automatic tuning. On the additional subset
at normal gain, exact supported-quality accuracy fell from 74.31% with the
reference frontend to 70.33%. Restoring automatic tuning to the nnAudio CQT
recovered most of that loss.

Quiet signals exposed a separate numerical problem. Float32 convolution and
normalization differences were amplified by the model. Even disabling oneDNN
in the original PyTorch implementation exceeded the 0.0001 posterior-error
limit. The failed raw export and its results were preserved.

A separate preprocessing candidate normalizes each non-silent recording to a
peak amplitude of one before feature extraction. It uses the same rule for
every recording, with no per-song thresholds. Silence remains zero. Combined
with automatic tuning and centered normalization in the ONNX export:

| Subset | Input gain | Root accuracy | Exact supported quality | Coverage |
| --- | --- | --- | --- | --- |
| Original 12 | 1 and 0.01 | 76.0310% | 68.1095% | 97.7033% |
| Additional 12 | 1 and 0.01 | 90.0191% | 74.0447% | 99.7496% |

All 48 track/gain cases passed the unchanged 0.0001 PyTorch-versus-ONNX
posterior limit. Maximum observed error was 0.0000076294. Their decoded labels
and boundaries matched. Both recording gains produced the same metrics.
This is numerical agreement on the measured corpus, not universal chord
accuracy or proof of Android performance.

## Rejected DSP experiments

Replacing additive harmonic evidence with a multiplicative blend broke Amadd9
and the existing recorded-segmentation check. Restoring the additive blend
removed those regressions.

An observed-bass rewrite improved root accuracy but reduced exact-quality
accuracy and coverage. It was separated from the other changes and not released.
Adding POWER to the existing full-chord dictionary preserved all 1,530 frozen
corpus predictions and recognized clean sinusoidal fifths. It failed all 24
plucked-fifth cases and dropped a rapid C5 passage. That is not an acceptable
electric-guitar fix, so it was not released either. Failing tests were retained.

## Model limits still under test

- The trained heads can represent add9, minor add9, and major/minor sixths, but
  the default dictionary omits them. Major/minor ninth chords contain a seventh
  and cannot be relabeled as added-ninth chords.
- The model has no POWER class. Adding a dictionary entry cannot create a
  missing trained output.
- The default decoder charges the same transition cost for a root change and
  a brief added seventh. An ideal-posterior probe showed a 0.325-second Cmaj7
  span could be erased at 95% confidence. This probe is not an acoustic test.
- The CNN spans 21 feature frames, followed by a bidirectional LSTM. A 23 ms
  output interval does not establish 23 ms acoustic resolution.
- On-device feature extraction, bounded long-song inference, acoustic timing,
  unsupported qualities, and full-band recordings remain release gates.

## Huawei classifier probe

Huawei YAL-L21 / Android 10 ran the exported classifier through ONNX Runtime
1.29.0, CPU provider, two intra-op threads. Five frozen feature vectors covered
256 and 512 frames from two recordings, plus silence. Three completed runs
passed all 90 output-head comparisons with zero top-class mismatches. Maximum
posterior error against the desktop PyTorch reference was 0.00000263924.

| Vector | Frames | Median inference | Range across three runs |
| --- | --- | --- | --- |
| Original subset | 256 | 117.83 ms | 113.92-119.67 ms |
| Original subset | 512 | 216.54 ms | 214.96-226.66 ms |
| Additional subset | 256 | 107.97 ms | 105.77-111.04 ms |
| Additional subset | 512 | 227.28 ms | 224.45-227.37 ms |
| Silence | 256 | 103.02 ms | 102.41-103.46 ms |

Native allocations rose from about 6 MB before runtime initialization to
187.6 MB while the session was open, then fell to about 12.8 MB after
session closure. These snapshots are not a leak diagnosis. They show why
bounded inference and session cleanup need verification before integration.
The probe excluded decoding, CQT extraction, UI rendering, and acoustic input.

An initial instrumentation attempt started before the target APK installation
session had returned and reported `Process crashed`. After both installations
completed, the three runs above passed. A later `dumpsys meminfo` returned a
system-wide report because instrumentation had exited; that report is not used
as application memory evidence. Memory figures above come from in-process
`Debug.getMemoryInfo` and `Debug.getNativeHeapAllocatedSize` markers.

The AAR's manifest injected Internet/network-state permissions and a telemetry
initializer into the test APK. These were removed before installation, and
runtime telemetry was also disabled. Both final packaged manifests were checked.
The QA app has no ONNX dependency; only the instrumentation APK contains it.
No production package data or system viewport changed. The phone was released
at 10:41 CEST and public temporary transfer files were removed.

Probe APK SHA-256:

- QA app: `f220a30a92fecfcd342401fc94a0b4f197ffd9318f05a3f82f933109db633b2f`.
- Instrumentation: `05fc222ceaf6693fa3c83eb663edfd9d0d0e2f71d18152d904c897fa76898286`.

An allocator-only A/B run disabled the CPU arena and repeated all three probe
runs. Numerical results still passed, but retained native allocations remained
about 187.5 MB and timings were similar. The change did not demonstrate a memory
benefit and was not retained as an optimization. It does not establish the
cause of the session-owned memory. The test APK hash was
`5fe66a2464c9d80fb355e2324f687530b5d61d855389c039bf2975d54b314296`.

A separate A/B restored the CPU arena and disabled memory-pattern optimization.
All three runs passed the same numerical checks. Retained native allocations
fell from about 187.6 MB to 101.5 MB, then to 12.7 MB after session closure.
This measures session-owned allocations at the logged checkpoints, not the
maximum transient allocation inside a kernel. Inference remained approximately
103-225 ms per vector, with workload-specific timing variation. The test APK
hash was `13d376172dd3258133a0317e73cfa735ac50bac1e1880b58e216e80843334427`.
The phone was released at 11:12 CEST after these checks.

## Portable frontend and bounded inference

A 35,004-byte ONNX frontend now accepts the complex CQT kernels and their
normalization lengths as explicit inputs. It reuses the existing nnAudio
operations, without a runtime FFT operator or a separate model per tuning.
The legacy exporter cannot trace `torch.func.functional_call`; exposing the
existing initializers as inputs worked instead. The old source model is unchanged.

Five tuning-offset probes passed the 0.0001 feature-error limit. The complete
48 track/gain pipeline also passed the unchanged feature/posterior limits,
with identical decoded labels and boundaries to the normalized reference.
The Kotlin/JVM kernel generator matched all 100 reference tuning steps exactly
and rejected four invalid tuning inputs. Android kernel generation and tuning
estimation have not been verified.

- Retunable frontend SHA-256: `216f7e78681a1d5e63bd42515757e8314df27288603032980ee1c393b199959f`.
- Kernel-control SHA-256: `1224c31ac8591106be2abc38220c119e6eaa9efe37da2433db75786540d26891`.

Bounded classifier windows were also measured without changing the decoder.
512-frame windows, half-window output intervals, and quarter-window context
scored 75.4941% root / 70.5916% exact supported quality on the original subset
and 90.1913% / 74.0161% on the additional subset. These gain-one controls show
the accuracy tradeoff from changing the model's temporal context; they are not
a drop-in, bit-identical replacement for whole-recording inference.

## Additional rejected recognition candidates

Matching the app's 14 non-power qualities in the CNN dictionary improved some
recording scores but still failed the named Amadd9 and rapid Cmaj7 PCM cases.
Globally reducing same-root transition costs changed root decisions elsewhere.
Neither change was released.

Basic Pitch raw note evidence distinguished thirds and sevenths the chord CNN
missed. Treating its sigmoid activations as calibrated Bernoulli probabilities
was incorrect. Alignment to its documented 0.3 frame operating point fixed the
12 named PCM cases, but the attempted note-likelihood/root-prior combination
regressed broader recording quality and still produced false chords on a single
note. Full-component fusion also lost valid spans to the no-chord state. These
controls remain separate from the original results.

### Canonical note events and octave pooling

The [pinned official Basic Pitch note-event decoder](https://github.com/spotify/basic-pitch/tree/fa5997af0a8210982619003269994a1be25eddf3)
was then used with its
unchanged onset/frame/minimum-duration defaults. Accepted note events, weighted
only by their temporal overlap, fed the unchanged native chord decoder. Replacing
octave-summed pitch-class evidence with maximum pooling fixed a Cmaj7-to-Em
error: doubling E in another octave had biased the chord representation.

That fixed control passed 12/12 Kotlin probes and 11/12 synthetic checks. All
negative controls stayed empty, and seventh-progression accuracy rose from
89.55% to 98.26%. Recording quality rose modestly:

| Subset | Sum pooling, normal/quiet | Max pooling, normal/quiet |
| --- | --- | --- |
| Original 12 | 48.91% / 49.45% | 49.82% / 50.83% |
| Additional 12 | 42.15% / 42.36% | 42.80% / 42.41% |

Clipped POWER fifths, timing errors, and low recording coverage remained.
Basic Pitch trained on GuitarSet, so this is not an independent model benchmark.
No production change followed.

A conditional diagnostic ruled out simple model fusion. Conflicting labelled
outputs covered 35.31% of recording duration. In those conflicts, exact-quality
precision was 18.14% for the note adapter versus 58.94% for the chord CNN;
root precision was 68.96% versus 83.69%. Agreement covered 35.99% of duration
and had 91.90% exact-quality precision. Note-only spans covered only 0.38%.
The Kotlin probes behaved oppositely: conflict spans had 95.72% note-adapter
quality precision versus 1.10% for the CNN. The note adapter was therefore not
merely sparse on real recordings; many of its conflicting labels were wrong.

### Single procedural fine-tune

One conservative run trained only the MIT s0 checkpoint's final classifier
weight and bias, freezing all other parameters. Training used newly generated
audio with disjoint procedural seeds, never the evaluation recordings, Kotlin
probes, private song, or their labels. The fixed run used AdamW at 0.0001,
weight decay 0.0001, gradient norm limit 1, and 256 steps of 1,024 frames.
There was no search, best-checkpoint selection, or post-result retuning.

The final checkpoint was frozen before the unchanged 48 recording, 12 Kotlin,
and 12 synthetic/negative controls ran. Baseline reproduction matched exactly.
With the same 14-quality dictionary, recording quality improved from 68.84%
to 69.51% on the original subset and 74.04% to 76.56% on the additional subset.
Root accuracy fell from 76.49% to 74.25% and 90.02% to 89.31%, respectively.
Thirty of 48 recording cases regressed on at least one required metric.
All 12 Kotlin probes still failed; added-ninth and rapid-change checks worsened.

The run was rejected. Training and evaluation used 213.56 CPU seconds with
two intra-op threads and one inter-op thread. Original parameters and failed
results were preserved. Candidate SHA-256:
`5fc3a05fb3c7eeeb91c3a35de221951866bc665b3515e21ce5ed4ad105417233`.

### Live-pitch replacement

SwiftF0 was checked separately as a possible live-tuner replacement, with its
unmodified model, default confidence threshold, and a declared latency proxy.
It achieved 149/282 quiet-decay windows within 10 cents versus 254/282 for the
bounded Kotlin YIN candidate. Buzzing E2 P95 error was 14.80 cents versus 2.47.
That comparator preceded the later observed-state safety correction; current
shipping-candidate counts are in the [quiet-note report](quiet-current-evidence-2026-09-08.md).
It also retained the old pitch through an audible +30-cent change. It was
rejected for the precision-tuner path. Slightly better nominal note identity
on some amplified-guitar recordings did not establish cent-level accuracy.

SwiftF0 source: [pinned official model and MIT repository](https://github.com/lars76/swift-f0/tree/64700fce8ef39c2970814bf427ac1d75a2f20d72).
Model SHA-256: `7e2390db8379cd9e1e2b22828e55b45b57c8559e4c8335678c717dc245c18176`.

## Reproducibility

Local research artifacts remain outside distributed app assets under
`.reference/tmp/musicxlab/`, `.reference/tmp/chord-calibration/`, and
`.reference/tmp/dsp-multiplicative-2026-09-08/`. They include frozen manifests,
original and failed outputs, scripts, model hashes, numerical vectors, and test
XML. No user recording was uploaded or added to the public repository.

Later controls are preserved under `.reference/tmp/note-pc-max-control/`,
`.reference/tmp/note-cnn-agreement/`, and
`.reference/tmp/musicxlab-procedural-finetune/` in the research checkout.

- Model source: [Music X Lab](https://github.com/music-x-lab/ISMIR2019-Large-Vocabulary-Chord-Recognition),
  commit `481f4ce703f8822b99f4037e9104ba1760e21ea3`, MIT licence.
- Checkpoint SHA-256: `921b42d5d1cf9ce1c0c0e45a74d409b8066e0acec46058ef74e24ee0fb540761`.
- Classifier export SHA-256: `369ea7f44cf898b31d9b60e1cb975d66f813348bb4384da590795f1d2bbc60ec`.
- Original subset manifest: `836d3fcbada616e921de8ce21c2cd9c20d6749cb3acda2f1f850ad40f25b8d63`.
- Additional subset manifest: `14d41127dcc7b2bb68b4da1f6d0d062f3abc66a25ebba653c037996cababdf4a`.
- Reference result SHA-256, unchanged: `8739a8b8989a7825c202bf2b7035f7f831ecc1e749d215cd899bd2ed63a683d6`.
