# TuneItAll auto-scroll and store showcase design

Date: 2026-08-24
Status: Approved by the user's explicit implementation and artwork direction

## Goal

Add the proven classic ScrollIt behavior to TuneItAll so a musician can keep
both hands on the instrument while chords or any other Android app scrolls.
Do not add Shizuku, ads, accounts, analytics, or network access. Replace the
raw Play screenshots with polished English phone-frame artwork captured from
the real emulator build.

This specification extends the musician toolkit design. It supersedes only
its `RECORD_AUDIO`-only permission rule; the user explicitly requested the
system-wide auto-scroll feature.

## Chosen approach

Three approaches were evaluated:

1. Native Compose scrolling only. This is smallest but cannot scroll another
   app, so it does not meet the request.
2. Full ScrollIt including Shizuku. This works broadly but adds an external
   dependency and was explicitly rejected.
3. Accessibility gesture scrolling with a floating overlay. This is the
   classic ScrollIt mode, works across Android apps, and needs no Shizuku.

Use approach 3. Reuse ScrollIt's tested speed curve and gesture batching.
Remove its Shizuku code, provider, queries, and mode selector.

## Entry and setup screen

Keep the five-item bottom navigation. Add a labeled `Auto-scroll` action to
the Chords header because chord reading is the primary use case. It opens a
secondary Auto-scroll screen; Back returns to Chords.

The screen contains:

- one plain-language explanation: scroll chords or any app hands-free;
- current Overlay and Accessibility permission status;
- one button for each missing Android permission;
- speed level 1 through 30, default 15, with minus, plus, and slider controls;
- one primary `Show floating controls` action.

No advanced gesture settings are exposed. Keep ScrollIt's proven distance,
interval, and duration defaults in validated code.

## Floating controls

Run a `specialUse` foreground service only after the user presses the action
while TuneItAll is visible. Show a compact movable overlay with:

- `Start` / `Stop`;
- speed minus, numeric level, and speed plus;
- `Hide` to collapse to a 48 dp edge bubble;
- `Close` to stop scrolling and remove the service notification.

Use TuneItAll black, warm-white, and `#63D17A` styling. Touch targets are at
least 48 dp. Start/Stop and permission status have screen-reader labels.

## Accessibility and privacy

The accessibility service performs swipe gestures only. It must not read,
store, inspect, or transmit screen content. Configure:

- `canPerformGestures=true`;
- `canRetrieveWindowContent=false`;
- no Shizuku, INTERNET, advertising ID, analytics, or data collection.

The setup screen and accessibility-service description state exactly why the
permission is needed. Update the public privacy policy and Play documentation
before the next store submission.

## State and lifecycle

Persist only the bounded speed level. Stop gestures when the overlay service
exits or the accessibility service is interrupted. Reconnect cleanly after
Android recreates either service. The tuner microphone stops when the user
navigates away from Tuner, as it does for other secondary screens.

## Verification

Write failing tests first for:

- speed clamp, step, and endpoint mapping;
- gesture profile bounds and batch timing;
- parent navigation from Auto-scroll to Chords;
- invalid stored speed fallback;
- accessibility metadata that forbids window-content retrieval.

Then verify on the dedicated TuneItAll emulator:

1. permissions missing -> clear setup guidance, no service start;
2. permissions enabled -> overlay appears;
3. Start moves a long Chords screen and Stop freezes it;
4. the same overlay scrolls a second Android app;
5. speed 1 and 30 remain responsive;
6. Hide, restore, Close, process recreation, and Back work;
7. no crash, ANR, INTERNET permission, or unintended accessibility data use.

## Store showcase

Create eight 1080 x 1920 English Play images from fresh emulator captures:

1. Precise guitar tuner
2. Tunings and guitar layouts
3. Chromatic tuning and calibration
4. Mechanical metronome
5. Playable chord library
6. Song chords and transpose
7. Ear trainer
8. Auto-scroll anywhere

Each image preserves the exact app screenshot inside a realistic black phone
frame on a restrained TuneItAll background. Add one short English headline
and one factual subline. Do not redraw, distort, or invent UI. Replace the
Fastlane phone screenshots and reuse the same files in the README showcase.

## Out of scope

- Shizuku or root integration
- web-page or copyrighted chord content
- recording screen content
- remote control, automation rules, or per-app profiles
- a sixth bottom-navigation destination
