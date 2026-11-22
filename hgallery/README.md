# HGallery

A personal, local-only recreation of the Huawei Gallery experience for a Xiaomi
POCO X3 Pro. Kotlin, Jetpack Compose, MediaStore. No network permission, no
account, no cloud.

## Install

Download `hgallery.apk` from the [latest release](../../releases/latest) and open
it on the phone (allow installs from unknown sources). Android 8.0 or newer.
Verify the download against `SHA256SUMS.txt`.

## What works today

* **Photos** — timeline grouped by day (`Today`, `Yesterday`, absolute dates), and
  a **month view** with 6 columns, switched by pinching or by the toolbar control,
  exactly as Huawei's own manual describes.
* **Albums** — smart albums (*All photos*, *Videos*, *Favourites*) with 2×2 mosaic
  covers and counts, plus device folders under **MORE ALBUMS**.
* **Album detail** — tap an album to see its contents, back to return.
* **Viewer** — full-screen on black, paged swiping, pinch and double-tap zoom with
  clamped panning, favourite toggle, delete behind a confirmation.
* **Video playback** — Media3, with tap-to-toggle chrome, a scrubber, skip, a speed
  menu, pause-at-end with a replay control, and full screen in landscape.
* **Multi-selection** — long-press to select, batch share, and delete behind a
  confirmation that offers an undo snackbar.
* **Search** — a field that filters as you type, with chips for kind, size, recency
  and album that AND together, and an empty state that distinguishes "too narrow"
  from "nothing matches".
* **English and Persian**, with the right-to-left layout verified on a device.
* 77 unit tests over the domain logic (grouping, sorting, byte and duration
  formatting, selection, delete policy, player chrome policy, search); instrumented
  Compose tests over navigation, album drill-down, localisation and view switching.

## What does **not** work yet

The photo editor, EXIF details, Discover and Highlights content, custom albums,
recently-deleted and the duplicate-finder UI are **not implemented**. Controls that
lead nowhere are disabled rather than silently inert.
`docs/FEATURE_PARITY_MATRIX.md` lists every feature and its true status;
`docs/KNOWN_LIMITATIONS.md` explains each gap.

## Build

There is no local Android SDK in the environment this was developed in, so the
build runs in GitHub Actions:

```
.github/workflows/hgallery-android.yml
  ├─ Domain core tests        :core:test
  ├─ Build debug APK          assembleDebug + unit tests + lint (fatal)
  ├─ Emulator run             instrumented tests, launch, screenshots
  └─ Signed release APK       apksigner-verified, published
```

Every failure mode in that pipeline is reported back as readable annotations, and
`evidence/` contains screenshots taken from the running app.

## Layout

```
hgallery/
├── core/      pure Kotlin: grouping, sorting, filtering, similarity (no Android)
├── app/       Compose UI, MediaStore access, bounded thumbnail cache
├── docs/      specification, architecture, parity matrix, bug tracker, limits
├── evidence/  screenshots and diagnostics from the emulator, refreshed by CI
└── tools/     reference measurement and comparison
```

## Honesty rules this project follows

1. A feature is only called done when it has been built, launched and photographed.
2. No screenshot is reported unless the app was in the foreground when it was taken.
3. No claim of device performance is made without a device.
4. Uncertainty in the reference is documented, never invented over.
5. Hidden and recently-deleted albums are **not** described as encrypted storage.
