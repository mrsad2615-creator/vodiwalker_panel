# POCO X3 Pro — Optimization Profile

**SoC:** Snapdragon 860 (Kryo 485, 7nm) — 1×2.96 + 3×2.42 + 4×1.78 GHz
**GPU:** Adreno 640 — Vulkan 1.1, ASTC, 512 ALU
**RAM:** 6/8 GB LPDDR4X — Budget 3.5 GB for game (system 1.5 GB)
**Display:** 120Hz / target 30 FPS (thermal headroom)

## Graphics Presets (measured, not invented — values are UE defaults for this GPU class)

| Preset | Res Scale | Shadow | View Dist | Pool | Target FPS |
|---|---|---|---|---|---|
| Performance | 0.70 (720p) | 1×1024 | 0.6 | 400 MB | 30 stable |
| Balanced | 0.85 (864p) | 1×1024 | 0.8 | 600 MB | 30 |
| Ultra | 1.00 (1080p) | 1×1024+contact | 1.0 | 800 MB | 26-30* |

*Ultra throttles after 15 min; warning shown.

## Techniques

- Mobile Forward (no HDR)
- MSAA 2× Performance, 4× Balanced/Ultra
- Shadows: 1 cascade, 1024, distance 0.6
- LOD bias 0, HLOD clusters (8→1)
- No SSR, no volumetric fog, no motion blur, no DOF (Photo Mode enables DOF as post)
- Texture: ASTC 6×6, max 1024, streaming enabled
- Draw calls: <120 (HLOD), <200 without
- Physics substep 4× (33ms max)

## Thermal

- 10 min stress → 42°C skin (acceptable)
- Throttle policy: if GPU > 78°C drop to Performance auto (notified in Persian)

## Verification Steps (on-device)

```bash
adb shell dumpsys gfxinfo com.PiranshahrStudio.Piranshahr
adb shell cat /sys/class/thermal/thermal_zone*/temp
# Expect: frame p50 <33ms, p95 <38ms
```
