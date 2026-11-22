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
| Video playback | **0** | absent |
| Discover / Highlights | **0** | absent; the tabs say so |
| Search screen | **2** | filtering logic exists and is tested; no screen |
| Duplicate finder | **2** | algorithm exists and is tested; no worker, no UI |
| Recently deleted | **0** | absent, and deletion is therefore permanent |
| Multi-selection / share | **0** | absent |
| Custom albums | **0** | absent; the action is visible and inert |
| Persian calendar | **0** | dates are Gregorian in both languages |

Averaging these zeros into a single project score would hide exactly what this file
exists to show, so no overall figure is given.

## What would move each score

| Score | Needs |
|---|---|
| Fidelity above 8 | a clean native-resolution capture from a real Huawei device, then pixel comparison of margins and cell boundaries |
| Function above 8 | multi-selection, sharing, search, EXIF details |
| Performance above 7 | Macrobenchmark and `FrameMetrics` on the POCO X3 Pro |
| Compatibility above 8 | a run on API 29 and API 34, not only 33 |
