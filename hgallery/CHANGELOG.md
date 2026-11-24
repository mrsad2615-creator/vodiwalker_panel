# CHANGELOG

## 2026-11-24 — camera data, rotation, and a real Persian calendar

### Added
* A camera block in the details sheet: make and model, lens, aperture, shutter,
  ISO and focal length, read from the file itself with `androidx.exifinterface`
  when the sheet opens. A missing tag omits its row rather than printing a
  placeholder, and a file with no EXIF says so.
* Rotation in the viewer, a quarter turn per press. It writes the EXIF
  orientation tag and invalidates the thumbnail, so the original pixels are
  never re-encoded and no second copy of a 12-megapixel photo is made. It is
  enabled only when the app can write to the file.
* A Solar Hijri calendar, converted by the app. Day headers read `1 مهر` and
  month headers `مهر 1405` on a Persian device instead of Gregorian dates with
  Persian month names.

### Fixed
* `java.time` on Android accepts a `-u-ca-persian` locale extension and then
  formats ISO without an error, so the previous attempt looked right on screen
  and was wrong in every number. The conversion now happens in
  `core/PersianCalendar.kt`; the platform is asked only for month names.
* Every release was tagged `0.1.<run number>` while the APK inside reported
  `versionName 0.1.0` and `versionCode 1`. The version in Settings identified no
  build, and Android never saw a later release as an upgrade. The workflow now
  passes its run number to the build and fails the job if the APK's badging does
  not match the tag being published.
* A mirrored EXIF orientation (2, 4, 5, 7) was reported as no rotation; the four
  rotations are now applied, and only the flip is dropped.

### Verified
* The Persian conversion agrees with an independent implementation on 47,848
  consecutive days, 1960 to 2090, with no mismatch. Unit tests cover the year
  boundary, a January date, a leap year and every day of a full year.
* `gradle :core:test` green, detekt clean, debug APK, emulator UI tests and the
  signed release APK all green in one run (CI run 45290743520).
* Published as `hgallery-v0.1.159`. Its `versionCode` is now the run number, so
  this build installs over `0.1.0` as an upgrade.

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
