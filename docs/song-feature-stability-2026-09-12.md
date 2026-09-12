# Note evidence during silence

## Reproduced failure

The feature extractor maintained each moving average by adding and
subtracting Float vectors. Tiny rounding residues remained after the
music left the window. Normalization amplified that residue into positive
and negative note evidence as large as 0.75 during digital silence.

This affected the result, not just an internal array. With two seconds of
C-major audio followed by eight seconds of zeros, Notes reported a C2 from
1962 to 10000 ms at confidence 0.82147. Both the silent-feature and public
note-timeline regression checks failed on the preceding implementation.

## Change

The extractor now sums each bounded window directly in Double precision.
The largest window has 19 frames. Summing actual zero inputs produces zero,
without subtractive residue or a new amplitude cutoff. The unused Float
accumulation helper was removed. No classifier weights, smoothing widths,
pitch settings, dependencies, or permissions changed.

## Recorded-song comparison

The two frozen GuitarSet subsets contain 24 performances, evaluated at
gains 1.0 and 0.01. All 48 timeline fingerprints match the baseline after
excluding the last-bit confidence differences. Fingerprints include chord
root, quality, inversion bass, start time, and end time. Per-record root
agreement, supported-quality agreement, coverage, and event counts also
remain identical. This is non-regression evidence, not perfect recognition.

Both new regressions pass. The release branch passes 304 local tests,
zero failures/skips, lint, debug/QA/test APK builds, and release-bundle
verification. The local AAB is unsigned and is not the Play upload artifact.

Huawei YAL-L21 reproduced the same false C2 on alpha24. Alpha25 passed all
13 selected device checks in 26.731 seconds with zero failures or skips.
These cover the new regression, stereo/file/tempo decoding, the private song
fixture, and tuner audio behavior. An isolated repeat processed the ten-second
clip in 149 ms, returned no note after 2133 ms, and kept all 81 silent frames zero.
No audio was played. The full playback/UI suite was deliberately not run
while the user was using Focusrite. The phone was released at 17:08 CEST.

## Rejected experiments

Multiplicative or capped harmonic evidence removed virtual single-note
chords but reduced recorded quality or coverage. A separate root/quality
representation also regressed recordings. None of these variants is shipped.

A same-window two-period tuner fit improved aggregate quiet-decay counts,
but the exact integration worsened an existing chime check from 2.18 to
4.16 cents, exceeding its unchanged 3-cent limit. It remains isolated research.
The tuner is unchanged in this release. Extremely weak tones, virtual
single-note chords, and dense/ambiguous song transcription remain unfinished.
