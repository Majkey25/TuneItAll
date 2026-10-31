# Intoniva 0.3.0-alpha.47 (50)

## Changes

* Keep settings tabs visible while their content scrolls. Each section retains its own scroll position and unfinished reference-pitch input.
* Use Material list rows for expandable settings and appearance groups, with icons, current values and announced expanded state.
* Add dropdown icons and expanded-state announcements to tuning and trainer note selectors.

No audio or song-analysis algorithm changes. Existing Android 8+ support, package, signing identities, stored preferences and distribution-channel donation separation remain unchanged.

## Verification

Both debug and release JVM suites passed 397 cases, with four optional corpus evaluations skipped in each. Debug/release lint has no errors. Full local assembly and lint passed in 3 minutes 25 seconds; the formatting-only rebuild passed in 3 minutes 28 seconds. Unsigned release identity and native/DEX checks passed.

The two original regressions failed on physical Huawei Android 10 with QA49. With QA50 installed, all 18 selected native tests passed in 46.207 seconds. They cover pinned settings tabs at 150% text size, retained draft/scroll state, custom-surface tab contrast, menu state, all nine appearance tests and all six real-window/keyboard tests. No emulator or host audio was used. A subsequent screenshot-only window confirmed light and dark settings and appearance layout. The shared handset was released after each bounded run; production data and global settings were not changed.

Independent read-only review caught and prevented a custom-colour indicator contrast regression. Opus 5.5 review attempts ended without a final answer, first at its weekly quota and later with a connection error. No Opus approval is claimed.

Other Android versions and a separate physical tablet were unavailable. No fresh live-guitar or audible-metronome test was performed for this UI-only change.

## Delivery

Release destinations: [GitHub v0.3.0-alpha.47](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.47) and the existing [Google Play app](https://play.google.com/store/apps/details?id=com.tuneitall.tuner). Upload, approval and actual availability are separate gates; this source-verification record does not establish their completion.
