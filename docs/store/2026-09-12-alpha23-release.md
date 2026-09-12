# Alpha23 release evidence

Version `0.3.0-alpha.23`, version code `26`, package `com.tuneitall.tuner`.
No permission, dependency, input-source, or song-analysis change is included.

## Source and checks

[PR #12](https://github.com/Majkey25/TuneItAll/pull/12) merged the bounded
gap-recovery fix and regression checks. The reviewed implementation is
`2ec09f6`; `9ee31542276bb573eb84e15022fcc0472fb39c51` adds explicit initial-tone
assertions to the safety tests. Production code is identical between them.

Source/tag: `301a88e0e8fc7117b6566017a1c89c1647c98a0a` / `v0.3.0-alpha.23`.
App and workflow source match the reviewed branch. The final
[PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34687261476) passed in
6 minutes 14 seconds, including the initial-tone assertions.

The [recovery report](../quiet-gap-recovery-2026-09-12.md) explains the cause,
48-case comparison, primed-signal controls, and unmet extreme-decay target.
Local checks passed **294/294 tests**, zero failures/skips, lint, all APK builds,
and release-bundle/manifest verification. Existing hash-verified corpus
fixtures were present. The local unsigned AAB is not used for Play.

The separate research worktree has the same three known failures: two rejected
spectral-policy experiments and the hardest decay target. Those tests were
not weakened or introduced as production fallbacks.

## Physical device

Huawei YAL-L21 / Android 10 passed **102/102 instrumentation tests** in
99.228 seconds, with zero failures or skips, plus six focused audio tests.
The existing retune test now checks three signals rather than one:

| Retune | Correct settled windows | P95 processing |
| --- | ---: | ---: |
| E4, seed 81 | 22/23 | 2.54 ms |
| D3, seed 31 | 23/23 | 2.04 ms |
| G3, seed 31 | 21/23 | 1.98 ms |

All remain within the 42.7 ms audio-hop budget. The default guitar-range check
measured 2.57 ms P95; ambiguous-D3 refinement measured 2.08 ms. Real microphone
callbacks averaged 42.77 and 42.76 ms across restarts, with zero bursts. Auto
selected Voice Recognition (`COMPATIBLE`); raw capture was not advertised.
Float capture contained zero sub-PCM16 increments in 49,152 samples. No
microphone audio was saved.

The private song fixture was copied from the existing local file, hash-checked
on-device, and included in the full suite. Its decoder coverage/runtime
checks passed. Only the isolated QA package was installed. Temporary phone
copies were removed afterward; the original local song was preserved.
The phone was released at 11:55 CEST with QA stopped and Home foreground.
Production data, system settings, and viewport were unchanged.

- QA APK SHA-256: `5ffa14086ab11e10fd23a80562687a1455bbc68a4e10a40f7613a63ae9929551`.
- Instrumentation APK SHA-256: `e9b0bc5101ab187c293d12e50277662110ee36ae013cb468a16f76c1310b5492`.

Synthetic PCM checks and microphone cadence do not establish acoustic
unplugged-guitar accuracy. Song chord recognition is unchanged.

## Previous Play release

Before preparing alpha23, code 25 / alpha22 was confirmed **available to
selected testers**. Play records its publication on 12 September at 11:47
CEST. The existing Alpha track remains active in 177 countries/regions.

## Verified public artifacts

The [GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.23)
is public with its APK and checksum. Downloaded APK bytes match the companion
checksum and GitHub asset digest. Apksigner verified the unchanged preview
signer. The public APK is debug-signed for direct testing and cannot replace
a Play installation with a different signer.

- APK SHA-256: `fb66b80cf7ef6e8bed91bb57234a620a83bb3bb367b07e6d15031eb58f8b960d`.
- Signed AAB SHA-256: `65e56dc3461caa2f7cc1e861558af07259c2ef9453c98cc7bc543ac9a22f7d2a`.
- Preview certificate: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Play upload certificate: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

[Main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34687572721),
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34687572250),
[preview build](https://github.com/Majkey25/TuneItAll/actions/runs/34687576769),
and [signed Play build](https://github.com/Majkey25/TuneItAll/actions/runs/34687576642) passed.
Jarsigner verified the AAB with the existing self-signed-certificate and
JarInputStream archive-order warnings; the signer was checked separately.
Bundletool validation passed. Actual artifact manifests confirm code 26,
the unchanged package, API 26+, target 36, and no Internet or advertising-ID
permission. The AAB contains no test-only D3 resource, experimental ONNX model,
or upload keystore. Only this signed CI AAB is used for Play.

## Google Play submission

At the 12:18 CEST verification on 12 September 2026, Publishing overview
confirmed **Probíhá kontrola změn** for exactly one change: code 26 / alpha23
on the existing Alpha track. Quick checks were still running. Submission is
confirmed; approval and tester availability are not yet confirmed.

The rollout uses 100% of the existing Alpha track, with managed publishing
off. English and Czech notes match the committed metadata. Testers, 177
countries/regions, prices, and store artwork were unchanged. Play reports
zero loss of supported devices. The two warnings concern optional
deobfuscation files and native debug symbols.
