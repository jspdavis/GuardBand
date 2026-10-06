# Alert Data and the Alert Tab

How the app reads the band's alerts out of Realtime Database and puts them on
screen.

Branch `feature/alert-tab`, commits `fb69971`, `ec5ccb3`, `919f815`, `7fa3328`.

This supersedes §6 ("Data layer") and §10 ("Notes for the Firebase pass") of
[home-host-and-bottom-nav.md](home-host-and-bottom-nav.md), which describe the
in-memory version this replaced.

**`SCHEMA.md` is the authority for the payload.** If this file, the code and
`SCHEMA.md` disagree, `SCHEMA.md` wins and the disagreement is a bug report,
not something to resolve by picking one.

---

## 1. What changed

Alerts were the last thing still served from `InMemoryStore`. They now come
from `devices/{deviceId}` over a live listener.

| | Before | Now |
|---|---|---|
| Source | `InMemoryStore`, 4 seeded alerts, 1.2 s fake latency | `devices/{deviceId}/history`, live |
| History type | `Flow<Result<List<Alert>>>` | `Flow<Result<AlertHistory>>` |
| Window | all of it | newest 100 raw, 50 shown |
| Tracking updates | shown | hidden |
| Latest card | highest `sequenceId`, any type | newest **non-tracking** entry |
| Failures | couldn't happen | `AlertError`, mapped by code |
| Error UI | toast of `error.message` | error view + **Retry**, or a toast if data is already on screen |
| Empty UI | "No alerts from your band yet." | says *which* kind of empty |

New files in `data/repository/`: `AlertError.kt`, `AlertParser.kt`,
`FirebaseAlertRepository.kt`.

---

## 2. The read path, end to end

```
devices/{deviceId}/history
        │  orderByKey().limitToLast(100)
        ▼
FirebaseAlertRepository          ValueEventListener in a callbackFlow
        │                        onCancelled → Result.failure(AlertError)
        ▼
AlertParser.parseHistory(...)    Map → Alert, skip + count malformed,
        │                        sort newest first
        ▼
AlertHistory(alerts, rawCount, malformedCount)
        │
        ▼
AlertViewModel                   D1 drop TRACKING_UPDATE
        │                        D3 take(50)
        │                        D2 latest = first survivor
        ▼
AlertUiState                     isLoading / latest / history
        │                        errorMessage / emptyMessage
        ▼
AlertFragment                    latest card + RecyclerView
```

Nothing on this path logs. An alert carries the wearer's coordinates.

---

## 3. Decisions and why

**D1 — `TRACKING_UPDATE` is hidden; unknown types are not.**
`PANIC`, `CHECKIN` and `LOW_BATTERY` are shown and colour-coded. Tracking
updates are the band's background chatter (FR-09 sends one a minute for half an
hour) and would bury the incidents. A type the app doesn't recognise is
*shown*, as "Unknown alert" — so a type a later firmware adds appears in the
list instead of silently vanishing from it.

**D2 — the latest card comes from history, not from `latest`.**
`devices/{id}/latest` is overwritten by *every* payload, tracking updates
included. Reading it would mean the card said "Location update" while a panic
sat one row below it in the same screen. The card is instead the newest
surviving entry of the filtered history. `observeLatestAlert()` still exists
and is correct — the Track tab wants exactly that node — but the Alert tab
never subscribes to it, which also saves a second live listener.

**D3 — read 100 raw, show at most 50.**
The window is over **raw** entries, before filtering. A chatty band therefore
yields fewer than 50 displayable alerts, and in the worst case none. See
[§11](#11-known-limitations).

**D4 — one device id.**
`DeviceConstants.DEFAULT_DEVICE_ID = "guardband-001"`, supplied through
`RepositoryProvider`. Pairing doesn't exist yet. ViewModels never see a device
id, so adding pairing later is a change to the wiring, not to the screens.

**D5 — `schemaVersion` is read as a String.**
`"1.0"` and a bare `1` both parse. It is read with `toString()`, so a firmware
that writes it unquoted doesn't drop the alert.

---

## 4. What the reader actually requires

`SCHEMA.md` has two lists that don't agree, and the reader has to pick a
behaviour. **Reported, not silently resolved** — flag it if you disagree:

- the **field table** marks `location.lat`, `location.lng` and
  `location.accuracyMeters` as Required = yes;
- the **receiver validation** list of required top-level fields omits
  `location` entirely (`schemaVersion`, `deviceId`, `type`, `timestamp`,
  `battery`, `sequenceId`).

The reader is deliberately **more forgiving than either**:

| Field | Reader | Why |
|---|---|---|
| `sequenceId` | **required** | the row cannot be keyed, ordered or diffed without it |
| `type` | **required**, non-blank | nothing meaningful to show |
| `timestamp` | **required**, non-blank | nothing meaningful to show |
| `schemaVersion` | salvaged → `""` | |
| `deviceId` | salvaged → `""` | |
| `battery` | salvaged → `null` | the row hides the battery line |
| `location` | salvaged → `null` | the row hides the coordinates line |

A missing coordinate drops the whole `location`, rather than reporting half a
position.

The reasoning: this is a **reader**, and the writer's rules are the place to be
strict. Refusing to show a panic alert because its `battery` node was missing
would be the wrong failure to pick.

---

## 5. Parsing — `AlertParser`

```kotlin
internal object AlertParser {
    fun parse(raw: Any?): Alert?                        // one node
    fun parseHistory(rawEntries: List<Any?>): AlertHistory
}
```

**It takes a plain `Map`, not a `DataSnapshot`.** That is the whole reason the
parsing logic is testable: a `DataSnapshot` can't be constructed in a JVM test
and the project declares **no mocking library**. The repository passes
`snapshot.value`, which the SDK has already turned into maps, numbers, strings
and booleans. 28 of the project's tests run against this object directly.

**A malformed history entry is skipped and counted, never fatal.** One bad row
from a firmware bug must not hide every good row behind it. `AlertHistory`
carries the counts:

```kotlin
data class AlertHistory(
    val alerts: List<Alert>,   // parsed, newest first, unfiltered
    val rawCount: Int,         // children read, before parsing or filtering
    val malformedCount: Int    // entries the parser rejected
)
```

Only `latest` can fail outright, as `AlertError.ParseFailure` — there is only
one node, so there is nothing to salvage.

> **The `isCharging` trap, avoided.** §10 of the bottom-nav doc warned that
> Firebase's object mapper turns Kotlin's `isCharging` getter into the key
> `charging`, so `getValue(Alert::class.java)` would always read charging as
> `false`. The hand-written parser reads the literal wire key `"isCharging"`,
> so the mapper is never involved and the trap doesn't apply. **Don't
> "simplify" this into `getValue(Alert::class.java)`** — that reintroduces the
> bug, and silently.

---

## 6. `FirebaseAlertRepository`

```kotlin
interface AlertRepository {
    fun observeLatestAlert(): Flow<Result<Alert?>>
    fun observeAlertHistory(limit: Int = DEFAULT_HISTORY_WINDOW): Flow<Result<AlertHistory>>

    companion object { const val DEFAULT_HISTORY_WINDOW = 100 }
}
```

### Ordering — `orderByKey()`, and it needs no index

**This corrects §10 of the bottom-nav doc, which said "RTDB keys are strings,
so sort by the `sequenceId` field, not by key."** That is wrong for the
*query*: history children are keyed by `sequenceId`, and RTDB sorts keys that
parse as 32-bit integers **numerically, ahead of all string keys**. So
`orderByKey().limitToLast(100)` really does return the newest 100.

It is also the cheaper option. `orderByChild("sequenceId")` would work but
needs `".indexOn": "sequenceId"` deployed under `history`; `orderByKey` needs
no index at all. **No rules change is required for this feature.**

The window is still re-sorted by `sequenceId` in memory, which is the half of
the old advice that was right. That way a key which *isn't* integer-like — a
push id, a zero-padded number — degrades the window (you may get the wrong 100)
rather than the order within it (the 100 you get are still newest-first).

Two things would break the window, both firmware-contract issues, not app bugs:
a `sequenceId` above 2³¹−1, and any key that isn't a plain integer.

### Listeners

Each method wraps a `ValueEventListener` in a `callbackFlow`. `awaitClose`
removes the listener when the collector cancels. **The flow stays open after a
failure** — the listener is still attached, so a reconnect or a rules fix makes
the next emission succeed on its own.

### Errors

`onCancelled` emits `Result.failure(AlertError)`, mapped from
`DatabaseError.getCode()` by `FirebaseDatabaseErrorMapper.mapToAlertError`.
**Never mapped from the message:** those are localised prose and change between
SDK versions. `AlertError` carries no message of its own and suppresses its
stack trace, so Firebase text cannot reach a log or a toast.

| `DatabaseError` code | `AlertError` |
|---|---|
| `PERMISSION_DENIED` | `PermissionDenied` |
| `NETWORK_ERROR`, `DISCONNECTED`, `UNAVAILABLE` | `Network` |
| `EXPIRED_TOKEN`, `INVALID_TOKEN` | `NotSignedIn` |
| anything else | `Unknown(code)` |
| *(not from a code)* | `ParseFailure` |

---

## 7. `AlertViewModel`

Applies D1 then D3 then D2, in that order:

```kotlin
val displayable = history.alerts
    .filter { it.alertType() != AlertType.TRACKING_UPDATE }   // D1
    .take(MAX_HISTORY)                                        // D3, 50

latest = displayable.firstOrNull()                            // D2
```

The cap counts **displayable** alerts, not raw ones, so 60 raw entries of which
half are tracking yield 30 rows and the cap never bites.

### Error vs. empty vs. stale

Three different situations, deliberately not collapsed into one:

| Situation | What the user gets |
|---|---|
| Read failed, **nothing** on screen | `errorMessage` → error view + **Retry** |
| Read failed, **content** on screen | list kept, `AlertEvent.ShowMessage` toast |
| Read succeeded, nothing to show | `emptyMessage`, saying which kind of empty |

Keeping a stale list beats blanking the screen: old incidents are more use in
an emergency than nothing, as long as the user is told the list is stale.

The three empty messages exist because they read identically as an empty list
but mean completely different things to someone wondering whether their band is
working:

| Condition | `MSG_*` |
|---|---|
| `rawCount == 0` | `EMPTY_NO_ALERTS` — the band has sent nothing |
| `malformedCount == rawCount` | `EMPTY_UNREADABLE` — nothing in the window parsed |
| otherwise | `EMPTY_ONLY_TRACKING` — only location updates recently |

That last one is why `AlertHistory` carries counts at all. Without them a
perfectly healthy, chatty band reads as a silent one.

### Retry

```kotlin
fun onRetryClicked() {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    observeHistory()          // cancels observeJob first
}
```

Cancelling the job cancels the `callbackFlow`, whose `awaitClose` removes the
RTDB listener. So retry **replaces** the listener rather than stacking a second
one on it. If you change this, keep the cancel.

### Wording

User-facing strings are `MSG_*` constants on the ViewModel, not in
`strings.xml` — the project convention, because a ViewModel holds no `Context`.
`strings.xml` holds only what the layout references.

---

## 8. The Alert tab

View ids (`fragment_alert.xml`):

| Id | What |
|---|---|
| `alert_progress` | spinner, first load and retry |
| `alert_error_view` | container, gone unless `errorMessage != null` |
| `alert_error_message` | the error text |
| `alert_retry_button` | → `onRetryClicked()` |
| `alert_latest_card` | current status card (D2) |
| `alert_latest_dot` | type colour |
| `alert_latest_type` / `_time` / `_location` / `_battery` | card fields |
| `alert_empty_view` | the "which kind of empty" text |
| `alert_history_list` | `RecyclerView`, `ListAdapter` + `DiffUtil` on `sequenceId` |

**The latest card is its own block, not an `<include>`.** It used to include
`item_alert.xml` and bind it with a hand-built
`AlertHistoryAdapter.AlertViewHolder` — a ViewHolder constructed outside any
RecyclerView. An `<include>` can't rename its children, so the card and a
history row both answered to `tv_item_alert_*`. `item_alert.xml` is history
rows only now.

Type colours and timestamp formatting are unchanged — see
[§5 of the bottom-nav doc](home-host-and-bottom-nav.md#5-screens).
`AlertFormatting` still parses ISO 8601 with **and** without milliseconds,
because `SCHEMA.md`'s example omits them and the mock sender writes them.

---

## 9. Testing

**59 tests added; 224 pass, 0 fail** (`./gradlew assembleDebug testDebugUnitTest`).

| File | Tests | Covers |
|---|---|---|
| `AlertParserTest` | 20 | every field, D5, unknown types, each malformed case, the salvaged branches |
| `AlertHistoryParsingTest` | 8 | ordering, out-of-order input, skip-and-count |
| `InMemoryAlertRepositoryTest` | 7 | the double stays interchangeable: ordering, window |
| `AlertViewModelTest` | 19 | D1, D2, D3, the three empties, each error's wording, retry |
| `FirebaseDatabaseErrorMapperTest` | +5 | the `AlertError` code table |

**Not covered, and why.** `FirebaseAlertRepository` itself — the
`callbackFlow`, the live listener, and **`awaitClose` actually removing it**.
That needs a real `FirebaseDatabase`, and the project declares no mocking
library. Rather than add one, everything testable was lifted out of the
Firebase class: parsing and ordering into `AlertParser`, the error table into
`fromCodeToAlert`, retry semantics into the ViewModel (which asserts a second
*subscription*, not a stacked listener).

**Listener cleanup is verified by reading the code only.** Worth one manual
check: open the Alert tab, background the app, confirm RTDB traffic stops.

`FakeAlertRepository` is backed by a replaying `MutableSharedFlow`, not a
`StateFlow` — a StateFlow drops an emission equal to the previous one, which
makes "same history, but this time the read failed" invisible to the collector.
That cost one debugging round; don't convert it back.

---

## 10. For Jedd — rules, indexes, the mock sender

**No rules change is needed for this feature.** `orderByKey` needs no
`.indexOn`. If anyone switches the query to `orderByChild("sequenceId")`, it
then needs:

```json
"history": { ".indexOn": "sequenceId" }
```

**Two things in the draft worth your attention** (`RTDBS-RULES.md` is a draft;
the deployed rules were not visible from here, so none of this was verified
against what's live):

1. `devices/$deviceId/history` and `/latest` are `".read": true` — **world-readable
   wearer coordinates.** Worth closing before any public demo.
2. `users/$uid/emergency_contacts/$contactId/phone` has **no `.validate` at
   all**. The app now requires a `+63` number to be a PH mobile (`+639` + 9
   digits); the rules don't. A matching constraint is roughly
   `newData.isString() && newData.val().matches(/^\+639[0-9]{9}$/)` for the PH
   case, but RTDB rules can't cleanly express "not `+63` unless mobile" in one
   alternation, so the honest version is two rules or accepting that the
   generic E.164 branch stays looser than the app.

**The mock sender writes nothing at all.** `INGEST_ALERT_URL` in
`AlertSender.kt` is still the literal `"REPLACE_ME_ONCE_DEPLOYED"`, and it
`POST`s to a Cloud Function — which the Spark plan cannot host, and which
doesn't match the RTDB-REST design. Every send fails, and since `sequenceId++`
only runs on success it is pinned at `1`.

**Until that is fixed, the Console is the only way to inject a test alert.**
The fix is one constant and one verb — `PUT` to:

```
/devices/guardband-001/latest.json
/devices/guardband-001/history/<sequenceId>.json
```

The payload itself is already correct: all six required children, `type` one of
the four enum names, `percent` 0–100, `isCharging` boolean, `sequenceId`
numeric. Its timestamps carry milliseconds where `SCHEMA.md`'s example doesn't;
both are valid ISO 8601 and the app parses both.

> `ui/mocksender/AlertType` is a **second, private copy** of the enum, kept
> deliberately — the harness is throwaway debug-only code and de-duplicating it
> means editing `app/src/debug/`. It has no `fromWire`, so the two can drift.
> `data/model/AlertType` is the one the app uses.

---

## 11. Known limitations

- **A tracking-heavy band can empty the tab.** D3 reads 100 *raw* entries. At
  FR-09's one-per-minute, 100 entries is about 100 minutes, which may hold zero
  incidents — and then both the list and the card are empty. The empty state
  says so rather than implying the band is silent, but it is a workaround, not
  a fix. The real fix is server-side exclusion, which RTDB can't do without
  either `.indexOn` on `type` plus one query per type, or a separate
  `/devices/{id}/incidents` path written by the firmware. **Firmware decision,
  worth making before December.**
- **No pagination.** 50 rows is the end of the list; there is no "load older".
- **`latest` is unread by this tab** (by design, D2) — Track picks it up in the
  next pass.
- **RTDB disk persistence is off,** so there is no offline cache. A cold start
  with no connection shows the error state, not stale alerts.
- **PH landlines are now rejected** as emergency contacts, a side effect of
  tightening `+63` to mobile-only. An emergency contact has to be reachable by
  SMS, so this is intended — but it is a behaviour change.
- The seeded `InMemoryStore` alerts still exist and are reachable only from
  tests.

---

## 12. Manual test checklist

Needs a real device and the Console (the mock sender can't write — §10).

**Happy path**
- [ ] Console → add `devices/guardband-001/history/5` with a full `SCHEMA.md`
      payload, `type: "PANIC"`. The card and a new top row appear within a
      second or so, without reopening the tab.
- [ ] The card shows type, local time, coordinates and battery; the dot is red.
- [ ] Add `6` with `type: "CHECKIN"`. The card becomes the check-in; the panic
      stays in history.

**D1 and D2**
- [ ] Add `7` with `type: "TRACKING_UPDATE"`. **Nothing visible changes** — not
      in the card, not in the list.
- [ ] Confirm `devices/guardband-001/latest` now holds the tracking update
      while the card still shows the check-in. That's D2 doing its job.
- [ ] Add `8` with `type: "FALL_DETECTED"`. It **does** appear, as "Unknown
      alert" in grey.

**Empty states**
- [ ] Delete all of `history`. → "No alerts from your band yet."
- [ ] Add only tracking updates. → the "only location updates" wording.
- [ ] Add a row missing `sequenceId`. → it is skipped; the others still show.

**Errors and retry**
- [ ] Airplane mode, then open the tab cold. → error view + **Retry**.
- [ ] Restore the connection, tap **Retry**. → the list loads.
- [ ] With the list already on screen, turn the connection off. → the list
      **stays** and a toast appears, no error view.

**Listeners**
- [ ] Open the tab, background the app, confirm RTDB traffic stops
      (§9 — this is the untested path).
