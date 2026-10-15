# Intoniva 0.3.0-alpha.41 (44)

## Changes

- Language and appearance open inline, with icons, current values and expanded/collapsed accessibility state.
- Appearance separates modes, themes, custom colours, layout and text into expandable groups. Light and dark choices have distinct icons.
- Reference pitch, note notation and headstock layout are in Tuner settings. Frequency validation and extreme-value confirmation remain; a draft survives collapse and restoration.
- Profile and input choices wrap instead of squeezing labels beside icons.
- The metronome vector has centered bounds and padding around its strokes. Its animated arm uses the matching translated pivot. Audio phase and playback code are unchanged.

## Verification

- Clean `tools/build.ps1 -AllowUnsigned`: JVM tests, lint, debug/QA APKs, test APK, release bundle and release identity/signature regressions passed.
- JVM: 395 passed, 4 optional corpus tests skipped, 0 failed.
- Huawei YAL-L21, Android 10: 27 focused native tests passed, 0 skipped, 0 failed. They cover settings navigation, language in landscape, 200% text, appearance persistence, reference pitch 444/400/420/440 Hz and restoration, headstock/notation callbacks, metronome controls and rendered artwork bounds.
- The new artwork test failed against the installed alpha.40 baseline: center 312 px instead of 281.5 px. It passed after the vector/pivot correction.
- Inspected General, Tuner, Appearance and Metronome on the physical phone. No emulator or audio output through Focusrite was used.
- A read-only Claude Code review identified constrained chip labels and a pitch-draft restoration problem. Both were corrected and checked on-device. Unverified suggestions were not treated as findings.

## Limits and publication

This is a settings/layout release, not a new accuracy or sound-quality claim. No live guitar, arbitrary-song corpus or audible metronome evaluation was performed for this UI-only change. The unrelated custom-tuning return test documented in alpha.40 was not modified or counted as passing.

The [GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.41) records the final source, artifact hashes and signing provenance. Google Play publication must be checked separately; submission is not proof of availability. Before this update, version 43 was confirmed active in production on 15 October 2026.
