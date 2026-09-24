# Tuner screen and song-tempo changes

## Screen timeout

The tuner now uses Android Compose's native
[`keepScreenOn`](https://developer.android.com/reference/kotlin/androidx/compose/ui/keepScreenOn.modifier)
modifier while it is listening. The request does not depend on detecting a
note, so silence between strings does not release it. Stopping listening or
removing the tuner screen releases the request. Android still controls manual
locking and background activity behavior. No wake-lock permission or global
display setting was added.

The Huawei test failed before this change and passed afterward. It checks
Auto, Manual and Chromatic mode without a pitch reading, stopped listening,
resumption and composition removal. It verifies the native View request, not
an elapsed screen-timeout measurement or an Activity lifecycle transition.

## Tempo evidence

The previous broadband RMS onset detector lost instrument changes at similar
volume. Sampled carrier ripple could become a false beat, while a positive
onset baseline inflated noise correlation.

The revised detector separates four frequency bands, smooths band power before
downsampling, removes the onset mean and considers recurring correlation peaks.
Bounded interpolation around the winning peak removes coarse BPM rounding.
All arrays remain bounded by the existing 30-minute limit. There is no new
dependency or change to pitch/chord recognition.

Frozen 24-second generated-audio comparison, 8 kHz unless stated:

| Input | Before | After |
| --- | --- | --- |
| Equal-level timbre changes at 120 BPM | 40 BPM, 19% | 120 BPM, 79% |
| Regular kick/snare at 120 BPM | 120 BPM, 99.6% | 120 BPM, 97.2% |
| Same pattern with 20 ms timing variation | 120 BPM, 59.9% | 120 BPM, 73.4% |
| Competing 90 and 140 BPM pulses | 140 BPM, 59% | 140 BPM, 46% |
| Aperiodic noise | 207 BPM, 31% | 56 BPM, 12% |

Thirty steady/rising-tone controls at 8/44.1/48 kHz produced 21 estimates before
and none afterward. Persistent tests also cover fading tones, syncopation,
sixteenth-note percussion, stereo polarity, chunking and 40/240 BPM bounds.
Off-grid controls at 137/173/211/235/238 BPM now return those exact integer
values instead of 136/171/214/231/240, without changing their confidence values.

The displayed score is named **Rhythm match** because it measures periodicity,
not a calibrated probability of correct musical tempo. Strong half-time accents
can legitimately select 60 instead of 120 BPM. No multiplier was added to make
percentages look better. The new song-analysis actions and explanations are
translated into all five app languages.

These controls establish the reproduced fixes, not universal real-song accuracy.
An exploratory GuitarSet comparison did not establish a broad accuracy gain.
The [Ellis beat-tracking paper](https://www.ee.columbia.edu/~dpwe/pubs/Ellis07-beattrack.pdf)
provides background on onset evidence and tempo correlation; this remains the
app's own bounded implementation.
