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
      ┌───────────┐       ┌───────────┐
      │ Track tab │       │ Alert tab │     GuardBand (receiver)
      └───────────┘       └───────────┘
```

**History is written before `latest`, deliberately.** The Alert tab derives
everything from history, so a send that only got as far as history is still
visible there and merely leaves the Track-facing `latest` stale. The reverse
would show the alert nowhere in the Alert tab *and* leave a permanent hole in
the history.

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
and its four tabs. Two of them read the band.

### Track tab — reads `latest`

The newest ping, whatever kind it was.

| Element | Shows |
|---|---|
| Map + one marker | Last known position. OpenStreetMap tiles via osmdroid. |
| **Reporting / Not reporting** | Whether the last report is under **180 s** old. |
| **Last seen …** | `just now` under a minute, then minutes / hours / days. |
| **Battery n%** | `· Charging` when the payload says so; `Battery unknown` if absent. |
| Empty overlay | *"Your band hasn't reported yet."* / *"No location fix yet"* / a read error. |
| **Navigate** | Opens a maps app on the position. Disabled with no fix. |
| Recenter (◎) | Re-centres and resumes following. |
| **Check-in pill** | Read-only status of the latest check-in: *"Last check-in: 5 minutes ago"*, or *"No recent check-ins"*. Read from **history**, not `latest` (see below). Not tappable; the app never sends a check-in. |
| Layers | UI-only stub. |

### Alert tab — reads `history`

The incident log. Reads the newest 100 raw entries, hides tracking updates, and
shows up to 50.

| Element | Shows |
|---|---|
| **Current status** card | The newest **non-tracking** alert: type, time, coordinates, battery. |
| **History** list | The rest, newest first, colour-coded by type. |
| Empty state | Says *which* kind of empty: nothing sent, only tracking updates, or nothing readable. |
| **Retry** | Re-subscribes after a failed read. |

### The one thing to understand about the two tabs

**They deliberately read different nodes.** Every payload overwrites `latest`,
tracking updates included. If the Alert tab read `latest`, then one tracking
ping after a panic would make its status card say "Location update" while the
panic sat one row below it. So the Alert tab derives its card from history and
ignores `latest` entirely — and Track reads `latest`, because "the newest ping
whatever it was" is exactly what a map wants.

### What each type does on each tab

| Type sent | Track tab | Alert tab |
|---|---|---|
| `PANIC` | marker + panel update | shown, and becomes **Current status** |
| `CHECKIN` | marker + panel update, and the **Check-in pill** updates | shown |
| `LOW_BATTERY` | marker + panel update | shown |
| `TRACKING_UPDATE` | marker + panel update | **hidden**, and never becomes Current status |
| an unrecognised type | panel updates | shown as **"Unknown alert"** |

A tracking update being invisible on the Alert tab is correct, not a bug. An
*unknown* type is still shown, so a type a future firmware adds will appear
rather than vanish.

---

## 6. Walkthroughs

Phone A = Mock Sender. Phone B = GuardBand, signed in, Track tab open.

### A · Empty database

Clear `devices/guardband-001` in the Console first.

- **Track:** Cebu City at zoom 13, *"Your band hasn't reported yet."*, no status
  panel, Navigate disabled.
- **Alert:** *"No alerts from your band yet."*

### B · First panic

Phone A → **Simulate Panic**. Expect `✓ Sent PANIC as #1`.

- **Track:** marker appears, camera moves to it at street zoom, panel reads
  *Reporting · Last seen just now · Battery n%*.
- **Alert:** Current status becomes **Panic** with its coordinates and battery.

### C · Walk mode — the marker moves

Phone A → **Start walk**.

- **Track:** the marker steps around a circle every 5 s; battery ticks down;
  *Last seen* stays *just now*.
- **Alert:** nothing changes. Current status stays **Panic** — tracking updates
  are hidden. This is the single best demonstration of §5's split.

### D · Going stale — the ticker

Stop the walk. Leave Track open and **wait 3 minutes**.

The badge must flip to **Not reporting** on its own, with no interaction, and
*Last seen* must start counting up. Nothing arrives from the database to cause
this — a silent band sends nothing — so it is driven by a 30 s timer in the
ViewModel. **If it never flips, that timer is broken**, and that is the whole
bug the timer exists to prevent.

### E · Follow, and not fighting the user

1. Start the walk; confirm the camera follows the marker.
2. **Drag the map away.** Keep walking: the camera must now stay where you put
   it, while the marker keeps moving.
3. Tap **recenter**: it jumps back to the band and follows again.
4. **Tap** (not drag) the map: following must survive — a tap is not a pan.

### F · Navigate

With a fix, tap **Navigate** → a maps app opens on the band's coordinates. With
no fix the button is disabled.

### G · Reporting, but no fix

**The harness cannot produce this** — it always sends a location. Do it by hand:
delete `latest/location` in the Console.

- **Track:** the marker **stays where it was** and the overlay reads *"No
  location fix yet"*, while *Last seen* still updates. The band is talking but
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
   reads *"Last check-in: just now"*, and the Alert tab gains a green
   *Check-in* row.
3. Wait a minute or two without sending: the pill ages to *"Last check-in: 1
   minute ago"*, *"2 minutes ago"*, ... on the 30 s ticker, with no new data.
4. Phone A: **Start walk**. Tracking updates overwrite `latest` every 5 s; the
   marker moves, but the pill keeps the check-in's time. (Reading `latest`
   would have lost it on the first ping.)
5. Tap the pill: nothing happens, by design. With TalkBack on it is read as
   text, not as a button.
6. Phone A: **Simulate Panic**. The Alert tab's Current status becomes the
   panic exactly as before; the pill still shows the check-in.

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
| Camera won't follow the band | Expected after you pan. Tap recenter. |
| Alert tab ignores your tracking updates | Correct (§5). Send a `PANIC` to see it react. |
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
