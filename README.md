![TuneItAll banner](assets/tuneitall-banner.png)

# TuneItAll

[![Android CI](https://github.com/Majkey25/TuneItAll/actions/workflows/android.yml/badge.svg)](https://github.com/Majkey25/TuneItAll/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/Majkey25/TuneItAll?include_prereleases&sort=semver)](https://github.com/Majkey25/TuneItAll/releases)
[![Downloads](https://img.shields.io/github/downloads/Majkey25/TuneItAll/total)](https://github.com/Majkey25/TuneItAll/releases)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/Majkey25/TuneItAll/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-native-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-proprietary-lightgrey)](LICENSE)

TuneItAll is a fast, offline Android tuner for guitar, bass, ukulele, and
chromatic use. It opens directly on the tuner surface. No account, ads,
analytics, tracking, onboarding, or network permission.

Status: public testing release `0.1.0-alpha.1`. TuneItAll is not published on
Google Play yet.

## Download

[![Download TuneItAll APK](https://img.shields.io/badge/Download-TuneItAll_APK-111111?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Majkey25/TuneItAll/releases/download/v0.1.0-alpha.1/TuneItAll-v0.1.0-alpha.1-debug.apk)

The current APK supports Android 8.0 and newer. It is a debug-signed testing
build distributed through GitHub, so Android may ask for permission to install
an app from this source. The APK and its SHA-256 checksum are also available on
the [release page](https://github.com/Majkey25/TuneItAll/releases/tag/v0.1.0-alpha.1).

## Features

- Auto, Manual, and Chromatic modes.
- Live frequency, signed cents, flat/sharp direction, and a −50…+50 cent rail.
- Guitar: 6, 7, 8, and 9 strings with inline and split headstocks.
- Four-string bass and ukulele.
- 38 built-in tunings, including lowered standard, Drop D through Drop F,
  DADGAD, open tunings, extended-range guitar, bass, and ukulele presets.
- Searchable library, favorites, last-used state, and up to 100 custom tunings.
- Adjustable A4 reference from 410.0 to 480.0 Hz in 0.1 Hz steps; 440.0 Hz is
  the safe default.
- Adjustable microphone sensitivity from 0 to 100; 50 exactly preserves the
  tested default detector gates.
- Tap any string to switch to Manual mode and hear its generated reference tone.
- One confirmation chime after the note stays in tune for 250 ms. It respects
  Silent/DND, is excluded from pitch detection, and never creates a notification
  or popup.
- Sharps or flats notation and generated reference audio with no bundled samples.
- Data-driven Compose headstocks. Peg count and note order come from the same
  typed tuning model used by the tuner engine.

## Screenshots

| Preset tuner | Chromatic tuner |
| --- | --- |
| ![TuneItAll preset tuner](fastlane/metadata/android/en-US/images/phoneScreenshots/1_tuner.png) | ![TuneItAll chromatic tuner](fastlane/metadata/android/en-US/images/phoneScreenshots/4_chromatic.png) |

## Architecture

One native Kotlin application module:

- `AudioRecord` mono PCM16 input with unprocessed/voice-recognition selection.
- Clean-room YIN pitch detection with confidence and RMS rejection.
- Equal-temperament note math, median stabilization, and target hysteresis.
- Harmonic-rich one-shot reference tones with click-free switching.
- Notification-sonification confirmation audio with a bounded microphone input gate.
- Jetpack Compose UI with one immutable tuner state.
- Bounded `SharedPreferences` storage and one validated JSON custom-tuning array.

The manifest requests only `android.permission.RECORD_AUDIO`. Microphone samples
are processed transiently in memory on-device and are never recorded, retained,
shared, or transmitted.

## Build

Requirements:

- JDK 17
- Android SDK 36
- A connected Android 8.0+ device or emulator for live QA

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
.\tools\build.ps1 -AllowUnsigned
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

Wireless ADB install:

```powershell
adb devices -l
adb -s '<wireless-device-serial>' install -r app\build\outputs\apk\debug\app-debug.apk
adb -s '<wireless-device-serial>' shell am start -n com.tuneitall.tuner/.MainActivity
```

Release bundle generation:

```powershell
.\gradlew.bat :app:bundleRelease --no-daemon
```

The generated bundle is unsigned for production until a private Play App
Signing/upload-key configuration is supplied outside the repository.

## CI and releases

GitHub Actions runs unit tests, Android Lint, debug assembly, release bundle
assembly, package/permission verification, and SHA-256 generation. Actions are
pinned to immutable commit SHAs.

Pre-release tags such as `v0.1.0-alpha.1` build a verified, debug-signed APK and
publish it with a SHA-256 checksum. A reviewed stable `vX.Y.Z` tag triggers the
signed release workflow only when all four keystore secrets are configured. It
creates a draft GitHub release; Play upload remains a deliberate manual step.
See [release process](docs/releasing.md), [architecture](docs/architecture.md),
and [security policy](SECURITY.md).

## Store assets and release documents

- Feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png`
- Play icon: `fastlane/metadata/android/en-US/images/icon.png`
- English/Czech listings: `docs/store/`
- Privacy policies: `docs/privacy/`
- Data Safety record and release checklist: `docs/store/`
- Deterministic SVG sources: `assets/source/`

## Licence

Copyright © 2026 TuneItAll. All rights reserved. This repository is proprietary;
see [LICENSE](LICENSE). Third-party components retain their own licences; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
