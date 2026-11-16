# MESAD Design Council — Flagship Review

## The product decision
**Instrument, not widget.** The clock is the primary object; utility screens orbit it. The product resists template dashboards, oversized gradients, and fake 3D. Obsidian is the quiet stage; titanium provides silhouette; champagne marks the temporal zero; cyan is reserved for live precision.

## Perspective vote (consensus)

| Perspective | Vote | Reason |
|---|---|---|
| Industrial designer | Orbital ring + single zenith marker | One memorable silhouette; legible at 24px; no generic clock hands in the launcher icon. |
| Horology specialist | Brushed titanium, sapphire facets, champagne index | Finish hierarchy communicates craft without decorative overload. |
| Motion designer | Continuous second sweep and restrained orbital motion | Time should flow; motion is paused or simplified for performance/accessibility. |
| Android platform engineer | Native Compose, Room/DataStore, AlarmManager, Glance | Use system contracts, preserve offline state, request exact-alarm access in context. |
| Real-time graphics artist | Procedural geometry, controlled 3-point lighting, PBR-oriented materials | Shading must respond to actual 3D geometry; never fake depth by rotating a 2D picture. |
| Accessibility reviewer | Contrast, 48dp targets, reduced motion, spoken clock state | Luxury cannot exclude; seconds and animation are configurable. |

## Icon concept selected by user

Bespoke zenith/orbit mark on obsidian: a brushed titanium outer orbit, sapphire-like faceted core and a single champagne zenith marker, with restrained cyan inlay. The generated master image is retained in `mesad-assets/concepts/mesad_icon_master.png`; launcher-ready, minimal, monochrome, dark/light, adaptive layers and preview sheet are exported into `mesad-assets/icons/` and Android `res/`.

## Review decisions

- **ORBIT:** primary daily engine — articulated orbit and smooth precision hands.
- **CHRONOS:** mechanical reading — layered metallic chapter ring and engineered indices.
- **NEXUS:** experimental expression — spatial luminous rings and a deeper core.
- **Performance Mode:** explicit 2D procedural Canvas, never a static image masquerading as 3D.
- **Default motion:** slow and continuous; users can pause it or enter Performance Mode.
- **Privacy:** no account, no analytics, offline-first state.
