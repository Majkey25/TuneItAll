# Legal pages and accessibility review

Scope: the shipped offline app architecture, project website, repository text,
and prepared store descriptions. This is a technical and document consistency
review, not a legal opinion or accessibility certification.

## Checklist results

| Check | Result |
| --- | --- |
| Privacy, terms, refunds, cookies | Separate English/Czech pages, linked from the policy hub, site footer, README, and App details. |
| Refunds | The app has no billing, paid download, subscription, or unlock. External support payments and mandatory rights are addressed separately. |
| App data minimization | No accounts, advertising, analytics SDK, Internet permission, or maintainer server. Audio/results are transient; preferences remain local. Backup exclusions and gesture-only Accessibility configuration checked. |
| Form consent | No website forms, marketing signup, or server submission. No unnecessary consent checkbox added. Android permissions remain separate from marketing consent. |
| Cookies and tracking | No scripts, embeds, optional cookies, or browser-storage writes in site source. Local browser cookie check returned an empty list. GitHub Pages security IP logging is disclosed. |
| Keyboard and layout | Skip link focuses the main content. Tab/Enter navigation and Czech section links checked. Seven pages at 320, 768, 1024, and 1440 CSS pixels had no horizontal overflow or broken images. |
| Website contrast | Light-mode body 17.73:1, secondary text 5.94:1, focus 6.14:1. Dark-mode body 16.90:1, secondary text 9.49:1, focus 12.00:1. |
| App contrast | Light green foreground was 1.825:1. Foreground roles now use dark green at 6.772:1. Existing selected controls keep bright green with dark text. Contrast assertions retain the 4.5:1 minimum. |
| Image alternatives and labels | Website images have alternatives; repeated brand icons are decorative. App controls retain labels. About disclosures announce expanded/collapsed state and retain it after state restoration. |
| Fake reviews | No testimonial or fabricated-review block found on the site or README. GitHub badges link to their real release/build counters. |
| Third-party embeds | None on the project website. Support/payment destinations open separately. Their processing is disclosed rather than described as anonymous. |
| Image copyright | Existing attributed Noun Project headstocks, CC0 metronome, and bundled dependency notices retained. No new third-party asset added. See THIRD_PARTY_NOTICES.md. |
| Unsupported claims | Removed the unqualified sample-accurate metronome headline, effect-free capture promise, obsolete tuning count, and broad marketing wording. Analysis remains described as an estimate. |
| Licence | Official app-build use is explicitly permitted. Source and original assets stay proprietary; third-party licences and mandatory rights are preserved. The previous blanket prohibition on any use was removed. |
| Publisher identity | MajkeyLab and majkeylab@gmail.com are verified from existing project records. The actual legal name, business status, and any required registration/address still need the owner's confirmation. None were invented. |
| Support retention | Private support correspondence is distinguished from public issue history and payment records. The existing twelve-month private-support retention policy is a publisher obligation; its mailbox implementation is not verifiable from this repository. |

## Verification

The unit suite ran 272 tests: 268 passed and four optional external-corpus checks
were skipped in the isolated legal worktree. Android Lint and both QA APK builds
passed. On Huawei YAL-L21 / Android 10, the final instrumentation run passed
97/97 tests in 103.357 seconds, including the private-song fixture. The initial
run caught a contrast assertion using the old foreground role instead of the
actual button background; the reference was corrected without lowering its
contrast requirement. The phone was released after the run. No production app
data or system viewport was changed.

Browser checks used the actual local static site. They do not certify every
screen-reader/browser combination. Later text-only clarification separated
private support retention from public GitHub history.

## Official references and limits

- [European Commission: transparency obligations](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/obligations_en).
- [ÚOOÚ: cookie consent and necessary storage](https://uoou.gov.cz/verejnost/qa-otazky-a-odpovedi/cookies).
- [FTC: privacy promises and data minimization](https://www.ftc.gov/business-guidance/privacy-security).
- [ČOI: business identification and consumer information](https://coi.gov.cz/faq/3-o-cem-me-musi-podnikatel-informovat-6/).
- [GitHub Pages: security IP logging](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages#data-collection).
- [Buy Me a Coffee: payment and contact processing](https://buymeacoffee.com/privacy-policy).

These references inform the documents. They do not prove compliance with every
local law. Applicability depends on the actual publisher, business activity,
users, and external service practices. Publisher identity and operational
retention checks remain open.
