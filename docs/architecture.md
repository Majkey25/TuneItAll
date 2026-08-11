# Architecture

TuneItAll is one native Android application module.

Audio flows through `AudioRecord` -> overlapping PCM windows -> YIN pitch
detection -> confidence/RMS gates -> median smoothing -> note targeting and
hysteresis -> immutable UI state. No microphone samples are persisted.

Reference tones and the confirmation chime are generated from PCM math at
runtime. Reference tones use a decaying harmonic pluck with a silent tail and
media audio routing. Switching tones applies a short volume ramp before the old
track is released, avoiding abrupt waveform cuts. The one-shot confirmation
uses notification sonification routing, respects system Silent/DND behavior,
and does not post a notification. A bounded input gate excludes the confirmation
window from pitch detection so the app cannot tune against its own chime.

Compose renders the cents rail and all 4–9-string headstocks from typed tuning
data. Physical string numbers descend from the lowest string to string 1. The
Chromatic mode uses a dedicated all-instrument surface with no headstock.
`SharedPreferences` stores bounded user settings, favorites, and at most
100 validated custom tunings. The manifest intentionally has no Internet
permission.
