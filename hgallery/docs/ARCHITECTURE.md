# ARCHITECTURE.md

## Modules

```
hgallery/
├── core/     pure Kotlin — no Android, no third-party runtime dependency
└── app/      Android + Compose
```

`core` is not a formality. Date grouping, album derivation, search filtering,
size/duration formatting and duplicate detection are the parts where a subtle
mistake is invisible in a screenshot and expensive in real use. Keeping them free
of Android means 20 unit tests run in ~10 seconds on a plain JVM, and a change
that breaks "Yesterday" fails immediately rather than in a manual pass.

The split is also a response to the environment: `core` tests run without an
emulator, so they are the fastest signal available in a project where the only
build environment is CI.

## Dependency rule

```
app  ──▶  core
```

`core` never knows about Android. It has no `Context`, no `ContentResolver`, no
`Uri` — media rows are plain `MediaItem` values. `app` maps the platform into those
values in `MediaRepository`.

## Data flow

```
MediaStore (platform)
   │  MediaRepository.loadAll()      — one projected cursor, DATE_TAKEN DESC
   ▼
MediaStore (app)                     — a StateFlow<List<MediaItem>>, the single truth
   │
   ├── PhotosScreen      → AppContainer.sections()  → DateGrouping.group()
   ├── AlbumsScreen      → AlbumBuilder.fromItems() / sort() / visible()
   ├── AlbumDetailScreen → AlbumBuilder.itemsOf()
   └── ViewerScreen      → items, with favourites and deletes patched in place
```

`MediaStore` is named after the platform concept it wraps, which is deliberate:
it is the app's own view of the device's media, and screens never touch a cursor.

**Load once, patch in place.** A favourite toggle rewrites one entry in the flow
rather than re-querying the platform. On a 20 000-item library a re-query is
visible as a stall; patching is instantaneous. Deletion removes the deleted ids
and leaves the rest of the list untouched.

## Thumbnails

`ThumbnailStore` is a byte-bounded `LruCache`, sized to a quarter of the heap and
capped at 48 MB. Cells request 256 px, which is the right size for a fourth of a
1200 px-wide screen.

An image-loading library was not used on purpose. What matters here is exactly two
things — the decoded size and the total budget — and both are explicit in ~90
lines rather than configured indirectly through a library's memory policy. It also
removes a dependency from a project that has to be buildable in one CI run.

Full-screen images are *not* taken from this cache: the viewer decodes its own
bitmap bounded to 2048 px, because a 48 MP file at full resolution needs ~190 MB
and would be killed on a 3 GB phone.

## UI composition

Screens are stateless with respect to data: they observe `MediaStore` and call into
`AppContainer`. There is no ViewModel layer, because there is no per-screen state
that survives recomposition except scroll position, which Compose already retains.

The shell (`HGalleryApp`) is a three-layer state machine: tabs → album detail →
viewer, with back handling that closes the innermost layer first.

The timeline is a **single** `LazyVerticalGrid` with date headers as full-span
items (`TimelineRowFactory`). The obvious alternative — a lazy column of lazy grids,
one per day — looks identical and is much slower, because every day keeps its own
scroll state and measured height.

## Permissions

`MediaPermissions` handles three regimes: `READ_EXTERNAL_STORAGE` up to API 32,
granular `READ_MEDIA_*` from API 33, and the API 34 partial-selection state, which
is reported as `PARTIAL` rather than as a failure. Writes go through MediaStore, so
no storage permission is needed from API 30 on.

There is no `INTERNET` permission. Nothing in this app can send a photograph
anywhere.

## Deliberate omissions

* **Room.** Nothing needs persisting that MediaStore does not already hold, and an
  edit history does not exist yet. Adding a database with no consumer would be
  ornament.
* **WorkManager.** The only background work would be duplicate hashing, which is
  not surfaced in the UI yet.
* **Media3.** Video playback is not implemented; the dependency would be dead
  weight until it is.
* **DI framework.** Four objects, one holder (`AppContainer`).
* **Navigation library.** Four sibling tabs and two overlays; a graph would be
  indirection.
