# Phase 1 — Player Session and Audio Policy

Document: verification record for the first implementation phase.
Commit: `3003025`.
CI run: https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/45336125604

This document uses the style of ASD-STE100 Issue 9 (2025-01-15): short sentences,
active voice, American English. It is not a certified compliance review. This
project has no approved terminology list certified by an ASD-STE100 reviewer.
Read `docs/TERMINOLOGY.md` for the terms this project uses.

## 1. Purpose

This phase closes defect A from `docs/AUDIT_2026-11-24.md`: a video played sound
with no picture. It also applies the audio rule in section 10.3 of the brief:

- A direct open plays with sound.
- A swipe onto a clip plays it muted.
- Manual mute and unmute are always available.
- Automatic advance does not depend on the mute state.

## 2. The cause

`rememberPlayerController` used `remember(uri)`. Each new clip therefore made a
new controller and a new `ExoPlayer`. The old player was released.

`AndroidView` runs its `factory` one time. It runs `update` after that. The view
kept the released player. Sound came from the new player. The picture came from
the released player. The platform reports no error for this condition. It draws
nothing.

## 3. The fix

1. The controller is remembered with no key. One controller lives as long as the
   screen.
2. A clip change calls `PlayerController.load()`. This replaces the media item
   on the same player. The surface keeps its binding.
3. `player` is a `val`. A player that can be reassigned is the shape that
   permitted the defect.
4. `PlayerSurface.update` compares `view.player` with `controller.player`. It
   sets the view's player when the two differ, unless the controller is
   released. This costs one reference comparison.

## 4. The audio policy

`PlayerPolicy.onArrival(entry)` returns one `Arrival(muted, autoplay)`.

| Entry | Muted | Autoplay |
|---|---|---|
| `DIRECT` (tap on a tile or a filmstrip cell) | no | yes |
| `SWIPE` (the viewer moves onto the clip) | yes | yes |

Reasons:

- Both paths autoplay. A clip that waits at frame zero looks like a still image.
- Only a deliberate choice makes sound. A clip that starts under a moving finger
  is not a request for sound.
- The volume is set before `prepare()` and before `playWhenReady`. A muted clip
  can therefore not make a noise while the surface attaches.

The "autoplay next" setting (`HgSettings.autoplayNext`) controls what occurs
when a clip ends. It does not control how a clip arrives. The two rules cannot
be derived from each other.

## 5. Related corrections

- `load()` sets `PlayerState(isPlaying = playWhenReady)`. The previous code used
  a default that says "paused". The chrome then drew a play button over running
  video, and it did not auto-hide, because the auto-hide rule reads `isPlaying`.
- The constructor uses the same initial state, for the same reason.
- `PlayerChrome` has a mute control: `HgIcons.SoundOn`, `HgIcons.SoundOff`,
  strings `video_mute` and `video_unmute` in both languages, test tag
  `video-mute`. The policy is only correct if the control it promises exists.

## 6. Defect C, first half

The viewer paged over clips and drew each one with `ZoomableImage`. That function
decodes a bitmap. A video URI fails that decode. A clip opened in the viewer, or
reached by paging from a photograph, drew a black page.

A clip now has a real page: the poster frame from the existing thumbnail cache,
a centered play target, the duration, and the file name. The play target opens
the clip in the player.

The viewer has `onOpenVideo`. The shell sends the id to the player with
`DIRECT`, because the user moved toward that clip on purpose. This does not
start a second player session.

## 7. Removed work, disclosed

An earlier draft added `core/ViewerReplace.kt` and nine tests for a
page-replacement rule. No code called it. The viewer is a pager of stills, so
the rule does not apply there. The file and its tests are deleted. Shipping it
would repeat the finding this project criticizes in its own audit:
`staggerFraction` and `SimilarityDetector` are correct code with no caller.

## 8. Test results

All results below come from CI run 45336125604 at commit `3003025`.

| Job | Result |
|---|---|
| Domain core (pure Kotlin tests) | success |
| Build debug APK | success |
| Signed release APK | success |
| Emulator run, UI tests and screenshots | success |

Test counts, from the source at this commit:

- Core unit tests: **144** `@Test` functions in 7 files.
- `VideoQueueTest`: 10 functions, 6 of them new for `videosAfter`.
- `PlayerAudioPolicyTest`: 4 functions, new. It replaced a test that asserted
  two constant functions returned their own literals.
- Instrumented test classes: 6.

## 9. What is not verified

State this clearly: this sandbox has no JDK, no Gradle distribution and no
Android SDK. `./gradlew` exits 127 for `:core:test`, `:app:lintDebug` and
`:app:assembleDebug`. No code in this phase was compiled or run on the machine
that wrote it. Every build and test statement above comes from CI.

Not verified:

- The audio rule on a physical device. The emulator job runs UI tests, but no
  test plays a clip and measures the stream volume.
- The 3-clip gesture path from the filmstrip. No instrumented test covers it.
- Picture-and-sound together. The CI emulator uses a software GPU and has no
  audio sink. A person must confirm this on a phone.

## 10. Errors found by CI in this phase

Three CI failures occurred before the green run. They are recorded because each
one found a real defect:

1. `FunctionOnlyReturningConstant` in `autoplaysWhenSwipedOnto()`. The function
   returned a literal. Detekt was correct. It became `onArrival()`.
2. Two missing arguments at the only call site of `PlayerTransport` and
   `PlayerActions`. An edit call failed and its failure was not checked.
3. `Platform declaration clash` for `setMuted`. `var muted` generates
   `setMuted(Z)V`. The file already documents this problem for `changeSpeed`.

## 11. Release

- Tag: `hgallery-v0.1.167`
- Commit: `3003025`
- APK size: 17,091,805 bytes
- Download: https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/hgallery-v0.1.167/hgallery.apk
- Checksum: see `SHA256SUMS.txt` on the same release. This document does not
  quote the value, because the download failed during this session and an
  unverified digest is worse than none.
