# NEUROX — repository audit and implementation plan

Audit date: 2026-11-24. Base: 78a60848f4419621cbb36a8e730cdb1e29ce64e8.

## Inventory and inspection

The repository is not empty. It contains an unrelated Python network panel. Do not replace it.

| File | Role | Lines |
|---|---|---:|
| main.py | FastAPI server, authentication, configuration, network management | 7520 |
| pages.py | Embedded HTML, CSS and JavaScript | 2662 |
| relay_vless.py | WebSocket relay | 211 |
| tcp_relay.py | TCP relay | 224 |
| xhttp_siz10.py | Streaming transport | 494 |
| speed_limit.py | Token-bucket throttle | 68 |
| sales.py | Plans and orders | 144 |
| telegram_bot.py | Telegram control interface | 1129 |

All Python source files were read by the audit script and parsed with Python AST. Top-level definitions, imports, exception handlers, timing calls, persistence calls and concurrency primitives were inspected. This is static inspection, not a full security audit. `README.md`, `CHANGES_FA.md`, `requirements.txt`, `Dockerfile.txt`, `env.example`, `railway.json`, and the data-file inventory were inspected. Private runtime data is not copied into NEUROX.

## Existing architecture and dependencies

The existing system uses Python, FastAPI 0.104.1, Uvicorn 0.24.0, HTTPX 0.25.1, WebSockets 12.0, aiofiles, cryptography and psutil. Deployment uses a Python 3.11 container and Railway. The system uses module-level dictionaries, asyncio locks and JSON files. There is no Android source, Gradle configuration, test suite or CI configuration. Circular runtime imports couple the relay and panel code. The English dependency versions are not a NEUROX dependency baseline.

## Risks and incomplete functionality

* Existing wildcard CORS with credentials needs a separate review.
* The monolithic panel and embedded pages make changes difficult to isolate.
* Broad exception handlers and scheduled persistence need failure tests.
* Background relay activity, unbounded histories and frequent persistence can affect resources.
* No existing automated test results establish correctness of the panel.
* All requested NEUROX functionality is new work. No medical benefit is established.
* This environment initially has no Java runtime, Android SDK, Gradle or emulator.
* Network access to Google Android downloads, Google Maven and Gradle distribution services is restricted. An Android build must not be reported as verified here without a successful build.

## Architectural decision

Add a separate native Android project in `neurox/`. Preserve the panel files and deployment configuration. This is a new Android product, not a framework migration of the panel. Use Kotlin, Compose, Room and DataStore. Keep domain and encryption modules free of Android dependencies. Do not use the old panel as a backend. The Android manifest must not request internet access.

## Tool setup

The sandbox uses a Java 17 runtime from `jdk4py==17.0.9.2` and the JVM Kotlin compiler from `kotlin-compiler@2.2.21`. These packages come from permitted package registries. The runtime is **not a full Android JDK/SDK**. It can execute the Kotlin compiler and domain tests. It is not an APK build environment. See `../scripts/bootstrap-tools.sh`.

## Controlled implementation sequence

0. Record inventory, dependencies, risks and acceptance gates (this document).
1. Add native build configuration, domain contracts, Room schema, preferences and profile foundation.
2. Add vector identity, theme tokens, bilingual navigation, dashboard and Color Studio foundation.
3. Implement one real, independently scored exercise in each engine. Test timing, input and completion. Keep all remaining exercise requirements in the backlog; do not substitute labels for functional exercises.
4. Add history-based recommendations and summaries. Exclude baseline, aborted and invalid sessions from training rewards.
5. Add transaction-safe XP and progress foundations; full quests and achievements remain gated.
6. Add library/search/favorites foundations. Deliver the custom builder in a subsequent verified milestone.
7. Add profile isolation and preferences. Add audio assets, focus handling and device haptics only with lifecycle tests.
8. Implement portable authenticated encryption and validation. Gate the Android vault UI on restore integration tests.
9. Run available automated tests. Run Android database, Compose, interruption, accessibility, security and performance tests in an Android-capable environment.
10. Produce a signed release and APK only after the release gates pass.

The full master specification remains the product scope. A milestone is not acceptance of a reduced product. See `12-release-notes.md` for the exact delivered scope and remaining work.
