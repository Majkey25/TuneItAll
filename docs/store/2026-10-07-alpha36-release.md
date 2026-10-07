# Alpha 36 appearance release

Version `0.3.0-alpha.36` (39), package `com.tuneitall.tuner`.
[PR #28](https://github.com/Majkey25/TuneItAll/pull/28) merged at
`20cc45dcb80213d8b18cac984c00406824a97ad4`.

## Appearance

Settings → General opens a dedicated Appearance screen. It provides seven
built-in themes and custom palettes, separate light/dark choices, Material You,
font and text-size controls, Material/custom corners, high contrast, black dark
backgrounds, and reset. The back button stays visible during scrolling.

Preferences are stored locally as bounded JSON. Existing System/Light/Dark
preferences are preserved. No new dependencies or permissions were added.
Tuner confirmation remains green. Custom backgrounds determine system-icon
brightness, and transparent tuner content uses background-safe colours.

The interaction follows [T3code Appearance](https://github.com/pingdotgg/t3code/blob/main/docs/user/appearance.md).
The implementation and palettes are original Compose code. Wallpaper colours
require Android 12+. Huawei checks cover Android 10 and the fallback palette.

## Verification

- Local QA/test APK build, Android lint, and all 352 JVM tests passed.
- [Android CI](https://github.com/Majkey25/TuneItAll/actions/runs/37645794013) passed.
- [Silent Android 10 CI](https://github.com/Majkey25/TuneItAll/actions/runs/37645794016) passed 51 tests, no failures or skips.
- Huawei YAL-L21: all seven appearance/nearby-theme checks passed. These cover persistence, corrupt storage, independent palettes, immediate changes, custom input, hue selection on white, fonts, scaling, reset, back navigation, and visible tuner text on an inverted surface.
- Actual light/dark picker and custom-editor screenshots were captured and inspected. The phone was released Home after each reserved window. Production data, T3code settings, global phone settings, and PC audio were untouched.

Requested Claude Opus 5.5/high review could not run because of the weekly limit.
One independent Codex review completed. Its grey-background crash and custom
contrast/system-icon findings were fixed and covered by focused checks.

Two initial device attempts exposed test orchestration/finder issues: the test
started before installation finished, and a text child needed the unmerged tree
plus scrolling before pixel capture. The final installed build passed all checks.

## Artifacts

[Signed Play bundle build](https://github.com/Majkey25/TuneItAll/actions/runs/37645787015) passed.
Bundletool validated the downloaded AAB. `jarsigner` reported `jar verified`
with the usual self-signed certificate, missing timestamp, and archive-order warnings.
Package/version/minimum SDK 26/target SDK 36 and the upload certificate were verified.

AAB SHA-256: `9d8c98712fff0e0e89b85896a222e7b9928d83aba75f837ae851d28fcb2499bc`.
Upload certificate: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

[GitHub release](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.36)
is published. The downloaded testing APK and checksum file match:
`e4a74795cdda135100c216465b9186b3db4c370300c0dcb09e541e68f4f29164`.
`apksigner verify` passed with the existing preview certificate
`768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.

## Google Play

Production release `39 - Themes and custom appearance` was uploaded and submitted
on 7 October 2026 for a 100% rollout in the existing targeted countries.
The publishing overview confirms `Probíhá kontrola změn`. Managed publishing is off,
so publication follows successful quick checks and Google review automatically.
This records submission, not public availability.

Only the existing optional missing deobfuscation/native-symbol warnings appeared.
The default store artwork remains representative because the default appearance
is unchanged. Prices, regions, and privacy disclosures were not edited.
