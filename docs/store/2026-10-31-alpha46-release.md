# Intoniva 0.3.0-alpha.46 (49)

## Changes

* Replace Activity's edge-to-edge helper with one decor-fitting configuration and direct, theme-aware system-bar icon appearance.
* Use SDK-qualified window themes. Android 8+ keeps transparent system bars; Android 9/10 keeps short-edge cutouts; Android 11+ uses the always cutout mode. Android 15+ inherits a clean base without legacy color or status-bar contrast attributes.
* Remove the unused graphics-path JNI bridge and binary from optimized Play builds only. R8's retention report traced both remaining native classes solely to the vendor keep rule, not to an app feature.
* Reject a final release bundle if native libraries, graphics-path bridge references or deprecated window color setters return. Debug, QA and GitHub APKs retain their normal native dependencies.

No tuner, song-analysis or audio-playback algorithm changes. Package name, minimum Android 8.0 support, user data, donation-channel separation and signing identities are unchanged.

## Verification

Nine tests passed on the physical Huawei YAL-L21 running Android 10 in 14.895 seconds with QA49 installed. They cover the real window's transparent bars and cutout mode, light/dark system icons, five keyboard flows and favorites/selection. No emulator or host audio was used. The exclusive device window was released early after testing.

The final49 build passed debug/release JVM tests, debug/release lint and debug/release APK/AAB assembly in 4 minutes 33 seconds. Each JVM suite has 397 passing cases and four skipped optional corpus evaluations. Both lint reports have zero errors and 21 existing warnings. Ten bundle-signature tests, 17 release-policy checks and the full adversarial artifact suite passed, including the native-library and both DEX rejection fixtures.

The previous48 bundle failed the new native-library guard. Final49 passes unsigned identity, manifest, optimization and artifact checks. R8 mapping has no graphics-path class. The compiled resource table preserves explicit theme parents and the intended API28/30/35 cutout/contrast values.

A separate minified49 APK with an isolated package and no native libraries ran on the Huawei. Tuner, metronome, chord grid/diagram, trainer, auto-scroll entry, settings, appearance and light/dark icon contrast rendered correctly. BPM editing with the real keyboard saved137. Cold launch took343ms; a later memory snapshot measured48,411KiB PSS. These are individual measurements, not a performance benchmark. The captured process log has no fatal exception or native-link/class-loading error. Only the disposable test package was removed; production data was untouched and the device was released early at12:57UTC.

Android API26/28/30/35+ behavior was checked in source and compiled resources, not on physical devices running those versions. No fresh live-guitar or audible metronome evaluation was performed for this window/packaging-only change.

Independent review identified the need to preserve API28/29 landscape cutouts and explicit theme parents, and to retain nondeprecated navigation contrast control. Those requirements are included in the qualified themes.

## Delivery

Release destinations: [GitHub tag v0.3.0-alpha.46](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.46) and the existing [Google Play app](https://play.google.com/store/apps/details?id=com.tuneitall.tuner). Signing, upload, review and production availability are separate gates; this source-verification record does not establish their completion.

## References

* [Window system-bar APIs](https://developer.android.com/reference/android/view/Window).
* [AGP consumer-rule filtering](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/ConsumerKeepRules).
* [Native packaging options](https://developer.android.com/reference/tools/gradle-api/8.12/com/android/build/api/variant/JniLibsPackaging).
