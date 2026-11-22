# IMPLEMENTATION_PLAN.md

## Done

1. Environment audit — established, with evidence, that no Android toolchain can
   exist locally and that GitHub Actions is the only build path.
2. CI pipeline — proven green end to end; failures surface as readable annotations.
3. Reference research and pixel measurement — palette and geometry taken from
   captures, not memory.
4. Design system — measured palette, type ramp, spacing, grid, motion.
5. Domain core — grouping, sorting, formatting, search, similarity, 25 unit tests.
6. Photos timeline — day view, month view, pinch and a toolbar switch.
7. Albums — smart albums with mosaic covers, counts, device folders.
8. Album detail — contents grid with back navigation.
9. Viewer — paged swiping, pinch and double-tap zoom, favourites, confirmed delete.
10. Localisation — English and Persian, verified on device.
11. Release — signed APK, `apksigner`-verified, published with a SHA-256.
12. Multi-selection — long press and toolbar entry, R3-style header, ticks,
    receding unselected tiles.
13. Safe deletion — confirmation, then the platform trash on API 30+, with the
    dialog text derived from the same decision so it cannot over-promise.
14. Share — batch and single, real `ACTION_SEND` with clip data and a grant.

## Next, in order of value

| Order | Work | Why it is next |
|---|---|---|
| 1 | Search screen | the filtering logic is written and tested; only the screen is missing |
| 2 | Recently-deleted *album view* | deletion is now safe at the platform level; an in-app album would show what is in the trash |
| 3 | Selection in album detail and the viewer | `Selection` is reusable; the screens are the work |
| 4 | Favourites reflected in the grid | the data is already tracked |
| 5 | Photo details / EXIF panel | needs a projected EXIF read |
| 6 | Media3 video playback | a gallery without video playback is incomplete |
| 7 | Editor | largest single item; needs a real non-destructive pipeline |
| 8 | Duplicate finder UI | algorithm and tests already exist |
| 9 | Persian (Jalali) calendar | affects every date header |
| 10 | Baseline Profile | only after real-device measurement |

## Working rules

* One change, one verification. The CI loop is ~5–7 minutes; guessing is slower.
* A failing screenshot is a bug, not an inconvenience to be worked around.
* Never widen a claim to match an intention. If it was not run, it is not written
  as though it were.
