# CHANGELOG

## 2026-11-22 — multi-selection, real trash, working share

### Added
* Multi-selection on the Photos timeline, entered by a long press or by the new
  toolbar control. The header shows a close glyph and `N selected`, the shape of
  Huawei's own multi-select capture (R3). Unselected tiles recede; selected ones
  carry a blue circular tick.
* Batch action row: **Share · Select all · Delete**, Delete in the danger colour.
* Real sharing from both the batch row and the full-screen viewer, via
  `ACTION_SEND` with clip data for every URI and a read grant.
* A confirmation dialog whose body text depends on whether this Android version
  actually has a trash.

### Changed
* Deletion is no longer irreversible on Android 11+. It goes through
  `MediaStore.createTrashRequest`, so items land in the phone's own Recycle bin
  and can be restored for up to 30 days. The dialog says so only there; on API
  26-29 the same dialog says the deletion is permanent. That is the difference
  between a safety feature and a false promise.
* Deleted thumbnails are evicted from the bitmap cache instead of lingering in
  the LRU until size pressure pushed them out.

### Fixed
* The selection action bar was measured to zero height and never drawn: the grid
  sat in an unweighted `Column` child calling `fillMaxSize()`. Caught by looking
  at the feature's own screenshot, then pinned by a test that asserts the bar's
  pixel height.

### Verified
* 17 new unit tests (11 for `Selection`, 6 for the delete-path decision).
* 6 instrumented tests on the emulator, including the height assertion.
* CI run green end to end: core tests, debug build, lint, emulator UI tests,
  signed release APK. Evidence: `evidence/09-selection.png`,
  `evidence/10-delete-confirmation.png`.

## 0.1.41

The first build that is genuinely usable on a phone.

### Added
* Photos timeline with day sections (`Today`, `Yesterday`, absolute dates) in a
  seamless grid, with the screen title scrolling with the content as in the
  reference.
* Month view with six columns and month headers, switched by pinching or by the
  toolbar control — the behaviour Huawei's own Gallery manual describes.
* Albums tab: smart albums with 2×2 mosaic covers and counts, plus device folders
  under `MORE ALBUMS`.
* Album detail screen with back navigation.
* Full-screen viewer: paged swiping, pinch and double-tap zoom with clamped
  panning, favourite toggle, delete behind a confirmation dialog, and a tap that
  hides the chrome without closing the photo.
* English and Persian strings, every user-facing string routed through resources.
* Measured design system: palette sampled from official captures, type ramp, spacing
  and motion tokens.

### Fixed
* The app never appeared because `enableEdgeToEdge()` ran before `super.onCreate()`.
* Emulator runs photographed the launcher because the app was never installed.
* `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when the harness installed before AGP.
* "ADD ALBUM" was vertically clipped by a 48 dp target inside a 34 dp row.
* The search icon rendered as an open hook.
* Tapping an album jumped straight into the viewer instead of opening the album.
* Delete was immediate, with no confirmation.

### Changed
* Dropped the unused KSP plugin, whose resolution alone could fail the build.
* Lint is fatal; exactly two checks are silenced, each with a written reason.

### Not present
Editor, video, multi-selection, sharing, search screen, Discover, Highlights, custom
albums, recently deleted, duplicate-finder UI, Persian calendar. See
`docs/KNOWN_LIMITATIONS.md`.

## 0.1.0
Project scaffold, CI pipeline, domain core with 25 unit tests, signed release build.
