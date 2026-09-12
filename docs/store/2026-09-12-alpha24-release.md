# Alpha24 release evidence

Version `0.3.0-alpha.24`, code `27`, package `com.tuneitall.tuner`.

## Source and checks

[PR #13](https://github.com/Majkey25/TuneItAll/pull/13) merged
`bca3deea7f9c80724228cb2f0c9045e300de927f`. The release tag
`v0.3.0-alpha.24` points to merge commit
`5e54c0c18a4b2a2ceef70405a3355b3f5831289b`; its tree matches the reviewed branch.

The [feedback report](../confirmation-feedback-2026-09-12.md) describes the
false subharmonic, rejected filter variants, activation safeguards, and limits.
No song-analysis experiment, dependency, permission, or microphone-source
change is included.

Local checks passed **302/302 tests**, zero skips/failures, lint, APK/AAB
builds, and manifest/version checks. Huawei YAL-L21 / Android 10 passed all
**104/104 integration tests on the final code 27 QA APK** in 108.321 seconds,
plus eight focused audio tests. The installed APK hash matched the local
build. Device windows were released at 14:34 and 14:46 CEST; only QA was
installed, temporary song copies were removed, and original data was kept.

The six-string echo mixture kept 7/7 fresh estimates per string within three
cents; complete audio-processing P95 was 3.46875 ms against a 42.7 ms hop.
These are controlled PCM and device execution checks, not a recording of
the user's unplugged guitar through the Huawei microphone. The hardest
quiet-decay target and arbitrary-song chord accuracy remain unresolved.

## Verified artifacts

The [GitHub prerelease](https://github.com/Majkey25/TuneItAll/releases/tag/v0.3.0-alpha.24)
is public. Downloaded APK bytes match its companion SHA-256 file and GitHub's
asset digest. The APK is debug-signed for direct testing; it cannot replace
a Play installation signed with a different certificate.

- APK SHA-256: `8e6a2e5856903821432786bcc084b9e53295b7c9cdd03c695a9808b942c028a1`.
- Signed AAB SHA-256: `b2c957aaabbe657f4c43476ddec316cebc554a77faf8639ee5e6d6f095ef87ec`.
- Preview certificate: `768843c2e67e38838c8bd7751443a4cfa5b21dbfe6db077f93677100dbcccda6`.
- Upload certificate: `DE46935ECA9035EEDA463E1E68FA5881396282D3E1F38546A41A352B5C3ED096`.

Apksigner and jarsigner verified signatures; keytool verified the pinned
upload certificate. Jarsigner reported the existing self-signed-certificate
and JarInputStream archive-order warnings. The signed archive was not modified.
Bundletool validation passed. Actual manifests confirm code 27, API 26+,
target 36, unchanged application ID, and no Internet or advertising-ID
permission. The AAB contains no experimental ONNX model, private song,
test-only D3 fixture, or upload keystore.

Passed workflows:
[PR CI](https://github.com/Majkey25/TuneItAll/actions/runs/34694340213),
[main CI](https://github.com/Majkey25/TuneItAll/actions/runs/34694671157),
[Pages](https://github.com/Majkey25/TuneItAll/actions/runs/34694670532),
[preview APK](https://github.com/Majkey25/TuneItAll/actions/runs/34694674552),
[signed Play bundle](https://github.com/Majkey25/TuneItAll/actions/runs/34694674454).

## Google Play

Before preparing this update, code 26 / alpha23 was confirmed available to
the selected testers on the existing Alpha track. Alpha24 uses only the
verified signed CI bundle above.

At 15:02 CEST on 12 September 2026, Publishing overview confirmed
**Probíhá kontrola změn** for exactly one change: code 27 / alpha24 on Alpha,
with full rollout to the existing test group. Quick checks were running.
Submission is confirmed; approval and tester availability remain pending.

Managed publishing remains off. Testers, 177 countries/regions, pricing and
store artwork were unchanged. English/Czech notes match the committed files.
Play reports zero loss of supported devices. Its only two warnings concern
optional deobfuscation files and native debug symbols. No new agreement or
security-sensitive permission was accepted during submission.

Before preparing alpha25, the Alpha track confirmed code 27 / alpha24
available to the selected testers. Play displayed publication on
12 September at 15:24, superseding the earlier pending status above.
