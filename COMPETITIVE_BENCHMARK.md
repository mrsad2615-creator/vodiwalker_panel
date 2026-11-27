# Competitive benchmarking — initial evidence

Research is limited to the official documentation linked below; no competitor application, screenshot, animation, price tier, accessibility or offline behavior was independently tested. Observed capability means documented capability, not direct observation. Weakness/opportunity are design hypotheses, not assertions of measured superiority. Full module-by-module benchmarking is outstanding.

## Tasks

| Reference product | Observed capability | Strength | Weakness / limitation | KAIROS opportunity | Proposed implementation | Validation |
|---|---|---|---|---|---|---|
| Todoist | Official documentation describes natural-language recurring dates, distinguishing scheduled-date and completion-date bases. https://www.todoist.com/help/articles/introduction-to-recurring-dates-YUYVJJAV | Explicit recurrence semantics and quick entry. | Documentation alone does not establish Persian parsing, offline behavior or quality on our target device. | Explain recurrence in Persian and preview next occurrence without guessing. | Typed occurrence model and explicit base; parse only recognized text, then preview/confirm. | Jalali boundary, ambiguity, concurrent completion, duplicate and safe undo tests plus Persian user trials. |
| Todoist | Official documentation distinguishes reminders tied to individual occurrences from recurring reminders. https://todoist.com/help/articles/introduction-to-reminders-9PezfU | Explains behavior that could otherwise surprise the user. | No device inspection performed. | Give a clear per-occurrence reminder preview. | Separate reminder rule from task occurrence; idempotent native scheduling. | Device tests around date changes, permission denial and process death. |

## Personal knowledge and backup

| Reference product | Observed capability | Strength | Weakness / limitation | KAIROS opportunity | Proposed implementation | Validation |
|---|---|---|---|---|---|---|
| Obsidian | Official documentation says vault notes are local Markdown files and distinguishes sync from backup. https://help.obsidian.md/data-storage and https://help.obsidian.md/backup | Portable content and clear local-data explanation. | Its documentation warns file recovery is limited and per-device; no claim that the whole product is cloud-free. | Make actual export scope and recovery limitations visible before users rely on it. | Versioned backup, preview, validation, safe merge and full recovery designed separately. | Nonempty real-shaped datasets, migrations, failure injection, device restore. |

## Widgets and AI — platform references, not competitor comparisons

Android widget guidance: https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide ; https://developer.android.com/develop/ui/views/appwidgets/advanced . User interaction updates and periodic updates have different behavior; launcher testing remains necessary.

LiteRT-LM examples: https://ai.google.dev/edge/litert-lm/overview . Hardware and model example tables are not proof of suitability for Persian on POCO X3 Pro. Runtime evaluation has not yet been carried out.

## Outstanding matrices

Home, habits, projects, goals, calendar, focus, AI, motivation, analytics, journal, search, automation, settings and individual widget types need their own cited competitor comparison and actual KAIROS validation. They are not silently considered researched or complete.
