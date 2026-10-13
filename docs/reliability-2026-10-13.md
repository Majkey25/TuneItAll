# Reliability review, 13 October 2026

Alpha 38 candidate (`0.3.0-alpha.38`, code 41), not yet published at this review
checkpoint. The public GitHub release was alpha 37 at the start of the review.
No signing credentials or release protections changed.

## Accepted fixes

- Constant microphone PCM no longer becomes a confident 345 Hz pitch through
  floating-point filter error. Variable PCM keeps the existing processing path.
- Pitch-search bounds follow A4 calibration. Custom notes outside the physical
  search range cannot construct an inverted range; the fallback prevents a crash,
  not detection of unsupported frequencies.
- Confirmation requires fresh measurements. Callback stalls reset pending
  confirmation, and brief missing readings no longer count as time in tune.
- Stable, independently observed, unambiguous chord voicings can retain a weak
  defining seventh. Ambiguous sets such as Am7/C6 retain the original support gates.
- Song-note extraction retains a sharp C8 and rejects the tested octave-down
  substitutions from notes above the selected instrument range.
- Tempo requires two attack pairs supporting the proposed period, rather than
  three unrelated attacks.
- Metronome playback-head queries run outside player/control and renderer locks.
- Custom tunings cannot override built-in IDs. Restored favourites discard missing
  IDs in UI state without rewriting raw preferences; adding a favourite remains safe.
- Ear-quiz distractors exclude both nominally equivalent chords and chords matching
  the actually played voicing. Random order and four distinct choices remain.
- Barre diagrams group strings by the actual finger, not just a shared fret.
- Metronome step controls remove excessive padding and can grow vertically with
  enlarged text. The language picker scrolls in landscape. Colour-picker handles
  use contrast-aware theme colours.
- The optional recorded-electric-guitar benchmark now rejects regressions against
  a measured baseline instead of only printing detection scores.
- The optional twelve-track performed-chord benchmark now guards measured root,
  quality and coverage baselines. The thresholds reject regressions, not establish
  an acceptable accuracy standard for arbitrary songs.
- Auto-scroll checks its overlay permission before every gesture batch and stops
  when permission is missing. An already dispatched batch can still finish within
  its two-second bound. The normal-Activity device acceptance below verifies this
  guard independently of the instrumentation service-connection limitation.
- README download links now point to the verified existing alpha 37 release.

## Review and evidence

18 Codex reviewers used the requested Astra/6.1 mix at xhigh. Independent final
reviews challenged the first chord and trainer proposals; the resulting corrections
were checked against existing regressions rather than accepted by model vote.
Claude Code Haiku 5.5 completed an xhigh review. Opus 5.5 hit the account session
limit and did not complete this review. Antigravity Gemini 3.8 Flash completed the
bounded tuner review at its supported high effort, explicitly approved by the owner.
External reviews were read-only; their suggestions were independently evaluated.

The owner subsequently requested 100 additional Gemini 3.8 Flash high audits.
The queue contains 20 bounded source areas with five different review questions
each, at most six concurrent CLI processes. Four reviews returned complete answers;
three stayed entirely tool-free, and one attempted only a failed read of a missing
snapshot path. All four reported no proven local defect, not universal correctness.
Twenty-eight recorded attempts then failed on the provider's individual quota.
The controller and its remaining four processes were stopped; no other project's
processes were stopped. The resumer now stops launching work on the first quota
response and checks source hashes before reusing accepted results.

At 12:00 UTC, `agy -p /usage` reported 0% five-hour Gemini capacity, 81% weekly
capacity and a five-hour reset at 14:06:54 UTC on 13 October. These are runtime
account figures, not the earlier screenshot. The queue is incomplete; 97 jobs
remain under the original strict no-tool acceptance rule. A heartbeat will resume
after 14:10 UTC when capacity is available, without substituting another model,
using the phone or publishing changes.

The central pre-fix JVM run reproduced 18 failures. Subsequent focused runs
reproduced the brief-dropout and actual-voicing quiz problems before their fixes.
The first combined build exposed a chord regression and four timing-sensitive
metronome test failures. The chord regression was corrected without weakening its
test; all 20 metronome tests passed on a serial rerun.

The recorded EGSet12 benchmark covers three frozen tracks, 93 labelled windows,
and gains 1, 0.01 and 0.001. Each gain produced 93 voiced windows, 91 nominal-note
matches within 50 cents, and one octave error. These are recording identity checks,
not proof of microphone acquisition or cent accuracy on an unplugged instrument.

The twelve-track performed GuitarSet benchmark measured 64.5% root agreement,
57.1% supported-quality agreement and 95.6% coverage. It remains diagnostic.
Universal song transcription is not established by these results.

Huawei YAL-L21, Android 10: the first targeted run passed 20 of 22 cases, including
five preference-restoration tests, all three quiet-DSP tests, eleven native
metronome tests and the colour-picker contrast test. A second run passed all twelve
trainer UI tests and three song-feature tests. The two failing UI checks prompted
a flexible step-button height and correction of the test's sensor-orientation
restoration assumption. The final UI recheck passed all three cases, bringing
these 37 distinct scenarios to passing results. It checks actual glyph line and
visible-node bounds at 2.6x text size: Compose's semantics reconstruction reports
an incorrect width-overflow flag for the measured 97 px glyph in a 144 px paragraph.
The installed QA APK was pulled back and matched the built APK's SHA-256.

The final serial JVM run passed 398 tests with no failures, errors or skips.
Lint completed with zero errors and 24 warnings. QA and instrumentation APKs built.
The follow-up serial run also passed all 398 tests, lint and both QA APK builds,
including the auto-scroll permission guard and removal of temporary test logging.

Auto-scroll revocation acceptance was attempted twice. First the QA accessibility
service was disabled; then only that service was temporarily enabled through
system UI. Huawei reported it as enabled but stuck in Binding with no bound service,
and the test skipped before revoking the overlay app-op. Skips are not passes.
The QA accessibility setting was restored to its original Off state and verified;
only the QA package was stopped, then Home was sent. No production app data or
other accessibility services were changed.

On this device, quiet synthetic decays retained 46–47 of 47 checked windows per
string. DSP P95 was 2.7–6.4 ms; 1–4 kHz checks were 5.7–9.5 ms, below the
42.67 ms audio-hop budget. Thirty-minute note-state decoding took 613 ms in ANY
and 311 ms in VIOLIN. These are single-device, synthetic-input measurements.
No desktop or Focusrite audio was played. No live guitar was available for this run.

## Final candidate physical acceptance

The complete 191-test instrumentation suite ran on the connected Huawei without an
emulator: 187 passed, no failures, four skips. The skipped private dense-song case
requires its missing source file. Three Accessibility gesture cases could not bind
the QA service under the test runner; their skips are not counted as passes.
The core block passed 78 cases, the UI/store-capture block 52, the main application
flows 55, and the non-gesture auto-scroll block two.

At 13:38–13:40 UTC, the exact private dense-song file was recovered and the skipped
case rerun on the Huawei. It passed in 27.039 seconds total, asserting coverage
of at least 85% and analysis within 30 seconds. This adds one passing distinct
case, not a correctness score for the chords. Only the temporary device copies
were removed; QA was stopped, Home sent and all ADB released early at 13:40 UTC.

A local-only normal QA Activity then used the actual production gesture service,
without UiAutomation while it was active. It verified movement at speeds 1, 15
and 30, stable Stop, rapid restart and long-press-sensitive rows at speeds 1 and 15
without an accidental long press. A separate revocation run removed only the QA
overlay app-op. The service stopped and measured scroll position remained 492 px
after both settling periods. The first controller attempt missed its 15-second
revocation checkpoint; the bounded repeat revoked at a fresh checkpoint and passed.
The original overlay app-op mode was restored to default and verified.

The temporary probe APK was replaced by the clean candidate QA APK; the package
scan confirmed the probe Activity was absent. Original QA preferences were restored.
Final Accessibility Enabled/Bound/Binding sets were empty, QA was stopped and Home
was sent. The last ADB checks finished at 13:21:52 UTC; the explicit release message
was sent at 13:22:06 UTC, with no ADB command after the 13:22 boundary. Production
data, accounts and global services were untouched. Sixteen English/Czech synthetic
store captures used production composables and were pulled from the phone.

[PR 33](https://github.com/Majkey25/TuneItAll/pull/33) required
[quality CI](https://github.com/Majkey25/TuneItAll/actions/runs/38398026853)
passed on the candidate head. The complete local unsigned release gate also passed.
The optional emulator-only song-decoder workflow was temporarily disabled for this
release; required CI and protections remained intact. It must be restored after
the delivery PRs close.

At 13:06–13:10 UTC, the own Play Console tab still showed version 40 in production
review, managed publishing off and a usable Create new release action. It also
reported a nonblocking DEX obfuscation warning with a February 2027 deadline.

The subsequent [alpha 38 delivery record](store/2026-10-13-alpha38-release.md)
contains the merged-source CI, published GitHub assets and actual Play submission
state. Those later delivery facts do not change the benchmark accuracy limits.

## Remaining limits

- A single visible high harmonic can remain ambiguous with a missing fundamental.
  Removing that support blindly would break the existing missing-fundamental case.
- Two providers that never return from IPC can occupy both import workers.
  Replacing threads would leak resources; a complete fix needs reclaimable process
  isolation, not another timeout wrapper.
- Native overlay touch cancellation remains unverified. The service-level overlay
  permission revocation guard passed the normal-Activity acceptance above.
- Release code shrinking remains disabled; Play's DEX optimisation warning needs a
  separate shrunk-build device acceptance before the February 2027 deadline.
- Allocation and same-root confidence scans have optimisation opportunities, but
  no new missed audio deadline or user-visible slowdown was demonstrated.
