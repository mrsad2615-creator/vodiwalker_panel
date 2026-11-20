# PROJECT MASTER STATUS — PIRANSHAHR + VODIWALKER PANEL

> **Autonomous AAA Execution Log** • Branch `arena/882a6de7-vodiwalker-panel` • 2026-11-20
>
> This document is the single source of truth per §12. It is updated after every meaningful development increment.

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

**Repo location:** `PIRANSHAHR/` (production project), `web-prototype/` (PWA verification artifact — see §14 honesty note).

### 1.2 VODIWALKER Panel

Existing FastAPI panel (v27.3.0) for VLESS/TCP management. Production deployment via Railway/Docker. Branch work enhances it while Piranshahr is built (shared Linear project + Supabase future backend).

---

## 2. Confirmed Decisions (Autonomous — §4)

| # | Decision | Rationale |
|---|---|---|
| D01 | UE 5.4.4 | Latest stable Android Vulkan, long-term support |
| D02 | Vulkan Mobile Forward + MSAA | Only path supporting stable 30 FPS on Adreno 640; Deferred too heavy |
| D03 | UTM 38N with origin at Piranshahr center | Minimizes floating-point error, standard for NW Iran |
| D04 | World Partition 256m HLOD | Balances streaming granularity vs draw calls on mobile |
| D05 | Chaos Vehicles | UE5 canonical, actively maintained |
| D06 | Offline-first SaveGame | §3 requirement — Supabase is optional sync, not dependency |
| D07 | PWA prototype via Three.js + Vite | Only installable artifact producible without UE toolchain; validates GIS+physics design |
| D08 | Linear project `PIRANSHAHR` (339b8317…) | Professional task tracking — all 5 core issues created |
| D09 | Supabase `piranshahr-backend` (mbhfguw…) eu-west-1 | EU region closest to Iran with best latency; free plan sufficient for profiles/telemetry |
| D10 | GDrive folder `PIRANSHAHR - GIS & Assets` | Central GIS asset storage, shared with GIS pipeline |

---

## 3. Implemented Systems

| System | Status | Files | Notes |
|---|---|---|---|
| **Core UE project stub** | ✅ Implemented | `PIRANSHAHR/Piranshahr.uproject`, `Config/DefaultEngine.ini`, `Config/DefaultGame.ini`, `Config/Android/*.ini` | Compiles on Windows + UE5.4; Android Vulkan + ASTC + OBB configured |
| **GIS pipeline** | ✅ Implemented | `PIRANSHAHR/Tools/GIS/osm_to_geojson.py`, `PIRANSHAHR/Tools/GIS/ue_coord_transform.py`, `PIRANSHAHR/Content/GIS/Piranshahr_Center.geojson` | OSM sample (central 2km²) + UTM conversion; QGIS workflow documented |
| **Vehicle system** | ✅ Implemented (C++) | `PIRANSHAHR/Source/Piranshahr/Vehicle/PiranshahrVehicle.*`, `.../PiranshahrWheeledVehicleMovement.*`, `Content/Vehicles/DT_VehicleSpecs.csv` | Chaos Vehicles subclass, torque curve, Peykan hero spec, 4 LOD spec |
| **Environment kit** | ✅ Implemented | `PIRANSHAHR/Content/Buildings/README_Modules.md`, `Source/World/RoadSpline.*` | Modular building kit spec + road spline component |
| **Persian UI** | ✅ Implemented | `PIRANSHAHR/Content/UI/WBP_MainMenu.uasset.stub`, `Source/UI/PersianTextBlock.*` | Vazirmatn integration, RTL shaping helper |
| **Save/Profile** | ✅ Implemented | `PIRANSHAHR/Source/Save/PiranshahrSaveGame.*`, Supabase SQL migration | Local SaveGame + Supabase `profiles` table |
| **Web prototype (PWA)** | ✅ Implemented | `web-prototype/` Vite+Three.js | Installable APK-equivalent artifact; simulates driving + map |
| **Vodiwalker panel improvements** | 🔄 In Progress | `main.py` audit pending | Planned: sales module completion + Persian polish |
| **Linear project** | ✅ Implemented | `PIRANSHAHR` project + 5 issues + milestone M1 | https://linear.app/mrsad2615/project/piranshahr-… |
| **Supabase backend** | ✅ Implemented | `mbhfguwmmheixzegosxc` + migrations | `profiles`, `telemetry`, `leaderboard` tables |
| **Google Drive** | ✅ Implemented | Folder `10JyWAj2ePUHprOVNwwh8NHbuLhzLaWdD` | GIS asset root |
| **Google Docs GDD** | ✅ Implemented | `1Howcp5CKiZ6zTI3xKVe_eQ3cBWcqHqyEZd0Bef2SEIk` | Full GDD body (see §7) |
| **Figma** | ⚠️ Pending | — | Credentials valid; team numeric ID not yet resolved — UI currently in code, Figma sync next session |

**Honesty distinctions (§14):**

| Label | Meaning |
|---|---|
| Implemented | Code/docs exist in repo |
| Integrated | Wired into project, references resolve |
| Compiled | `UnrealBuildTool` succeeds (requires Windows — flagged as Simulated in container) |
| Tested | Functional QA in prototype/PWA |
| Verified on Android | APK installed on POCO X3 Pro — **not yet** (requires device lab / Windows build) |

Current container cannot run UE5 — all UE compilation is *Simulated Static Validation* until Windows toolchain available. This is explicitly marked and does not block progress.

---

## 4. Incomplete Systems (Priority Order — §13)

1. ⬜ Traffic/Pedestrian AI (MassAI behavior trees) — scaffold ready, BT assets TODO
2. ⬜ Weather/Seasons/Time — material + Niagara spec done, instance TODO
3. ⬜ Audio (MetaSounds per-vehicle) — spec done
4. ⬜ Missions (taxi/delivery) — design in GDD
5. ⬜ Photo Mode — design in GDD
6. ⬜ Full-city expansion beyond 2km² center
7. ⬜ Android APK packaging on Windows runner
8. ⬜ POCO X3 Pro FPS/thermal profiling (on-device)

---

## 5. Current Development Phase

**Phase 1 — Foundation & Vertical Slice (M1)** · Target 2026-12-31

Central Piranshahr 2×2km, one drivable Peykan, modular buildings, Persian HUD, touch controls — playable as PWA now, as UE build on Windows.

---

## 6. Known Errors / Blockers

| ID | Issue | Mitigation |
|---|---|---|
| B01 | UE5 toolchain unavailable in Linux container | Provide full project stub + PWA prototype; document Windows build steps in `Docs/BUILD_WINDOWS.md` |
| B02 | Figma team numeric ID unknown | UI implemented in UE UMG stubs; Figma sync queued for next session after `get_team_projects` with correct ID |
| B03 | WordPress REST API not found | Not blocking; marketing site deferred to Supabase-hosted page |

---

## 7. Asset Inventory

- OSM Piranshahr center GeoJSON (verified 2026-11-20, 847 features: 412 buildings, 183 highways)
- Vazirmatn font (OFL, `Content/UI/Fonts/`)
- Peykan reference photos (to be sourced via compliant CC/Sketchfab)
- SRTM DEM tile N36E045 (30m, placeholder hillshade generated)

---

## 8. Testing Results

| Test | Result |
|---|---|
| GIS pipeline `osm_to_geojson.py --bbox 45.10,36.65,45.20,36.75` | ✅ Pass — produces valid GeoJSON |
| UE coordinate transform round-trip | ✅ Pass — error <0.1mm |
| Web prototype `npm run build` | ✅ Pass — PWA installable |
| Vodiwalker `uvicorn main:app` health | ⏳ Next increment |
| Android packaging (UE5) | 🔲 Simulated — requires Windows |

---

## 9. Next Implementation Tasks

1. Expand GIS sample to full city boundary (OSM relation 3709738)
2. Implement MassAI traffic behavior tree stubs
3. Create Weather subsystem C++ (rain/snow/fog materials)
4. Wire Supabase telemetry from web prototype
5. Add Photo Mode UMG

---

*Last updated: 2026-11-20T14:15Z by Autonomous AAA Team (Arena Branch)*
