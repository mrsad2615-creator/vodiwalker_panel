# BUG_TRACKER.md

Every entry was found during this project, not invented. Severity follows the
usual scale: **critical** = wrong result or data loss, **major** = clearly wrong
behaviour, **minor** = cosmetic.

## Fixed

| # | Severity | Problem | Root cause | Fix | Verified by |
|---|---|---|---|---|---|
| 1 | critical | App never appeared; the launcher filled every screenshot | `enableEdgeToEdge()` was called *before* `super.onCreate()` | call `super` first | `am start` reports `Status: ok`; focus on `MainActivity` |
| 2 | critical | Emulator run never installed the app, so screenshots showed the launcher | harness only built the test APK, never the app | install the app APK, then launch | `evidence/adb-install.txt` |
| 3 | critical | `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | harness installed before AGP's own test install | run instrumented tests first, then install cleanly | run 44965964758 |
| 4 | major | Lint failed on `produceState` not assigning in the lambda | value assigned inside `withContext` | `remember` + `LaunchedEffect` | lint clean |
| 5 | major | "ADD ALBUM" was vertically clipped | 48 dp touch target inside a 34 dp row | `heightIn(min = 48.dp)` | screenshot crop of `02-albums.png` |
| 6 | major | Search icon rendered as an open hook | single `arcToRelative` never closed the ring | two half arcs | `01-photos.png` |
| 7 | major | Tapping an album jumped straight into the viewer | `onOpenAlbum` set the viewer id | added `AlbumDetailScreen` | `AlbumNavigationTest` ×2 |
| 8 | major | Delete was immediate, no confirmation | missing dialog | `AlertDialog` before `mediaStore.delete` | code + lint |
| 9 | minor | All fixture photos grouped under "Today" | light media scan does not read EXIF `DateTimeOriginal` | set `date_taken` explicitly in the harness | `evidence/retime.txt` |
| 10 | minor | Tab taps landed above the bar | Y anchor computed at `H - 110` | `H - 55` | four distinct screenshots |
| 11 | minor | Lint's `GradleDependency`/`OldTargetApi` made every build red | no lint policy | `lint.xml` with stated reasons | lint green, everything else still fatal |

## Open

| # | Severity | Problem | Plan |
|---|---|---|---|
| 12 | major | No multi-selection, so batch delete / share / move are impossible | implement selection mode with the reference's toolbar (R3) |
| 13 | major | Favourites are written but never shown in the grid | overlay a heart on favourited tiles |
| 14 | major | Photo editor absent | needs a real non-destructive pipeline; not to be simulated |
| 15 | major | Video playback absent | Media3 `ExoPlayer` in the viewer |
| 16 | major | Search screen absent although filtering is implemented and tested | bind `SearchFilters` to a Compose search screen |
| 17 | major | Recently deleted absent; deletion is permanent | needs a recycle mechanism before deletion is truly safe |
| 18 | minor | No storage info, no duplicate-finder UI | hash in `WorkManager`, present groups |
| 19 | minor | Persian calendar not used for date headers | `java.time.chrono.HijrahDate` is the wrong calendar; needs a Jalali implementation |
| 20 | minor | Dark theme is unverified against a Huawei capture | obtain a dark reference or state the token set as inferred |
