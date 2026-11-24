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
| 12 | major | No multi-selection, so batch delete / share / move were impossible | selection was never modelled | `Selection` state machine in `core`, tick overlay, R3-style header and action bar | 11 unit tests; `SelectionTest` on the emulator |
| 17 | critical | Deletion was permanent even on devices that have a trash | the app called `ContentResolver.delete` directly | `MediaStore.createTrashRequest` on API 30+, with the dialog text chosen by the same decision | `DeleteFlowTest`; screenshot `09-selection.png` |
| 24 | major | The confirmation dialog read "Delete 1 items?" | the title was a plain string with a count spliced in | `<plurals>` for the title, both bodies and both toasts, in English and Persian | screenshot `10-delete-confirmation.png` |
| 25 | minor | The selection action bar was never drawn (zero height) | an unweighted `Column` child called `fillMaxSize()` | grid moved into `Modifier.weight(1f)` | screenshot, then a test asserting the bar's pixel height |
| 26 | minor | The e2e harness photographed the wrong screens twice | tap coordinates were guessed | Delete's position measured from the screenshot by locating its danger-coloured glyph | `09-selection.png` / `10-delete-confirmation.png` |

| 27 | major | Favourites were never written at all, and nothing said so | three separate causes, each silent: `ContentResolver.update` on a row owned by MediaStore returns "0 rows" instead of throwing; the stored URI is a `.../file/<id>` one and the favourite APIs answer "All requested items must be Media items"; and after a granted consent dialog the platform has already applied the change, so retrying wrote the old value back | `mediaUriFor` builds the typed `images`/`video` URI, `buildFavouriteRequest` asks with `createFavoriteRequest` on API 33+ and logs any refusal, and a granted dialog is recorded rather than rewritten | screenshots `23-viewer-before-favourite.png` and `24-viewer-favourited.png`, plus the app's own log lines echoed into the run |
| 28 | major | A favourited tile looked identical to any other | no overlay was ever drawn | `FavouriteBadge` in the bottom-left corner, opposite the selection tick, white with a shadow so it cannot be mistaken for it; the spoken description gained a favourite variant | `24-viewer-favourited.png`; `docs/KNOWN_LIMITATIONS.md` records that the post-viewer grid shot is not yet taken |

| 29 | critical | A video played sound with no picture | `rememberPlayerController` used `remember(uri)`. Each clip built a new controller and a new `ExoPlayer`, and released the previous one. `AndroidView` runs `factory` once and only `update` after that, so the surface kept the released player. Sound came from the new player, the picture from the released one, and the platform reports no error for it | one controller per screen, remembered with no key; a clip change calls `load()` and replaces the media item on the same player; `player` is a `val`; `PlayerSurface.update` re-asserts `view.player` when it differs | core tests green; all four CI jobs green in run 45343129275; `evidence/41-video-second-clip-no-chrome.png` shows the second clip with its own title, its own duration, a drawn picture and a muted sound control. Sound itself is **not measured**: the emulator has no audio sink |
| 30 | major | The chrome drew a play button over running video and did not auto-hide | `load()` reset to a bare `PlayerState()`, which says "paused", while `playWhenReady` said play. The auto-hide rule reads `isPlaying` | `load()` and the constructor seed `PlayerState(isPlaying = playWhenReady)` | run 45336125604 |
| 31 | major | No mute control existed anywhere in `ui/video/` | the audio policy had no control to express itself | `SoundOn` / `SoundOff` icons, `video_mute` / `video_unmute` strings in both languages, tag `video-mute`, `PlayerController.toggleMuted()` | run 45336125604 builds and lints it. **Not pressed on a device** |
| 32 | major | A clip opened in the viewer, or paged onto from a photograph, drew a black page | the viewer drew every page with `ZoomableImage`, which decodes a bitmap; a video URI fails that decode | a video page: poster from the thumbnail cache, centered play target, duration, file name. The target hands the clip to the player | run 45336125604 |
| 33 | major | Reaching a clip by swiping gave it sound | no path existed, so the rule could not be applied | `PlayerPolicy.onArrival(Entry)` returns `Arrival(muted, autoplay)`: `DIRECT` is unmuted, `SWIPE` is muted, both autoplay | 4 core tests. **The audible result is not measured** |

## Open

| # | Severity | Problem | Plan |
|---|---|---|---|
| 22 | major | Batch share exists for the Photos tab only; the viewer still shares one item and albums have no selection | reuse `Selection` in `AlbumDetailScreen` |
| 23 | major | The system trash dialog could not be confirmed end to end on the emulator | the emulator has no Recycle bin UI; needs a real POCO X3 Pro |
| 14 | major | Photo editor absent | needs a real non-destructive pipeline; not to be simulated |
| 18 | minor | No storage info, no duplicate-finder UI | hash in `WorkManager`, present groups |
| 19 | minor | Persian calendar not used for date headers | `java.time.chrono.HijrahDate` is the wrong calendar; needs a Jalali implementation |
| 20 | minor | Dark theme is unverified against a Huawei capture | obtain a dark reference or state the token set as inferred |
| 34 | major | The audio rule is not measured. No test plays a clip and reads the stream volume. The CI emulator has a software GPU and no audio sink | an instrumented test that asserts `player.volume` and `player.isPlaying` after a direct open and after a swipe. `volume` is checkable without an audio device |
| 35 | major | No instrumented test covers the viewer, and the player test does not play a clip. Both defects A and C survived a green CI run for this reason | `PlayerSessionTest` (9 tests, added) covers player identity, mute state and state reset without a media file. Still open: a test that plays a real clip and asserts the surface draws. `ViewerVideoPageTest` for the video page. |
| 36 | minor | A photograph pushed onto a clip in the viewer still pages one step instead of handing over | `ViewerReplace` was designed and removed: no call site existed. The hand-over needs a settled-page effect on the viewer, which is a separate change with its own risk to the pager |
| 37 | minor | `docs/PERFORMANCE_REPORT.md` quoted a 11.7 MB release APK and 25 tests | corrected at commit `3003025`: 17,091,805 bytes and 144 tests |
