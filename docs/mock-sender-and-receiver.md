# The mock signal and the receiver: both launchers

**How to make the band's data appear in the app when there is no band.**

The firmware does not exist. A debug build therefore ships **two launcher
icons**: one pretends to be the wrist band and writes alerts, the other is the
real app and reads them. This document covers both ends and the path between
them.

As of `feature/track-map` @ `73993e6`. For the design reasoning behind any of
it, see [`../CLAUDE.md`](../CLAUDE.md) ("Mock sender", "Track and map", "Alert
data"); this file is the operating manual.

---

## 1. The two launchers

Both are **the same app**. `applicationId` is `com.example.guardband` for both,
so a debug install puts two icons on one phone — it is not two installs, and
you cannot have one without the other.

| Icon | Opens | What it is | Needs a sign-in? | In a release build? |
|---|---|---|---|---|
| **GuardBand** | `ui.splash.SplashActivity` | The real app. The receiver. | **Yes** | Yes |
| **GuardBand Mock Sender** | `ui.mocksender.MockSenderActivity` | Stand-in for the ESP32 firmware. Writes alerts. | **No** | **No** |

The sender lives in the **debug source set** (`app/src/debug/`), including its
own `AndroidManifest.xml` entry, so a release build contains neither its code
nor its icon. OkHttp is `debugImplementation` for the same reason.

**The sender needs no account.** It writes to the Realtime Database over
unauthenticated REST, so a phone that only plays the band never has to log in.
The receiver does need an account, because `HomeActivity` is behind the auth
gate.

---

## 2. Before you start

| | |
|---|---|
| **A debug build** | `./gradlew installDebug`. A release build has no sender. |
| **`app/google-services.json`** | Required to build at all. Points at `guardband-aae65`. |
| **Your own debug SHA-1** | Only needed for **Google** sign-in on the receiver. Email + password works without it. Register it in the Firebase Console, then re-download `google-services.json`. |
| **Network on both phones** | Everything goes through the database; there is no local or Bluetooth path. |
| **One device id** | Everything reads and writes `devices/guardband-001` (`DeviceConstants.DEFAULT_DEVICE_ID`). There is no pairing yet, so **every account sees the same band.** |

**On the Security Rules.** The sender's writes depend on `".write": true` under
`devices/{deviceId}`, and the receiver's reads on `".read": true`. That is what
the draft in `RTDBS-RULES.md` says; nobody has read the *deployed* rules from
the tooling side. If a send comes back `401`, the live rules are tighter than
the draft and the sender needs an `?auth=` token added.

---

## 3. The signal path

```
┌─────────────────────────┐
│  GuardBand Mock Sender  │   (debug only, no sign-in)
└───────────┬─────────────┘
            │  HTTPS PUT ×2, in this order
            │    1. /devices/guardband-001/history/<sequenceId>.json
            │    2. /devices/guardband-001/latest.json
            ▼
┌───────────────────────────────────────────┐
│   Firebase Realtime Database              │
│   guardband-aae65 · asia-southeast1       │
│                                           │
│   devices/guardband-001/                  │
│     ├── latest              ← overwritten every send
│     └── history/{sequenceId} ← append-only
└───────────┬───────────────────┬───────────┘
            │                   │
   live listener on       live listener on
   latest                 history (newest 100)
            │                   │
            ▼                   ▼
      ┌───────────┐       ┌──────────────────────────────┐
      │ Track map │       │ Track's recent-alerts panel  │  GuardBand
      └───────────┘       │ + Alert history (See all)    │  (receiver)
                          └──────────────────────────────┘
```

**History is written before `latest`, deliberately.** The alert screens (the
panel and Alert history) derive everything from history, so a send that only
got as far as history is still visible there and merely leaves the
Track-facing `latest` stale. The reverse would show the alert on neither *and*
leave a permanent hole in the history.

**No Cloud Function is involved.** The project is on the Spark plan, which
cannot host one. `SCHEMA.md`'s "Receiver validation requirements" section still
describes an `ingestAlert` Function that was never deployed; the Security Rules
do that validation now. The payload shape in `SCHEMA.md` is still authoritative
and unchanged.

---

## 4. The sender launcher

### Controls

| Control | Writes | Position | Battery |
|---|---|---|---|
| **Simulate Panic** | one `PANIC` | Phone A's real GPS fix; falls back to random ±0.01° of Cebu City if no fix or permission denied | random 20–100% |
| **Simulate Check-in** | one `CHECKIN` (the band's FR-10 double-press) | same | same |
| **Simulate Low Battery** | one `LOW_BATTERY` | same | same |
| **Simulate Tracking Update** | one `TRACKING_UPDATE` | same | same |
| **Start walk / Stop walk** | a `TRACKING_UPDATE` every **5 s** | a ~165 m synthetic circle anchored at Phone A's real fix (or Cebu City if no fix), 24 steps | drains 95% → 20% |

Every send writes the full `SCHEMA.md` v1.0 payload: `schemaVersion` (the
*string* `"1.0"`), `deviceId`, `type`, `timestamp` (ISO 8601 UTC with
milliseconds), `location{lat,lng,accuracyMeters}`, `battery{percent,isCharging}`
and `sequenceId`.

### Reading the status line

| You see | Meaning |
|---|---|
| `Ready to send alerts` | Nothing sent yet this session. |
| `Sending PANIC...` | In flight. |
| `✓ Sent PANIC as #7 (real GPS)` | Written using Phone A's real GPS fix. |
| `✓ Sent PANIC as #7 (simulated)` | Written using the Cebu City fallback — no fix available or permission denied. |
| `⚑ Walking: 12 sent, latest #19 (real origin)` | Walk running, loop anchored at the real fix. |
| `⚑ Walking: 12 sent, latest #19 (simulated)` | Walk running, loop anchored at Cebu City. |
| `Walk stopped` | You tapped Stop. |
| `Walk stopped (harness left the foreground)` | You backgrounded the harness. See below. |
| `✗ Failed: HTTP 401 on PUT /devices/…` | See §8. |
| `✗ Walk stopped after 4: …` | A step failed, so the whole walk stopped. |

The **GPS badge** below the status line shows the current fix state independently of sends:

| Badge text | Meaning |
|---|---|
| `GPS fix: 10.3157, 123.8854 (±8 m)` | Real fix in hand; sends will use it. |
| `Acquiring GPS fix…` | Permission granted, waiting for first fix. Sends fall back to Cebu City in the meantime. |
| `No GPS fix — using simulated Cebu City location` | Permission granted but no fix yet and the provider never fired (e.g. indoors for a long time). |
| `Location permission denied — using simulated Cebu City location` | Permission was denied; all sends use Cebu City. |

### Sequence ids

`sequenceId` is both the history key and the field inside the payload. On the
first send of each app session the harness reads the existing history keys
(`?shallow=true`) and continues from the highest one, so **relaunching it
appends instead of overwriting what it wrote last time.** If you clear the
database from the Console, ids restart from 1.

### Two things the walk does on purpose

- **It stops when you background the harness.** A walk still writing while
  nobody is looking would make a dead band look alive — the exact thing the
  receiver's staleness badge exists to detect.
- **One failed step stops the whole walk.** Once a write is being refused every
  later one will be too, and a status line rewriting the same error every 5
  seconds hides which step first broke.

**5 s is not realistic.** FR-09's real interval is 60 s. The harness is fast so
a demo shows movement in seconds and stays inside the receiver's 180 s
staleness window.

---

## 5. The receiver launcher

Splash → Login (or straight to Home if a session is stored) → `HomeActivity`
and its three tabs: Track, Contacts, Profile. **Track reads the band twice**:
its map reads `latest`, and its recent-alerts panel reads `history`. The full
history is **Alert history**, opened with the panel's **See all**. (Until
2026-10-19 that was a fourth tab, the Alert tab.)

### Track tab: the map reads `latest`

The newest ping, whatever kind it was.

The band status panel (Reporting / Not reporting, Last seen, Battery) was
removed from the screen on 2026-10-14; the Check-in pill is now the tab's one
status line. Battery is still on each alert row.

| Element | Shows |
|---|---|
| Map + one marker | Last known position. OpenStreetMap tiles via osmdroid. |
| Empty overlay | *"Your band hasn't reported yet."* / *"No location fix yet"* / a read error. |
| **Navigate** (round arrow) | **Tap:** opens a maps app on the position. **Long-press:** re-centres and resumes following. Disabled with no fix. (Since 2026-10-19; the white Navigate pill is gone.) |
| **Check-in pill** | Read-only status of the latest check-in: *"Last check-in: 5 minutes ago"*, or *"No recent check-ins"*. Read from **history**, not `latest` (see below). Not tappable; the app never sends a check-in. |
| Layers | UI-only stub. |
| **Recent alerts sheet** | A draggable bottom sheet over the map: collapsed it shows only its header, dragged up the newest **three**, from `history`, tracking updates hidden, in the same rows as Alert history. **See all** opens Alert history. |

### Alert history: reads `history`

The incident log, opened from **See all** on Track's panel (Back returns to
Track). Reads the newest 100 raw entries, hides tracking updates, and shows up
to 50. The panel and this screen share one ViewModel and one listener, so they
always agree.

| Element | Shows |
|---|---|
| **Current status** card | The newest **non-tracking** alert: type, time, coordinates, battery. |
| **History** list | The rest, newest first, colour-coded by type. |
| Empty state | Says *which* kind of empty: nothing sent, only tracking updates, or nothing readable. |
| **Try again** | Re-subscribes after a failed read. The panel has its own. |

### The one thing to understand about the map and the alerts

**They deliberately read different nodes.** Every payload overwrites `latest`,
tracking updates included. If the alert screens read `latest`, then one
tracking ping after a panic would make the status card say "Location update"
while the panic sat one row below it. So the alerts are derived from history
and ignore `latest` entirely — and the map reads `latest`, because "the newest
ping whatever it was" is exactly what a map wants.

### What each type does

| Type sent | Track map | Recent-alerts panel and Alert history |
|---|---|---|
| `PANIC` | marker moves | new top row; becomes **Current status** |
| `CHECKIN` | marker moves, and the **Check-in pill** updates | new top row |
| `LOW_BATTERY` | marker moves | new top row |
| `TRACKING_UPDATE` | marker moves | **hidden**, and never becomes Current status |
| an unrecognised type | marker moves if it has a location | shown as **"Unknown alert"** |

A tracking update being invisible in the alert lists is correct, not a bug. An
*unknown* type is still shown, so a type a future firmware adds will appear
rather than vanish.

---

## 6. Walkthroughs

Phone A = Mock Sender. Phone B = GuardBand, signed in, Track tab open.
"Panel" is Track's recent-alerts panel; "History" is Alert history, opened
with the panel's **See all**.

### A · Empty database

Clear `devices/guardband-001` in the Console first.

- **Track:** Cebu City at zoom 13, *"Your band hasn't reported yet."*, Navigate
  disabled.
- **Panel:** *"No alerts from your band yet."* **History** says the same.

### B · First panic

Phone A → **Simulate Panic**. Expect `✓ Sent PANIC as #1`.

- **Track:** marker appears and the camera moves to it at street zoom.
- **Panel:** a red **Panic** row appears at the top, without reopening anything.
- **History:** Current status is **Panic** with its coordinates and battery,
  and the same row heads the list.
- Send three more panics: the panel shows the newest three, History shows all
  four.

### C · Walk mode — the marker moves

Phone A → **Start walk**.

- **Track:** the marker steps around a circle every 5 s.
- **Panel and History:** nothing changes. Current status stays **Panic** —
  tracking updates are hidden. This is the single best demonstration of §5's
  split, and you can watch both halves on one screen.

### D · Going stale — the ticker

Stop the walk. Leave Track open and **wait 3 minutes**.

**Nothing on screen shows this any more**: the badge and *Last seen* went with
the status panel on 2026-10-14. The 30 s timer still runs in the ViewModel and
`TrackViewModelTest` still covers the flip, and the Check-in pill keeps ageing on
the same timer, so a check-in's "N minutes ago" counting up is the visible sign
the timer is alive.

### E · Follow, and not fighting the user

1. Start the walk; confirm the camera follows the marker.
2. **Drag the map away.** Keep walking: the camera must now stay where you put
   it, while the marker keeps moving.
3. **Long-press** the round Navigate button: the map jumps back to the band and follows again.
4. **Tap** (not drag) the map: following must survive — a tap is not a pan.

### F · Navigate

With a fix, tap the round **Navigate** button → a maps app opens on the band's coordinates. With
no fix the button is disabled.

### G · Reporting, but no fix

**The harness cannot produce this** — it always sends a location. Do it by hand:
delete `latest/location` in the Console.

- **Track:** the marker **stays where it was** and the overlay reads *"No
  location fix yet"*. The band is talking but
  cannot see the sky, and throwing away the last known position in that moment
  would discard the most useful thing on screen.

### H · A check-in (FR-10)

The band's double-press, standing in. The Check-in pill is the one part of
the Track tab that reads **history** rather than `latest`, so it survives the
tracking pings that follow.

1. Fresh state: the pill reads *"No recent check-ins"* (or *"Loading
   check-ins…"* for a moment). If an older check-in is already in the newest
   100 history rows, it shows that one instead.
2. Phone A: **Simulate Check-in**. Within a second or two the pill on Phone B
   reads *"Last check-in: just now"*, and the panel gains a green
   *Check-in* row at the top (History too).
3. Wait a minute or two without sending: the pill ages to *"Last check-in: 1
   minute ago"*, *"2 minutes ago"*, ... on the 30 s ticker, with no new data.
4. Phone A: **Start walk**. Tracking updates overwrite `latest` every 5 s; the
   marker moves, but the pill keeps the check-in's time. (Reading `latest`
   would have lost it on the first ping.)
5. Tap the pill: nothing happens, by design. With TalkBack on it is read as
   text, not as a button.
6. Phone A: **Simulate Panic**. The panel's top row, and History's Current
   status, become the panic exactly as before; the pill still shows the
   check-in.

---

## 7. One phone is enough

Two phones are nicer for a demo, but because both icons belong to one install
you can run the whole thing on a single device: send from the Mock Sender, press
Home, open GuardBand, watch it arrive. The database is the only channel, so the
sender does not even have to be running for the receiver to show what it wrote.

Equally, the **Firebase Console works as a third sender**. Editing
`devices/guardband-001` by hand is still the only way to produce malformed
payloads, missing fields and unknown types — see §9.

---

## 8. Troubleshooting

| Symptom | Cause |
|---|---|
| `✗ Failed: HTTP 401 …` | Deployed rules are tighter than the draft. The sender writes unauthenticated; it would need an `?auth=` token. |
| `✗ Failed: HTTP 400 …` | A `.validate` rejected the payload — check the enum name, `percent` 0–100, and all six required children. |
| `✗ Failed:` a network error | No connectivity, or the database URL from `google-services.json` is wrong. |
| **GPS badge stuck on "Acquiring…"** | Indoors or no satellite view. Sends fall back to Cebu City automatically — there is nothing to fix. |
| **No location permission dialog on first launch** | Already denied once. Go to App Settings → Permissions → Location and grant it manually. |
| **Sends show `(simulated)` even though permission is granted** | No fix has arrived yet — the badge will show "Acquiring…". Walk until outdoors or use the network provider fix. |
| **Every tile reads "access blocked"** | The user agent. OSM blocks shared identifiers — `osmdroid` and the `com.example.*` prefix that is still this app's `applicationId`. The block is an **HTTP 200 PNG** with the text drawn in, so osmdroid caches and draws it and the log stays clean. `OsmdroidConfig.USER_AGENT` and `GuardBandTileSource` are the two halves of the fix; neither works alone — [`osmdroid.md`](osmdroid.md). |
| **Map is blank/grey, everything else works** | `OsmdroidConfig` did not run before the `MapView` was built. It must be applied in `onCreate`, ahead of inflation. |
| Marker never appears, panel does | Reporting without a fix (§G), or the payload has no `location`. |
| Badge stuck on **Reporting** after the band goes quiet | The 30 s staleness ticker (§D). |
| Camera won't follow the band | Expected after you pan. Long-press the round Navigate button. |
| Recent alerts panel / Alert history ignore your tracking updates | Correct (§5). Send a `PANIC` to see them react. |
| Receiver shows nothing at all | Wrong Firebase project, no network, or you are looking at a different `deviceId`. There is only one: `guardband-001`. |
| Sequence ids restarted at 1 | The history node was cleared. |
| No Mock Sender icon | You installed a release build. |

---

## 9. What you cannot demo yet

| | |
|---|---|
| **Real hardware** | No firmware. Everything above is the stand-in. |
| **SMS to contacts (FR-05)** | The band sends it, not the app. Nothing here exercises it. |
| **A real check-in (FR-10)** | A band double-press. **Simulate Check-in** sends the same `CHECKIN` payload, which is what the receiver would see (walkthrough H). |
| **Pairing** | One hardcoded device id, so every account watches the same band. |
| **Malformed payloads, unknown types, missing fields** | Console only. The harness always writes a valid payload. |
| **Map layers** | UI stub. |
| **A public demo** | The draft rules leave the wearer's coordinates **world-readable**, and the map runs on OpenStreetMap's volunteer tile servers, whose usage policy is written for light use. Close both first. |
