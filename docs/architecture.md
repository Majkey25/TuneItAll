# Architecture

TuneItAll is one native Android application module.

Audio flows through `AudioRecord` -> 4,096-sample overlapping PCM windows ->
multi-candidate YIN analysis -> adaptive noise floor -> bounded online pitch
tracker -> note targeting and hysteresis -> needle-only visual smoothing ->
immutable UI state. This is a clean-room, pYIN-derived streaming tracker, not a
full offline pYIN implementation. No microphone samples are persisted.

Reference tones and the confirmation chime are generated from PCM math at
runtime. Reference tones use a decaying harmonic pluck with a silent tail and
media audio routing. Switching tones applies a short volume ramp before the old
track is released, avoiding abrupt waveform cuts. The one-shot confirmation
uses notification sonification routing, respects system Silent/DND behavior,
and does not post a notification. A bounded input gate excludes the confirmation
window from pitch detection so the app cannot tune against its own chime.

The metronome owns one continuous mono PCM16 `AudioTrack` at 48 kHz. A bounded
schedule and fixed 1,024-frame buffers drive its generated clicks. Playback is
foreground-only: leaving the Metronome destination or backgrounding the app
stops and releases the output session.

Compose renders the cents rail and all 4–9-string headstocks from typed tuning
data. Physical string numbers descend from the lowest string to string 1. The
Chromatic mode uses a dedicated all-instrument surface with no headstock.
The Core shell uses the final simple Light/Dark tuner and mechanical-metronome
panels. Chords and Trainer remain disabled destinations and are not part of
Core.
`SharedPreferences` stores bounded user settings, favorites, and at most
100 validated custom tunings. The manifest intentionally has no Internet
permission.

Task 12 acceptance on the dedicated API 35 emulator measured an 8.67 ms p95 for
500 pre-generated tuner frames against the 42.7 ms hop budget, with no backlog.
The real metronome player ran for five minutes at 137 BPM with zero reported
`AudioTrack` underruns and no app crash. The target Samsung `SM-S938B` was not
available, so acoustic sound quality and quiet/room-noise behavior remain a
physical-device release caveat.
