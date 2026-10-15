# Alpha 40 settings release

Version `0.3.0-alpha.40` (43), package `com.tuneitall.tuner`.

## Changes

- Add Google Material outlined icons to General, Tuner, Metronome and Appearance settings.
- Wrap category chips on narrow screens instead of requiring horizontal scrolling.
- Verify unselected chip icon contrast with custom-theme colors.
- Preserve setting callbacks, saved preferences, audio detection and playback.

The work is isolated from the older unfinished tuner changes. No project migration,
dependency update, emulator or desktop/Focusrite playback is included.

## Verification

The local APK/AAB build and lint passed: zero lint errors, 24 existing warnings.
JVM results: 395 passed, four optional corpus tests skipped, zero failures.
Ten bundle-signature checks and 17 release-policy checks passed, including
rejection of substituted identity, digest, permissions and accessibility metadata.

The first Huawei Android 10 run passed 69 of 70 native cases. The custom-tuning
return test failed because `tuning_picker` was not displayed; an isolated repeat
failed at the same assertion. The pinned published alpha 39 QA APK also failed
this test, but earlier, because `Tunings` was not displayed. This does not establish
the same root cause. That unrelated workflow remains an explicit validation gap;
the test was not changed or disabled.

The new large-text category regression failed before the icon/layout changes and
passed after them. The final version 43 Huawei run passed all 24 targeted native
cases, with no skips, in 51.366 seconds. These cover settings categories at 200%
text size, callbacks, custom-color contrast, appearance persistence, language,
reference-pitch validation, headstock choice, metronome controls and support.
An optional chip-color adjustment was removed because the device test showed
the existing fallback already preserved contrast.

The installed QA APK was read back at 07:43 UTC and matched the tested build:
`9cc060ff1ff2e2dc626316af4a1eb87a032ca8e4543edcafab0477b2a2706221`.
Only QA preferences were restored and the own QA process stopped. All ADB access
was released at 07:44:43 UTC. No production user data was changed.

Delivery is pending. The previous production version 42 was confirmed active
in Google Play Console on 15 October at 07:32 UTC.
