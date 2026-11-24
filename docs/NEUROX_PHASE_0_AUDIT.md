# NEUROX — Phase 0 Repository Audit

**Audit date:** 2026-11-24 (UTC)  
**Branch:** `arena/a9ba88c4-vodiwalker-panel`  
**Base:** `78a60848f4419621cbb36a8e730cdb1e29ce64e8`

## Executive summary

The checked-out repository is **VodiWalker**, a Python/FastAPI web control panel and relay service. It is not an empty project and it is not an Android application. There is no Android/Gradle project, Kotlin source, Android manifest, Compose UI, Room database, or Android test infrastructure. Replacing the existing project with NEUROX would remove an unrelated working product and is not a safe implementation of the instruction to preserve existing functionality.

This audit intentionally makes no changes to the VodiWalker application. NEUROX is being introduced as a separate native project under `android/`; it does not import or overwrite VodiWalker code or runtime data. The first Android foundation slice has since been added. It is explicitly incomplete and has not been built in this environment.

## Project inventory

| Area | Current contents |
|---|---|
| Runtime | Python 3.11 container; FastAPI application served by Uvicorn |
| Main application | `main.py` (~265 KB), with app setup, persistence/state, authentication, panel APIs, relay/proxy routes, and application lifecycle |
| Web UI | `pages.py` (~365 KB) containing large embedded HTML/CSS/JS documents |
| Relay engines | `relay_vless.py`, `tcp_relay.py`, `xhttp_siz10.py`, and `speed_limit.py` |
| Optional sales/bot | `sales.py`, `telegram_bot.py` |
| Configuration/deployment | `requirements.txt`, `env.example`, `Dockerfile.txt`, `railway.json` |
| Data | `data/` contains existing runtime/data artifacts; it must not be overwritten by an unrelated product |
| Tests/docs | No test suite, `pyproject.toml`, Gradle wrapper, Android manifest, or NEUROX documentation set was found |

## Architecture and dependencies

The current application is a single large FastAPI module with supporting Python modules. Several features import shared in-memory globals and functions directly from `main.py`. The web UI is embedded in Python strings rather than built as a separate frontend. Runtime persistence is file-based; there is no Room/SQLite domain repository architecture. The project dependencies are FastAPI 0.104.1, Uvicorn 0.24.0, HTTPX 0.25.1, websockets 12.0, aiofiles, cryptography, and psutil.

This is a description of the existing product, not a recommendation for NEUROX. NEUROX's native Android requirements call for Kotlin, Jetpack Compose, Room, DataStore, Coroutines/Flow, and Android-specific lifecycle, security, and test infrastructure. Those cannot be supplied by the current Python runtime.

## Validation performed

- Inspected repository file inventory, tracked files, deployment configuration, dependency list, application/module entry points, and representative relay/bot modules.
- Parsed all tracked top-level Python modules with `python -m compileall`: **passed**.
- Searched for existing test files and Android/Gradle project files: **none found**.
- Android compilation was not attempted: Java/Android SDK/Gradle are not available in this environment, and this checkout has no Android project.
- The compile check validates Python syntax only. It does not establish runtime correctness, security, or feature completeness.

## Risks and observations

1. **Product/scope mismatch (blocking):** NEUROX is a new Android cognitive-training product; the repository implements VodiWalker network service management. A direct rewrite is destructive and technically unrelated.
2. **No Android toolchain or project:** Android build/test acceptance cannot currently be demonstrated in this checkout.
3. **Large monolithic modules:** `main.py` and `pages.py` make isolated refactoring and comprehensive testing difficult.
4. **No automated tests detected:** relay, persistence, access control, and UI regressions currently lack an evident repository test suite.
5. **Offline-first conflict:** portions of the current web UI reference remote web assets (including hosted fonts/icons). That is unlike NEUROX's fully offline asset requirement.
6. **Persistence/security require separate review:** current file-based runtime state and environment configuration are not a substitute for NEUROX's profile isolation, Room migrations, or encrypted backup design.
7. **Existing runtime data:** `data/` is present and must be preserved. Do not use it for NEUROX data.

These are audit findings, not proof of exploitable vulnerabilities. A security assessment requires targeted threat modeling and tests.

## Recommended implementation plan

1. **Scope gate:** keep VodiWalker intact; establish a separate native Android project directory/repository for NEUROX. Confirm supported Android minimum/target SDK and signing/release ownership before release work.
2. **Native foundation:** Gradle/Kotlin/Compose app, module boundaries, design tokens and bilingual direction handling, navigation, Room/DataStore, profile-scoped repositories, lifecycle-aware session persistence, and CI/unit-test baseline.
3. **Identity and shell:** vector identity assets, skippable one-time intro, distinct dark/light themes and Color Studio, responsive home/navigation, localization and accessible components.
4. **Training vertical slices:** implement each of VISION, REFLEX, READ, and TYPE with real state machines, monotonic timing where applicable, persisted outcomes, interruption recovery, and deterministic unit/UI tests. Do not present placeholder metrics as real results.
5. **Shared product systems:** adaptive rules, planning, analytics, library/custom exercise schema, quests, profile isolation, local audio/haptics.
6. **Security and release:** validated transactional restore, authenticated encryption, security/performance/accessibility regression suites, bilingual user documentation, reproducible release build and APK. Report limitations rather than claiming unchecked acceptance.

The requested full product spans many substantial systems. It should be delivered in reviewed, testable phases, not represented as complete by an initial dashboard or successful build.
