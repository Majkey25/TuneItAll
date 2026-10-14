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

At 17:35 UTC, authenticated discovery still listed Gemini 3.8 Flash high and
reported 74% five-hour and 75% weekly Gemini capacity. None of the earlier recorded
worker PIDs was running, and the interrupted controller had no final summary.
The resume dry run verified unchanged input hashes, six accepted tool-free audits
and 94 pending assignments. The same six-worker controller was resumed without
changing its model, account or permissions. These counts are a checkpoint, not
completion of the requested 100 audits.

The additional accepted capture reviews supplied no proven defect. One rejected
tool-using response proposed three callback-lifecycle fixes; source inspection
did not establish the claimed app failures. The app invalidates session/revision
tokens before asynchronous Stop, rejects stale windows, and serializes Stop/Start
on its IO controller. The proposed self-stop ownership release would allow a new
worker while the previous callback still runs. Its multi-hop reproduction also
assumed a 2048-sample read where the actual 1024-window configuration reads 512.
The app's error callback schedules cleanup rather than restarting capture directly.
No speculative audio change was made. At 17:42 UTC, the existing AudioPipelineTest
and DetectionContextTest suites passed all 24 JVM cases, with no failures or skips.
This continuation used no ADB, browser, publication, merge or credential changes.

At 18:01 UTC, 21 audits met the strict tool-free acceptance rule; 79 remained.
Four current responses exceeded the provider's output limit and were not accepted.
Accepted audit 015 claimed an immortal retained pitch after eight repeated weak
220 Hz candidates. Its exact input sequence was run against the actual tracker:
the weak candidate became a fresh 220 Hz reading on gap frame six, and all three
following strong attack frames returned 220 Hz. The proposed dropped attack did
not reproduce. Its calculation omitted the positive-mass observation normalization
and repeated candidate's persistence. The local-only probe and all 21 existing
tracker/gap cases passed. The probe was not added to the permanent test suite;
no tracker change was made from the disproved report.
The follow-up production-source run passed 41 cases: all 21 tracker/gap cases
and 20 tempo cases, including recorded syncopated guitar, stereo anti-phase input,
quiet beats, isolated attacks, common/off-grid tempos and carrier/noise rejection.

Accepted audit 027 proposed clearing already-confirmed tuning after a callback-free
gap. Its initial two-frame example never confirms: the 250 ms callback gap exceeds
the existing 200 ms pending-confirmation limit. A corrected confirmed sequence
would exercise the explicitly tested contract that confirmation stays latched
until observed missing/out-of-tune evidence rearms it. Normal Stop, error and
context changes already reset it, and amplitude-gated windows still publish missing
readings. Changing that contract was not accepted as a proven app bug. Its separate
AudioTrack marker-tail claim needs native playback evidence; the final PCM sample
it identifies is zero. No marker or rearming change was made from this report.
At 18:08 UTC, all 52 existing JVM cases in the confirmation tracker, chime/DSP
pipeline, audio pipeline and chord-emission regression suites passed. This verifies
the latched-confirmation contract and synthetic feedback handling, not native
AudioTrack marker delivery on every device.

At 18:16 UTC, 32 of the 100 assignments had accepted complete tool-free answers.
Seven responses exceeded the native output limit and remain pending, rather than
being counted from partial text. Runtime usage reported 0% five-hour Gemini
capacity, 63% weekly capacity and the next reset at 19:06:54 UTC. The first actual
quota error stopped further launches; five already-started workers were allowed
to finish. The two accepted reports claiming defects were evaluated above; no new
app-source bug was confirmed at this checkpoint. The source-hash check still passed.
The queue, raw attempts and original source-hash ledger remain intact for resume;
this is not a completed 100-audit result or proof of universal accuracy.

The controller finished draining at 18:20 UTC: 35 accepted tool-free audits,
seven output-limit failures, three quota failures and 55 not-yet-attempted
assignments. All 65 non-accepted assignments remain pending for the next resume.
Accepted audit 041 proposed holding the final event beyond its end. The existing
ChordModelsTest explicitly requires a blank result at the final 2500 ms boundary,
while permitting a 500 ms hold only across intermediate gaps. Its proposed removal
would violate that contract, not establish a missing song note. The native marker
question remains unverified; no app source or audit input was changed.
All eight current song-model/chord-model JVM tests passed, including the explicit
final-event clearing rule. The temporary pitch probe files were removed after use;
unfinished audit evidence and queue files were preserved. No merge or publication
was performed by this heartbeat.

At 19:14 UTC, the authenticated model catalogue still included Gemini 3.8 Flash
high. Runtime usage reported 96% five-hour and 59% weekly Gemini capacity, with
the next five-hour reset at 00:06:54 UTC on 14 October. The dry run verified the
original source hashes, 35 accepted audits and 65 pending assignments. The same
read-only six-worker queue was resumed without changing model, account or effort.

Audit 042's initial-note hypothesis reproduced through the production PCM
extractor, not just fabricated feature frames: 13 of 40 bass-intro/violin-melody
variants started A4 at 0 ms even though its first observed salience was zero.
Zero-emission initial note states could win a later path without paying the
no-note transition. The decoder now makes only those unsupported initial states
unreachable. Later transitions, short gaps, thresholds and timing formulas remain
unchanged. The lasting two-rate PCM regression failed before the fix; afterward,
all 40 scratch variants and all 36 focused permanent cases passed, with no skips.
Astra at xhigh independently checked the initial-state and numerical invariants
using supplied evidence only; it ran no tests and made no edits. It found no
counterexample to this narrow fix, not universal correctness of the extractor.

Audits 042, 043 and 045 repeat the already-rejected final-event hold change.
Audit 042's suggested terminal-boundary replacement also conflicts with the
half-overlapping-window contract. Its example's 50 ms terminal frame is shorter
than the extractor's required half-window and the 80 ms minimum note duration.
No end-boundary or display-hold change was accepted.

Before editing either audited melody file, the original source files and complete
hash ledger were copied to the local audit history. Their SHA-256 values match
the original ledger, based on checkout `1a40ddb0f10a0e0c69944ab43a55be43b92ee8f5`.
The currently running controller still uses the complete inline snapshots built
at 19:14 UTC; its reviews are evidence for that original revision, not the new fix.
The next resume must reconcile the two changed melody hashes and re-audit its five
affected assignments while retaining their original results. No hash check was
disabled. This fix is local only; no device test, merge or publication occurred.

At 19:55 UTC, the follow-up complete JVM run passed all 399 cases without failures,
errors or skips. Lint retained zero errors and 24 warnings. Both clean QA and
instrumentation APKs built. The earlier 40-case local-only PCM probe is not included
in those 399 permanent cases.

Audits 057, 058 and 060 identified a separate unsupported native-player call:
timeline chips and the seek slider can become available before asynchronous media
preparation finishes, but `seekTo` checked only for a non-null player. It now uses
the same prepared-state guard as Play and Pause. This ignores premature seeks;
it does not queue them or fake a playback position. Android's
[MediaPlayer state contract](https://developer.android.com/reference/android/media/MediaPlayer)
defines side effects while Preparing as undefined; the reports' guaranteed crash
and permanent-failure claims were not established on a device. The new native
unprepared-player regression compiled but was not executed, because this heartbeat
forbids ADB. Ordinary prepared seeking still uses the unchanged bounded native call.
Audit 058's separate broken-separator claim is disproved by the actual UTF-8 source:
the string contains U+00B7, not U+FFFD. No typography change was made.

The original ChordViewModel and its native test were also preserved before editing.
The active 19:14 controller must finish or drain on quota before reconciliation.
The inspected local `ReconcileVerifiedFixes.ps1` verifies all unchanged ledger
entries and the exact three reviewed old/new input hashes, obtains the controller's
exclusive lock, archives the original five melody and five song-UI assignment
results, then requeues only those ten assignments. It must run once after the
controller ends, followed by the standard resume dry run. The script is prepared,
not yet run at this checkpoint; the original ledger remains unchanged. A future
heartbeat must not start a second controller while the existing run is active.
No original-revision audit is represented as a review of either new source fix.
At the subsequent checkpoint, 51 complete tool-free original-revision audits were
accepted, 49 remained pending, and nine latest attempts had exceeded the provider's
output limit. Tool-using and partial error responses are still not accepted.
Nine of those accepted audits cover the changed melody/song-UI inputs and therefore
must be re-audited; only 42 accepted assignments remain applicable to unchanged
current inputs. The original queue continues with at most six workers.

At 20:09 UTC, authenticated discovery still listed Gemini 3.8 Flash high. Usage
showed 35% five-hour and 44% weekly capacity, with the same 00:06:54 UTC reset.
The original six-worker controller was still active, so no duplicate controller,
resume dry run against changed inputs, or premature hash reconciliation was started.
By 20:11 UTC it had 59 accepted original-revision assignments; 50 remain applicable
to unchanged current inputs after excluding the nine accepted affected assignments.

Audit 068's claimed stranded stop callback was checked against every production
caller. Only TunerViewModel supplies a completion callback, guarded by the current
reference generation; a newer Stop supersedes the old generation. Trainer calls
Stop without waiting on completion. The proposed callback composition would retain
superseded callbacks and build a growing closure chain, without a demonstrated
current caller failure. No reference-player change was made from that hypothesis.
The other newly accepted reference-audio, trainer and diagram reports supplied no
proven local defect. Native output and lifecycle evidence remains device-dependent;
no device or publication action was performed during this continuation.
The final checkpoint for this continuation contains 60 accepted original-revision
audits, 40 pending original assignments and ten latest output-limit failures.
Excluding the nine affected accepted results leaves 51 applicable current-input
audits and 49 assignments needing completion or re-audit. The active controller
and all evidence were preserved for its drain and subsequent reconciliation.

The original-revision controller completed at 20:42 UTC with 75 accepted audits,
not 100. After checking the exact three changed inputs and preserving original
sources, hashes and affected reports, reconciliation requeued the five melody and
five song-UI assignments. The dry run verified every current input hash and counted
66 applicable accepted assignments, with 34 pending. Usage then showed 0% five-hour
capacity, so no more model request was sent that evening. At 08:31 UTC on 14 October,
authenticated discovery and usage confirmed the same requested high model and
100% five-hour/35% weekly capacity. The verified six-worker queue resumed; its
results are separate from the original-revision evidence. The automation remains
active because the 100-audit task is incomplete.

The owner separately authorised deployment. On the Huawei, both lasting native
regressions failed against the verified alpha 38 QA APK: A4 was backdated to 0 ms,
and the unprepared player received a seek call. The initial seek assertion only
checked error state and missed Android's deferred native error, so the regression
now also observes whether the real MediaPlayer receives the forbidden call.
An initial run was interrupted by an unfinished package install and is not counted.
All installs completed before the corrected red run and subsequent candidate tests.

The first alpha 39 native group passed 23 of 24 cases. The unchanged bounded-import
test rejected a concurrent setup request while a previous worker was returning;
the bounded executor intentionally reports DECODE_FAILED on saturation. The isolated
repeat passed, and a fresh repeat of the complete group passed all 24 cases without
skips in 14.108 seconds. This intermittent handoff/setup failure is retained in the
record; no queue limit, worker logic or test assertion was changed to obtain a pass.
Both new regressions passed, as did prepared seeking, mode switching, cancellation,
stereo-independent melody and the selected chord workflows. No microphone PCM or
live guitar was used, and playback in the native workflow checks was muted.

The alpha 39 clean local release gate passed 399 JVM cases, lint with zero errors
and 24 warnings, all APK/AAB builds, ten signature-regression checks and 17 policy
checks. The identity verifier rejected wrong versions, digests, substituted QA APKs
and unapproved Accessibility capabilities. These are not claims of universal song
accuracy. The later [alpha 39 delivery record](store/2026-10-14-alpha39-release.md)
tracks its actual publication state separately from the unfinished audit queue.

The newly accepted UI and overlay audit hypotheses are not included as fixes in
this narrow song-analysis release. The settings-return microphone claim is disproved
by TunerViewModel.onStart, which already rechecks permission. Unison-string highlight,
permanent-denial restoration, BPM gesture/draft behavior and shortened Accessibility
component handling need their own focused verification. Adding an inter-batch delay
would also change the physically measured scroll cadence; it was not accepted from
the reports' unsupported claim that every zero-delay continuation is a runaway loop.

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
