# Low song notes and quantization noise

This change affects imported-song analysis, not microphone tuning.

## Reproduced failures

- Guitar Notes excluded pitches below E2, including supported extended-range tunings. Bass Notes excluded B0 and A0.
- A0 at 44.1 kHz could fall just below the extractor's 27.5 Hz cutoff. Its measured peak was 27.359 Hz, so the fundamental was discarded before tuning correction.
- Out-of-range tones could leave numerical or PCM16 quantization residue inside the analysis band. Normalization amplified that residue into notes and chords. A physical Huawei test reproduced a false F5 in the final 165 ms of a 20 Hz WAV.

## Changes

Guitar Notes now covers MIDI 23–88 and Bass Notes covers MIDI 21–72. Notes
extraction retains a half-semitone margin below A0 until tuning correction.
The Chords and Power modes retain their existing frequency boundary.

Frame admission measures spectral peak prominence above neighbouring bins,
not the full height of a ripple sitting on spectral leakage. A frame is
rejected only when prominence is small both absolutely and relative to input
level. Rejected frames do not affect the estimated tuning reference. The
existing input-silence threshold is unchanged.

Global lower-band expansion changed chord statistics and was rejected.
Absolute peak-energy gating lost quiet recording segments. Relative-only
prominence gating missed some quiet PCM16 artefacts. Absolute-only prominence
gating delayed one recording's first label by 138 ms. None of those variants
is shipped.

## Frozen comparisons

Fresh checks on 22 September compare the candidate with released alpha28.

- 216 low-note/reference controls pass, including Float and PCM16, 44.1/48/96 kHz, A0/B0, and 432/440/444 Hz references. The old snapshot failed 132.
- 240 negative controls pass for silence, DC, and 20/24/26 Hz input at two levels. The old snapshot failed 130.
- 288 quiet melody controls pass in both versions. Each has one correct event covering the full two seconds. There are no additional gaps or false notes.
- Four quiet Float/DC-offset controls reject an absolute-only prominence gate and pass the combined gate. The corresponding unit test protects the quiet-signal exception.
- All 1,665 chord predictions across 48 frozen recording/gain cases exactly match alpha28, including labels, boundaries and confidence.

The recordings are 24 isolated GuitarSet performances at two gain levels,
not an independent corpus of full-band metal, rock and jazz mixes. These
checks establish the stated fixes and guard against regressions. They do
not establish universally accurate song transcription. The private problem
recording remains local and is not included in releases.

The unit and Android tests retain the reproduced low-note and PCM16 cases.
Release and device evidence is recorded separately with the shipped version.
