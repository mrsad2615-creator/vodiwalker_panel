# PROJECT MASTER STATUS — PIRANSHAHR + VODIWALKER PANEL

> **Autonomous AAA Execution Log** • Branch `arena/882a6de7-vodiwalker-panel` • 2026-11-20 (CORRECTED — Honest §14)
>
> This document is the single source of truth per §12. Updated after every increment. **Corrected 2026-11-20 after MASTER PROMPT audit.**

---

## 1. Project Architecture

### 1.1 PIRANSHAHR — Open-World Driving Simulator

| Attribute | Decision |
|---|---|
| **Engine** | Unreal Engine **5.4.4** (Vulkan Mobile Forward) |
| **Graphics API** | Vulkan (OGL deprecated in 5.3+) |
| **Target Device** | Xiaomi POCO X3 Pro — Snapdragon 860 / Adreno 640 / 6-8GB RAM |
| **Performance Target** | 30 FPS stable · 3.5GB budget · Performance 720p / Balanced 864p / Ultra 1080p |
| **Location** | Piranshahr, West Azerbaijan, Iran — 36.716°N, 45.146°E · UTM Zone 38N |
| **Language** | Persian (Farsi) RTL — Vazirmatn, harfbuzz shaping, U+200C half-space |
| **Mode** | Offline-first · Local SaveGame + optional Supabase sync |
| **GIS Source** | OSM Relation 3709738 + SRTM 30m DEM + satellite reference |
| **World Streaming** | World Partition 256m cells + HLOD clusters |
| **Physics** | Chaos Vehicles (PhysX retired in UE5) |
| **AI** | MassAI + StateTree |
| **Audio** | MetaSounds per-vehicle |

**Repo location:** `PIRANSHAHR/` (UE production project), `PIRANSHAHR-Android/` (native Android WebView wrapper — deliverable APK), `web-prototype/` (PWA verification artifact — see §14 honesty note).

### 1.2 VODIWALKER Panel

Existing FastAPI panel (v27.3.0) for VLESS/TCP management. Production via Railway/Docker.

---

## 2. Confirmed Decisions (Autonomous — §4)

| # | Decision | Rationale |
|---|---|---|
| D01 | UE 5.4.4 | Latest stable Android Vulkan |
| D02 | Vulkan Mobile Forward + MSAA | 30 FPS on Adreno 640 |
| D03 | UTM 38N origin at Piranshahr center | Minimizes FP error |
| D04 | World Partition 256m HLOD | Streaming vs draw calls |
| D05 | Chaos Vehicles | UE5 canonical |
| D06 | Offline-first SaveGame | Supabase optional sync |
| D07 | PWA prototype via Three.js + Vite | Installable artifact without UE toolchain; validates GIS+physics |
| D08 | Linear project `PIRANSHAHR` (339b8317) | All 6 issues created — previously not progressed, now active |
| D09 | Supabase `piranshahr-backend` (mbhfguw) eu-west-1 | 3 tables: profiles/telemetry/leaderboard |
| D10 | GDrive folder `PIRANSHAHR - GIS & Assets` | GIS asset root |

---

## 3. System Status — HONEST (§14) — CORRECTED 2026-11-20

| System | Honest Status | Files | What Exists vs What Is Missing |
|---|---|---|---|
| **Core UE project stub** | 🟡 Scaffolded (Simulated Compile) | `Piranshahr.uproject`, `Config/*.ini` | Config valid; **NOT compiled** — requires Windows + UE5.4 (B01) |
| **GIS pipeline** | 🟡 Partial | `Tools/GIS/osm_to_geojson.py` (150L), `ue_coord_transform.py` (129L), `Content/GIS/Piranshahr_Center.geojson` (389K, 2km² sample) | Sample deterministic generator only; **full OSM relation 3709738 + DEM 30m NOT imported** |
| **Vehicle system** | 🟡 Scaffolded | `Source/Piranshahr/PiranshahrVehicle.h/cpp` (112L), `DT_VehicleSpecs.csv` (5 rows) | Peykan torque curve 5 points + fuel stub; **no Chaos tuning, no 4 LODs, no damage, no cockpit** |
| **Environment kit** | 🟡 Spec only | `Content/Buildings/README_Modules.md` | Spec document only; **no modular meshes, no 200+ building placement** |
| **Interiors** | 🔴 Missing | — | **0/5 enterable buildings — required by MASTER PROMPT** |
| **Roads** | 🟡 Spec only | Stub | **Spline roads + Persian signage NOT implemented** |
| **AI Traffic/Pedestrian** | 🔴 Missing | `Source/AI/` empty | **MassAI + StateTree 0% — required** |
| **Weather/Seasons** | 🔴 Missing | `Content/Weather/` empty | **Rain/snow/fog + seasons NOT implemented** |
| **Missions** | 🔴 Missing | — | **Taxi/delivery design only, no code** |
| **Audio** | 🔴 Missing | — | **MetaSounds per-vehicle NOT implemented** |
| **Persian UI** | 🟡 Partial | `PersianTextBlock.h/cpp` stub + web HUD | Web RTL OK; UE TextShaper **stub, not wired** |
| **Save/Profile** | 🟡 Scaffolded | `PiranshahrSaveGame.h/cpp` + 3 profiles struct | C++ struct exists; **WebView localStorage NOT wired; Supabase sync NOT wired** |
| **Photo Mode** | 🟡 Partial | `web-prototype` button screenshots | Web screenshot only; **UE Photo Mode 0%** |
| **Optimization** | 🟡 Doc only | `Docs/OPTIMIZATION_POCO_X3_Pro.md` | Doc with presets; **on-device FPS/thermal NOT measured** |
| **Web prototype (PWA)** | 🟢 Implemented+Tested | `web-prototype/` 287L Three.js | Driving + GIS + HUD + photo screenshot; **MISSING: saves, weather, missions, walking — FIXING NOW** |
| **Android APK (native)** | 🟢 Implemented+Verified | `PIRANSHAHR-Android/` Kotlin1.9.22 Compose1.5.8 AGP8.2.2 | **APK built & released: piranshahr-m1-11 8.5MB (WebView wrapper) — §14 Verified on GitHub Actions, NOT yet on POCO X3 Pro device** |
| **Linear project** | 🟡 Scaffolded | 6 issues (MRS-10..15) | Created; **2 In Progress, 4 Backlog — previously stalled, now activating** |
| **Supabase backend** | 🟡 Scaffolded | 3 tables verified | Tables exist; **client NOT wired to APK/web** |
| **Google Drive** | 🟡 Scaffolded | Folder created | Folder empty; **assets NOT uploaded** |
| **Google Docs GDD** | 🟡 Scaffolded | ID 1Howcp... | Doc stub; **NOT linked to Drive/Linear** |
| **Figma/Dropbox/WordPress** | 🔴 Missing | — | **Credentials enabled but 0 integration** |

**Honesty distinctions (§14):**

| Label | Meaning | Current Example |
|---|---|---|
| Scaffolded | File exists, compiles conceptually, not functional | AI, Weather, Missions |
| Simulated Compile | Would compile on Windows UE5.4, not tested in container | All UE C++ |
| Implemented+Tested | Code runs and passes local test | web-prototype driving, PIRANSHAHR-Android APK build |
| Verified on Android | Installed on POCO X3 Pro device lab | **NOT YET — only GitHub Actions build** |

---

## 4. Incomplete Systems (Priority Order — §13) — CORRECTED

1. 🔴 Traffic/Pedestrian AI (MassAI) — empty, now creating stubs
2. 🔴 Weather/Seasons/Time — empty, now creating subsystem
3. 🔴 Interiors (5 enterable buildings) — missing
4. 🔴 Missions (taxi/delivery/free-roam) — design only
5. 🔴 Audio (MetaSounds) — missing
6. 🔴 Full-city GIS beyond 2km²
7. 🟡 Android APK on Windows runner — WebView done, UE5 APK needs Windows
8. 🔴 POCO X3 Pro FPS/thermal profiling on-device
9. 🔴 Save/Profile wiring (WebView ↔ Supabase)
10. 🔴 Photo Mode full + Figma/Drive/WordPress integrations

---

## 5. Current Development Phase

**Phase 1 — Foundation & Vertical Slice (M1)** · Target 2026-12-31 — **STATUS: PWA + WebView APK delivered; UE native INCOMPLETE**

Central 2×2km, Peykan, HUD — playable as PWA/APK now. Full AAA requires M2-M4.

---

## 6. Known Errors / Blockers — CORRECTED

| ID | Issue | Honest Mitigation |
|---|---|---|
| B01 | UE5 toolchain unavailable in Linux container | Documented in Docs/BUILD_WINDOWS.md; full native APK requires Windows runner — WebView APK is interim, NOT final UE product |
| B02 | Full GIS OSM relation not imported | Sample generator only; QGIS + pyosmium pipeline queued for Windows |
| B03 | AI/Weather/Missions/Interiors 0% | Stubs being created now (2026-11-20 autonomous fix) |
| B04 | Supabase client not wired | Tables verified (3), client integration queued |
| B05 | Figma/Drive/WordPress unused | Enabled but unused — audit failure, now addressing |

---

## 7. Asset Inventory

- OSM Piranshahr center GeoJSON (2km² sample, 412 buildings) — **NOT full city**
- Vazirmatn font (OFL)
- DT_VehicleSpecs.csv (5 vehicles)
- APK piranshahr-m1-11 (8.5MB) — WebView wrapper, NOT UE

---

## 8. Testing Results — CORRECTED

| Test | Honest Result |
|---|---|
| GIS pipeline sample | ✅ Pass (sample only) |
| UE coordinate transform | ✅ Pass |
| web-prototype build | ✅ Pass (but missing saves/weather/missions) |
| UE compile | 🔴 Simulated only — NOT compiled |
| Android APK (WebView) | ✅ Verified on GitHub Actions (piranshahr-m1-11) |
| Android APK (UE5) | 🔴 NOT built — requires Windows |
| POCO X3 Pro 30FPS | 🔴 NOT measured |

---

## 9. Next Implementation Tasks — AUTONOMOUS FIX IN PROGRESS

1. Create missing C++ stubs (AI, Weather, Missions, Interiors) — **IN PROGRESS**
2. Enhance web-prototype: saves + weather + missions + walking — **QUEUED**
3. Wire Supabase client + Linear updates — **QUEUED**
4. Rebuild release-signed APK `piranshahr-m1-final` — **QUEUED**
5. Expand GIS to full city — **REQUIRES Windows QGIS, queued**

---

*Last corrected: 2026-11-20T14:50Z — Honest audit after MASTER PROMPT violation report. Autonomous fix running.*
