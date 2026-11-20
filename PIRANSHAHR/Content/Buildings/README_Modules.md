# Piranshahr Modular Building Kit

Regional architecture: NW Iran — brick + concrete frame, flat roofs, 2-4 floors, bazaar storefronts on ground floor, small balconies, Persian signage.

## Kit Pieces (all 4 LODs, HLOD cluster)

| Piece | Dimensions | Material | Notes |
|---|---|---|---|
| Wall_3m | 3×3m | Plaster_01 / Brick_02 | Window-ready (socket) |
| Wall_4m | 4×3m | same | Door variant |
| Window_Single | 1.2×1.4m | Glass_01 | LOD0 glass + frame |
| Door_Standard | 1.0×2.2m | Wood_02 | Enterable trigger |
| Roof_Flat | 4×4m | Concrete_Roof_01 | Parapet variant |
| Balcony_2m | 2×1m | Iron_Balcony | NW Iran style |
| Shop_Shutter | 3×2.5m | Metal_Shutter | Bazaar |
| Mosque_Dome_Small | r=2m | Tile_Turquoise | For religious buildings |
| Sign_Fa | 2×0.4m | Sign_FA | Vazirmatn text decal |

## Placement Pipeline

1. OSM building footprint → `Tools/GIS/osm_to_geojson.py`
2. Houdini/Blender batch: footprint → kit assembly (random seed per building)
3. UE World Partition: spawn as `BP_Building_Master` with HLOD
4. Enterable heroes: 5 buildings get interior sub-level (separate streaming cell)

## Optimization (POCO X3 Pro)

- No Nanite on mobile — manual LOD (screen size 1.0 / 0.6 / 0.3 / 0.1)
- HLOD clusters: 8 buildings → 1 mesh at distance >500m, 2 draw calls
- Texture: ASTC 6×6, 1K max, streaming pool 600MB
- Draw call budget: <120 per frame

## Reference Photos

Stored in `SourceAssets/ReferencePhotos/` (CC-licensed)
