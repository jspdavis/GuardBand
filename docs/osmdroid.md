# osmdroid: the map library and its configuration

Everything about the map library itself — why it was chosen, the two settings
that must be right before a tile is requested, the custom tile source, the
lifecycle calls, and why no location permission is involved.

| | |
|---|---|
| **Library** | `org.osmdroid:osmdroid-android:6.1.20` |
| **Branch** | `mvvm-main` |
| **Status** | `assembleDebug` + `testDebugUnitTest` green, 295 tests, 0 failures. **Tile rendering not yet confirmed on a device** — see §8. |
| **Owns it** | [`ui/track/OsmdroidConfig.kt`](../app/src/main/java/com/example/guardband/ui/track/OsmdroidConfig.kt), [`ui/track/GuardBandTileSource.kt`](../app/src/main/java/com/example/guardband/ui/track/GuardBandTileSource.kt) |
| **Uses it** | [`ui/track/TrackFragment.kt`](../app/src/main/java/com/example/guardband/ui/track/TrackFragment.kt) only |
| **Related** | [`../CLAUDE.md`](../CLAUDE.md) ("Track and map"), [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md) (troubleshooting) |

This file is about the **library**. What the Track tab *shows* — band status,
the "Reporting" badge, the staleness ticker, retained positions — is a
ViewModel concern and lives in `CLAUDE.md` under "Track and map".

---

## 1. Why osmdroid and not the Maps SDK

| | osmdroid | Google Maps SDK |
|---|---|---|
| API key | none | required |
| Billing-enabled GCP project | none | required |
| compileSdk pressure | none (see below) | varies |
| Tile cost at demo scale | free | free tier, then billed |
| Production-ready tiles | **no** (§7) | yes |

For an academic project with a Spark-plan Firebase and no billing account, the
key requirement decided it. The trade is §7: the tiles are not ours to ship.

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

## 4. `GuardBandTileSource`, and why not `TileSourceFactory.MAPNIK`

> **Do not "simplify" this back to `MAPNIK`.** The map goes blocked again and
> the log stays clean, so the regression is close to invisible.

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
`com.example.guardband/1` goes out instead — the blocked prefix.

`GuardBandTileSource.OSM` is the same server, the same tiles and the same
attribution, with `FLAG_NO_BULK or FLAG_NO_PREVENTIVE or FLAG_USER_AGENT_MEANINGFUL`
— every `MAPNIK` restriction **except** normalization. `maxConcurrent` stays at
`MAPNIK`'s **2**, which is what the policy asks for.

### 4.1 The name is load-bearing

The source is named **`GuardBandOsm`**, not `Mapnik`. osmdroid's tile cache
keys rows by `(key, provider)`, where `provider` is the tile source's name
(`SqlTileWriter`, `… and provider=?`). A distinct name therefore starts on
clean rows, and the blocked PNGs already cached under `Mapnik` are never read
again.

**This is why no cache clearing or reinstall is needed** after the fix.
Renaming the source back to `Mapnik` would silently re-serve them.

### 4.2 Constants are declared first, on purpose

`GuardBandTileSource` puts its `private const val`s **above** `POLICY` and
`OSM`, which is the opposite of the house style (`TrackFragment` keeps its
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

---

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

---

## 6. Troubleshooting

| Symptom | Cause |
|---|---|
| **Every tile reads "access blocked"** | The user agent. Either `OsmdroidConfig` is not setting it, or the tile source normalizes it away (§4). Both halves are needed; neither works alone. |
| Tiles still blocked **after** fixing the agent | The cache. Expected only if the tile source is still named `Mapnik` (§4.1). Otherwise clear app storage. |
| **Blank or grey** map, everything else fine | `OsmdroidConfig.apply` ran *after* the `MapView` was built. It must be in `onCreate` (§2.3). Or: no connectivity, or a typo in `BASE_URL`. |
| Map works, **marker never appears** | Not an osmdroid problem. The band is reporting without a fix, or the payload has no `location`. |
| Camera will not follow the band | Expected after any pan — `ACTION_MOVE` switches following off. Long-press the round Navigate button. |
| Tiles load, then stop at high zoom | `MAX_ZOOM` is 19, OSM's limit. |

Because a blocked tile is an HTTP 200, **logcat will not tell you any of
this.** If the map looks wrong, check the user agent first.

---

## 7. Not for production as-is

OpenStreetMap's public tile servers are a **volunteer-funded** service whose
usage policy is written for light use. A real deployment needs its own tile
source or a commercial provider.

**Being correctly identified is not the same as being entitled to the
traffic.** The §2.1 fix stopped this app from being blocked as
*unidentifiable*; it does not make a shipping consumer app's tile volume
acceptable. A clearly labelled app over the line is easier for them to block,
not harder.

There is also a trap specific to shipping: **the user agent is baked into the
installed APK.** If a published version's agent is blocked, already-installed
copies stay broken and cannot be fixed by an update — only by the user
upgrading. That alone argues against depending on OSM's public tiles in
anything released.

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

`OsmdroidConfig`, `GuardBandTileSource`, the `MapView` lifecycle, the marker
and the follow logic all need a real `Context`, a running map, or an
instrumented test — and the project declares no mocking library. The testable
logic was lifted out instead: `BandStatus`, `NavigationUrl` and
`TrackViewModel`, all covered under
[`app/src/test/java/com/example/guardband/ui/track/`](../app/src/test/java/com/example/guardband/ui/track/).

**So the map is verified by running it.** After any change in this file's
scope, open the Track tab on a device and confirm tiles actually render — the
reasoning can be right and a typo'd base URL still gives you a grey map.

A green build proves **none** of this. The compiler cannot tell you that the
user agent reached OSM, that the tile source is the one being used, or that a
tile came back. `assembleDebug` and the 295 unit tests pass as of the user
agent fix; **a device check is still outstanding.** Update the Status row above
once someone has looked at the map.

---

## 9. Reference

- [OSM tile usage policy](https://operations.osmfoundation.org/policies/tiles/) — the rules
- [OSM wiki: Blocked tiles](https://wiki.openstreetmap.org/wiki/Blocked_tiles) — what gets blocked and why
- [osmdroid on GitHub](https://github.com/osmdroid/osmdroid) — source for the flag behaviour in §4
