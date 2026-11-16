# MESAD — Design System
## Luxury Horology × Futuristic Precision

---

### 1. Color Palette (Material 3 Tokens)

| Token | Hex | Usage |
|---|---|---|
| `Obsidian` | `#06070B` | Primary background, icon base |
| `Graphite` | `#12161F` | Surface, cards, bottom sheet |
| `Titanium` | `#C8D0DC` | Primary text, orbital ring, indices |
| `TitaniumHi` | `#E8EDF5` | Highlights, crescent accent |
| `Midnight` | `#0B1A2E` | Sapphire core base, secondary surface |
| `Cyan` | `#00E5FF` | Accent, seconds hand, best lap, focus |
| `Champagne` | `#E6C79C` | Zenith index, bezel, premium accent |
| `Amber` | `#FFB347` | Worst lap, warning, snooze |
| `Emerald` | `#2EE6A8` | Success, running state |

**Dark Theme (Default):** Obsidian background, Graphite surfaces, Titanium text, Cyan/Champagne accents.  
**Light Theme:** Alabaster `#F2F4F7` background, White surfaces, Obsidian text, Midnight/Cyan accents.  
**Dynamic Color:** Material You sync — Cyan/Champagne shift to system seed, geometry unchanged.

---

### 2. Typography

| Style | Font | Weight | Size | Tracking |
|---|---|---|---|---|
| Display Large | `Space Grotesk` | 700 | 96sp | -2.0 |
| Display Medium | `Space Grotesk` | 600 | 64sp | -1.5 |
| Headline | `Inter` | 600 | 28sp | -0.5 |
| Title | `Inter` | 500 | 20sp | 0.0 |
| Body | `Inter` | 400 | 16sp | 0.2 |
| Label | `JetBrains Mono` | 500 | 12sp | 1.2 (uppercase) |
| Numeric | `JetBrains Mono` | 600 | 48sp | -1.0 |

**Persian (Jalali) Dates:** `Vazirmatn` for fa-IR locale, same metrics.

---

### 3. Shape & Elevation

- **Corner Radius:** 24dp (cards), 16dp (chips), 28dp (bottom sheet), Full (FAB, pills).
- **Elevation:** 0dp (flat obsidian), 1dp (Graphite surface), 3dp (floating clock), 6dp (dialog).
- **Glass Material:** `surfaceVariant` at 72% opacity + 16dp blur + 1dp Titanium border at 12% opacity.
- **Depth Layers:** Background (Obsidian) → Surface (Graphite) → Floating (Glass) → Foreground (Clock 3D).

---

### 4. Motion

| Token | Duration | Easing | Usage |
|---|---|---|---|
| `motionFast` | 150ms | `EmphasizedDecelerate` | Button press, chip select |
| `motionMedium` | 300ms | `Emphasized` | Screen transition, sheet |
| `motionSlow` | 600ms | `EmphasizedAccelerateDecelerate` | Clock engine switch |
| `motionClock` | Continuous | `Linear` (seconds), `Spring` (hands) | Analog hands, orbital rings |
| `motionPulse` | 2000ms | `Sinusoidal` | Next alarm indicator, recording dot |

**Clock Motion Rules:**
- Seconds hand: Continuous sweep (no tick), 60fps.
- Minutes/Hours: Spring physics (stiffness 120, damping 18).
- Orbital rings: Constant angular velocity, parallax on device tilt.
- Engine Switch: 600ms morph (scale 0.92 → 1.0, fade, ring rotation).

---

### 5. Materials (3D PBR)

| Material | Base Color | Metalness | Roughness | Clearcoat | Transmission |
|---|---|---|---|---|---|
| Brushed Titanium | `#C8D0DC` | 1.0 | 0.32 (anisotropic) | 0.1 | 0.0 |
| Sapphire Glass | `#0B2D48` | 0.0 | 0.05 | 1.0 | 0.92 (IOR 1.77) |
| Champagne Gold | `#E6C79C` | 1.0 | 0.18 | 0.3 | 0.0 |
| Obsidian Ceramic | `#06070B` | 0.2 | 0.42 | 0.6 | 0.0 |
| Cyan Emission | `#00E5FF` | 0.0 | 0.1 | 0.0 | 0.0 (Emissive 2.4) |

**Lighting Rig:**
- Key Light: Warm Champagne `#E6C79C`, 3.2 intensity, 45° upper-right.
- Fill Light: Cool Cyan `#00E5FF`, 1.1 intensity, 30° lower-left.
- Rim Light: Titanium White `#E8EDF5`, 2.0 intensity, behind.
- Environment: Procedural studio HDRI (Filament IBL), intensity 0.85.

---

### 6. Iconography

- **System Icons:** Material Symbols Rounded (outlined, 24dp, 2dp stroke).
- **Brand Icon:** Bespoke orbital/sapphire/zenith emblem (see `mesad-assets/icons/`).
- **Adaptive Icon:** 108dp container, 66dp safe zone, `<foreground>` + `<background>` + `<monochrome>`.
- **Monochrome:** Single-color stencil, no gradients, 5.8dp ring stroke, clean 12-o'clock gap.

---

### 7. Accessibility

- **Contrast:** Titanium on Obsidian = 15.2:1 (AAA). Cyan on Obsidian = 9.8:1 (AAA).
- **Touch Targets:** Min 48dp, Clock Studio sliders 56dp.
- **Motion:** Respects `AccessibilityManager.isAnimationEnabled` — disables orbital rotation if off.
- **TalkBack:** All clock engines announce time, date, next alarm. Laps announced on add.
