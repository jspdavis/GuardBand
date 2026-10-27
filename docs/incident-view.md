# Incident view

One panic on a map with the tracking updates that followed it drawn as a
path, and a summary underneath. Built 2026-10-27 (`feature/incident-view`).
It gives FR-02's "incident status" and FR-09's tracking something to show.

| | |
|---|---|
| **Screen** | [`ui/incident/IncidentFragment`](../app/src/main/java/com/example/guardband/ui/incident/IncidentFragment.kt), `fragment_incident.xml`: a pushed screen in the Home host |
| **State** | [`IncidentViewModel`](../app/src/main/java/com/example/guardband/ui/incident/IncidentViewModel.kt), `IncidentUiState` |
| **Grouping** | [`IncidentGrouper`](../app/src/main/java/com/example/guardband/ui/incident/IncidentGrouper.kt) (pure; `IncidentGrouperTest`) |
| **Read** | `AlertRepository.observeIncident(panicSequenceId)` |
| **Related** | [`alert-data.md`](alert-data.md), [`osmdroid.md`](osmdroid.md) §5.5, [`../SCHEMA.md`](../SCHEMA.md) |

---

## 1. Ways in: four taps, one opener

| Entry | Where | What calls it |
|---|---|---|
| A panic row in the recent-alerts sheet | Track | `RecentAlertsPanel` → `AlertHistoryAdapter`'s `onPanicClick` |
| A panic row in Alert history | Alert history | `AlertHistoryFragment` → the same adapter callback |
| A panic pin on the map | Track | `TrackViewModel.onAlertPinTapped` → `TrackEvent.OpenIncident` |
| "View incident" on the face's bubble | Track | `TrackViewModel.onViewIncidentClicked` → `TrackEvent.OpenIncident` |

**Every one ends at `HomeViewModel.onOpenIncident(alert)`**, the one way to
open an incident. It ignores anything `IncidentEntry.opensIncident` says no to,
which is everything but a panic, and sends `HomeEvent.ShowIncident(sequenceId)`.
`IncidentEntryPointsTest` checks that the row, the pin and the bubble open the
same incident.

- **Only panics are tappable.** Check-in, low-battery, unknown and (never
  listed) tracking rows stay plain rows with no click, ripple or action. A
  tappable row has the card's ripple and a content description: what it
  shows, then "Opens the incident."
- **Not tappable:** Alert history's latest card and the Notifications inbox's
  rows. They were not asked for; either can call the same opener.
- **The bubble's action** shows while the band's latest **non-tracking** alert
  is a panic (`TrackUiState.bandPanic`, D2's "latest", the one Alert history's
  card shows). It stays through the tracking updates that follow, and goes
  when a check-in or a low-battery alert arrives. `devices/{id}/latest` is not
  used: every tracking update overwrites it, so a panic would leave it within
  seconds. The action is how to reach a panic whose pin the face hides (the
  pin rule hides anything within 10 m of the band).

**Hosting.** The one Home destination not added once: `HomeActivity` adds a new
`IncidentFragment` (panic number in its arguments) each time one opens, and
**removes** it when anything else is shown, so its ViewModel and its listener
end with it. Back returns to the screen that opened it, Track or Alert history
(`incidentReturnTag`, saved across a restore), not to the last tab. There is
still no back stack and no `replace()`.

---

## 2. How tracking updates are tied to a panic

**There is no link in the payload.** SCHEMA.md v1.0 has no incident id or
reference: a `TRACKING_UPDATE` carries the same seven fields as every alert.
So `IncidentGrouper.incidentFor(panicSequenceId, alerts)` groups by order and
time, as FR-09 describes the band sending them:

1. **After the panic, before the next panic, by `sequenceId`.** The band's
   own counter, the order every screen uses; a band whose clock jumps cannot
   move an update into another incident. A check-in or a low-battery alert in
   between does not end the incident; the next panic does.
2. **Within 30 minutes of the panic (`WINDOW_MS`), by timestamp.** Later is
   left out. Stamped *before* the panic (the clock went back) is kept, because
   the counter says it came after. If either timestamp is unreadable, only the
   order applies.
3. **Nothing before the panic belongs to it.** A tracking update with no
   earlier panic belongs to no incident.

Every tracking update in the group is counted. Only the ones with a usable
position (`AlertPinSelector.isUsable`: present, in range, not (0, 0)) are on
the path. **SCHEMA.md and the payload are unchanged.** If the firmware ever
adds an incident id, `incidentFor` is the one place to use it.

---

## 3. What the screen reads

**Its own live read, from the panic on:** `observeIncident(panicSequenceId)`
is `history.orderByKey().startAt("<panic>").limitToFirst(400)`. No index is
needed, and no rules change: `history` already has its own `.read`.

- **Why not the alert screens' window.** That is the newest 100 raw entries.
  The mock sender's tracking sends one every 5 s, so after about 8 minutes the
  panic, and the path behind it, have left the window. Reading from the panic
  on gets the whole path however much has arrived since.
- **400** (`AlertRepository.INCIDENT_WINDOW`) covers 30 minutes of the mock sender's tracking
  (360), or FR-09's real 30, with room for other alerts. About 80 KB at most.
- **One listener per open screen**, in `IncidentViewModel`'s `viewModelScope`.
  `awaitClose` removes it when the ViewModel is cleared: when the host
  removes the Fragment (Back, or any other screen shown) and when Log Out
  finishes the host. `IncidentViewModelTest` checks that clearing the store
  leaves no collector.
- **Tracking updates reach nothing else.** No list and no pin shows them; the
  alert screens' filtering (D1) is untouched.
- **Place name:** the panic point only, once, through the same
  `GeocoderPlaceRepository` and the same 4-decimal rounding as the alert rows.
- **Nothing is written.** The app still never writes to `devices/`.

---

## 4. The screen

- **Map (top 55%)**: Track's setup through `ui/track/MapTilerMap`: the no-key
  guard, the base map Track last used (`MapLayerPrefs`, read once), the dark
  map in dark mode, the logo for the tiles, and the credits through
  `MapAttribution`. `OsmdroidConfig` runs in `onCreate`. Overlays, bottom to
  top: the path (a `Polyline` in `alert_tracking`, 4 dp, from the panic
  through each tracked position in `sequenceId` order), the panic's pin
  (`ic_alert_pin_panic`, the Track pin), and the end marker at the newest
  tracked position (`ic_incident_end`, a ringed dot). None has a bubble.
  No tracked position yet: only the panic's pin. A panic with no position: a
  pill says so.
- **Fit**: once, when the incident is first drawn, after the first layout:
  the whole path with a 48 dp margin, never closer than zoom 18; one point
  at zoom 16. Later updates extend the path without moving the camera.
- **Summary**: "Panic", then "5 min ago · Oct 27, 3:04 PM" (only the date
  from a day on); the place name (coordinates until it arrives); the
  **latest** battery reading in the incident, not only the panic's; "12
  tracking updates" and "Latest update: 1 min ago · 3:15 PM", or "No tracking
  updates received yet". The times age on a 30 s ticker.
- **Errors**: a failed first read, or a panic no longer in the history, is
  the error view with Try again; a failure with the path on screen is a toast.
- Every visual choice is marked `DESIGN: Jul`.

**Not covered by tests:** the Fragment, the map and its fit, the host's
add/remove/Back, and the RTDB query itself (`startAt` on an integer key
needs a real database). The grouping, the summary state, the entry points,
the opener and the listener's removal are covered.

---

## 5. Phone checks

Two phones: the Mock Sender on one (the band), the app on the other. Keep
the Mock Sender on screen: its tracking stops when the phone locks.

- [ ] Hold the band button 1.5 s: a panic, and tracking starts by itself.
      Walk a minute or two (or let the synthetic loop run indoors).
- [ ] **From the sheet:** drag the recent-alerts sheet up, tap the panic row.
      The Incident view opens with the panic's pin, a blue path round the
      loop, and the end dot where the face is on Track. The map fits the
      path. Back returns to Track.
- [ ] **From Alert history:** See all → tap the panic row. Same path. Back
      returns to Alert history, and Back again to Track.
- [ ] **From the map pin:** if the band has walked more than 10 m away, tap
      the red pin. Same path. Back returns to Track.
- [ ] **From the face:** tap the face; the bubble says "Your band" and "View
      incident". Tap it. Same path. Back returns to Track.
- [ ] While the incident is open and tracking continues, the path grows and
      the count and "Latest update" move on; the camera does not jump.
- [ ] Double-press (a check-in). The face's bubble no longer offers "View incident";
      tapping the face still opens it and pans to the band.
- [ ] Tap a check-in or low-battery row, or a check-in pin: no incident (a
      pin shows only its label).
- [ ] **No updates:** send a fresh panic and open it within 5 s, before the
      first tracking step: only the pin, and "No tracking updates received
      yet".
- [ ] **Cancel:** during a panic, hold the band button 5 s. Tracking stops, a
      check-in arrives, and the incident's path stops growing.
- [ ] **Panic pin priority:** send a panic, then drag the battery slider to
      20% without moving (a low-battery alert). Walk away: the red panic pin
      is the one on that spot.
- [ ] **Dark mode** and a non-default layer (Satellite): the incident map
      follows both; the credits and logo show and the logo suits the tiles.
- [ ] TalkBack on a panic row reads it and "Opens the incident."
- [ ] Log Out from Settings with an incident opened earlier; RTDB traffic
      stops.
