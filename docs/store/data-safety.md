# Google Play Data Safety record

Proposed answers for release `0.3.0-alpha.3`, based on the current manifest and runtime
dependency set:

- Data collected: **No**
- Data shared: **No**
- Data encrypted in transit: **Not applicable**
- Account creation: **No**
- Data deletion request: **Not applicable; no server-side data exists**

Microphone samples remain on-device, are processed transiently in memory, and
are discarded immediately. Local preferences and custom tunings do not leave
the device. A song selected through Android's system document picker is decoded
transiently on-device; TuneItAll stores only in-memory chord events and does not
copy, upload, or retain the source audio.

Evidence gates before submission:

1. Merged release manifest contains `RECORD_AUDIO` and no `INTERNET` permission.
2. Runtime dependency review finds no ads, analytics, tracking, telemetry,
   remote crash reporting, or network client introduced after this record.
3. Network observation during the full device flow shows no application traffic.
4. Published privacy policy matches the release behavior.

Reassess the Play Data Safety form whenever code, permissions, or dependencies
change. Play Console answers, not this file, are the authoritative submission.
