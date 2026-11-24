# PERFORMANCE_REPORT.md

## What can and cannot be measured here

| Target | Status |
|---|---|
| POCO X3 Pro (Snapdragon 860, 120 Hz) | **not measurable** — no device, and no emulator reports 120 Hz |
| Emulator (x86_64, API 33, software GPU) | measured, but not representative of the phone |
| Frame timing on real hardware | **not measured** |

Nothing in this file is extrapolated into a claim about the phone. Where a number
comes from the CI emulator it is labelled as such.

## Measured on the CI emulator

| Metric | Value | How |
|---|---|---|
| Cold start (`am start -W`, `TotalTime`) | **950 ms** | `evidence/am-start.txt`, run 44965964758 |
| APK size, debug | 17.2 MB | workflow artefact `hgallery-debug-apk` |
| APK size, release | **17,091,805 bytes** (16.3 MiB) | Release asset `hgallery.apk`, tag `hgallery-v0.1.167`, commit `3003025` |
| Domain unit tests | **144 tests** in 7 files | Count of `@Test` in `core/src/test` at `3003025`; the run reports success |

Two numbers in an earlier version of this table were wrong. It said 11.7 MB for
the release APK, and 25 domain tests. The measured asset is 17,091,805 bytes,
and the source holds 144 tests. The old figures are replaced rather than kept
in a footnote: a performance report that quotes the wrong size is a report
nobody can use for a decision.

A 950 ms cold start on an emulator with a software GPU is unremarkable and should
not be read as the phone's figure; it is recorded because it is the only
startup number that exists.

## Design decisions that exist for performance, and why

| Decision | Reason |
|---|---|
| One `LazyVerticalGrid` for the whole timeline | a lazy column of per-day lazy grids keeps scroll state and measured height for every day; this is the classic cause of jank in gallery apps |
| Thumbnails decoded at 256 px, cached in a byte-bounded LRU (heap/4, ≤48 MB) | a screenful of 4-column cells needs ~280 px cells; decoding at screen width would multiply the bitmap heap |
| Projected MediaStore columns only | a full row on a 20 000-photo library costs measurable time and allocation for columns nothing renders |
| Library loaded once, then patched in place | a favourite toggle re-queries nothing; a delete removes ids |
| Viewer decodes bounded to 2048 px | a 48 MP decode needs ~190 MB and is killed on a 3 GB phone |
| `graphicsLayer` for zoom and pan | the transform never re-runs layout or re-decodes |
| Bundle-format `ImageVector`s instead of a PNG icon set | smaller APK, and the icons tint correctly |

## Not yet done

* **Baseline Profile.** Worth adding, but only once the hot paths are measured on
  real hardware; guessing which frames to precompile is how a baseline profile ends
  up making startup slower.
* **Macrobenchmark on device.** Cannot run without the phone. The CI emulator can
  run it, but its frame numbers would not transfer.
* **120 Hz verification.** Explicitly out of reach; see `KNOWN_LIMITATIONS.md`.
