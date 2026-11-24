# Map performance: what was costing something, and what now guards it

The map optimisation pass of **2026-11-24**: what was actually slow or
wasteful, what changed, and — just as important — the eight things that
*looked* like problems and turned out to be already correct, so nobody spends
an afternoon "fixing" them again.

| | |
|---|---|
| **Date** | 2026-11-24 |
| **Branch** | `mvvm-main` |
| **Library** | `org.osmdroid:osmdroid-android:6.1.20` (unchanged — no dependency was added) |
| **Status** | Built, unit-tested (**1041 tests, 0 failures**; 10 new), `lintDebug` 0 errors / 199 warnings — one *fewer* than before. **Nothing in it has been watched on a phone yet**: every behaviour below needs a running map, and the project has no mocking library and no instrumented tests beyond the template. Checklist in [`osmdroid.md`](osmdroid.md) §8, "Loading, state and incremental updates". |
| **Owns it** | [`ui/track/OsmdroidConfig.kt`](../app/src/main/java/com/example/guardband/ui/track/OsmdroidConfig.kt), [`ui/track/MapTilerTileSource.kt`](../app/src/main/java/com/example/guardband/ui/track/MapTilerTileSource.kt), [`ui/track/MapTilerMap.kt`](../app/src/main/java/com/example/guardband/ui/track/MapTilerMap.kt), [`ui/track/TrackFragment.kt`](../app/src/main/java/com/example/guardband/ui/track/TrackFragment.kt), [`ui/incident/PathSimplifier.kt`](../app/src/main/java/com/example/guardband/ui/incident/PathSimplifier.kt), [`data/repository/FirebaseAlertRepository.kt`](../app/src/main/java/com/example/guardband/data/repository/FirebaseAlertRepository.kt) |
| **Related** | [`osmdroid.md`](osmdroid.md) §2.4, §2.5, §5.2, §5.6 (the library-level detail), [`incident-view.md`](incident-view.md) §4 (the path), [`../CLAUDE.md`](../CLAUDE.md) ("Track and map", "Alert data") |

This file is the **reasoning and the record**: why each change exists and what
it is allowed to do. The rules themselves live in `osmdroid.md` and
`CLAUDE.md`, which is where they are enforced. If this file and one of those
disagree, **they win** and the disagreement is a bug in this file.

---

## 1. Already correct — do not "fix" these

The brief that started this pass asked for several things the app had already
done, some of them for a month. Changing any of them would be a regression,
so they are recorded here with where to look.

| Looks like a problem | Why it is not |
|---|---|
| "The `MapView` is recreated on every tab switch" | It is not. `HomeActivity` adds each tab Fragment **once by tag** and then `show()`/`hide()`s it, which is the whole reason tabs keep their state. [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) |
| "The selected layer is lost" | `MapLayerPrefs` has persisted it per phone since 2026-10-27 (SharedPreferences `map`), and `TrackUiState.selectedLayer` carries it across a theme change. |
| "Tiles are not served from cache first" | osmdroid's provider order is assets → **cache** → archive → approximation → downloader. An *expired* cached tile is served through `mapTileRequestExpiredTile` and refreshed in the background, so offline shows what you already looked at. |
| "It pre-fetches tiles it does not need" | `FLAG_NO_BULK` and `FLAG_NO_PREVENTIVE` are in `MapTilerTileSource.POLICY` and must stay. `TilesOverlay` asks only for the tiles a draw pass needs. `osmdroid.md` §4.0 |
| "Failed tiles get cached" | `TileDownloader` returns null **before** its `saveFile` for anything that is not HTTP 200, and `BitmapTileSourceBase.getDrawable(String)` deletes a cached file that will not decode. |
| "Failed tiles are never retried" | They are, on the next draw pass: `mapTileRequestFailed` caches nothing, so the tile is simply missing and asked for again. |
| "Geocoding repeats for the same point" | It does not. `AlertViewModel` rounds each point to 4 decimals (~11 m, `AlertPlaces.keyOf`), keeps a `placesRequested` set, never asks twice, and only re-asks an *unavailable* answer after `PLACE_RETRY_MS` (60 s). `IncidentViewModel` does the same for its one point. [`alert-data.md`](alert-data.md) §7 |
| "The app polls the phone's location" | It cannot. There is **no location permission in the release manifest at all** — the band supplies the position. `CLAUDE.md`, "Track and map". |
| "Each screen opens its own band listener" | One host-scoped `AlertViewModel` serves Track's sheet, Alert history and the Device tab; the pins read the list the Fragment hands over and open no listener. The Incident view's own query is the one deliberate extra, and it is removed with the Fragment. |

---

## 2. What was actually costing something

Nine findings, in the order they were worth fixing.

### 2.1 The 30-second tick redrew the map

`TrackViewModel` emits a new state every `TICK_INTERVAL_MS` (30 s) because
`elapsed` has aged. `TrackFragment.render` then called `showFix`, which
unconditionally reassigned the marker's position, called
`controller.animateTo` and `mapView.invalidate()`.

So **a band standing perfectly still re-animated the camera twice a minute**,
for as long as the user was looking at the screen. The tick itself is right
and has to stay — it is what lets "Reporting" go stale with no emission to
react to. What was wrong was treating a new *state* as a new *position*.

### 2.2 One new alert rebuilt all ten pins

`renderPins` detached and recreated **every** `Marker` whenever anything in
the list changed, and force-closed the open label on the way. A single
arriving alert therefore made all ten pins and any open label blink. The old
code said so in its own KDoc — "All of them are replaced rather than diffed" —
on the reasoning that ten markers is cheap. It is cheap; the *flicker* is what
costs.

### 2.3 Identical Firebase snapshots propagated as new state

RTDB fires `onDataChange` for a write **anywhere** under the query and
re-delivers the whole window on a reconnect. `AlertViewModel.render` sets
`nowMillis = clock.nowMillis()` on every read, so an identical snapshot still
produced a *different* `AlertUiState`, which the `StateFlow` could not
conflate. Downstream, that re-ran `AlertPinSelector`, re-submitted both
adapters and re-rendered the map for data already on screen.

### 2.4 The incident polyline carried every point

`AlertRepository.INCIDENT_WINDOW` is 400 entries and the mock sender reports
every 5 s, so the `Polyline` could hold a few hundred points — each
re-projected to pixels on **every frame of a pan** — almost all of them a band
walking in a straight line.

### 2.5 The disk tile cache was osmdroid's default: 600 MB

`DefaultConfigurationProvider.tileFileSystemCacheMaxBytes` is
`600L * 1024 * 1024`, trimming to 500 MB. `OsmdroidConfig` set the cache
*paths* (into `cacheDir`, correctly) but never the *budget*, so a map that
only ever shows one city was entitled to 600 MB of a possibly-16 GB phone.

### 2.6 The memory tile cache only ever grows

`MapTileCache.ensureCapacity` raises the capacity and **never lowers it**, so
it ends a session at the largest screenful that session ever needed.
`garbageCollection` removes off-screen tiles only once the cache is *over*
that capacity. Nothing was tuned for a phone with no memory to spare, and with
`cacheMapTileOvershoot` at 0 a tile that scrolled one pixel off screen was
dropped as soon as a pan pulled a new one in.

### 2.7 A tab switch never paused the map

**`FragmentTransaction.hide()` does not move a Fragment's lifecycle state.**
Track stayed RESUMED the whole time the user was on Contacts or Device, so
`onPause` — and therefore `mapView.onPause()` — was never reached until Log
Out. This is the most counter-intuitive thing in the pass, and it is true of
every Fragment in the host, not just this one.

### 2.8 Zoom and centre were lost on a view recreation

A theme change (Settings → Display) recreates the Activity and so the
Fragment. `setUpMap` then applied D5's default view unconditionally: the map
reopened on **Cebu City at zoom 13** and jumped to the band at zoom 16 when
the first fix arrived — a double camera move, over tiles it had to fetch
again, while the tiles for where the user *was* sat cached and unused.

### 2.9 A 200 response that is not a tile was cached and drawn

`TileDownloader` writes the body to the cache **before** calling
`tileSource.getDrawable`, so a response that decodes but is not a map tile — an
error graphic or a placeholder served with 200 — was cached and rendered like
any other tile. That is the exact shape of the OpenStreetMap "access blocked"
image in [`osmdroid.md`](osmdroid.md) §3, the trap that already cost this
project an afternoon.

---

## 3. What changed

Grouped by what it protects. Each row points at the rule that now enforces it.

### 3.1 Loading and state

| Change | Where | Rule |
|---|---|---|
| Camera (centre, zoom, `followBand`, `hasZoomedToFix`) round-trips through `Fragment.onSaveInstanceState`; `setUpMap` restores it instead of applying the default view | `TrackFragment` | `osmdroid.md` §5.2 |
| Memory cache keeps a real off-screen buffer: `cacheMapTileOvershoot` = 12 tiles (~3 MB), **0** on a low-RAM phone | `OsmdroidConfig` | `osmdroid.md` §2.4 |

**The buffer is a buffer, not a pre-fetch.** `cacheMapTileOvershoot` is how
many tiles that have *just left the screen* survive
`MapTileCache.garbageCollection`, so panning back costs nothing.
`FLAG_NO_PREVENTIVE` stays on, so no tile is ever **requested** for an area
the user is not looking at. Those are two different things, and this pass did
not change the second.

### 3.2 Rendering

Three memos, each guarding one kind of redraw:

| Memo | Guard |
|---|---|
| `appliedLayer` | the tile source is swapped only when the layer changes (already there, 2026-10-27) |
| `markerLocation` | the band marker moves, the camera animates and the map is invalidated **only for a new position** |
| `appliedPins` + `pinMarkers` | a pin is matched to its marker by `sequenceId`: gone pins lose their own marker, survivors keep theirs (moved only if the spot moved), new pins get one marker |

Three details in the pin diff are load-bearing and easy to break:

- **The marker carries its pin as its `relatedObject`,** not captured in the
  tap listener. A reused marker that still held the pin it was *built* with
  would report the wrong alert.
- **The folder's item order is the drawing order.** `reorderPins` puts it back
  to oldest-first after an add or a remove, so where two pins overlap on
  screen the newest is on top and takes the tap. `FolderOverlay.getItems()` is
  documented as the folder's own list and holds nothing but these markers, so
  reordering it moves **references** — it detaches, re-creates and redraws
  nothing.
- **An open label follows its own pin.** It stays open while its pin is there
  and has not moved (so it no longer blinks when an unrelated alert arrives),
  is reopened by `renderPinLabel` when the pin moved, and closes with the pin
  when it goes. osmdroid's `InfoWindow` does **not** follow a marker that
  moves under it, which is why "moved" has to count as a change.

Deduplication happens one layer down, in `FirebaseAlertRepository.asFlow`,
which now ends in `distinctUntilChanged` — and `observeLatestCheckIn` again
after its selector, because the window changes whenever the band sends
anything and almost none of that is a check-in. Every value these flows carry
is a data class (`Alert`, `AlertHistory`, `CheckIn`) or an `AlertError`
object, so the comparison is structural and correct. **A retry subscribes to a
new flow**, so it still reports its first emission, failure included.

The incident path is thinned by `ui/incident/PathSimplifier` — see §4.

### 3.3 Compatibility and reliability

| Change | Where |
|---|---|
| Disk cache = 20% of free space under `cacheDir`, clamped 16–64 MB, trim at 75%, computed **once per process** | `OsmdroidConfig` |
| `MapTileCache.setStressedMemory(true)` on a phone Android itself calls low-RAM | `MapTilerMap.prepare` |
| Both `getDrawable` forms refuse an image that is not a plausible tile (square, 64–1024 px) and delete the cached file when the path is known | `MapTilerTileSource` |

The cache budget is a **share, not a number**, so a nearly full phone keeps a
usable map and a roomy one still never fills up. A filesystem that cannot
answer reports 0, which lands on the 16 MB floor — the same place a full phone
lands, and the floor is the right answer for both. `usableSpace` is a
filesystem call and both Track and the Incident view call
`OsmdroidConfig.apply`, so the figure is computed once per process; the user
agent and the cache paths are still re-set on every call, which is what keeps
`osmdroid.md` §2.1 and §2.2 unconditional.

### 3.4 Battery and network

`TrackFragment.onHiddenChanged` pauses and resumes the map by hand, the same
way `DeviceFragment` drives its band viewer. **Nothing is detached**, so the
camera, the zoom, the layer and every overlay are still there and coming back
is instant.

Be honest about what this is worth: `MapView.onPause()` in 6.1.20 only
forwards to the overlay manager — it does not stop the tile downloader. Tile
requests stop anyway, because a hidden Fragment's view is GONE and
`TilesOverlay` only asks for tiles from a draw pass. The call is correct
housekeeping, not the win.

---

## 4. `PathSimplifier`, and why 5 m

The one piece of this pass that came out as a pure helper, and therefore the
one piece that is **tested**
([`PathSimplifierTest`](../app/src/test/java/com/example/guardband/ui/incident/PathSimplifierTest.kt),
10 tests).

- **Ramer–Douglas–Peucker**, keeping the first and last point always and every
  point further than the tolerance from the line its surviving neighbours
  draw. A corner is kept; a point that only says "still walking the same way"
  goes.
- **5 m, because that is below the band's own error.** The NEO-6M is about
  2.5 m in open sky and routinely 5–10 m between buildings, and a phone
  standing in for it wanders as much. So what the simplifier removes is
  *jitter along a run the band really did walk straight*, never a turn it
  really made. A guardian reads the same route. It is also the same order of
  magnitude as `AlertPinSelector.MERGE_RADIUS_METERS` (10 m) and the place
  names' 4-decimal rounding (~11 m): the app's three "near enough to be the
  same spot" figures are deliberately in one range.
- **Drawing only.** The tracking updates, their count, the newest one, the end
  marker and the fitted bounding box all still come from `Incident`'s own,
  complete path. Nothing simplified is stored, counted or reported.
- **Distances on a local flat projection**, not the sphere: over the few
  hundred metres an incident spans the difference is far under a millimetre,
  and it keeps this a plain JVM test — the same trade
  `AlertPinSelector.distanceMeters` makes, for the same reason.
- **An explicit stack, not recursion**, because a long walk is hundreds of
  points deep.

FR-09's real band sends one update a minute for half an hour: 30 points, which
no map minds and which the simplifier barely touches. It exists for the mock
sender's 5 s interval, and for whatever the firmware's interval turns out to
be.

---

## 5. Two deliberate limits

Both are decisions, not oversights. Changing either needs someone's sign-off,
not a patch.

### 5.1 The camera is in instance state, not on disk — so process death loses it

Restoring the camera after the **process** dies would mean writing a
coordinate to the phone, and that coordinate is the wearer's approximate
location kept somewhere it is not kept today. `CLAUDE.md` is explicit that new
stored data of that kind needs the **Privacy Notice changed and
`CONSENT_VERSION` bumped in the same commit** — see
[`privacy-and-consent.md`](privacy-and-consent.md).

So the camera lives in `Fragment.onSaveInstanceState`, Android's own transient
bundle: it survives the case that actually happens in a session (a theme
change) and costs nothing. A restore after process death opens on D5's default
view, on purpose.

It is also **not** in `TrackViewModel`, for the separate reason `CLAUDE.md`
already gives for recentring: where the camera points is view state, like a
scroll offset.

### 5.2 Bad tile bytes cannot be rejected before the write

Rejecting them at the byte level means a custom `TileDownloader`, which has to
be installed with `MapTileDownloader.setTileDownloader` on the downloader
*inside* the tile provider. A `MapView` inflated from XML builds its own
`MapTileProviderBasic`, whose `mDownloaderProvider` is private, and `MapView`
has no `setTileProvider`. There is no route to it without constructing the
`MapView` in code — which the layouts (Jul's) do not do, and which is a far
larger change than this pass.

So the guard is where it can be: a bad-but-decodable blob can still reach
`cache.db`, it is **refused on every read and never drawn**, and it goes when
it expires and the trim removes it. `osmdroid.md` §2.5 has the mechanics.

---

## 6. Files

| File | Change |
|---|---|
| [`ui/track/OsmdroidConfig.kt`](../app/src/main/java/com/example/guardband/ui/track/OsmdroidConfig.kt) | disk cache budget from free space; memory overshoot; shared `isLowRamDevice` |
| [`ui/track/MapTilerMap.kt`](../app/src/main/java/com/example/guardband/ui/track/MapTilerMap.kt) | `setStressedMemory` on low-RAM phones |
| [`ui/track/MapTilerTileSource.kt`](../app/src/main/java/com/example/guardband/ui/track/MapTilerTileSource.kt) | tile-plausibility check in both `getDrawable` forms |
| [`ui/track/TrackFragment.kt`](../app/src/main/java/com/example/guardband/ui/track/TrackFragment.kt) | `onHiddenChanged` pause/resume; camera in instance state; `markerLocation` memo; incremental pins + `reorderPins` |
| [`data/repository/FirebaseAlertRepository.kt`](../app/src/main/java/com/example/guardband/data/repository/FirebaseAlertRepository.kt) | `distinctUntilChanged` on `asFlow` and after the check-in selector |
| [`ui/incident/PathSimplifier.kt`](../app/src/main/java/com/example/guardband/ui/incident/PathSimplifier.kt) | **new** — pure Douglas–Peucker |
| [`ui/incident/IncidentFragment.kt`](../app/src/main/java/com/example/guardband/ui/incident/IncidentFragment.kt) | draws the simplified path |
| [`PathSimplifierTest.kt`](../app/src/test/java/com/example/guardband/ui/incident/PathSimplifierTest.kt) | **new** — 10 tests |
| [`osmdroid.md`](osmdroid.md) | §2.4 and §2.5 new, §5.2 extended, §5.6 new, §8 checklist |
| [`incident-view.md`](incident-view.md) | §4, the thinned path |
| [`../CLAUDE.md`](../CLAUDE.md) | "Track and map", "Alert data"; test count 1011 → 1041 |

No dependency was added, no layout was touched, and no feature was removed.

---

## 7. What was verified, and what was not

| | |
|---|---|
| `assembleDebug`, `compileReleaseKotlin` | pass |
| `testDebugUnitTest` | **1041 tests, 0 failures** (10 new) |
| `lintDebug` | 0 errors, 199 warnings — one **fewer** than the 200 before this pass, and none in the changed files |
| The library behaviour the reasoning rests on | read in the osmdroid 6.1.20 **sources**, not assumed: `TileDownloader` (cache-before-decode, non-200 handling), `MapTileCache` (`ensureCapacity`, `garbageCollection`, `shouldKeepTile`), `DefaultConfigurationProvider` (the 600 MB default), `MapView.onPause` (overlays only), `FolderOverlay` (`getItems` is the live list), `GeoPoint.equals` (structural) |
| Everything on screen | **not verified.** Incremental marker and pin updates, the tab-switch and theme-change restores, the offline cached map, a failed tile, and the thinned path all need a running map. The project declares no mocking library and has no instrumented tests beyond the template. |
| Screen sizes, OS versions, low-memory and slow-network behaviour | **not performed.** No device or emulator was run. |

The phone checklist is in [`osmdroid.md`](osmdroid.md) §8, under "Loading,
state and incremental updates". It is a reference list, not a queue: the
developer tests on their own phone each session.

---

## 8. Still open

1. **The biggest network win is still on the table, and it is a design call.**
   With `setTilesScaledToDpi` off, 256 px tiles on a 3× screen mean roughly 45
   tile requests per view. MapTiler's `@2x` 512 px endpoints together with
   `setTilesScaledToDpi(true)` would cut that to about 6 at equal sharpness —
   some 4× fewer requests, on a Free plan that pauses at its monthly limit. It
   visibly changes label size and detail density, so it is **Jul's** call, not
   an agent's. `DESIGN: Jul`
2. **Byte-level tile rejection** — §5.2.
3. **The camera after process death** — §5.1.
4. **A pre-existing bug this pass did not touch:** `MapTilerMap.applyLayer`
   sets the dark loading-grid colours only for Streets Dark and never resets
   them, so switching Auto (dark) → Satellite within one session keeps the
   dark loading grid. Cosmetic, and visible only for the moment a tile is
   loading.
5. **The unbounded history listener** (`UNLIMITED_HISTORY_WINDOW`, no
   `limitToLast`) is unrelated to the map but is the other live performance
   item — already tracked in [`alert-data.md`](alert-data.md) §13.
6. **No Changelog entry yet.** `CLAUDE.md` requires one keyed to the day's
   last commit hash, and this pass has not been committed.
