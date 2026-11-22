# QUALITY_SCORE.md

The brief asks for a weighted score per screen and per feature. A score is only
useful if the evidence behind it is stated, so every number below names what it
rests on and what it does not. No category is scored at 100, and none could be:
the target device was never available.

Weights are the brief's: visual fidelity 30 %, functional completeness 25 %, code
quality 15 %, performance 15 %, interactions 10 %, compatibility 5 %.

## Implemented screens

| Screen | Fidelity | Function | Code | Perf | Interaction | Compat | Weighted |
|---|---|---|---|---|---|---|---|
| Photos — day view | 7.5 | 8.0 | 9.0 | 7.0 | 8.0 | 8.0 | **7.8** |
| Photos — month view | 6.5 | 7.0 | 9.0 | 7.0 | 7.0 | 8.0 | **7.2** |
| Albums | 8.0 | 6.0 | 9.0 | 7.5 | 7.0 | 8.0 | **7.6** |
| Album detail | 7.0 | 8.0 | 9.0 | 7.0 | 7.0 | 8.0 | **7.6** |
| Viewer | 6.5 | 6.5 | 8.0 | 7.0 | 7.5 | 7.0 | **7.0** |
| Bottom bar | 9.0 | 9.0 | 9.0 | 9.0 | 8.0 | 8.0 | **8.8** |

### Why Photos day view is 7.8 and not 9

* Function loses points for the absent multi-selection and share: both are core to
  a gallery, and both are listed in `FEATURE_PARITY_MATRIX.md` as not implemented.
* Fidelity loses points because the screen gutter is an estimate and the grid's
  pillarboxing could not be measured against the reference — the corpus contains
  no clean full-screen capture (`MEASURED_COMPARISON.txt`).
* Performance is scored on design decisions, not measurement: the single lazy grid
  and the bounded thumbnail cache are the right choices, but the frame timing was
  never observed on hardware.

### Why the viewer is 7.0

Fidelity is genuinely uncertain: no viewer reference was captured, so the icon
order is an inference. Function is thin — no metadata panel, no video. Its strongest
part is the interaction model (clamped panning, double-tap toggle, paged swiping),
which is implemented and photographed.

## Unimplemented features

| Feature | Score | Reason |
|---|---|---|
| Photo editor | **0** | absent; its control is disabled rather than faked |
| Video playback | **6** | a real Media3 player exists and is screenshotted in four states (mid-playback, chrome hidden, ended, landscape). Gestures beyond tap and rotation were not driven by the harness, and no clip longer than six seconds or other codec was tried. |
| Discover / Highlights | **0** | absent; the tabs say so |
| Search screen | **6** | a real screen: field, kind/size/recency/album chips, results grid reusing the photos cell, distinct empty states, unit-tested filtering. Three of the four axes were photographed; size, recency and album chips were not driven, and no result-of-a-chip-differs shot exists yet. |
| Duplicate finder | **2** | algorithm exists and is tested; no worker, no UI |
| Recently deleted | **0** | absent, and deletion is therefore permanent |
| Multi-selection / share | **8** | selection, batch share and recoverable delete are implemented, tested and photographed on API 33. |
| Custom albums | **0** | absent; the action is visible and inert |
| Persian calendar | **0** | dates are Gregorian in both languages |

Averaging these zeros into a single project score would hide exactly what this file
exists to show, so no overall figure is given.

## What would move each score

| Score | Needs |
|---|---|
| Fidelity above 8 | a clean native-resolution capture from a real Huawei device, then pixel comparison of margins and cell boundaries |
| Function above 8 | EXIF details, a photo editor, and search across size/recency/album axes that the harness has actually driven |
| Performance above 7 | Macrobenchmark and `FrameMetrics` on the POCO X3 Pro |
| Compatibility above 8 | a run on API 29 and API 34, not only 33 |

## Revision — 2026-11-22 (multi-selection, trash, share)

| Category | Weight | Score | Evidence |
|---|---|---|---|
| Huawei visual fidelity | 30% | 7.5/10 | Selection header, tick, receding tiles and the bottom action row follow R3. The action row's icon order is still an inference for the viewer. |
| Functional completeness | 25% | 5.5/10 | Selection, batch share and recoverable delete are real and tested. Editor, video, search screen, Discover and custom albums remain absent and are labelled absent on screen. |
| Code quality | 15% | 8/10 | Platform-specific work isolated in `MediaActions`; the two rules that could corrupt state (selection, delete path) are pure and unit-tested; lint is fatal and currently clean. |
| Performance | 15% | 5/10 | Bitmap budget is capped and deleted tiles are evicted, but nothing has been profiled on a real device. No 120 Hz claim is made. |
| Interactions and animations | 10% | 7/10 | Fade on the selection chrome, tick fade, 120 ms transitions. No measured frame timing. |
| Compatibility | 5% | 7/10 | API 26–35 paths for permissions and deletion, each unit-tested at the decision level; only an API 33 emulator was actually run. |

The score is deliberately not higher: "functional completeness" is measured
against the brief's full feature list, and the editor and video playback are a
large part of it and are missing. Nothing here was profiled on the POCO X3 Pro,
which is the device this project exists for.

## Revision — 2026-11-22 (first real render of the new player and type)

This is the first revision whose visual numbers rest on a screenshot of the code
in this repository rather than on design intent. The player, Inter + Vazirmatn and
the eight fixture photos had never been rendered before this pass.

| Category | Weight | Score | Evidence |
|---|---|---|---|
| Huawei visual fidelity | 30% | 7.5/10 | The photos grid, the toolbar and the player bottom bar now read as the reference. Two icons were wrong and were fixed after being seen: the month/day switch rendered as a hashtag, Discover as a tennis ball. Fidelity still cannot exceed this without a native-resolution capture from a real Huawei phone. |
| Functional completeness | 25% | 6.5/10 | Video playback is real and photographed in four states, so the zero that dominated the previous revision is gone. Editor, search screen, Discover content, EXIF and custom albums are still absent. |
| Code quality | 15% | 8.5/10 | detekt is fatal and clean across both modules after nine rounds of real decomposition; lint is fatal and clean. The delete-path enum lost a branch that named an API newer than the levels it claimed to serve. |
| Performance | 15% | 5/10 | Unchanged and unmeasured. Nothing has been profiled on the POCO X3 Pro; no 120 Hz claim is made. |
| Interactions and animations | 10% | 7/10 | The chrome fade, the auto-hide and the pause-at-end/replay cycle were observed on the emulator. Double-tap seek, drag-to-seek, the speed menu and fill were not. |
| Compatibility | 5% | 7/10 | The delete decision is now exhaustively tested for API 26–35 including the corrected 30 boundary. Only an API 33 emulator was run. |

Weighted: **6.95/10** (0.30×7.5 + 0.25×6.5 + 0.15×8.5 + 0.15×5.0 + 0.10×7.0 +
0.05×7.0). An earlier draft of this paragraph said 7.3, which the arithmetic does
not support; the figure is corrected rather than left flattering. The two low
scores are honest, not modest: nothing has run on the phone the project is for.

## Revision — 2026-11-22 (search screen exists)

| Category | Weight | Score | Evidence |
|---|---|---|---|
| Huawei visual fidelity | 30% | 7.5/10 | The search screen is photographed for the first time and reads as the reference: a bordered field with the magnifier inside, chips beneath it, a plain result count, then the same 4-column grid as Photos. Unchanged from the previous revision because nothing was re-measured against a native capture. |
| Functional completeness | 25% | 7.5/10 | Search went from 2 to 6. The field filters as you type ("clip" → 2 videos), chips AND together in tested code, and the empty state differs for "filters too wide" versus "no such file". Still absent: editor, Discover content, EXIF, custom albums. |
| Code quality | 15% | 8.5/10 | `SearchChips.toQuery(nowMs)` takes the clock as a parameter instead of reading it, so the date buckets are testable; four new tests cover bucket contiguity, span arithmetic, chip emptiness and AND-ing. detekt rejected the first two attempts — unnamed byte and time literals — and both were fixed rather than suppressed. |
| Performance | 15% | 5/10 | Unchanged and unmeasured. Search filters in-memory over the already-loaded item list, which is fine for 8 fixtures and unknown for 20 000 photos; nobody has measured it. |
| Interactions and animations | 10% | 7/10 | The field takes focus on arrival, the Back key leaves search, and a chip visibly lights in the accent colour when chosen. No transition on the search/results switch and no measured frame timing. |
| Compatibility | 5% | 7/10 | Unchanged. Only an API 33 emulator was run; the chip row scrolls horizontally, which was exercised only as a swipe in the harness. |

Weighted: **7.20/10** (0.30×7.5 + 0.25×7.5 + 0.15×8.5 + 0.15×5.0 + 0.10×7.0 +
0.05×7.0). Functional completeness rises because one of the brief's named absent
screens was replaced by a working one, not because more was claimed. Performance
staying at 5 is what keeps the total from moving further: the single largest weight
after fidelity is the one nobody has measured.
