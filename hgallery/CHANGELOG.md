# CHANGELOG

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
