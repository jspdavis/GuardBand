# osmdroid: the map library and its configuration

Everything about the map library itself — why it was chosen, the two settings
that must be right before a tile is requested, the MapTiler tile source, its
key and its attribution, the lifecycle calls, and why no location permission is
involved.

| | |
|---|---|
| **Library** | `org.osmdroid:osmdroid-android:6.1.20` |
| **Branch** | `mvvm-main` |
| **Tiles** | MapTiler Cloud, Streets style, 256 px raster, since 2026-10-19 (OpenStreetMap's public server before that) |
| **Status** | MapTiler swap merged into `mvvm-main` (`1ca8177`), 588 tests passing. **MapTiler tiles not yet confirmed on a device**, which needs a real key — see §8. The MapTiler logo is on the map since 2026-10-19 (§4.6). |
| **Owns it** | [`ui/track/OsmdroidConfig.kt`](../app/src/main/java/com/example/guardband/ui/track/OsmdroidConfig.kt), [`ui/track/MapTilerTileSource.kt`](../app/src/main/java/com/example/guardband/ui/track/MapTilerTileSource.kt) |
| **Uses it** | [`ui/track/TrackFragment.kt`](../app/src/main/java/com/example/guardband/ui/track/TrackFragment.kt) only |
| **Related** | [`../CLAUDE.md`](../CLAUDE.md) ("Track and map"), [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md) (troubleshooting) |

This file is about the **library**. What the Track tab *shows* — band status,
the "Reporting" badge, the staleness ticker, retained positions — is a
ViewModel concern and lives in `CLAUDE.md` under "Track and map".

---

## 1. Why osmdroid and not the Maps SDK

| | osmdroid | Google Maps SDK |
|---|---|---|
| API key | none for the library; the tiles need MapTiler's (§4.4) | required |
| Billing-enabled GCP project | none | required |
| compileSdk pressure | none (see below) | varies |
| Tile cost at demo scale | free, on MapTiler's Free plan (§7) | free tier, then billed |

For an academic project with a Spark-plan Firebase and no billing account, the
billing requirement decided it. osmdroid draws tiles from any XYZ server;
which one, and on what terms, is §4 and §7.

**It is safe on compileSdk 34 without a bump.** osmdroid's POM declares **zero
dependencies** and it publishes no Gradle module metadata, so there is none of
the `minCompileSdk` constraint that pins `androidx-credentials` at 1.3.0. This
is noted in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) so
nobody re-litigates it.

Declared as `implementation(libs.osmdroid.android)` — a normal
`implementation`, not `debugImplementation`: the map is a release feature,
unlike the mock sender's OkHttp.

---

## 2. The two settings that must be right

`OsmdroidConfig.apply(context)` is the **only** place osmdroid is configured.
It sets exactly two things, and both must be right **before the first tile is
requested**.

### 2.1 The user agent

```kotlin
const val USER_AGENT = "GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)"
```

**The MapTiler key is restricted to this value** (§4.4), so it is now
load-bearing twice over: change it and every tile is refused until the key's
restriction in the MapTiler dashboard is changed to match. Change the
dashboard first.

It was first fixed for OpenStreetMap, when the tiles still came from there.
OpenStreetMap's [tile usage policy](https://operations.osmfoundation.org/policies/tiles/)
requires a **"proper, unique and identifiable"** `User-Agent`. Their servers
block identifiers that many apps share. Two of those matter here:

| Blocked identifier | Where it comes from |
|---|---|
| `osmdroid` | the library default, if nothing is configured |
| `com.example.*` | the Android project template — **still this app's `applicationId`** |

`com.example.*` was blocked because OSM measured millions of daily tile
requests under it from thousands of unrelated sample apps, with no way to tell
them apart. Setting the user agent to `context.packageName` therefore satisfies
the letter of the policy and still gets blocked, which is exactly what this app
did until it was fixed.

**The agent is deliberately not derived from the package name or the version
code.** A server that blocks by string should not see a different value because
someone renamed a package or shipped a new build.

### 2.2 The cache paths

```kotlin
configuration.osmdroidBasePath  = File(context.cacheDir, "osmdroid")
configuration.osmdroidTileCache = File(base, "tiles")
```

osmdroid's own default wants **external storage**. Pointing both at
`Context.getCacheDir` keeps tiles in app-specific storage, which needs **no
storage permission on any API level** and is removed with the app.

### 2.3 Why `onCreate`

The `MapView` is constructed during `onCreateView`, when
[`fragment_track.xml`](../app/src/main/res/layout/fragment_track.xml) inflates.
Configuration applied after that point is too late for the first batch of tile
requests. `TrackFragment` therefore calls `OsmdroidConfig.apply` in
**`onCreate`**, and the layout carries a comment saying so.

---

## 3. What "blocked" actually looks like

This is the part that costs people an afternoon.

**OSM does not return an HTTP error for a blocked user agent.** It returns
**HTTP 200 with a PNG** that has "access blocked …" drawn into the image.

Consequences:

1. osmdroid sees a valid tile, so it **renders it**.
2. osmdroid caches valid tiles, so it **writes it to the tile cache**.
3. Nothing appears in logcat, because nothing failed.

So the symptom is a map tiled with readable error text, a clean log, and tiles
that **keep coming back after the bug is fixed** unless the cache is dealt
with. §4.1 explains why it is dealt with for free here.

A genuinely **blank or grey** map is a different bug — see §6.

---

## 4. `MapTilerTileSource`

The one tile source, and the only place a MapTiler URL, the key, the zoom
range or a credit is spelled out. `TrackFragment.setUpMap` sets it on the
`MapView`; nothing else names a tile server.

```
https://api.maptiler.com/maps/streets-v2/256/{z}/{x}/{y}.png?key=YOUR_MAPTILER_API_KEY
```

It subclasses `OnlineTileSourceBase` rather than reusing `XYTileSource`,
because `XYTileSource` builds `baseUrl + z/x/y + extension` and has nowhere to
put the `?key=` query.

> **Never fall back to `TileSourceFactory.MAPNIK`** (or any
> `tile.openstreetmap.org` source), with or without a key. It is also the
> `MapView`'s own default, which is why the Fragment sets this source even in a
> build with no key (§4.4).

### 4.0 The policy, and why not `MAPNIK`'s

`TileSourceFactory.MAPNIK` is constructed with `TileSourcePolicy(2, 15)`.
Those flags, from osmdroid 6.1.20:

| Flag | Value | Meaning |
|---|---|---|
| `FLAG_NO_BULK` | 1 | no bulk downloading |
| `FLAG_NO_PREVENTIVE` | 2 | no speculative tile loading |
| `FLAG_USER_AGENT_MEANINGFUL` | 4 | reject a null/blank/`osmdroid` agent |
| `FLAG_USER_AGENT_NORMALIZED` | 8 | **send `packageName/versionCode` instead of the configured agent** |

`15` is all four. And `TileDownloader` does:

```java
if (policy.normalizesUserAgent()) userAgent = config.getNormalizedUserAgent();
if (userAgent == null)            userAgent = config.getUserAgentValue();
```

With flag 8 set, `OsmdroidConfig.USER_AGENT` is **ignored** and
`com.example.guardband/1` goes out instead. OSM blocked that prefix; MapTiler
would refuse it, because the key only accepts `USER_AGENT` (§4.4).

`MapTilerTileSource` uses `FLAG_NO_BULK or FLAG_NO_PREVENTIVE or FLAG_USER_AGENT_MEANINGFUL`
— every `MAPNIK` restriction **except** normalization, the same policy the OSM
source had. `maxConcurrent` stays at **2**.

### 4.1 The name is load-bearing

The source is named **`MapTilerStreets`**. osmdroid's tile cache keys rows by
`(key, provider)`, where `provider` is the tile source's name (`SqlTileWriter`,
`… and provider=?`), so the MapTiler tiles never mix with the OpenStreetMap
ones cached under the old `GuardBandOsm` source, or the blocked PNGs cached
under `Mapnik`. **No cache clearing or reinstall is needed** after the swap.

The API key is **not** part of the cache key, so a new key reuses tiles
already cached.

The dark style (§4.8) is **`MapTilerStreetsDark`**, for the same reason: a
theme switch must never serve the light tiles from the cache, or the reverse.

### 4.2 Constants are declared first, on purpose

`MapTilerTileSource`'s companion puts its constants **above** `POLICY` and
`STREETS`, which is the opposite of the house style (`TrackFragment` keeps its
constants in a `private companion object` at the bottom).

That is not a preference. Kotlin's initialization analysis rejects a forward
reference to a property declared later in the same object body **even for a
`const val`**, so constants-at-the-bottom fails to compile here with eight
`Variable 'X' must be initialized` errors. Keep the order.

### 4.3 Never call `Configuration.load()`

`Configuration.getInstance().load(context, prefs)` is the **only** thing in
osmdroid that computes the normalized agent — it calls
`computeNormalizedUserAgent(context)`, which returns
`packageName + "/" + versionCode`.

The app has never called `load()`, so `getNormalizedUserAgent()` is null and
the fallback to `getUserAgentValue()` applies. **That was luck, not design.**
Dropping flag 8 (§4) and not calling `load()` are two independent guards on the
same failure; keep both. Adding a `load()` call is otherwise a perfectly
reasonable-looking change.

### 4.4 The key

| | |
|---|---|
| Stored in | `local.properties` (gitignored), as `MAPTILER_API_KEY=YOUR_MAPTILER_API_KEY` |
| Read by | `app/build.gradle.kts`, into `BuildConfig.MAPTILER_API_KEY` |
| Used by | `MapTilerTileSource` only |
| Restricted to | **Allowed user-agent header:** `GuardBand/1.0 (+https://github.com/jspdavis/GuardBand)`, i.e. `OsmdroidConfig.USER_AGENT` (set in the MapTiler dashboard) |

**Never put the real key in a tracked file**, docs and examples included; use
`YOUR_MAPTILER_API_KEY`. Each developer adds the line to their own
`local.properties`.

**A build without the key still succeeds**, with an empty string. Then:

- `MapTilerTileSource.hasApiKey` is false and `getTileURLString` returns `""`,
  which osmdroid's `TileDownloader` drops before opening a connection;
- `TrackFragment` calls `mapView.setUseDataConnection(false)`, so nothing is
  downloaded from anywhere, and logs one warning (`TrackFragment`:
  "MAPTILER_API_KEY is not set in local.properties; map tiles are disabled.");
- the empty-state pill reads **"Map unavailable"**, ahead of any band message.

The map area stays grey, the marker and the controls still work, and **no
request goes to `tile.openstreetmap.org`.** Three guards, any one of which is
enough.

**The key ships inside the APK.** `BuildConfig` is a compiled constant and
anyone with the APK can read it. What protects it is the **user-agent
restriction** (which a determined copier can spoof, since the agent is in the
APK too) **plus MapTiler's monthly cap**, which stops the damage at the free
plan's limit rather than billing it. That is acceptable for an academic demo
and not for a public release, which would want tiles behind a server of its
own.

**The key travels in every tile URL**, so the URL must never be logged.
osmdroid only logs it with `Configuration.isDebugMode` or
`isDebugMapTileDownloader` on — never turn those on — plus, in 6.1.20, in two
rare warnings: a redirect, and a 200 response that is not an image. Neither
should happen against MapTiler, but they would put the key in logcat if they
did.

### 4.5 Traffic rules

- **The tile cache stays on**, at osmdroid's defaults, in `cacheDir` (§2.2).
  Do not disable or shrink it: the cache is what keeps a guardian who leaves
  the Track tab open from spending the monthly quota.
- **No bulk downloading, no pre-fetching, no offline-region downloader** —
  `CacheManager`, `downloadAreaAsync` and the like are off limits.
  `FLAG_NO_BULK` and `FLAG_NO_PREVENTIVE` (§4.0) say the same to osmdroid.
  Normal on-demand viewing only.
- **No OSM public server**, for any reason, including as a fallback.

### 4.6 Attribution

MapTiler's [attribution rules](https://docs.maptiler.com/guides/map-design/attribution/add-attribution)
for a dynamic map in a mobile app:

| Requirement | Status |
|---|---|
| **Text, on every map:** "© MapTiler" linked to `https://www.maptiler.com/copyright/` and "© OpenStreetMap contributors" linked to `https://www.openstreetmap.org/copyright`, bottom right | **Done, collapsible** (below). `track_map_attribution`, a `TextView` whose text and links come from `MapTilerTileSource.attribution()`, bottom right, just above the strip the collapsed sheet covers; `track_map_attribution_info`, the ⓘ, in the same corner. |
| **Logo, Free plan only:** the MapTiler logo, linked to `https://www.maptiler.com/`, bottom left | **Done (2026-10-19), never collapses.** `track_map_logo`, an `ImageView` of `@drawable/logo_maptiler`, bottom left in the credits' row; `MapAttribution` opens `MapTilerTileSource.LOGO_URL` on a tap. Paid plans can drop the logo; nobody can drop the text without MapTiler's written permission. |

**The logo (2026-10-19).** The source was MapTiler's own PNG (812 × 212). It was
cropped to its visible bounds (721 × 186, so the transparent margin does not
eat the 20 dp) and exported once per density bucket, `drawable-mdpi` to
`drawable-xxxhdpi`, at 20 dp tall (20 px to 80 px). It is drawn 20 dp tall,
width from the aspect ratio (about 78 dp), never stretched or tinted, on the
same 70% white (`map_attribution_bg`) and 4 dp side padding as the credit,
with 2 dp above and below. Content description "MapTiler". **It must stay
visible:** `MapAttribution` never changes its visibility, the credit's
collapse does not touch it, and the collapsed sheet does not cover it (it
sits on the same `space_track_sheet_peek` edge as the credit). To replace the
asset, keep the five buckets and crop to the visible bounds first.

**The shared row is 24 dp** (`track_map_attribution_height`, 18 dp before the
logo), so the logo fits inside its 2 dp padding. The **ⓘ is 48 dp**, the
minimum touch target, around the same 24 dp disc as before (its inset went
from 8 to 12 dp), anchored to the logo's row. Being taller than the row, it
needs 24 dp above it, which `space_track_credit_clearance` holds; the check-in
pill stacks on top of that (below). The pill and the round buttons above it
sit 14 dp higher than before the logo; they never move when the credit
collapses.

**Beside the logo, or above it (resolved 2026-10-19,
`feature/credit-overlap-fix`).** The logo and the expanded credit need about
319 dp side by side at the default font size (logo and margins 94 dp, the
line 210 dp at 10 sp plus padding, margins and an 8 dp gap). The credit is in
`sp` and the logo in `dp`, so a narrow screen or a large system font ran them
into each other. Now, **when the line does not fit beside the logo, it moves
onto a line of its own directly above the logo**, right-aligned; if even that
line is too narrow (the largest font sizes) it wraps. It is never made
smaller than 10 sp.

- **How it decides.** `MapAttribution` measures the line's real width
  (`Layout.getDesiredWidth`, with the TextView's own paint, so the font scale
  counts) against the room right of the logo, once, before the first draw
  after Track is laid out, and skips the draw that would show it in the wrong
  place. The XML holds the side-by-side position; stacking rewrites the
  credit's constraints. **Why not layout alone:** ConstraintLayout's `Flow`
  and chains wrap downwards, which would lift the logo off its row, and an
  upward wrap needs Flexbox (`wrap_reverse`), a new dependency. Once per view
  is enough: a change of width, density or font scale recreates the Activity.
  It waits for a non-zero width because a restore can rebuild Track while it
  is a hidden tab, with no width, which would always read as "does not fit".
- **What moves.** Nothing, as long as the credit is one line, beside or
  above: the pill stacks on `barrier_track_credits`, the higher of the ⓘ's
  24 dp clearance and the credit's top, and a one-line credit above the logo
  exactly fills that clearance. Only a wrapped credit lifts the pill, and the
  round buttons stacked on it, by its extra height (36 dp at 411 dp and the
  largest font). The collapsed credit is `INVISIBLE`, so the barrier, and so
  the pill, do not move when it collapses.
- **What does not change:** the 5 s / first-pan collapse, the 10 s reopen,
  the links, the logo, the 48 dp ⓘ, and the collapsed sheet never covering
  the row.

**Checked on a phone on 2026-10-19** (a Realme RMX5111, 360 dp wide, with
`wm density` and `font_scale` overridden for the other cells and then
restored), from `uiautomator` view bounds, collapsed and expanded:

| Width, font scale | Credit, expanded | Overlaps | Pill moved? |
|---|---|---|---|
| 320 dp, 1.0 | **above** the logo, one line (236 × 24 dp) | none; meets the pill's bottom edge | no |
| 360 dp, 1.0 | **beside** the logo (233 × 24 dp) | none | no |
| 360 dp, 1.3 | **above** the logo, one line (301 × 24 dp) | none; meets the pill's bottom edge | no |
| 411 dp, 1.0 | **beside** the logo (233 × 24 dp) | none | no |
| 411 dp, 2.0 (largest) | **above** the logo, two lines (395 × 60 dp) | none | up 36 dp, with the buttons above it |

Collapsed, every cell showed the logo and the ⓘ with no overlaps, and in
every cell the collapsed sheet covered nothing in the row. Not exercised on
the device: tapping the two credit links (their code did not change).

Before 2026-10-19 the map showed **no** attribution at all: the OSM source had
a copyright string, but nothing drew it (osmdroid only does with a
`CopyrightOverlay`). The credit is a `TextView` rather than a
`CopyrightOverlay` because the overlay draws plain text with no links, at the
`MapView`'s bottom edge, which the collapsed sheet covers.

**The credit collapses to an ⓘ (2026-10-19), on the OSMF's rule.**
`ui/track/MapAttribution` shows the full line every time the Track view is
created, collapses it after **5 s** or at the **first pan or pinch** (the
Fragment's existing `ACTION_MOVE` listener), and the ⓘ brings it back for
10 s. That is exactly what the [OSMF attribution guidelines](https://osmfoundation.org/wiki/Licence/Attribution_Guidelines)
allow: a credit may "fade or collapse … automatically after map interaction
such as panning, clicking, or zooming, or automatically after five seconds",
provided "the user must still be able to find the licence information if
they look for it", e.g. through an "(i)" button. It does not reappear when the
app returns from the background or the tab is switched back to; the guidelines
do not require that either.

**MapTiler's guide does not mention collapsing.** It says only that the text
"must appear on every map". MapTiler's own web SDK collapses its credit to an
ⓘ on narrow maps, which suggests they accept it, but that is a reading, not a
permission. **Ask MapTiler before a public release** (DECISION: Jul); if they
say no, drop `MapAttribution`'s timed collapse and the `collapse()` call in
`TrackFragment`'s touch listener, and leave the line expanded.

**Styling, within the "easily readable" rule:** 10 sp
(`TextAppearance.GuardBand.MapAttribution`, one step under LabelSmall's 11),
`text_secondary` on a 70% white background (`map_attribution_bg`) so the map
shows through while the text keeps its contrast. Do not go smaller or fainter:
the guidelines judge readability on "font, size, colour, contrast,
positioning and amount of time that it is visible". The collapsed line is
`INVISIBLE`, not `GONE`, in a fixed-height row
(`track_map_attribution_height`), so the check-in pill above it never moves.

**Never hide it by default** — behind the sheet, off screen, or only in
Settings. Both MapTiler and the OSMF require it on the map without the user
having to go looking. The same goes for the logo, which may not even
collapse. Dragging the sheet up still covers both, as it covers the map
controls; that is the user's own, temporary action, and the sheet at rest
(collapsed, `BottomSheetBehavior`'s default) never does.

### 4.7 Zoom range

0 to **22**, set on the tile source and applied to the `MapView` with
`setMinZoomLevel` / `setMaxZoomLevel`. 22 is the `maxzoom` in MapTiler's
documented TileJSON for its standard raster maps; MapTiler's Maps API
reference gives no per-style limit. To confirm it for this style, read
`maxzoom` from `https://api.maptiler.com/maps/streets-v2/tiles.json?key=YOUR_MAPTILER_API_KEY`.

---

### 4.8 Dark mode (2026-10-20)

In dark mode the map is MapTiler's **Streets Dark**,
`https://api.maptiler.com/maps/streets-v2-dark/256/{z}/{x}/{y}.png`, from
`MapTilerTileSource.STREETS_DARK`; `forNightMode(night)` is the only
chooser, and `TrackFragment.setUpMap` asks it from the configuration's
`uiMode`. Same key, same plan, same credits, same policy; only the style
and the cache name differ. On 2026-10-20 one tile of each style was fetched
with the project's key and the app's user agent: both `200 image/png`.

- **A theme change recreates the Fragment**, so the new source is set from
  scratch; nothing swaps the source on a live `MapView`.
- **The loading grid is dark too.** osmdroid draws a light grey grid behind
  a tile still loading; in dark mode `setUpMap` sets the tiles overlay's
  loading colours to `surface_card` and `divider`.
- **The credits and the logo stay light.** `logo_maptiler.png` has dark
  lettering, so both keep `map_attribution_bg` (70% white) in dark mode,
  with `map_attribution_text` rather than `text_secondary`, which turns
  light. MapTiler offers a white logo for dark maps; swapping it in is Jul's
  call, and the credits would then follow it.

## 5. Permissions, the manifest, and the lifecycle

### 5.1 No location permission, ever

**The band supplies the location; the handset does not.** The app declares only
`INTERNET` in [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)
(`ACCESS_NETWORK_STATE` arrives via Firebase).

osmdroid's own manifest declares **no `uses-permission` at all**. It merges in
four *optional* feature entries:

| Merged `uses-feature` | `required` |
|---|---|
| `android.hardware.location.network` | `false` |
| `android.hardware.location.gps` | `false` |
| `android.hardware.telephony` | `false` |
| `android.hardware.wifi` | `false` |

All optional, so none of them restricts Play Store availability.

**Never add `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`, and never use
osmdroid's `MyLocationNewOverlay`** — that overlay is the one class that would
drag them back in, and a safety app asking for the guardian's own location
would be both unnecessary and a privacy regression.

### 5.2 The `MapView` must be detached by hand

osmdroid holds a tile cache, a background worker and overlay references. The
Fragment's view leaks without all three of these:

```kotlin
override fun onResume()      { super.onResume(); mapView.onResume() }
override fun onPause()       { mapView.onPause(); super.onPause() }
override fun onDestroyView() { mapView.onDetach(); marker = null; super.onDestroyView() }
```

`marker = null` matters too: a `Marker` holds the `MapView` that created it.

### 5.3 Coordinates are never logged

An alert carries the wearer's position. Nothing in `data/` logs at all, and
neither the Track tab nor anything osmdroid-facing logs a coordinate. Same rule
as contact phone numbers — see `CLAUDE.md`.

### 5.4 The band's marker (2026-10-19)

The one marker on the Track map is the band's last reported position (never
the phone's). Since `feature/track-profile-marker` it is a **face**: a round
picture in a white ring, with a soft shadow and a short pointer underneath.
Before that it was osmdroid's default pin. What the marker means, how it
moves, follow mode and the rest of the map are unchanged. There are no other
markers or polylines on Track.

| | |
|---|---|
| Face | 44 dp circle: the photo, centre-cropped, or initials in white on `brand_green` (Poppins SemiBold, 17 dp; a person glyph if there are no initials) |
| Ring | 3 dp, white; with the face, 50 dp across |
| Pointer | 8 dp tall, 14 dp wide, white, joined to the ring |
| Shadow | 3 dp blur, 1 dp down, 33% black, inside 4 dp of padding on every side |
| Bitmap | 58 × 66 dp |
| **Anchor** | **the pointer's tip:** horizontally centred, 62 of 66 dp down. `BandMarkerIcon.anchorU` / `anchorV` compute it from the bitmap's rounded pixel size, so the tip is on the coordinate to the pixel. The old pin used `ANCHOR_CENTER, ANCHOR_BOTTOM`, its own tip, so the position marked is the same |

The white ring and shadow keep it readable on light tiles and on water or
other dark areas alike.

**Whose face.** `TrackViewModel.markerFace()` is **the one place** that
decides. Today it is the signed-in user's own: there is no wearer or guardian
role yet. When roles exist, change that function and nothing else.

**Where the picture comes from**, reusing the Profile tab's avatar code rather
than a second mechanism:

- `BandMarkerFace.of(user, providers)` (pure, tested) takes the link from
  `ui/avatar/AvatarPhoto.urlFor`: a Google account only, `https` only, and
  Google's `=s96-c` size suffix rewritten to `=s288-c`. 288 px is about twice
  the 48 dp marker on an xxhdpi screen, and it is the same link Profile loads,
  so the two share Coil's caches.
- **Initials** are the first letter of the first and the last word of the
  session's display name, or one letter for a single word. With no usable
  name, the email's first letter. They work on code points in any script,
  skip punctuation and emoji, and upper-case one code point at a time.
- **The initials face is drawn at once** on the main thread, one small bitmap,
  so the marker is never blank and nothing waits on the network. The photo
  then loads through **Coil** (already a dependency) off the main thread, and
  the marker is composed inside a Coil `Transformation` on Coil's thread. The
  composed bitmap sits in Coil's memory cache, keyed by the link and the
  screen density. A failed or offline load keeps the initials.
- **Drawn only when the face changes.** `BandMarkerFace` is a data class, set
  once per `TrackViewModel` and carried through every state `copy`, and
  `BandMarkerIcon.bind` returns early on an equal face. So a new position
  costs what it did before: move the marker, invalidate. Nothing recycles a
  bitmap; Coil owns the photo ones.
- **The link is never logged, and the picture is never stored or uploaded**
  beyond Coil's own on-phone caches, the same ones Profile uses. Loading it
  means Google's image servers see the phone's IP address: see the profile
  picture row in the Privacy Notice (since 1.2) and [`privacy-and-consent.md`](privacy-and-consent.md)
  §4.

**Accessibility:** osmdroid overlays are not accessibility nodes, so the
marker cannot carry a content description of its own; a tap shows its title,
"Your band", as before.

---

## 6. Troubleshooting

| Symptom | Cause |
|---|---|
| **"Map unavailable"**, grey map | The build has no key: `MAPTILER_API_KEY` is missing or empty in `local.properties` (§4.4). Add it and rebuild; logcat has the `TrackFragment` warning. |
| **Grey map, every tile refused** (logcat: `Problem downloading MapTile … HTTP response: Forbidden`) | The key or its restriction. The key is wrong, or the dashboard's allowed user agent does not match `OsmdroidConfig.USER_AGENT`, or the tile source normalizes the agent away (§4.0). |
| Tiles **stop loading mid-month** | The Free plan's monthly limit; MapTiler pauses the key until the next period (§7). |
| **Every tile reads "access blocked"** | Only possible from OSM's server, which nothing should reach any more. Something is setting an OSM source again (§4). |
| **Blank or grey** map, everything else fine | `OsmdroidConfig.apply` ran *after* the `MapView` was built. It must be in `onCreate` (§2.3). Or: no connectivity, or a typo in `BASE_URL`. |
| Map works, **marker never appears** | Not an osmdroid problem. The band is reporting without a fix, or the payload has no `location`. |
| Camera will not follow the band | Expected after any pan — `ACTION_MOVE` switches following off. Long-press the round Navigate button. |
| Tiles load, then stop at high zoom | `MAX_ZOOM` is 22 (§4.7). |

MapTiler refuses with real HTTP errors, which osmdroid logs as
`Problem downloading MapTile` (without the URL). If the map looks wrong,
check the key and the user agent first.

---

## 7. Not for production as-is

The OSM public-server problem is gone: since 2026-10-19 the tiles come from
MapTiler, and nothing requests `tile.openstreetmap.org`. What remains is
MapTiler's **Free plan**:

- **It is for non-commercial use.** A commercial release needs a paid plan.
- **It pauses at its monthly limit** instead of billing. Past the cap the key
  stops serving tiles until the next period, so a busy month leaves every
  install with a grey map. The cache (§4.5) is what keeps a demo under it.
- **It requires the MapTiler logo** on the map, which is there (§4.6).

**The key and the user agent are both baked into the installed APK** (§4.4).
If either has to change, installed copies keep sending the old values until
they are updated, so a rotated key or a changed restriction breaks every
older install. Change the dashboard and ship the app together, and keep the
old key alive until the old builds are gone.

### 7.1 The `applicationId` rename

`com.example.guardband` is a publishing problem independently of the map: Play
rejects the `com.example.*` prefix.

It is **not** a map problem any more — the user agent no longer derives from
the package name, so a rename neither fixes nor breaks tiles. But note the hard
deadline: **`applicationId` cannot be changed after a first Play release.** It
is the identity of the listing, the old name cannot be reused, and existing
installs have no upgrade path. So if this is ever published, the rename happens
*before* the first upload, and it means re-registering the Firebase Android app
and **every developer's debug SHA-1** for Google sign-in.

Tracked in [`handoff.md`](handoff.md).

---

## 8. Not covered by tests

`OsmdroidConfig`, `MapTilerTileSource`, the `MapView` lifecycle, the marker's
placement and the follow logic all need a real `Context`, a running map, or an
instrumented test — and the project declares no mocking library. The testable
logic was lifted out instead: `BandStatus`, `NavigationUrl`,
`TrackViewModel`, `BandMarkerFace` (photo or initials, and the initials
themselves) and `BandMarkerIcon` under Robolectric (drawn once per face, and
the anchor; the photo path needs a device), all covered under
[`app/src/test/java/com/example/guardband/ui/track/`](../app/src/test/java/com/example/guardband/ui/track/).

**So the map is verified by running it.** After any change in this file's
scope, open the Track tab on a device and confirm tiles actually render — the
reasoning can be right and a typo'd base URL still gives you a grey map.

A green build proves **none** of this. The compiler cannot tell you that the
user agent reached the server, that the tile source is the one being used, or
that a tile came back. For the MapTiler swap, check on a device with a real
key: tiles render, the credits show and their links open, the logo shows
bottom left, stays when the credit collapses and opens maptiler.com, and logcat (or a
network inspector) shows tile requests to `api.maptiler.com` and none to
`tile.openstreetmap.org`. Then build once **without** the key and check for
"Map unavailable", no crash and no tile requests at all. Update the Status row
above once someone has looked at the map.

---

## 9. Reference

- [MapTiler: attribution](https://docs.maptiler.com/guides/map-design/attribution/add-attribution) — the credit and logo rules (§4.6)
- [MapTiler Maps API](https://docs.maptiler.com/cloud/api/maps/) — the raster tile endpoint
- [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) — why the OSM server was dropped
- [OSM wiki: Blocked tiles](https://wiki.openstreetmap.org/wiki/Blocked_tiles) — what gets blocked and why
- [osmdroid on GitHub](https://github.com/osmdroid/osmdroid) — source for the flag behaviour in §4
