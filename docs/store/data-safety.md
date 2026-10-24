# Google Play Data Safety record

Proposed answers for the current release candidate, based on the merged manifest
and runtime dependency set:

- Data collected: **No**
- Data shared: **No**
- Data encrypted in transit: **Not applicable**
- Account creation: **No**
- Data deletion request: **Not applicable; no server-side data exists**

Microphone samples remain on-device, are processed transiently in memory, and
are discarded immediately. Local preferences, custom tunings, trainer scores,
and Auto-scroll disclosure acceptance do not leave the device. Songs selected
through Android's system document picker are copied to private app cache for
on-device decoding. Playback and reanalysis retain one cached copy while the
song is loaded, including while paused or in the background. Tempo analysis and
unsuccessful imports attempt to delete their working copies when finished.
The retained copy is deleted on a best-effort basis when the song is removed or
replaced, or the app releases the loaded song. Copies left after a crash or
failed deletion may remain until startup cleanup, Android cache cleanup,
clearing app data, or uninstalling. The original file is not changed and no audio
is uploaded. Chord, note, tempo, and arrangement results remain in memory.

The optional Accessibility service performs only user-requested swipe gestures.
Its metadata explicitly disables window-content retrieval. Floating controls
use Android's overlay and foreground-service APIs. Intoniva does not read,
store, record, or transmit screen content.

Google Play builds contain no external donation action or payment link. Email
and issue-reporting support opens outside the app. Data voluntarily sent through
those external services is not collected by Intoniva. The Play privacy policy
describes support correspondence and its providers. GitHub APKs separately retain
optional Buy Me a Coffee support and the original legal pages.

Evidence gates before submission:

1. Merged release manifest contains `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, and
   the required foreground-service declarations, but no `INTERNET` or `AD_ID`
   permission.
2. Accessibility metadata has `canPerformGestures=true` and
   `canRetrieveWindowContent=false`.
3. Runtime dependency review finds no ads, analytics, tracking, telemetry,
   remote crash reporting, or network client introduced after this record.
4. Network observation during the full device flow shows no application traffic.
5. Published privacy policy matches the release behavior.

Reassess the Play Data Safety form whenever code, permissions, or dependencies
change. Play Console answers, not this file, are the authoritative submission.
