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
- After the final shared radio-colour adjustment, all 27 cases passed again in 53.432 seconds, including rendered radio contrast on both background and dialog planes. QA preferences were restored and all ADB access was released at 12:45:05 UTC. Production data was untouched.
- The new artwork test failed against the installed alpha.40 baseline: center 312 px instead of 281.5 px. It passed after the vector/pivot correction.
- Inspected General, Tuner, Appearance and Metronome on the physical phone. No emulator or audio output through Focusrite was used.
- A read-only Claude Code review identified constrained chip labels and a pitch-draft restoration problem. Both were corrected and checked on-device. Unverified suggestions were not treated as findings.

## Limits and publication

This is a settings/layout release, not a new accuracy or sound-quality claim. No live guitar, arbitrary-song corpus or audible metronome evaluation was performed for this UI-only change. The unrelated custom-tuning return test documented in alpha.40 was not modified or counted as passing.

## Delivery

- [PR 39](https://github.com/Majkey25/TuneItAll/pull/39) passed the required [CI check](https://github.com/Majkey25/TuneItAll/actions/runs/38737089137) and merged through protected main at 12:54:47 UTC. Its reviewed head `4a10625f911b00fe28cf2c3ef5da394cea278619` and merge `345769c085032c72fa74e89512af88ea2df3e737` have identical trees. The separate [main CI](https://github.com/Majkey25/TuneItAll/actions/runs/38738318395) also passed.
- The immutable [tag build](https://github.com/Majkey25/TuneItAll/actions/runs/38738374314) and unsigned package/version/digest verification passed. The signing job stopped before key access with `Resource not accessible by integration` while inspecting classic main protection. Fresh owner-authenticated checks verified main and all three release environments. Existing owner-local signing used the exact hosted files; no protections or credentials changed. Tag creation used the existing owner exemption.
- Both certificate pins matched. All 182 AAB and 184 APK non-signature payload entries matched the hosted originals. The provenance identifies owner-local signing and is not a GitHub Actions cryptographic attestation.
- [GitHub alpha 41](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.41) was published at 13:05:47 UTC. The APK, checksum and provenance asset digests matched the checked local files after publication.
- By 13:09 UTC, Google Play confirmed production `44 - Organized settings` was submitted for a 100% rollout in all existing targeted countries. Both English and Czech notes were supplied. The Console showed **Changes in review**, with quick checks running and managed publishing off. Submission is not confirmation of approval or availability; version 43 was confirmed active before this update.
- Play reported no blocking error or loss of supported devices. Existing warnings about missing deobfuscation mapping and native debug symbols remain.

Signed Play AAB SHA-256:
`3cdb3d8eb6c77d94e5455e0dcb26c21c86e9f54fcdcd2244c698b58c1318037a`.
Public preview APK SHA-256:
`6ed4e7026dbdff6f21481b1d1765cb4964c8f5e4877720feb767da8b1b32a41b`.
