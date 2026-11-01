# Intoniva 0.3.0-alpha.47 (50)

## Changes

* Keep settings tabs visible while their content scrolls. Each section retains its own scroll position and unfinished reference-pitch input.
* Let the keyboard take the tabs' space during editing so Apply remains reachable in landscape.
* Use Material list rows for expandable settings and appearance groups, with icons, current values and announced expanded state.
* Add dropdown icons and expanded-state announcements to tuning and trainer note selectors.
* Point runtime policies and release workflow references at the renamed Intoniva repository. The former Pages policy URL returned 404; the canonical page returned 200.

No audio or song-analysis algorithm changes. Existing Android 8+ support, package, signing identities, stored preferences and distribution-channel donation separation remain unchanged.

## Verification

Both debug and release JVM suites passed 397 cases, with four optional corpus evaluations skipped in each. Debug/release lint has no errors and retains 21 existing warnings per variant. The final full local build passed in 9 minutes 10 seconds. Exact unsigned release identity, permissions, native/DEX and R8 checks passed. All 17 release-policy self-checks passed; hosted-source and signed-artifact verification remain separate publication gates.

The two original regressions failed on physical Huawei Android 10 with QA49. The first QA50 iteration passed 18 selected native tests in 46.207 seconds. An additional landscape-keyboard regression then exposed an unreachable Apply action. Tabs now yield their space during keyboard input; the final physical suite passed all 19 tests in 46.04 seconds. The tests cover pinned tabs at 150% text size, retained draft/scroll state, custom-surface tab contrast, menu state, appearance and real-window/keyboard flows. No emulator or host audio was used. Fresh screenshots confirmed light and dark settings and appearance layout. The final shared-handset window ended at 09:55:46 UTC on 1 November, before its eight-minute limit; production data and global settings were not changed.

Independent read-only review caught and prevented a custom-colour indicator contrast regression. After two blocked attempts, Opus 5.5 completed a no-tools xhigh review. It recommended clearer inactive labels and identified the landscape-keyboard risk independently. The lead checked its claims against the theme safeguards, callers and physical tests before accepting changes.

Other Android versions and a separate physical tablet were unavailable. No fresh live-guitar or audible-metronome test was performed for this UI-only change.

## Delivery

Release destinations: [GitHub v0.3.0-alpha.47](https://github.com/Majkey25/Intoniva/releases/tag/v0.3.0-alpha.47) and the existing [Google Play app](https://play.google.com/store/apps/details?id=com.tuneitall.tuner). Upload, approval and actual availability are separate gates; this source-verification record does not establish their completion.
