# FEATURE_PARITY_MATRIX.md

Honest status of every feature in the brief. `done` means implemented, compiled,
and exercised on an emulator. `partial` means implemented but not fully verified.
`no` means not implemented — never silently, never faked.

## Photos

| Feature | Status | Evidence |
|---|---|---|
| Timeline | done | emulator screenshot `01-photos.png` |
| Date grouping (Today / Yesterday / date) | done | `DateGroupingTest` ×5, incl. timezone-midnight edge case |
| Adaptive grid | partial | fixed 4 columns as in the reference; no per-orientation column count |
| Thumbnails | done | bounded in-memory LRU, ring-buffer free, 256 px cells |
| Fast scrolling | partial | single lazy grid (no nested lazy per day); not profiled on device |
| Multi-selection | done | long press enters it, toolbar button reaches it; `SelectionTest` (unit) ×11 + `SelectionTest` (instrumented) ×5 |
| Favourites | partial | viewer toggle + MediaStore write; no Photos-screen indicator yet |
| Sharing | done | real `ACTION_SEND` with clip data for every URI and a read grant; viewer button and batch action |
| Deletion | done | confirmation first, then `createTrashRequest` on API 30+ so it is recoverable; permanent path documented in the dialog on older API |
| Photo details / EXIF | **no** | button present, wired to nothing |

## Albums

| Feature | Status | Evidence |
|---|---|---|
| Camera / Screenshots / Downloads buckets | done | derived from MediaStore `BUCKET_DISPLAY_NAME` |
| Videos / Favourites / All photos synthetic albums | done | `AlbumBuilderTest` ×5 |
| Mosaic covers | done | screenshot `02-albums.png` |
| Recently deleted | partial | items now go to the **system** Recycle bin on API 30+, where the phone restores them; an in-app album view is still absent |
| Custom albums (create / rename / delete) | **no** | `ADD ALBUM` present and inert |
| Cover selection, move, copy | **no** | — |
| Sorting | partial | `AlbumSort` NAME/DATE/COUNT exists in core; no UI to change it |

## Viewer

| Feature | Status | Evidence |
|---|---|---|
| Full-screen display on black | done | zoomable pager, `ViewerScreen` |
| Pinch zoom | done | 1×–5×, pan clamped to the overhang |
| Double-tap zoom | done | toggles 1× ↔ 2.5× |
| Swipe navigation | done | `HorizontalPager`, 1 page either side cached |
| Favourite toggle | done | writes `IS_FAVORITE` on API 30+ |
| Delete | partial | immediate, no confirmation |
| Metadata / EXIF panel | **no** | — |
| Editing | **no** | see below |

## Photo editor

| Feature | Status |
|---|---|
| Crop, rotate, flip | **no** |
| Exposure, contrast, brightness, saturation, temperature, tint | **no** |
| Highlights, shadows, sharpness | **no** |
| Filters | **no** |
| Drawing, text | **no** |
| Undo / redo / before-after | **no** |
| Non-destructive editing | **no** |

The editor is deliberately absent rather than simulated. A slider that does not
change pixels is the clearest possible example of fake functionality.

## Video

| Feature | Status |
|---|---|
| Playback, seeking, speed, full screen | **no** — Media3 is not yet integrated |

## Discover / Highlights / Search / Advanced

| Feature | Status |
|---|---|
| Discover content | **no** — tab shows an explicit "not implemented" message |
| Highlights | **no** — same |
| Search | partial — filtering logic exists and is unit-tested (`SearchFiltersTest`), no search screen |
| Duplicate detection (exact hash + dHash clustering) | partial — algorithm implemented and unit-tested, no UI/worker |
| Large-file discovery, storage info | **no** |
| Batch operations | **no** |

## Privacy

| Feature | Status |
|---|---|
| Album hiding | **no** — and would never be described as encryption |
| Recently deleted | **no** |
| No cloud, no network | **done** — no `INTERNET` permission in the manifest |
| Confirmation before destructive action | done | `HgDeleteDialog`, with body text that depends on whether the device really has a trash |

## Localisation / theming

| Feature | Status | Evidence |
|---|---|---|
| English | done | default `values/strings.xml` |
| Persian (RTL) | done | `values-fa/`, verified by `LocalisationTest` on device |
| Mixed-direction text | partial | relies on the platform bidi algorithm; not specifically tested |
| Persian calendar | **no** | dates use `java.time` Gregorian formatting |
| Light theme | done | measured palette |
| Dark theme | partial | palette and theme exist; no Huawei dark capture to verify against |

## Correction log

| Date | Correction | Why |
|---|---|---|
| 2026-11-21 | "Adaptive grid" downgraded from `done` to `partial`, and the Photos tab now has month and day views with a pinch to switch | Huawei's own manual states the Photos tab switches between month and day view by pinching. Only day view existed, so the previous `done` was wrong. Source: [Huawei Mate 10 user manual, "Viewing Photos and Videos"](https://manuals.plus/huawei/viewing-photos-videos-huawei-mate-10) |
| 2026-11-21 | Album tiles documented as 4:3 | Confirmed by EMUI 11 release coverage: the Albums tab "is now laid out in 4:3 aspect ratio with new settings to sort photos" ([Gizmochina](https://www.gizmochina.com/2020/09/10/huawei-announces-emui-11-features-updated-beta-devices/)) |
| 2026-11-22 | Deletion now routes through the system trash on API 30+, and the confirmation text says so only there | The previous dialog said items "will be removed", which was true; the new one says they can be restored, which is only true because the code changed with it. On API 26–29 the same dialog still says the deletion is permanent. Verified by `DeleteFlowTest` for the decision and the on-device screenshot `09-selection.png` for the UI. |
| 2026-11-21 | Hidden albums noted | EMUI 11 added "hidden albums" to Gallery ([Gizmochina](https://www.gizmochina.com/2020/09/10/huawei-announces-emui-11-features-updated-beta-devices/)). Not implemented; recorded rather than implied. |
