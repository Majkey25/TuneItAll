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

The metronome UI reads phase from the hardware playback head. A native frame
callback rotates one cached pendulum layer, while the body stays static. Main
click frames map to alternating pendulum endpoints. Typed BPM input remains a
local draft until the user confirms it, so partial values never reschedule audio.

The Chords destination reads canonical Standard E guitar and Standard C ukulele
shapes from a pinned offline `chords-db` snapshot. It does not generate arbitrary
fingerings for other tunings. Song analysis opens one user-selected URI through
the system document picker. `MediaExtractor` and `MediaCodec` stream decoded PCM
into an 8,192-frame STFT, 12-bin chroma feature, major/minor/dominant-seventh
template matcher, and bounded temporal smoother. Only chord events and playback
position remain in memory. The source audio is not copied or uploaded. Trainer
audio reuses the click-free generated-tone player for chord and single-note ear
exercises and stores only bounded scores.

Compose renders the cents rail and extended headstocks from typed tuning data.
The six-string guitar offers original 3+3 and pointed 6-inline line-art vectors
with functional string controls. The metronome uses an animation-ready native derivative of a
CC0 SVG while Compose rotates only the lightweight, audio-synchronized arm.
Both vectors tint from the active Light/Dark theme. Physical string numbers
descend from the lowest string to string 1. The
Chromatic mode uses a dedicated all-instrument surface with no headstock.
The shell uses simple Light/Dark tuner, metronome, Chords, Library, and Trainer
destinations. Both gear buttons open one global Settings screen. The metronome
rhythm summary opens a separate quick-control sheet.
`SharedPreferences` stores bounded user settings, favorites, and at most
100 validated custom tunings. The manifest intentionally has no Internet
permission.

Task 12 acceptance on the dedicated API 35 emulator measured an 8.67 ms p95 for
500 pre-generated tuner frames against the 42.7 ms hop budget, with no backlog.
The real metronome player ran for five minutes at 137 BPM with zero reported
`AudioTrack` underruns. A 400 BPM edit scenario retained one output session and
zero underruns before and after confirmation. A generated C-major WAV completed
local decoding, chord recognition, timeline, playback, and position-sync checks.
The target Samsung `SM-S938B` was not
available, so acoustic sound quality and quiet/room-noise behavior remain a
physical-device release caveat.
