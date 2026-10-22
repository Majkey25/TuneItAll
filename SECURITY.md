# Security policy

The latest Intoniva `0.3.x` prerelease is the only supported line.

Do not disclose a vulnerability in a public issue. After the repository is
published, use its private GitHub Security Advisory form. Include the affected
version, Android version, reproduction steps, impact, and the smallest useful
proof. Do not include credentials, signing keys, private recordings, or other
people's data.

There is no bug-bounty program and no guaranteed response time. Confirmed
reports will be assessed, fixed in a supported release when appropriate, and
credited if the reporter requests it and disclosure is safe.

## Release credentials

Signed release workflows build without signing secrets and use a separate runner
for signing. That runner executes only verifier/signing logic from `main`, never
candidate Gradle or build scripts. It checks the tag, immutable source SHA,
unsigned digests, exact artifact identity and every bundle payload signer.
Separate verification jobs check Accessibility declarations and resource variants
in both the AAB and the independently built preview APK before and after signing.
Publication checks the final signed digests again. Signing changes archive bytes;
`provenance.json` records both unsigned and signed SHA-256 values.

Before enabling this workflow, the repository owner must:

- Protect `main`: enforce strict `Test, lint, and build` checks and disallow
  force pushes, deletion and admin bypass. No independent PR approval is required.
- Restrict creation, updates and deletion of `v*` tags to the repository owner/admin
  with a tag ruleset. Unmerged tag workflows must not reach signing environments.
- Create `play-signing`, `preview-signing` and `release-publication` environments
  with bypass disabled. Restrict deployment refs to the intended release tags
  (and `main` for Play). The owner authorizes releases without another reviewer.
- Put the four `TUNEITALL_KEYSTORE_*` / `TUNEITALL_KEY_*` Play credentials only
  in `play-signing`; put `TUNEITALL_PREVIEW_KEYSTORE_BASE64` only in
  `preview-signing`. Remove their repository/organization-scoped copies after
  confirming the environment copies. Never commit or print key material.
- Check the immutable SHA and unsigned artifact digests before signing;
  check the signed provenance before publication.

Workflow checks fail closed when required main protection or deployment refs are missing.
Source changes alone cannot isolate repository-scoped secrets: another workflow
on a writable ref could still request those legacy credentials. Rotate an upload
or preview key if there is evidence it was exposed; do not discard a working key
without a verified replacement/Play upload-key reset.

## Song import resource limits

User-selected songs are staged in private cache before codec access. Metadata and
descriptor opening use cancellation signals. The app has two import worker slots,
one queued handoff, a five-minute total deadline, a five-second decode-output
stall limit, 512 MiB source/encoded-byte limits, 500,000 encoded packets, 1 MiB PCM
buffers and 192 million decoded channel samples. Normal 30-minute 48 kHz
PCM16 mono/stereo songs remain within the byte/sample-work limits; unusually large files
can hit an independent byte/work limit. Each expensive analysis stage polls the
same deadline. Readiness polling makes ordinary pipe reads cancellable; finite
asset offsets/lengths are preserved and early EOF is rejected. Staging files are
deleted on failure, cancellation and song removal;
leftovers from a terminated process are removed on the next decoder initialization.

A faulty provider can ignore Android cancellation. Its worker retains its slot
until it really exits; UI cancellation does not wait for that provider, and new
imports cannot create an unbounded set of blocked workers. Android/platform native
code is outside the app's interruptibility guarantee.
