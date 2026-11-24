# Adult practice content and assessment separation

Date: 2026-11-24. User decisions: general adults; professional skills practice **and** a separate validated-assessment area; no existing Figma file. Persian is the primary experience. Keep prior English support; do not remove it without a requirement.

## Status and limits

The user rejected the 0.5 visual and content quality. Its build evidence remains historical technical evidence, not design acceptance. No new APK is claimed here. This change adds a production-domain content model, a small **draft** editorial bank and an interactive review tool. The bank is **not connected to Android session selection**, does not replace old checkpoints, and is not a completed content library. Do not silently change questions underneath a saved session.

There are six original fictional Persian adult scenarios, two at each of three editorial levels, with eighteen four-choice questions. Each choice has its own rationale. Each question cites an exact evidence span from its passage and declares a target reading skill. Topics include public services, purchasing decisions, survey interpretation, transport evaluation, weighted averages and policy exceptions. No commercial test items were copied. The scenarios are not reports of actual companies or studies.

The levels are editorial hypotheses, not measured item difficulty, school-grade equivalents or cognitive norms. The six texts are draft calibration material. They are far too few for the final library. Level 1 uses explicit constraints and practical inference; level 2 distinguishes observation, inference and evidence; level 3 combines claims, weights or conditional exceptions. These three content levels are not silently mapped onto the existing five engine difficulty values.

## Official-source review

### NIH Toolbox

The official page describes iPad distribution, a subscription for scoring/reporting and special approval for Cognition measures. It specifies C-level qualifications or supervision. These conditions do not establish a right to embed its tasks in an unrelated offline Android app. [1](https://nihtoolbox.org/get-the-toolbox/)

Decision: do not copy, port, unlock or label a NEUROX task as an NIH Toolbox measure. Persian validation and offline redistribution permission have not been established in this review. Absence of evidence here is not a claim that no Persian research exists.

### MoCA

Read the official [Permissions page](https://mocacognition.com/permission/) and [FAQ](https://mocacognition.com/faq/), including the permission page's development/web-use sections. The permission page restricts electronic redevelopment and distinguishes use categories. It also states training requirements more broadly than the current FAQ, which distinguishes total-score use from domain-specific interpretation. These pages are not fully aligned on training wording. Do not resolve the discrepancy by selecting the more permissive statement.

Decision: no test content was accessed, reproduced or implemented. No permission or qualified-administrator status is established. Contact the rights holder before any proposed integration. A clinical screen is not automatically appropriate for general-adult skills training. No MoCA cutoff, score conversion or diagnostic recommendation is included in the new content.

### ICAR research candidate

The official project lists the original development/validation paper. [3](https://icar-project.org/papers/papers.html) This reference is a research lead, not evidence that the exact Persian translation, Android administration, reuse terms or target-population norms required here are approved. Do not convert an original NEUROX practice result into an ICAR score, IQ or percentile.

No candidate is selected for product integration yet. Keeping assessment work blocked on actual evidence is not deletion of the assessment requirement.

## Two product modes

### Skills practice

- Use original material, disclosed as practice, with skill-specific feedback.
- Prefer self-paced reading before timed exercises. Keep comprehension separate from presentation speed.
- Permit access to evidence after submission. State whether consulting the passage is allowed; the review tool permits it and is not a memory test.
- Use examples, guided practice and clear errors. Do not infer intelligence or health from performance.
- Record content IDs and revisions when this model is integrated. Preserve legacy content for old sessions or migrate with an explicit compatibility contract.
- Track exposure by **text ID**, not only by question, so changing a question or revision does not disguise a repeated passage as fresh material.

### Validated assessments

Before release review, document all of the following for each exact instrument/version:

1. Authoritative source and version.
2. Permission for the intended product, use and audience.
3. Validation evidence for the Persian version.
4. Fit to the intended adult population and any required norms.
5. Administration, scoring and interpretation protocol, including device/mode changes.
6. Qualified content/clinical review as appropriate to the instrument.
7. Rights and technical conditions for offline distribution.

`AssessmentReleasePolicy` reports missing categories. Even a complete checklist returns only `ELIGIBLE_FOR_RELEASE_REVIEW`, never “validated”, “licensed” or “runnable”. A boolean or a list of URLs cannot grant rights or establish validity. There is no runnable validated test in this change.

## Content contracts and release gates

`LearningContent.kt` validates stable identifiers, revision bounds, title/topic/text safety, four unique choices, a unique answer key, rationales, exact evidence spans and multiple reading skills. Mechanical validation cannot decide whether a distractor is plausible, an inference is warranted or the language is well graded.

`ReadingSelectionPolicy` sorts by stable IDs before seeded ordering. It excludes previously exposed text IDs. It refuses duplicate revisions and global question IDs. It never repeats texts silently to fill a requested count: a shortfall is explicit. Draft content is excluded unless `editorialPreview=true`. Do not mark drafts reviewed just to make selection nonempty.

Required human review: Persian language, ambiguity, evidence sufficiency, distractor plausibility, option-length cues, answer-position cues, cognitive demand, cultural accessibility and feedback usefulness. A small agent-written bank and automated tests do **not** satisfy this gate. A later pilot must examine item performance with consent and without transmitting training data by default. Population norming, if needed for an assessment, is separate work.

## Local review tool

Open `/design/content-review/` on a static server rooted at `neurox/`. The live local review currently uses port 4174. It reads a JSON artifact exported from the **actual Kotlin domain bank**, not a duplicated JavaScript question set. Answers stay in page memory. A user can deliberately download a local review report. Reload clears answers. No third-party requests, telemetry, XP or native history writes are used.

From repository root:

```sh
NEUROX_CONTENT_REVIEW_OUT="$PWD/neurox/test-output/learning-content.json" neurox/scripts/test-core.sh
neurox/scripts/bootstrap-design.sh
# Serve neurox/ on port 4174, then:
LD_LIBRARY_PATH="$PWD/.local/tools/design/sysroot/lib" node neurox/scripts/test-content-review.mjs
```

`CONTENT_REVIEW_URL` selects another server origin. Generated JSON/reports/screenshots remain in ignored `test-output/`.

## Executed evidence

- 247 core/security/content cases passed, 0 failed; 17 added content/policy cases.
- 48 static checks, 5 SQLite checks and 6 evidence-parser tests passed.
- 57 Kotlin/KTS files parsed, 0 syntax errors. This grammar check is not Android type validation.
- Browser reviewer: 46 layout checks and 46 WCAG A/AA axe audits, 0 failures or script errors.
- All 18 questions were submitted in browser tests with correct/incorrect choices. Tests checked the selected-choice rationale, source quote, immutable submitted choice, actual summary counts, local export and reload clearing. No third-party requests were observed in that local run.
- Agent visual inspection: mobile/desktop library and answer-feedback screenshots. This is not an adult user study, expert editorial approval, a renewed 20-screen redesign or Android acceptance.

The older 44-case Android suite attests to 0.5, not this new editorial work. Report any later hosted run separately. No 0.6 version or APK is released by adding this policy.

## First native Persian-label correction

Localize the section eyebrows, brand wordmark and system names in Home, Studios, onboarding, setup, library, analytics, builder and settings. Do not change stored enum identifiers. Remove artificial letter spacing in Persian headings/wordmarks. Keep English when English is selected. A dedicated Compose regression checks Persian system names and the retained English route; update navigation expectations to the actual Persian labels. Two narrow static guards detect regressions in section labels/raw module enum display; these are not a full localization audit. Native validation remains a separate hosted gate.
