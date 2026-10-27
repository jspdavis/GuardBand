# The mock signal and the receiver: both launchers

**How to make the band's data appear in the app when there is no band.**

The firmware does not exist. A debug build therefore ships **two launcher
icons**: one pretends to be the wrist band and writes alerts, the other is the
real app and reads them. This document covers both ends and the path between
them.

As of `feature/mock-sender-band-button` (2026-10-27): the sender is one band
button and a battery slider (§4). For the design reasoning behind any of
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

**On the Security Rules.** The sender's writes depend on open writes under
`devices/{deviceId}`, and both its sequence-id seed read and the receiver's
reads on `".read": true`. The rules deployed on 2026-10-11 allow all of it.
The proposal pending in `RTDBS-RULES.md` (Option A+,
[`database-rules.md`](database-rules.md) §4) keeps all of it working but makes
history **append-only**: a history id can be written once, never again. If a
send comes back `401`, see §8.

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

**It behaves like the band (2026-10-27):** one button, decoded with the
firmware's own timings, and a battery slider. It replaced six separate
buttons (Simulate Panic, Check-in, Double-press Check-in, Low Battery,
Tracking Update, Start walk).

### The band button

| On the button | Sends | Then |
|---|---|---|
| **Hold 1.5 s** | one `PANIC`, the moment the hold reaches 1.5 s, while still held (a phone vibration says it went) | **tracking**: a `TRACKING_UPDATE` every **5 s** for **30 minutes** (FR-09) |
| **Double-press** (second press within 2 s) | one `CHECKIN` (FR-10) | nothing; a check-in during a panic does **not** stop the tracking |
| **During a panic, hold 5 s** | one `CHECKIN` | tracking **stops**: the panic is cancelled |
| **Single press** | nothing | the status line says "Single press: no action" |

- **A hold during a panic never sends a second panic.** One is already
  running. Let go before 5 s and nothing is sent ("Hold to 5 s to cancel the
  panic"); at 1.5 s the status line says to keep holding.
- **A check-in cannot cancel a panic, on purpose.** Someone standing over
  the wearer could make them double-press. The cancel is a deliberate 5 s
  hold, and it sends a check-in so the guardian sees "I'm OK" right after
  the incident.
- **Tracking starts after the panic has landed**, so its first position is
  always after the panic in the history. It stops after 30 minutes, on a
  cancel, on a failed send, or when the Mock Sender leaves the screen.
- **TalkBack:** the button offers the same three things as accessibility
  actions ("Send a panic", "Check in", "Cancel the panic"), since a hold or a
  double-press cannot be timed through it.
- The timings live in one place, `BandButtonDecoder` (`PANIC_HOLD_MS` 1.5 s,
  `DOUBLE_PRESS_MS` 2 s, `CANCEL_HOLD_MS` 5 s), with tests in
  `app/src/testDebug/`. If the firmware's change, change them there.

### The battery slider

The battery level every message reports, 0–100%, never charging (the band is
on a wrist). It starts at Phone A's own level.

- **Dropping it to 20% or below sends a `LOW_BATTERY` by itself** (FR-11), on
  release of the slider, not while dragging through. Once only: it sends
  again only after the slider has gone back above 20%. The label reads
  "Battery: 15% (low battery alert sent)".
- Starting at or below 20% sends nothing: only a drop does.

### Position

| | Panic, check-in, low battery | Tracking after a panic |
|---|---|---|
| GPS fix | Phone A's real fix | Phone A's real fix, read afresh every step |
| No fix, or permission denied | a random point within ±0.01° of Cebu City | a ~165 m synthetic circle (24 steps) around Cebu City |

Every send writes the full `SCHEMA.md` v1.0 payload: `schemaVersion` (the
*string* `"1.0"`), `deviceId`, `type`, `timestamp` (ISO 8601 UTC with
milliseconds), `location{lat,lng,accuracyMeters}`, `battery{percent,isCharging}`
and `sequenceId`. **One send at a time** (`AlertSender`'s lock): a check-in
pressed while a tracking step is in flight waits for it, rather than taking
the same sequence id and overwriting it.

### Reading the screen

The **tracking line** (under the button):

| You see | Meaning |
|---|---|
| `Not tracking` | No panic running. |
| `Tracking after panic: 12 sent, 28 min left` | Running. "(every 5 s; the band sends every 60 s)" underneath. |
| `Tracking ended: 30 minutes after the panic` | It ran its course. |
| `Tracking stopped: panic cancelled` | The 5 s hold. |
| `Tracking stopped: the Mock Sender left the screen` | You switched away or the screen locked. See below. |
| `Tracking stopped after 4: …` | A step failed, so tracking stopped. |

The **status line**:

| You see | Meaning |
|---|---|
| `Sending PANIC...` | In flight. |
| `✓ Sent PANIC as #7 (real GPS · 🔋64%)` | Written at Phone A's real fix. |
| `✓ Sent PANIC as #7 (simulated · 🔋64%)` | Written at the Cebu City fallback. |
| `Press again within 2 s to check in…` | One press so far. |
| `✗ Failed: HTTP 401 on PUT /devices/…` | See §8. |

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

### Two things tracking does on purpose

- **It stops when the Mock Sender leaves the screen**, including when the
  phone locks. A loop still writing while nobody is looking would make a dead
  band look alive, the exact thing the receiver's staleness check exists to
  detect. For a walking test, keep the screen on (raise the screen timeout).
- **One failed step stops it.** Once a write is being refused every later one
  will be too, and a line rewriting the same error every 5 seconds hides which
  step first broke.

**5 s is not realistic.** FR-09's real interval is 60 s. The harness is fast so
a demo shows a path in seconds and stays inside the receiver's 180 s
staleness window.

---

## 5. The receiver launcher

Splash → Login (or straight to Home if a session is stored) → `HomeActivity`
and its three tabs: Track, Contacts, Device (Profile until 2026-10-27; the
Device tab shows the band's status, battery and last alert from the same
history read, [device-tab.md](device-tab.md)). **Track reads the band twice**:
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
| Map + one marker | Last known position. MapTiler tiles via osmdroid; "Map unavailable" if the build has no `MAPTILER_API_KEY`. |
| Empty overlay | *"Your band hasn't reported yet."* / *"No location fix yet"* / a read error. |
| **Re-centre** (round target) | **Tap:** animates back to the band's last known position, zooms back in to street level if you had zoomed far out, and resumes following. Disabled with no fix. (Since 2026-10-27; it opened Google Maps on a tap until then.) |
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

Phone A → **hold the band button 1.5 s**. Expect `✓ Sent PANIC as #1`, and
tracking starts (the tracking line counts up). For a panic on its own, cancel
it at once: hold 5 s, which also sends a check-in.

- **Track:** marker appears and the camera moves to it at street zoom.
- **Panel:** a red **Panic** row appears at the top, without reopening anything.
- **History:** Current status is **Panic** with its coordinates and battery,
  and the same row heads the list.
- Cancel and send three more panics: the panel shows the newest three,
  History shows them all (with the cancels' check-ins between them).

### C · Tracking after a panic — the marker moves

Phone A → **hold 1.5 s** for a panic; tracking follows by itself.

- **Track:** the marker follows Phone A every 5 s (a circle round Cebu City
  without a GPS fix).
- **Panel and History:** nothing changes. Current status stays **Panic** —
  tracking updates are hidden. This is the single best demonstration of §5's
  split, and you can watch both halves on one screen.

### D · Going stale — the ticker

Cancel the panic (hold 5 s). Leave Track open and **wait 3 minutes**.

**Nothing on screen shows this any more**: the badge and *Last seen* went with
the status panel on 2026-10-14. The 30 s timer still runs in the ViewModel and
`TrackViewModelTest` still covers the flip, and the Check-in pill keeps ageing on
the same timer, so a check-in's "N minutes ago" counting up is the visible sign
the timer is alive.

### E · Follow, and not fighting the user

1. Send a panic so tracking runs; confirm the camera follows the marker.
2. **Drag the map away.** Keep tracking: the camera must now stay where you put
   it, while the marker keeps moving.
3. **Tap** the round target button: the map animates back to the band and follows again.
4. **Tap** (not drag) the map: following must survive — a tap is not a pan.

### F · Re-centre

With a fix, drag the map far away and zoom out a long way, then tap the round
**target** button: the map animates back to the band at street level. Zoom in
closer than street level first, and the tap keeps your zoom. With no fix the
button is disabled.

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
2. Phone A: **double-press** the band button. Within a second or two the pill on Phone B
   reads *"Last check-in: just now"*, and the panel gains a green
   *Check-in* row at the top (History too).
3. Wait a minute or two without sending: the pill ages to *"Last check-in: 1
   minute ago"*, *"2 minutes ago"*, ... on the 30 s ticker, with no new data.
4. Phone A: **hold 1.5 s** for a panic. Its tracking updates overwrite
   `latest` every 5 s; the marker moves, but the pill keeps the check-in's
   time. (Reading `latest`
   would have lost it on the first ping.)
5. Tap the pill: nothing happens, by design. With TalkBack on it is read as
   text, not as a button.
6. The panel's top row, and History's Current status, are the panic; the
   pill still shows the check-in. Hold 5 s to cancel: a new check-in, and
   the pill shows it.

---

## 7. One phone is enough

Two phones are nicer for a demo, but because both icons belong to one install
you can run the whole thing on a single device: send from the Mock Sender, press
Home, open GuardBand, watch it arrive. **Tracking stops as soon as the Mock
Sender leaves the screen**, so a panic's path needs two phones. The database is the only channel, so the
sender does not even have to be running for the receiver to show what it wrote.

Equally, the **Firebase Console works as a third sender**. Editing
`devices/guardband-001` by hand is still the only way to produce malformed
payloads, missing fields and unknown types — see §9.

---

## 8. Troubleshooting

| Symptom | Cause |
|---|---|
| `✗ Failed: HTTP 401 on PUT /devices/…/history/…` | Under the append-only rules (Option A+, [`database-rules.md`](database-rules.md) §4), that history id already exists: an earlier send landed but its reply was lost, or a second sender is running. Relaunch the sender; it re-reads the keys and moves past them. |
| `✗ Failed: HTTP 401 reading the history keys` | The rules require sign-in to read `devices/` (Option B or C). The sender reads unauthenticated; it would need an `?auth=` token. |
| `✗ Failed: HTTP 400 …` | A `.validate` rejected the payload — check the enum name, `percent` 0–100, and all six required children. |
| `✗ Failed:` a network error | No connectivity, or the database URL from `google-services.json` is wrong. |
| **GPS badge stuck on "Acquiring…"** | Indoors or no satellite view. Sends fall back to Cebu City automatically — there is nothing to fix. |
| **No location permission dialog on first launch** | Already denied once. Go to App Settings → Permissions → Location and grant it manually. |
| **Sends show `(simulated)` even though permission is granted** | No fix has arrived yet — the badge will show "Acquiring…". Go outdoors, or wait for the network provider's fix. |
| **A panic's path stops partway** | The Mock Sender left the screen (the phone locked, or you switched app): tracking stops then. Keep the screen on. |
| **A double-press sent nothing** | The second press came more than 2 s after the first ("Single press: no action"), or the first was held past 1.5 s, which makes it a hold. |
| **"Map unavailable", grey map** | The build has no MapTiler key. Add `MAPTILER_API_KEY` to `local.properties` and rebuild — [`osmdroid.md`](osmdroid.md) §4.4. |
| **Grey map, tiles refused** | The MapTiler key, or its user-agent restriction not matching `OsmdroidConfig.USER_AGENT`, or the Free plan's monthly limit — [`osmdroid.md`](osmdroid.md) §6. |
| **Map is blank/grey, everything else works** | `OsmdroidConfig` did not run before the `MapView` was built. It must be applied in `onCreate`, ahead of inflation. |
| Marker never appears, panel does | Reporting without a fix (§G), or the payload has no `location`. |
| Badge stuck on **Reporting** after the band goes quiet | The 30 s staleness ticker (§D). |
| Camera won't follow the band | Expected after you pan. Tap the round target button. |
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
| **A real check-in (FR-10)** | A band double-press. The Mock Sender's double-press sends the same `CHECKIN` payload, which is what the receiver would see (walkthrough H). |
| **A low-battery alert** | Drag the battery slider to 20% or below. |
| **Pairing** | One hardcoded device id, so every account watches the same band. |
| **Malformed payloads, unknown types, missing fields** | Console only. The harness always writes a valid payload. |
| **Map layers** | UI stub. |
| **A public demo** | The draft rules leave the wearer's coordinates **world-readable**. Close that first. The map runs on MapTiler's Free plan, which is non-commercial and pauses at its monthly limit. |
