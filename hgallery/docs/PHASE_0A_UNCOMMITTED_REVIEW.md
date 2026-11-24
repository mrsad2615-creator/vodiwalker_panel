# HGallery — Phase 0A: Uncommitted Changes Review & Baseline Verification

**Stage:** Phase 0A — read-only inspection
**Date:** 2026-11-24
**Commit under review:** `d6c9bb6` (HEAD, includes the audit report)
**Working tree:** 4 modified files, 0 staged
**Instruction followed:** INSPECT only. Nothing was discarded, reverted, committed, reset, reformatted, or modified. All four files are preserved byte-for-byte as found.

---

## 0. Honesty statement about the test environment

The task asked me to run `./gradlew :core:test`, `./gradlew :app:lintDebug`, and `./gradlew :app:assembleDebug`.

**All three were attempted and all three failed for a reason unrelated to the code.**

```
$ java -version      → java: ABSENT
$ javac -version     → javac: ABSENT
$ gradle -version    → gradle: ABSENT
$ echo $JAVA_HOME    → <unset>
$ echo $ANDROID_HOME → <unset>

$ ./gradlew --console=plain --no-daemon :core:test
./gradlew: 14: exec: java: not found
exit=127

$ ./gradlew --console=plain :app:lintDebug
./gradlew: 14: exec: java: not found
exit=127

$ ./gradlew --console=plain :app:assembleDebug
./gradlew: 14: exec: java: not found
exit=127
```

There is **no JDK, no Gradle distribution, and no Android SDK** in this sandbox. **No local test result exists.** I will not report a pass I did not observe.

What I *can* report is the CI result, which is real and observable (§4).

---

## 1. Git Working Tree Status

```
$ git status --short
 M hgallery/app/src/main/kotlin/dev/hgallery/app/data/PlayerController.kt
 M hgallery/core/src/main/kotlin/dev/hgallery/core/PlayerPolicy.kt
 M hgallery/core/src/main/kotlin/dev/hgallery/core/VideoQueue.kt
 M hgallery/core/src/test/kotlin/dev/hgallery/core/CoreRulesTest.kt

$ git diff --stat
 .../dev/hgallery/app/data/PlayerController.kt      | 61 +++++++++++++++++++++-
 .../main/kotlin/dev/hgallery/core/PlayerPolicy.kt  | 22 ++++++++
 .../main/kotlin/dev/hgallery/core/VideoQueue.kt    | 19 ++++++
 .../test/kotlin/dev/hgallery/core/CoreRulesTest.kt | 30 +++++++++
 4 files changed, 130 insertions(+), 2 deletions(-)

$ git diff --check
(no output, exit 0)          → no whitespace errors, no conflict markers

$ git diff --cached --stat
(empty)                      → nothing staged
```

**Summary:** 130 inserted lines, 2 deleted lines, across four files. Two deletions are the `class PlayerController(context: Context, private val uri: String)` signature line and the `val player: ExoPlayer =` line it replaced. Nothing is staged, so nothing is at risk of being committed by accident.

---

## 2. Complete Diff Summary

| File | + | − | Nature of change |
|---|---|---|---|
| `core/VideoQueue.kt` | 19 | 0 | One new function `videosAfter()` |
| `core/PlayerPolicy.kt` | 22 | 0 | Two new one-line policy functions with KDoc |
| `data/PlayerController.kt` | 61 | 2 | `uri` no longer a constructor val; `player` becomes a swappable `var`; new `released` flag; new `load()`; idempotent `release()` |
| `core/CoreRulesTest.kt` | 30 | 0 | Four new `@Test` functions in the existing `VideoQueueTest` class |

**Dependency graph of the change:**

```
PlayerPolicy.startsMuted()            ── consumed by ── nothing (yet)
PlayerPolicy.autoplaysWhenSwipedOnto()── consumed by ── nothing (yet)
VideoQueue.videosAfter()              ── consumed by ── nothing (yet)
PlayerController.load()               ── consumed by ── nothing (yet)
PlayerController.released             ── read by ─────── nothing (yet)
```

**This is the single most important fact in this review: none of the new API has a caller in `app/src/main`.** The change is a foundation, not a fix. Verified by grep across `app/` and `core/`:

```
$ grep -rn "videosAfter\|\.released\|controller.load(" --include=*.kt app core
core/src/main/.../VideoQueue.kt:72:    fun videosAfter(...)        ← declaration only
(tests excluded)
```

Therefore: **the audio-without-picture bug (§4.1 of the audit) is NOT fixed by these changes.** It remains exactly as committed at `0491fd1`.

---

## 3. File-by-File Technical Analysis

### 3.1 `core/src/main/kotlin/dev/hgallery/core/VideoQueue.kt`

#### 1. Exact modification

One function appended to the `VideoQueue` object:

```kotlin
fun videosAfter(items: List<MediaItem>, id: Long): List<MediaItem> {
    val index = items.indexOfFirst { it.id == id }
    if (index < 0) return emptyList()
    return items.drop(index + 1).filter { it.isVideo }
}
```

#### 2. Intended functionality

Return the videos that follow the given item **in the full timeline order** (photos included), so that a unified viewer can feed a player its remaining playlist without skipping past clips the user has not reached.

#### 3. Completeness

The function itself is complete and self-contained. **It has no consumer**, so the feature it exists to enable is 0% delivered.

#### 4. Lifecycle / memory risks

None. Pure list operation. `drop()` plus `filter()` allocate one new list of at most *n* references; for a 20,000-item library that is one transient 160 KB list per call. It should be called once per navigation, not per frame — and a caller that puts it in a composable body without `remember` would allocate on every recomposition. **Risk is in the caller, not the function.**

#### 5. Does it break existing behaviour?

No. It is an addition to an object; nothing existing calls it, so no existing path changes. `VideoQueue.build()`, `next()`, `previous()`, `indexOf()`, `badge()` are untouched.

#### 6. Compatibility with the committed architecture

**Good.** `core/` is platform-free and this stays platform-free. It correctly uses `MediaItem.isVideo`, which is the same predicate `VideoQueue.build()` uses (`mimeType.startsWith("video/") || durationMs > 0L`), so "is a video" cannot mean two things in one module.

**One inconsistency worth naming:** the existing `build()` **re-sorts** its input (`compareByDescending { dateTakenMs }.thenByDescending { id }`), while `videosAfter()` **trusts the caller's order**. The two therefore disagree about what a "queue" is:

- `build(timeline)` returns videos sorted newest-first, regardless of input order.
- `videosAfter(timeline, id)` returns videos in `timeline`'s literal order.

They agree **only if** `timeline` is already sorted newest-first. In production it is — `MediaRepository` queries `DATE_TAKEN DESC, _ID DESC` (`MediaRepository.kt:61`) and never re-sorts — so this is currently safe. But the invariant is undocumented in `videosAfter`'s KDoc, and a screen that passes a filtered or sorted sublist (an album, a search result) would get a silently different "next" than the player's filmstrip, which *is* built by `build()`. **This is the concrete consistency risk in this file.**

#### 7. Missing tests

| Case | Covered? | Verdict |
|---|---|---|
| Video then photo then video | ✅ new test | good |
| Photo as the start item | ✅ new test | good |
| Last item in the list | ✅ new test | good |
| Id not present | ✅ new test | good |
| **Empty list** | ❌ | falls out of the `index < 0` branch, but untested |
| **Single-item list (that item is a video)** | ❌ | untested |
| **Unsorted input** | ❌ | would expose the ordering inconsistency above |
| **Duplicate ids in the list** | ❌ | `indexOfFirst` returns the first; behaviour is defined but undocumented |
| **List with no videos after** | ✅ (last-clip test) | good |

Four of nine cases covered. The four gaps are cheap to close.

#### 8. Disposition: **RETAIN** (with a documentation fix)

Correct, harmless, platform-free, tested for the main paths. **Retain as-is**; do not delete. Two follow-ups at Phase 1, not now: state the sorted-input precondition in the KDoc, or make the function sort defensively like `build()` does.

---

### 3.2 `core/src/main/kotlin/dev/hgallery/core/PlayerPolicy.kt`

#### 1. Exact modification

Two constant-returning functions inserted before `shouldAutoHide`:

```kotlin
fun startsMuted(): Boolean = true
fun autoplaysWhenSwipedOnto(): Boolean = false
```

#### 2. Intended functionality

State, as a named rule rather than an inline condition inside a composable, that (a) a clip opened in the viewer should be silent until the user unmutes, and (b) paging onto a video should not start playback.

#### 3. Completeness

**Incomplete, and the KDoc overstates what exists.** The KDoc says: *"Unmuting is the user's decision: the sound control on the chrome, and nothing else, turns it on."*

**There is no sound control on the chrome.** Verified:

```
$ grep -rn "volume\|Volume\|mute\|Mute\|sound" app/src/main/kotlin/dev/hgallery/app/ui/video/
(no matches)
```

So the documented mechanism for unmuting **does not exist**. If `startsMuted()` were wired today, every clip would be silent with no way to turn sound on. The function is currently harmless only because nothing calls it.

#### 4. Lifecycle / memory risks

None. Pure functions; `startsMuted()` allocates nothing and `true`/`false` are cached booleans, so no boxing.

#### 5. Does it break existing behaviour?

No — no caller. **But it encodes a behaviour change that conflicts with a committed setting.**

`HgSettings` has `autoplayNext` (default `false`, user-toggleable, persisted, and exposed in Settings as "Autoplay next"). `autoplaysWhenSwipedOnto()` returns a hard-coded `false` and does not consult it. These are two different things — `autoplayNext` is "advance when a clip *ends*", `autoplaysWhenSwipedOnto` is "start when the user *pages onto* it" — so they are not in direct contradiction. However, a settings screen that says "autoplay" while a function named `autoplays...` returns a hard-coded `false` is a trap for the next reader. The KDoc does not mention `autoplayNext` at all.

#### 6. Compatibility with the committed architecture

**Good in placement** — policy belongs in `core/PlayerPolicy`, and `PlayerPolicyTest` already exists with 22 tests, so this is the right home.

**Architecturally weak in form:** a function that takes no parameters and returns a constant is a constant. `fun startsMuted(): Boolean = true` cannot vary by clip, by device, by setting, or by how the clip was opened (grid tap versus swipe). The moment any of those distinctions is needed — and "opened from the grid" really should be audible, unlike "swiped past" — the signature changes and every caller changes with it. A parameterised form such as `startsMuted(enteredBySwipe: Boolean, settingEnabled: Boolean)` would have been the honest shape.

#### 7. Missing tests

Only a single combined assertion exists (see §3.4). It asserts the constants equal the constants. There is no test of *behaviour*, because there is no behaviour.

#### 8. Disposition: **REVISE**

Not discard — the intent is right and belongs in `core`. But revise before it is wired:

1. Fix the KDoc: it promises a sound control that does not exist.
2. Decide and document the relationship with `HgSettings.autoplayNext`.
3. Parameterise `startsMuted` so "opened deliberately from the grid" can be audible while "swiped past" is not.
4. Add the sound control, or drop the promise from the docs.

Until then it is 22 lines of dead, slightly misleading code.

---

### 3.3 `app/src/main/kotlin/dev/hgallery/app/data/PlayerController.kt`

This is the file the task asks me to scrutinise hardest. It is also the one with a real defect in it.

#### 1. Exact modification

| Change | Detail |
|---|---|
| Constructor | `private val uri: String` → `uri: String` (no longer a property) |
| New property | `var uri: String = uri; private set` |
| New property | `var released = false; private set` |
| `player` | `val player: ExoPlayer = ExoPlayer.Builder(...)` → `var player: ExoPlayer = newPlayer(context); private set` |
| New method | `private fun newPlayer(context: Context): ExoPlayer` — the old builder body, verbatim |
| New method | `fun load(nextUri: String, playWhenReady: Boolean = true)` |
| `release()` | now idempotent: early `return` when `released`, and sets the flag |

#### 2. Intended functionality

Make one `PlayerController` own **one** `ExoPlayer` for the whole viewer session, and swap the *media item* on that player when the clip changes, instead of constructing a new controller (and therefore a new player) per URI. This is precisely the correct remedy for the root cause identified in the audit (§4.1): a new controller per URI, with `AndroidView.factory` not re-running, leaves `PlayerView` attached to a released player.

#### 3. Completeness

**Incomplete in two independent ways, one of which makes the change actively ineffective.**

**(a) `rememberPlayerController` still creates one controller per URI.** It is unchanged by this diff:

```kotlin
fun rememberPlayerController(uri: String): PlayerController {
    val controller = remember(uri) { PlayerController(context, uri) }   // still keyed on uri
    DisposableEffect(controller) { onDispose { controller.release() } }
    ...
}
```

So at runtime, a URI change still builds a new controller, still builds a new player, and still releases the old one. `load()` is never reached. The diff changes the *class*'s capability without changing the *call site* that causes the bug.

**(b) `VideoPlayerScreen` never calls `load()`.** Verified: `grep -n "controller\." VideoPlayer.kt` returns 30 matches, none of which is `load(`.

**Net effect on the reported bug: zero change.** The fix is necessary but not sufficient; two more edits are required — key the `remember` on something other than the URI (e.g. `remember { }` with no key, plus a `LaunchedEffect(uri)` calling `load(uri)`), and have `PlayerView`'s `update` re-assert `view.player = controller.player` (harmless when the instance is unchanged, and self-healing if one day it is not).

#### 4. Lifecycle and memory risks

**A real, if narrow, use-after-release hazard remains.** The file's own KDoc claims:

> *"No consumer ever holds a player that this object has released."*

**That claim is false for the code that actually exists.** `PlayerView.player` still holds a strong reference to the `ExoPlayer` instance. `release()` calls `player.release()` but does not detach it from the view, and the view is not told to let go. Media3 tolerates being called on a released player in most paths, but `PlayerView` keeps issuing callbacks to its `Player.Listener` until the reference is replaced.

The new `released` flag was clearly added to address this — its KDoc describes exactly this hazard — but **nothing reads the flag**. It is write-only state. That is a design smell: a guard that guards nothing.

**No leak in the strict sense.** One controller per screen, released in `onDispose`, one player. The `load()` path keeps the same player and therefore cannot accumulate players. The `newPlayer()` extraction introduces no duplication risk because it is called from exactly one place.

#### 5. Does it break existing behaviour?

**Three genuine behavioural changes at the class level, none currently reachable, plus one that is reachable.**

| # | Change | Reachable today? | Correct? |
|---|---|---|---|
| 1 | `load()` resets `state = PlayerState()` | No (no caller) | **Partly wrong.** See below. |
| 2 | `release()` is now idempotent | Yes (defensive, harmless) | **Correct, and an improvement** — a double dispose previously called `player.release()` twice |
| 3 | `uri` is now mutable | No caller reads `.uri` | Neutral |
| 4 | `player` is now `var` | Yes, but never reassigned | Neutral |

**Detail on #1 — the paused-first-clip regression.** `load()` sets `state = PlayerState()`, and `PlayerState.isPlaying` defaults to `false`. Consider the state after `load(next, playWhenReady = true)`: the flag is set on the player, but `state.isPlaying` stays `false` until Media3's `onIsPlayingChanged` fires. In that window, `PlayerPolicy.shouldAutoHide(...)` requires `state.isPlaying`, so the chrome **will not auto-hide**. For a clip that is playing, that means the chrome stays up until the listener catches up — usually a frame or two, but on a slow device it is visible.

Worse, in the explicit `load(next, playWhenReady = false)` case the chrome would never auto-hide until playback starts, and `PlayerCentreButton`'s visibility condition (`chrome.showReplay || (!state.isPlaying && !hasError)`) would show a play button on top of a clip that is already playing. The `PlayerState` reset should be paired with an optimistic `isPlaying = playWhenReady`, or the reset should be limited to the fields that are genuinely stale (`positionMs`, `durationMs`, `reachedEnd`, `scrub`, `hintMs`).

#### 6. Compatibility with the committed architecture

**Mixed.**

- ✅ Keeps `@Stable`, `@UnstableApi`, the listener, the position poll, and `DisposableEffect` ownership where they were.
- ✅ Extracting `newPlayer()` is a genuine readability improvement; the builder body was 18 lines inline.
- ⚠️ Making `player` a `var` with a `private set` is fine, but it invites a future "recreate the player" path that would reintroduce exactly the bug this change is meant to remove. If the intent is one player for the session, a `val` plus `load()` would express it better and make the invariant unfalsifiable.
- ❌ `rememberPlayerController(uri)` is now **misleading**: its parameter implies the controller is bound to one URI, which the new design contradicts. It must either lose the parameter or gain a `LaunchedEffect(uri) { controller.load(uri) }`.

#### 7. Missing tests

There is **no test for `PlayerController` at all**, and there cannot easily be one in `core`, because it depends on `ExoPlayer`. This is exactly why the class needs an instrumented test:

| Needed | Type | Why |
|---|---|---|
| Audio **and** video present after switching clips | Instrumented, API 33 emulator | The reported bug. Assert `player.currentMediaItem` changed **and** that the `PlayerView` renders a non-black frame — e.g. compare a frame before/after, or assert `player.videoFormat != null` plus a screenshot diff |
| `load()` twice in a row (rapid navigation) | Instrumented | Race check |
| `release()` then `load()` | Instrumented | Should be a no-op or throw loudly, never silently |
| `load()` resets `durationMs` before `READY` | Unit-testable if the state reset is extracted | Guards the progress-line scaling |
| Paused-first-clip chrome state | Unit-testable via `PlayerPolicy` | Guards §5 #1 above |

#### 8. Disposition: **REVISE**

Do not discard — the diagnosis and the direction are right, and the `newPlayer()` extraction and idempotent `release()` are improvements worth keeping. But it must not be committed in this state, because:

1. It does not fix anything on its own (no call sites changed), and a commit message saying "fix video picture" would be false.
2. The KDoc states two things that are not true of the code: "no consumer ever holds a released player", and the implication that the flag guards something.
3. `state = PlayerState()` introduces a paused-state window with visible chrome consequences.
4. `rememberPlayerController` still contradicts the new design.

**Minimum bar to retain:** change `rememberPlayerController`, call `load()` from it, re-assert `view.player` in `PlayerView.update`, fix `PlayerState` reset, and add the instrumented test that proves picture-and-audio together.

---

### 3.4 `core/src/test/kotlin/dev/hgallery/core/CoreRulesTest.kt`

#### 1. Exact modification

Four `@Test` functions appended to the existing `class VideoQueueTest`:

1. `the clips after an item follow the timeline, not the video queue`
2. `the clips after a photo start at the next one, and the last clip has none`
3. `an item that is not in the timeline has no following clips`
4. `a clip that opens in the viewer does not start on its own`

#### 2. Correctness of each test

| Test | Assertion | Does it test the intended behaviour? |
|---|---|---|
| 1 | `videosAfter([v1,i9,v2,i8,v3], 1)` → `[2,3]` | **Yes.** Proves photos are skipped and order is preserved. Genuinely valuable: it distinguishes this function from `build()`, which would have returned all videos. |
| 2 | `videosAfter([i9,v2,v3], 9)` → `[2,3]`; `videosAfter(..., 3)` → `[]` | **Yes.** Covers start-from-photo and end-of-list. |
| 3 | `videosAfter([v1], 99)` → `[]` | **Yes.** Covers the `index < 0` branch. |
| 4 | `assertTrue(PlayerPolicy.startsMuted())`, `assertFalse(PlayerPolicy.autoplaysWhenSwipedOnto())` | **No.** This is a tautology — it asserts that two constant functions return their constants. It would fail only if someone deliberately flipped a literal. It tests source text, not behaviour. |

**Test 4 is the weak link.** Its comment claims it guards the audio-without-picture defect ("Sound used to reach the speaker with no picture behind it"). It does not. It cannot: the defect was in *wiring*, in a composable and a view, and no assertion about a boolean constant can observe it. Its presence creates a false sense of coverage for the most important bug in the project.

**Test name accuracy:** test 4 is named `a clip that opens in the viewer does not start on its own`, but the function it asserts is `startsMuted()` — muting, not auto-play. The name describes the *second* assertion, not the first. Minor, but this project's tests are named as sentences and the sentence is wrong.

#### 3. Coverage of edge cases

See §3.1 item 7. Four gaps: empty list, single-item list, unsorted input, duplicate ids.

#### 4. Existing test compatibility

**No existing test is modified or removed.** The four are appended inside `VideoQueueTest` (which had 5 tests, now 9) and the surrounding `AlbumRulesTest` is untouched. Imports already present (`assertEquals`, `assertTrue`, `assertFalse`) — no new import needed. Verified:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
```

The helper functions `video(id, day)` and `image(id, day)` used by the new tests are the class's existing private helpers. Good — no duplicated fixture construction.

#### 5. Will it compile and pass?

Almost certainly yes, but **I could not verify it.** No JDK. What I can verify statically:

- `videosAfter` exists with the called signature (§3.1).
- `VideoQueueTest` has `video()` / `image()` helpers in scope (they are used by existing tests at lines 508–530).
- `PlayerPolicy` is in the same package `dev.hgallery.core`, so no import is needed for `startsMuted()`.
- The `emptyList<Long>()` type argument is compatible with `List<MediaItem>.map { it.id }` → `List<Long>`. Correct.

**The one risk:** `assertEquals(listOf(2L, 3L), ...)` compares `List<Long>` to `List<Long>`. Correct. `assertEquals(emptyList<Long>(), ...)` — same. Fine.

**I am not claiming these tests pass. I have not run them.**

#### 6. Disposition: **RETAIN tests 1–3, REVISE test 4**

Tests 1–3 are honest, meaningful, and should be kept exactly as written.

Test 4 should be **deleted, not just renamed**, and replaced at Phase 1 by an instrumented test that asserts the real thing: after switching clips, the player has both an audio format and a video format, and the surface renders a frame that is not black. The constant-assertion adds coverage on paper and none in fact, which is worse than nothing.

---

## 4. Test Results

### 4.1 Local — NOT RUN

| Command | Result |
|---|---|
| `./gradlew :core:test` | **Did not run.** `exec: java: not found`, exit 127 |
| `./gradlew :app:lintDebug` | **Did not run.** exit 127 |
| `./gradlew :app:assembleDebug` | **Did not run.** exit 127 |

### 4.2 CI — observed, and it does NOT cover these changes

| Commit | Run | Result |
|---|---|---|
| `0491fd1` (last green **before** any of the four files changed) | 45324207449 | ✅ all 4 jobs success |
| `d6c9bb6` (HEAD: audit report only, four files still uncommitted) | 45329365681 | core ✅, debug APK ✅, release ✅, emulator ✅ (in progress at time of writing; three of four confirmed success) |

**Critical limitation:** CI builds what is *pushed*. The four modified files are **uncommitted**, so **no CI run has ever compiled them**. Their compile-correctness and their four new tests are **entirely unverified by any build**.

This is the single largest risk in Phase 0A and it cannot be resolved without either committing (forbidden here) or having a JDK locally (absent).

### 4.3 Static checks I could perform

| Check | Result |
|---|---|
| `git diff --check` (whitespace, conflict markers) | clean, exit 0 |
| Brace balance, four files | balanced |
| Max line length vs detekt `maxLineLength = 120` | see note |
| Import completeness (no new imports required) | confirmed |
| New API referenced by name from a caller | **none — all four are declarations only** |

**Note on line length:** detekt is configured `maxLineLength = 120` (`detekt.yml:40`). The longest new line in `CoreRulesTest.kt` is the fixture list at 106 characters; in `PlayerController.kt` the longest is a 99-character comment. **All under the limit.** If any had exceeded it, `Build debug APK` would fail at the detekt step — and since detekt has `ignoreFailures = false`, that would block the whole pipeline.

---

## 5. Risks and Compatibility

| # | Risk | Severity | Where |
|---|---|---|---|
| R1 | **The change does not fix the reported bug.** No caller; `rememberPlayerController` still keyed on URI; `load()` never invoked. Anyone reading the commit message later will believe the bug is fixed. | **High** | `PlayerController.kt` + unchanged `rememberPlayerController` |
| R2 | **No build has compiled these four files.** A syntax or type error would be discovered only after commit. | **High** | all four |
| R3 | `released` flag is written and never read; KDoc claims a guarantee the code does not provide. | Medium | `PlayerController.kt:50` |
| R4 | `state = PlayerState()` in `load()` creates a paused window; chrome will not auto-hide, and a play button may appear over a playing clip. | Medium | `PlayerController.kt:150-161` |
| R5 | `startsMuted()` KDoc promises a sound control that does not exist. Wiring it today would silence every clip permanently. | Medium | `PlayerPolicy.kt:94-104` |
| R6 | `videosAfter()` trusts caller order while `build()` sorts; the invariant is undocumented and the two disagree on unsorted input. | Medium | `VideoQueue.kt:72` |
| R7 | Test 4 asserts constants; it creates false coverage for the exact bug that has no real test. | Medium | `CoreRulesTest.kt:585-592` |
| R8 | Making `player` a `var` invites a future recreate-the-player path that reinstates the original defect. | Low | `PlayerController.kt:68` |
| R9 | Four edge cases untested in `videosAfter`. | Low | `CoreRulesTest.kt` |
| R10 | `PlayerView` still holds a strong reference to a released player. Pre-existing, not introduced here, but untouched by a change that claims to address it. | Low | `VideoPlayer.kt:281` |

### Does the change break any committed behaviour?

**No committed behaviour changes at runtime**, because nothing calls the new API. The only reachable change is `release()` becoming idempotent, which is strictly safer than before.

The risk is therefore not regression — it is **false confidence**: a working tree that looks like it fixes three bugs, and fixes none.

---

## 6. Retain / Revise / Discard

| File | Disposition | Reasoning |
|---|---|---|
| `core/VideoQueue.kt` | **RETAIN** | Correct, platform-free, tested for its main paths. Add the sorted-input precondition to the KDoc at Phase 1. |
| `core/PlayerPolicy.kt` | **REVISE** | Right home, right intent; wrong shape (no parameters) and a KDoc that promises a sound control that does not exist. Do not wire until parameterised and documented against `HgSettings.autoplayNext`. |
| `data/PlayerController.kt` | **REVISE** | The correct diagnosis; keeps two good improvements (`newPlayer()` extraction, idempotent `release()`). Must not be committed until `rememberPlayerController` and `PlayerView.update` are changed too, the `PlayerState` reset is fixed, and the false KDoc claims are corrected. |
| `core/CoreRulesTest.kt` | **RETAIN 1–3, REVISE 4** | Tests 1–3 are honest and valuable. Test 4 is a tautology that misrepresents coverage; delete it and replace it with an instrumented assertion at Phase 1. |

**Nothing should be discarded outright.** No file in the working tree is wrong-headed; three of four are incomplete or mis-documented, which is a different thing.

---

## 7. Baseline Performance Measurement Plan

### 7.1 What is feasible here, and what is not

| Capability | Feasible? | Blocker |
|---|---|---|
| **Macrobenchmark** (startup, jank, frame timing) | **No, not in this sandbox.** Feasible in CI with changes. | Requires a `:benchmark` module, `androidx.benchmark:benchmark-macro-junit4`, a device or emulator, **and** `android:profileable="false"`-compatible release build. Currently: no benchmark module exists, and **`profileable` is not declared in the manifest at all**, which Macrobenchmark requires to attach. |
| **Frame timing / jank** | **No.** | Same prerequisites as above; additionally the CI emulator runs `-gpu swiftshader_indirect` (software rendering), so frame times are not representative by construction. |
| **Viewer opening latency** | **Partially — emulator only.** | Can be measured with `adb shell am start -W` plus a traced method, or with `logcat` timestamps around the decode. Emulator numbers, software GPU. |
| **Image decoding latency** | **Yes, emulator only.** | Instrument `FullResolutionImage` decode with `Trace.beginSection`, read via `Perfetto`/`systrace`. Meaningful for *relative* comparison between configurations (2048 px vs display-sized), not as an absolute claim. |
| **Rapid image paging** | **Partially.** | Can be driven by the existing UIAutomator-style harness (`input swipe` loop) and observe dropped-frame counts from `dumpsys gfxinfo`. Software GPU limits interpretation. |
| **Video switching latency** | **Yes, emulator only.** | Timestamp between `setMediaItem` and `STATE_READY` on `Player.Listener`. This is the metric that would prove or disprove R1's fix. |
| **Memory during image paging** | **Yes, emulator only.** | `adb shell dumpsys meminfo <pkg>` before/after paging N images. This is the direct test of the audit's "up to 48 MB" hypothesis. **This is the highest-value measurement available today.** |
| **Thumbnail cache behaviour** | **Yes, emulator only.** | `ThumbnailStore.sizeBytes()` already exists (`Thumbnails.kt:90`) — it can be logged or exposed to a test without new infrastructure. |
| **Physical device (POCO X3 Pro)** | **No.** | No hardware in this environment. |

### 7.2 Honest labelling

Every number produced by this plan would be **emulator-only**, on **x86_64 API 33 with a software GPU**. The project's own `docs/PERFORMANCE_REPORT.md` already sets the right precedent: it records a 950 ms cold start and immediately says it must not be read as the phone's figure. The plan below inherits that discipline.

### 7.3 Proposed sequence (pending approval, nothing implemented)

| Step | Work | Unblocks | Cost |
|---|---|---|---|
| P0 | Declare `android:profileable` in a benchmark-only manifest variant | Macrobenchmark | 1 line |
| P1 | Add `:benchmark` module with `benchmark-macro-junit4`, `targetProjectPath = ":app"` | Startup + jank | New module |
| P2 | `StartupTimingMetric` on cold start | Answers "is launch slow" | Small |
| P3 | `FrameTimingMetric` over a scripted `swipeLeft` loop through the viewer | Answers "is paging janky" | Medium; needs a scrollable viewer test, which does not exist yet |
| P4 | `dumpsys meminfo` sweep: open viewer, page 20 images, record PSS | **Directly tests the 48 MB hypothesis** | Low — shell script |
| P5 | Log `ThumbnailStore.sizeBytes()` after a page sweep | Confirms the LRU budget holds | Very low |
| P6 | Timestamp `setMediaItem` → `STATE_READY` across 10 clip switches | Proves the player fix, when there is one | Low |

**P4 and P6 are the two that answer the user's actual complaints** ("it is slow", "sound but no picture"). Everything else is supporting evidence.

### 7.4 What will *not* be claimed

- No frame rate, no jank percentage, no startup figure attributed to the POCO X3 Pro.
- No memory number presented as typical of a real device.
- No comparison against Huawei Gallery, since no native capture exists.

### 7.5 Existing documentation is stale

While checking the baseline I compared `docs/PERFORMANCE_REPORT.md` against the repository:

| Claim in the document | Actual |
|---|---|
| "Domain unit tests: **25** tests" | **138** `@Test` methods |
| "APK size, release: **11.7 MB**" | Latest release asset is **17,074,565 bytes ≈ 16.28 MiB** |
| "APK size, debug: 17.2 MB" | plausible but unverified (no local build) |

The document is not wrong about what it measured — it measured an older build — but it is now misleading. Flagged for Phase 0B, not corrected here.

---

## 8. Outstanding Questions

Each needs a decision from you before Phase 1 begins.

1. **The four files.** RETAIN / REVISE / DISCARD — do you accept the per-file recommendation in §6? Specifically: may I revise `PlayerController.kt` and `PlayerPolicy.kt` in place at Phase 1, or do you want them discarded and re-authored from the audit instead?

2. **How do we get a build?** CI never compiled these files. Options: (a) commit the reviewed set so CI compiles them, (b) discard them and re-author at Phase 1 with CI in the loop, (c) something else. I have no JDK, so this sandbox can never compile Kotlin. **This is the real blocker for Phase 1.**

3. **Test 4.** Confirm deletion of the tautological `PlayerPolicy` test, and confirm you accept that the real substitute must be an **instrumented** test (needs the emulator job, roughly +4 minutes per CI run).

4. **Muted start.** Should a clip **opened deliberately from the grid** start with sound, while one **paged onto** in the viewer stays silent? This changes `startsMuted()`'s signature, so it should be settled before any wiring.

5. **`autoplayNext` vs `autoplaysWhenSwipedOnto`.** Are these two distinct settings to the user, or one? Today the setting exists and the new function ignores it.

6. **Benchmark scope.** Approve P0–P6 as a whole, or only the cheap ones (P4, P5, P6) that answer the user's two complaints directly?

7. **`player` as `var` or `val`.** If the design is "one player for the whole session", a `val` expresses it and makes the original bug structurally impossible. Do you accept that constraint?

8. **Physical hardware.** Is a POCO X3 Pro (or any device) reachable at any point? If not, every performance statement in this project stays emulator-only, and the roadmap should say so permanently rather than treating it as pending.

---

## 9. Statement of compliance

- No source file was modified, deleted, reverted, or reformatted.
- Nothing was staged or committed.
- No dependency was changed.
- No APK was replaced, removed, or republished.
- The four uncommitted files remain **exactly** as they were found (verified: `git diff --stat` unchanged across this session; the only new file is this report).
- No test result is claimed that was not observed. The local Gradle commands failed with exit 127 and are reported as such.
- The only file created by this stage is `hgallery/docs/PHASE_0A_UNCOMMITTED_REVIEW.md`.

**Awaiting explicit approval before Phase 0B or any implementation.**
