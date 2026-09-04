# Professional transcription, instruments, and privacy design

Status: design direction approved in chat on 4 September 2026. This written
review copy must be approved before implementation starts.

## Objective

Intoniva must turn a user-selected local song into a readable chord timeline
that can follow real chord changes as short as 333 ms. The default arrangement
must show how to play the song on a six-string guitar in Standard E. The app
must also expose bass and common orchestral or fretted-string tunings as clear
first-class choices.

The release must remain proprietary, offline, ad-free, and account-free. Its
privacy policy and Google Play declarations must match its real behavior.

Perfect transcription of every mastered recording is not a valid acceptance
claim. The implementation must report measured accuracy against annotated
audio, keep uncertain passages unlabelled, and avoid invented chords.

## Accepted assumptions

- Song import uses Android `OpenDocument` and user-owned local audio only.
- The app does not accept YouTube or remote URLs.
- An acoustic version means a synchronized chord chart, guitar shapes, and an
  optional capo recommendation. The app does not synthesize a new recording.
- Standard E is the default guitar arrangement.
- The publisher is MajkeyLab. The privacy contact is
  `majkeylab@gmail.com`.
- The app is a general-audience music tool and is not directed at children.
- No pretrained model enters the release without verified commercial rights,
  documented model provenance, and a Huawei performance result.

## Existing constraints

`StreamingHarmonicFeatureExtractor` currently averages chord features across
two seconds. `ChordAnalyzer` rejects events shorter than 300 ms and supports a
small chord vocabulary. Those choices stabilize slow material but erase fast
harmonic changes.

The source already contains four four-string bass presets. Five-string bass is
absent, and instrument discovery in the tuning library is too weak.

The public HTML privacy policy is online, but it does not identify MajkeyLab or
the contact email. The Markdown policies omit song-file and Auto-scroll
processing. The in-app policy text covers only microphone use. The Auto-scroll
data disclosure appears after the permission actions instead of before them.

## Architecture

### Song analysis flow

Keep `SongAudioDecoder`, `MediaExtractor`, `MediaCodec`, and the current
streaming PCM boundary. Do not add a network client.

The analysis flow is:

1. Decode mono PCM with the current duration and cancellation limits.
2. Extract 8192-sample harmonic frames with a 4096-sample hop.
3. Apply the existing tuning correction and local spectral standardization.
4. Produce unsmoothed frame chroma plus a three-frame local chroma.
5. Detect harmonic onsets from positive spectral flux.
6. Decode chord states with an onset-aware transition cost.
7. Refine event boundaries to the closest 85 ms frame.
8. Reject low-confidence segments and merge only equal adjacent chords.

The local three-frame chroma drives chord changes. A longer context profile may
break a close quality tie, but it must not move a boundary or replace the local
winner. The decoder lowers its transition penalty at a strong harmonic onset.
It keeps the normal persistence penalty between onsets.

The minimum accepted chord event is 170 ms. This permits three chords per
second while rejecting isolated one-frame guesses.

### Chord vocabulary

Add these common qualities to `ChordQuality`:

- major and minor
- power
- suspended second and suspended fourth
- diminished and augmented
- major sixth and minor sixth
- dominant seventh, major seventh, and minor seventh
- half-diminished seventh
- add ninth and minor add ninth

Each extended quality needs its defining interval in more than one local frame.
The decoder must fall back to the contained triad when the extension is weak.
It must not prefer a larger chord merely because the larger template accepts
more pitch classes.

`Chord` gains an optional bass pitch class for inversions. Transposition moves
both the root and the bass. Equality includes the bass only when the decoder
has enough stable low-frequency evidence.

The bundled guitar and ukulele JSON files already contain the required common
suffixes. `ChordShapeCatalog` must reuse them. It must not introduce a second
shape database or generate speculative fingerings.

### Evaluation

Add a small evaluator that consumes typed reference and estimated events. It
computes:

- root weighted chord symbol recall
- major-minor weighted chord symbol recall
- full supported-vocabulary weighted chord symbol recall
- over-segmentation, under-segmentation, and combined segmentation score
- median boundary error
- labelled coverage

Evaluation uses continuous event overlap rather than checkpoint sampling.
Test resources must have an explicit redistributable license and checksum.

The repository keeps the existing GuitarSet excerpt. Add deterministic
synthetic fixtures for distorted rapid changes, inversions, extensions,
silence, and changing broadband noise. Do not commit commercial recordings.

### Standard E acoustic arrangement

Song analysis produces sounding chords. A separate pure function converts the
timeline into guitar instructions without changing the detected events.

For one whole song, evaluate capo positions 0 through 8. Select the single capo
that yields the lowest total fingering cost across the song. The cost prefers:

- an existing bundled voicing
- fewer barres
- lower base frets
- more open strings
- fewer large position changes between consecutive shapes

The UI shows the sounding chord first. If a capo changes the fingering name, it
also shows the shape name, for example `F#m`, `Em shape`, `capo 2`.

`Exact` keeps every supported quality. `Simplified acoustic` maps unsupported
or difficult extensions to a major, minor, suspended, or power foundation.
The default is `Exact`. A missing reviewed voicing shows the chord name without
a diagram instead of inventing a fingering.

The current-event panel stays fixed above navigation. The timeline follows
playback. Three 333 ms events remain individually selectable.

### Instruments and tunings

Add an instrument filter before the tuning results. Favorites remain the first
filter and the first result group.

Retain the current four-string bass presets and add:

- four-string bass E-flat: `Eb1 Ab1 Db2 Gb2`
- four-string bass C standard: `C1 F1 Bb1 Eb2`
- five-string bass standard: `B0 E1 A1 D2 G2`
- five-string bass Drop A: `A0 E1 A1 D2 G2`
- five-string bass high C: `E1 A1 D2 G2 C3`
- five-string bass half-step down: `Bb0 Eb1 Ab1 Db2 Gb2`
- six-string bass standard: `B0 E1 A1 D2 G2 C3`

Add standard presets for:

- violin: `G3 D4 A4 E5`
- viola: `C3 G3 D4 A4`
- cello: `C2 G2 D3 A3`
- mandolin courses: `G3 D4 A4 E5`

Add `INLINE_5` and `SPLIT_3_2` layouts. The renderer must label five-string
bass from string 5 at `B0` to string 1 at `G2`. Four-string bowed instruments
use an instrument-neutral 2+2 peg layout. They must not display a six-string
guitar image.

The custom tuning editor accepts only string counts supported by the selected
instrument. Chromatic mode remains available for every pitched instrument.

### Privacy and policy compliance

Update these sources together:

- `docs/privacy/index.html`
- `docs/privacy/privacy-policy-en.md`
- `docs/privacy/privacy-policy-cs.md`
- `docs/store/data-safety.md`
- the full in-app privacy text in every shipped locale
- the Auto-scroll disclosure shown before its permission actions

The public and in-app policies identify MajkeyLab and
`majkeylab@gmail.com`. They describe:

- transient microphone processing
- local processing of one user-selected song file
- in-memory chord, note, tempo, and arrangement results
- local settings, favorites, custom tunings, and trainer scores
- the gesture-only Accessibility service and overlay
- the external Buy Me a Coffee link
- optional support correspondence that a user sends outside the app
- local deletion by clearing app data or uninstalling
- no accounts, ads, analytics, profiling, tracking, sale, sharing, or
  maintainer-operated server

The GDPR section states the purpose, local-only processing, retention,
legal basis, recipients, international-transfer status, data-subject rights,
contact route, and right to complain to the applicable supervisory authority.
It must state that MajkeyLab has no server-side app record to access, correct,
export, or delete.

If a user emails support or opens a GitHub issue, MajkeyLab may receive the
address, account name, message, and attachments that the user chooses to send.
The policy limits this processing to support, abuse prevention, and legal
compliance. The default retention limit is 12 months after the request closes,
unless a shorter deletion request or a legal duty applies. The policy names
the email or GitHub provider as an external service and uses legitimate
interests or steps requested by the user as the applicable legal basis.

The United States section states that Intoniva does not sell or share personal
information, use targeted advertising, offer a financial incentive, or
discriminate after a privacy request. It identifies optional support
identifiers and correspondence as the only information MajkeyLab may receive.
It lists a contact method for applicable state rights.

The children section states that the app is not directed to children and does
not collect personal information through the app. A child must not send
personal information through an optional support channel without a parent or
guardian.

Before Android opens Accessibility settings, show a dedicated disclosure. The
disclosure says that Auto-scroll can perform deterministic swipe gestures, does
not retrieve window content, and does not collect or share data. The user must
tap `I understand, continue` before Android settings open. Store the acceptance
locally only.

Keep the Play Data Safety answer `Data collected: No` and `Data shared: No`
only while the merged manifest has no Internet or advertising-ID permission
and the dependency audit finds no transmitting SDK.

This work improves the compliance posture. It is not a legal opinion or a
substitute for counsel in the publisher's jurisdiction.

## Error handling

- Unsupported audio returns the existing typed decode error.
- Cancellation cannot publish results from an earlier analysis generation.
- A low-confidence frame remains `No chord` or `No note`.
- A missing chord shape never blocks the chord timeline.
- A missing Accessibility or overlay permission keeps Auto-scroll stopped.
- A policy or Data Safety claim that differs from the manifest blocks release.

## Commands

Use the repository commands:

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:bundleRelease --console=plain
.\tools\build.ps1 -AllowUnsigned
```

Run physical QA only on:

```text
BQLDU19927002646
```

Use an isolated `.qa` application ID for device tests. Do not replace the
Google Play signature.

## Project structure

- `app/src/main/java/com/tuneitall/tuner/music`: features, chord decoding,
  evaluation, and acoustic arrangement
- `app/src/main/java/com/tuneitall/tuner/model`: instruments and tunings
- `app/src/main/java/com/tuneitall/tuner/ui`: selectors and synchronized output
- `app/src/test`: deterministic DSP, arrangement, catalog, and privacy tests
- `app/src/androidTest`: decoder, Compose, and Huawei acceptance tests
- `docs/privacy`: public policy
- `docs/store`: Play declarations and release checks

## Code style

Keep typed immutable results and bounded arrays. Reuse the existing streaming
decoder and bundled chord data. Do not add a one-implementation interface,
factory, network layer, or database.

```kotlin
data class AcousticChordInstruction(
    val soundingChord: Chord,
    val shapeChord: Chord,
    val capo: Int,
)
```

## Testing strategy

Implement each production behavior with a red-green test cycle. Run focused
tests after each slice and the full gate after the final slice.

Required song-analysis cases:

- three annotated chords inside one second
- clean major, minor, inversion, and each added quality
- distorted power chords with drum transients
- weak extension falling back to its triad
- silence and changing noise returning no chord
- GuitarSet continuous-overlap evaluation
- exact and simplified Standard E arrangements
- deterministic capo choice across a complete progression

Required catalog cases:

- unique IDs and note sequences
- exact string count and layout validation
- correct scientific pitches for every new preset
- string 1 and lowest-string numbering in the UI
- instrument filtering and Favorites ordering

Required privacy cases:

- release manifest has no `INTERNET` or `AD_ID`
- Accessibility metadata keeps `canRetrieveWindowContent=false`
- disclosure appears before the Accessibility settings action
- disclosure requires affirmative action
- public policy contains publisher, contact, song, microphone, Auto-scroll,
  retention, deletion, GDPR, United States, and children sections
- public policy URL returns HTTP 200 after deployment

## Success criteria

- The rapid synthetic progression returns the exact three chords in order.
- Median rapid-change boundary error is at most 100 ms.
- GuitarSet root WCSR is at least 95 percent.
- GuitarSet major-minor WCSR is at least 85 percent.
- GuitarSet supported-vocabulary WCSR is at least 75 percent.
- GuitarSet segmentation score is at least 0.85.
- Changing broadband noise produces zero chord events.
- The existing dense metal acceptance track keeps at least 85 percent labelled
  coverage, with no correctness claim without annotations.
- A 210-second song completes analysis on the Huawei within 30 seconds.
- Standard E produces one capo recommendation and a playable instruction for
  every event that has a reviewed bundled shape.
- All requested instrument presets appear through an obvious instrument
  filter and pass exact-note tests.
- Public, in-app, and Play privacy statements agree with the built artifact.
- Unit tests, Android tests, lint, APK, AAB, and Huawei QA pass before release.

## Boundaries

Always:

- Keep analysis offline and bounded to 30 minutes.
- Preserve cancellation and lifecycle cleanup.
- Use licensed test audio with a recorded checksum.
- Report measured accuracy and uncertain sections truthfully.
- Keep privacy text synchronized with code and Play declarations.

Ask first:

- Add a pretrained model or a new runtime dependency.
- Add any network feature or remote URL import.
- Change the application ID or Play track.

Never:

- Download or commit a commercial song for tests.
- Ship non-commercial model weights in the proprietary app.
- Fill uncertain audio with guessed chords.
- Claim legal certification or universal transcription accuracy.
- Commit secrets, signing keys, user recordings, or temporary QA artifacts.

## References

- MIREX Audio Chord Estimation:
  https://music-ir.org/mirex/wiki/2026%3AAudio_Chord_Estimation
- Essentia chord estimation:
  https://essentia.upf.edu/tutorial_tonal_chords.html
- Spotify Basic Pitch:
  https://github.com/spotify/basic-pitch
- European Commission GDPR principles:
  https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/principles-gdpr_en
- Google Play Data Safety:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play AccessibilityService policy:
  https://support.google.com/googleplay/android-developer/answer/10964491
- California Attorney General CCPA guidance:
  https://oag.ca.gov/privacy/ccpa
- FTC COPPA guidance:
  https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions
- Fender bass manual:
  https://jp-support.fender.com/hc/ja/article_attachments/360020767611
- Yamaha tuner specification:
  https://usa.yamaha.com/files/download/other_assets/0/333430/yt240_en.pdf

## Open questions

None. The assumptions above define the implementation scope. A licensed neural
model remains a separate decision only if the deterministic engine misses the
approved accuracy targets.
