# Alpha31 verification

Version `0.3.0-alpha.31`, code `34`, package `com.tuneitall.tuner`.
[PR #21](https://github.com/Majkey25/TuneItAll/pull/21).
Verified source: `942b7c1ef1dec825ef8dc9670535cbce4c57f5ea`.
Merged at 17:46 CEST as `f7003b74b093e58987eea7696abf966bccbedae1`.
Its tree matches the verified source. Tag `v0.3.0-alpha.31` points to the merge.

Song-tempo analysis is collapsed by default under **Get tempo from a song**.
The section contains file selection, progress, errors and detected BPM.
Collapsing it does not clear results. Expansion survives saved-state restoration.
The label is translated into all five languages; screen readers receive the
expanded/collapsed state. Audio engines, permissions and dependencies are unchanged.

## Local and physical checks

- 339 unit tests passed with no failures, errors or skips.
- Android Lint and both QA APK builds passed.
- Huawei YAL-L21, Android 10: four silent instrumentation tests passed in 5.866 seconds.
- The hidden-by-default assertion failed against alpha30 before implementation.
- The updated regression verifies collapsed content, label, accessibility state,
  expansion, saved-state restoration, BPM application, collapse and retained results.
- Nearby BPM editing, rhythm controls and tuner screen-on cleanup passed.
- Visually checked collapsed/expanded layouts in the running app. The import
  button opened Android's audio-file picker. No audio was played.
- Phone returned to Home and was released at 17:37 CEST. Production app data and
  Focusrite settings were not changed.

QA APK SHA-256: `a6053ff3a50a6b76476a59035357ef314e56ddd07c84239aadce31cd2c8987e9`.
Test APK SHA-256: `558b1c25f509589b6f6f1bc90853c18519b08156f97e8d30966d8a1a76753393`.

## Release checks

- [PR quality gate](https://github.com/Majkey25/TuneItAll/actions/runs/36021679770): passed.
- [Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/36021679778): 21 tests passed, no failures or skips, 19.073 seconds.
- [Signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/36021677330): passed.
- [Merge CI](https://github.com/Majkey25/TuneItAll/actions/runs/36022687726) and
  [Pages deployment](https://github.com/Majkey25/TuneItAll/actions/runs/36022685683): passed.
- AAB SHA-256: `060275e52b66f1fb175edea323b45e434ee0c887bc264aa1b314921b1303402b`.
- Upload signer SHA-256: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Bundletool validation and jarsigner verification passed. Existing self-signed
certificate, missing timestamp, ZIP-attribute and JarInputStream-order warnings
remain. The manifest confirms code 34 / alpha31, API 26+, target 36, no debuggable
flag, Internet, advertising-ID or wake-lock permission.
The 158-entry archive has no duplicates, audio recordings, experimental models
or signing keys. Play validation reports no lost supported devices. Its two
warnings concern optional deobfuscation data and native debug symbols.

## Distribution

Google production access was still pending at the start of this release.
The previous Alpha30 was available to selected testers. This update uses the
existing Alpha group and does not change countries, pricing or tester access.

At 17:51 CEST, Play publishing overview confirmed `Probíhá kontrola změn` for
code 34 / alpha31, with 100% rollout to the existing Alpha group. Quick checks
and review remain pending; this does not confirm tester or production availability.

[GitHub Alpha31](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.31)
was published at 17:52 CEST by the
[preview workflow](https://github.com/Majkey25/TuneItAll/actions/runs/36022760161).
The downloaded APK matches its companion checksum and GitHub asset digest.
Apksigner verification passed; packaged metadata confirms code 34 / alpha31.
This debug-signed testing APK is separate from the non-debuggable Play bundle.

- APK SHA-256: `890df318ddbb2ae82aa281c0489bf58ec7ce5165dba4d8090ccaea3edb6580d1`.
- Preview signer SHA-256: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
