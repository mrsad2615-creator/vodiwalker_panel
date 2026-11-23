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
| Photos — day view | 8.0 | 8.0 | 9.0 | 7.0 | 8.5 | 8.0 | **8.1** |
| Photos — month view | 6.5 | 7.0 | 9.0 | 7.0 | 7.0 | 8.0 | **7.2** |
| Albums | 8.0 | 6.0 | 9.0 | 7.5 | 7.0 | 8.0 | **7.6** |
| Album detail | 7.0 | 8.0 | 9.0 | 7.0 | 7.0 | 8.0 | **7.6** |
| Viewer | 6.5 | 7.0 | 8.0 | 7.0 | 7.5 | 7.0 | **7.1** |
| Search | 7.5 | 8.0 | 8.5 | 5.0 | 7.0 | 7.0 | **7.3** |
| Bottom bar | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 8.0 | **9.0** |

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
| File details | **7** | a real sheet from the row already loaded: name, size, dimensions, megapixels, duration for video, folder, album and type. Unit-tested, including the rule that omits a row rather than print a placeholder. Shows no EXIF — camera, aperture, ISO — which needs the file stream and a parser. |
| Video playback | **7** | a real Media3 player, screenshotted in four states. The chrome now uses gradient scrims, directional entrances and a draggable progress line with a permanent thumb. Gestures beyond tap and rotation are still not driven by the harness, and no clip longer than six seconds or other codec was tried. |
| Discover / Highlights | **0** | absent; the tabs say so |
| Search screen | **7** | a real screen: field, kind/size/recency/album chips behind labelled section headers, results grid reusing the photos cell, distinct empty states, unit-tested filtering, all reachable through accessibility labels. Photographed: empty, mid-typing, kind axis narrowing 2 results to 0, size axis narrowing to 0 and being swapped from Large to Small. Not driven: the recency chips and the album chips. |
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
| Function above 8 | EXIF (camera and exposure data, not just file facts), a photo editor, and search across the recency and album axes that the harness has not driven |
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

## Revision — 2026-11-23 (the redesign)

| Category | Weight | Score | Evidence |
|---|---|---|---|
| Visual design | 30% | 8.0/10 | Tonal surfaces instead of one white page, an 8-point radius scale on photographs, a display-size title with optical tracking, and a single nav indicator that travels. Measured against the app's own previous screenshots, not against a native capture; no pixel comparison with HarmonyOS has been done. |
| Functional completeness | 25% | 8.0/10 | Search works on the kind and size axes. Details sheet, favourites, delete, share, month view and the player all work and are photographed. Still absent: editor, Discover content, custom albums, true EXIF. |
| Code quality | 15% | 8.5/10 | Motion is one vocabulary of published spring constants with a reduced-motion path, not per-screen timings. Tokens replaced a flat palette; `hgPalette` and `HgMetrics` survive as adapters so 16 files did not have to change in one commit. detekt caught four real problems this pass. |
| Performance | 15% | 5/10 | Unchanged and unmeasured. Springs retire after settling; the staggered entrance is not wired yet, so its cost is unknown. No profile on a device. |
| Interactions and animations | 10% | 8.5/10 | Press scale on every tile and tab, a sliding nav indicator, directional chrome entrances, a progress line whose thumb is always visible. Reduced motion collapses all of it. Still no measured frame timing and no haptics. |
| Compatibility | 5% | 7/10 | Unchanged. API 33 emulator only. Dark mode was broken and is fixed: the system bar icons were forced to the light appearance and vanished on a dark background. |

Weighted: **7.62/10** (0.30×8.0 + 0.25×8.0 + 0.15×8.5 + 0.15×5.0 + 0.10×8.5 +
0.05×7.0).

Performance is the one number holding this below 8, and it is unmeasured rather than
bad. The honest next step is a macrobenchmark, not more motion.
