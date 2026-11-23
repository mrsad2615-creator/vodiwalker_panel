# KNOWN_LIMITATIONS.md

This file exists so that no reader of this project can mistake an unfinished
feature for a finished one. Every item below was a deliberate choice, not an
oversight that went unnoticed.

## Cannot be verified at all in this environment

| Limitation | Why |
|---|---|
| **No physical-device testing.** The POCO X3 Pro was never available to this build. | No ADB access to the phone. Everything was verified on an x86_64 emulator. |
| **No 120 Hz or thermal measurement.** | The emulator does not report a 120 Hz mode, and thermal behaviour is a property of the phone's SoC. |
| **No Android Studio / Layout Inspector session.** | The sandbox has no Android SDK and cannot download one; the build runs in GitHub Actions. |
| **Single AI model.** The brief asks for eight specialised engines. | This agent is one model. Research, implementation, audit and verification were performed as *separate passes* — which is what the brief allows when only one engine exists — but no second model reviewed this code. Claims of multi-model orchestration would be false. |

## Reference gaps (behaviour inferred, not captured)

| Screen | What is inferred |
|---|---|
| Full-screen viewer | The exact icon order of the bottom action row. Implemented as Share · Favourite · Edit · Delete · Details. |
| Month view | That it uses 6 columns. The *existence* of the mode is documented by Huawei; the column count is an inference. |
| Dark theme | Huawei's dark Gallery was never captured. The palette follows the light palette's structure and is not pixel-verified. |

## Implemented but unverified

* **Pinch to switch views** compiles and is wired, but a synthetic two-finger
  gesture could not be delivered to the app from the CI harness (`sendevent`
  produced a 0.0 % frame change). The toolbar switch performs the same state change
  and *is* verified. The pinch remains unverified.
* **Favourites** write `IS_FAVORITE` on API 30+; the write path was not exercised
  against a failure case (e.g. a read-only volume).
* **Deletion** confirms, then routes through `MediaStore.createTrashRequest` on
  API 30+, so the items are recoverable from the phone's own Recycle bin. The
  app's side of this is tested and photographed; the *system's* own second
  confirmation was not observed end to end, because the emulator image has no
  Recycle bin UI. Below API 30 there is no trash and no `createDeleteRequest`
  either — that API is 30, not 29, which lint's `NewApi` check caught and which
  forced the decision to become two-way. A unit test walks every level from 26 to
  35 and asserts the decision never names a mechanism the device cannot use.
* **The video player is now rendered, and was wrong once.** It has been
  screenshotted on the emulator mid-playback, with the chrome hidden, at end of
  playback, and in landscape (`hgallery/evidence/13`–`16`). The first render
  showed the replay disc drawn on top of the close button, because the disc was a
  direct child of the player's `Box` with no `align()`; that is fixed. Everything
  the player claims about gestures is still only exercised by two of them — the
  reveal tap and rotation. Double-tap seek, drag-to-seek, the speed menu and the
  fill toggle are implemented and unit-tested at the policy layer but were **not**
  driven by the harness.
* **The favourite heart is photographed in the viewer, not yet on a tile.** The
  overlay itself is drawn by `ThumbnailCell` and the state it depends on is the same
  one the viewer's filled heart proves, but the shot of a grid tile carrying the heart
  has not been taken: the harness closes the viewer with the control whose label it
  had guessed wrong, and that step now needs a run. The read path is also unverified
  after a relaunch — `IS_FAVORITE` is queried on API 30+ and the write path was the
  part that was broken, so a restart is what proves the value persisted.
* **Search: two of its four axes are unverified.** Photographed and driven: the
  text field, the kind chips ("clip" → 2 results, then Photos → 0), the size chips
  (Large → 0 matches, then swapped to Small, which proves one choice per axis), the
  empty state, the Clear action and Back out. Not driven: the recency chips, the
  album chips, and the interactive path of tapping a result to open it. The
  filtering rules for all of them are unit-tested; what is unverified is the screen
  on those two axes. The chip row scrolls horizontally, which the harness handles by
  swiping and re-reading the hierarchy up to three times.
* **Three controls are visible but disabled, and now say so.** The viewer's Edit
  and Details and its overflow menu are drawn but greyed, because the features
  behind them are absent. Their accessibility names end in ", unavailable" so a
  screen-reader user is not told a disabled control will act. The Photos and Albums
  toolbars no longer draw an overflow button at all: there was nothing behind it.
  The Albums screen's ADD ALBUM link is greyed for the same reason, though the
  reference shows it in the accent colour; the visual difference is deliberate and
  is the only place this build knowingly departs from a reference control's styling.
* **Persian plurals.** Lint's `ImpliedQuantity` check found that Persian's
  singular form also covers zero, so the delete bodies now carry the count in
  both variants. The wording was reviewed by inspection, not by a native speaker.
* **Partial media access** (Android 14 "selected photos") is detected and reported,
  but the UI that explains it was not built.

## Not implemented

Photos: photo details/EXIF, storage info, batch *move/copy* (share and delete are done).
Albums: custom album creation, renaming, cover selection, move/copy, hidden albums,
recently deleted, sort UI.
Viewer: metadata panel.
Editor: **entirely** — crop, rotate, flip, exposure, contrast, filters, drawing,
text, undo/redo. A slider that does not change pixels would be fake functionality,
so the editor is absent and its button is disabled rather than pretending.
Video: **entirely** — no Media3 integration.
Discover and Highlights: *entirely* — both tabs say so on screen.
Search: the filtering logic exists and is unit-tested, but there is no search screen.
Duplicate detection: the algorithm exists and is unit-tested, but no worker scans the
library and no UI shows results.
Persian calendar: dates use the Gregorian calendar in both languages.

## Deliberate non-claims

* This is **not** a Huawei product and contains no Huawei code, fonts or image
  assets. The icons are original vector geometry drawn to the same optical weight.
* Hidden or recently-deleted albums are **not** encrypted storage, and this project
  never describes them as such.
* No visual-similarity percentage is claimed for any screen. `tools/compare_reference.py`
  reports individual structural measurements instead, because the two images contain
  different photographs and a single score would mostly measure content.
