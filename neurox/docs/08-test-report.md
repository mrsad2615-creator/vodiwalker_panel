# Test report — 2026-11-24

## Current verified baseline

**Native source commit:** `4d96388cb072b027f14330a2cf2905a511bc3769`
**Version:** `0.4.0-dev`, version code 4
**Hosted run:** [45487593742](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/45487593742) — **SUCCESS**

| Check | Actual result | Scope |
|---|---|---|
| Kotlin core/security cases | **209 PASS, 0 FAIL** | Production domain/cipher; both languages; fourteen-exercise completion/recovery matrix |
| Static invariants and XML | **36 PASS** | Offline manifest, assets, preserved Python panel, known JUnit signature guard |
| Production migration SQL | **5 PASS** | Preservation, index, references, cascade, comparison with generated Room schema |
| CI evidence parser | **6 PASS** | Complete/reordered schema chunks; incomplete/duplicate/unsafe/malformed data rejected |
| Kotlin syntax | **46 files, 0 parse errors** | Grammar check; not a substitute for native compilation |
| Android compilation | **PASS** | App plus both Android test APKs, hosted SDK 36 / JDK 17 |
| Room/KSP and lint | **PASS** | Native job in the linked run |
| Hosted emulator instrumentation | **34 PASS, 0 FAIL, 0 skipped** | 23 data/migration cases and 11 app cases; API 35 x86_64 |
| Original Python panel | **Preserved** | Static hash comparison against the branch base |

This baseline passes the listed checks. It does **not** satisfy the complete product acceptance specification.

## APK and source integrity

[Download debug APK artifact](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/45487593742/artifacts/14964394216). GitHub access may be required. Extract `app-debug.apk` from the archive.

* APK bytes: **12,732,312**.
* APK SHA-256 (not the ZIP archive hash):

```text
2de5caae96298fd1647607aedd12005ce560f5828f3b2913303f336276b0d3b2
```

This is a **debug-signed development build**, not a production-signed release. It includes fourteen exercise paths, the finite builder and persistence hardening. Source/doc packaging after this run changes documentation only; the compiled Kotlin/Gradle baseline remains the commit above.

The APK exists on GitHub. Downloading GitHub log/artifact blob storage from this sandbox failed with EOF; no local APK download, installation or physical-device test is claimed. Debug signing keys can differ between CI runs. Do not uninstall an existing build to bypass a signature mismatch when it has valuable data. The integrated export/restore feature is not ready.

The generated Room-v2 schema is checked in at `core/data/schemas/com.neurox.data.NeuroxDatabase/2.json`. Its identity hash is `cfc53d208dc748bbea7b91f8748a7636`. The API evidence reader recovered complete numbered chunks from a successful native job. The first unchunked export was truncated and was rejected. A local SQLite check compares all migrated table columns, keys and indices with this generated schema. The Android migration test also opens the migrated database through Room.

## What the Android cases cover

Data/migration: profile histories, ownership rejection, profile deletion, idempotent completion, immutable session configuration, stale checkpoints, attempt-prefix protection, abort against latest durable progress, queued-write reactivation prevention, completed-row preservation, theme ownership, custom CRUD/duplicates/favorites, deletion/history snapshots, practice reward exclusion, invalid-save preservation, checkpoint compatibility, and additive migration from a hand-audited legacy fixture.

App: isolated home-to-exercise navigation, honest empty history, builder save-and-display, visual-search grid and pause, per-word sprint input, phrase-reading first frame, named color/direction actions and pause, real Activity settings navigation, and two live-ticker cases. The live-ticker cases use real SystemClock, the production main-thread ticker and Room to check unpublished waiting stimuli, active publication, timeout to MISSED, and no XP for an unanswered session.

Layout tests disable automatic timer publication in their isolated ViewModel fixture. This avoids Compose/Espresso idling draining an 8 ms reflex ticker until the session ends. Production Activity factories keep the default real ticker. The separate live-clock tests exercise that default. Layout tests are not presented as reaction-latency tests.

This is **not** end-to-end UI acceptance for all fourteen exercise paths, nor coverage of every device/lifecycle/fault scenario. Exact scoring, premature/wrong/missed outcomes, seeded stimuli, pauses and recovery receive additional deterministic core coverage.

## Failure history and corrections

Failures were retained and diagnosed, not deleted to obtain a green result.

| Run | Result | Finding and correction |
|---|---|---|
| `45479693104` | SDK setup failure | Replaced the failing SDK installer with the hosted runner's installed SDK |
| `45479907006` | Native job passed; emulator failed | Initial successful native build; detailed device results could not be downloaded |
| `45481036708` | 25 cases, 2 failures | Navigation tests used persistent application state and clicked without reliable visibility/arrival checks |
| `45482317590` | 27 cases, 1 failure | Isolated database fixed empty history; builder and Activity smoke passed; VISION card needed scroll-to-visible before click |
| `45483449140` | 28 cases, 1 failure | New color/direction layout scenario could not find expected actions; timing-only diagnosis was not yet proven |
| `45484467257` | 2 cases, 1 initialization error | A new expression-body coroutine test inferred a non-void return; the app suite was not reached. Added explicit Unit signatures and a static guard |
| `45485283383` | 32 cases, 2 failures | Data hardening passed; both reflex layout tests timed out. Increased waiting alone was not a solution |
| `45486497079` | 32 cases, 2 failures | Bounded semantics diagnostics showed RESULT/COMPLETE with three MISSED attempts, not a missing production control. Separated layout idling from live ticker execution |
| **`45487593742`** | **34 passed, 0 failed, 0 skipped** | **Both native and emulator jobs passed, including independent real-clock timer tests** |

The first local fourteen-exercise run also correctly failed the old three-per-module catalogue invariant. It was updated to the implemented 3/5/3/3 counts; the complete suite then passed. The append-only guard added ten production-domain regression cases. No failing behavior case was removed.

## Remaining acceptance gates

* Twenty of the required thirty-four exercises are not implemented.
* Reading Studio/import, full planner/QUEST/SENSE, PIN/Keystore/avatar, guided baseline, integrated transactional vault/restore and advanced analytics remain incomplete.
* Physical-device installation, TalkBack/Switch Access, large-font/contrast review, airplane-mode runtime acceptance, frame timing, memory, battery and reduced-motion/performance matrices have not been completed.
* No release signing or production APK acceptance exists. The version-1 migration fixture is hand-audited, not a historical Room-generated schema.
* The local sandbox still cannot download the complete Gradle/Google SDK toolchain. Hosted CI bypasses that limitation without TLS bypass or an unverified mirror.

## Reproduce

From `neurox/`:

```bash
scripts/test-core.sh
scripts/check-kotlin-syntax.sh
./gradlew :testing:run :app:assembleDebug :app:lintDebug \
  :core:data:assembleDebugAndroidTest :app:assembleDebugAndroidTest
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --continue
python3 scripts/read-ci-evidence.py 45487593742
```

The core script also runs the static, SQLite and six Python evidence-parser checks. Local raw evidence is ignored under `test-output/`. The evidence reader uses the existing authenticated GitHub CLI connection and does not read or store tokens. `--save-schema` accepts only complete schema chunks from a successful native build on the current branch. CI rejects success with missing module test evidence, no executed cases or reported failures.
