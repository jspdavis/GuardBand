# Design: Roles, Panic SMS & Alert Timeline

This design implements the requirements in `requirements.md`. Every change is
grounded in the current architecture; file paths and method names are the real
ones. Two sub-decisions you did not rule on are pinned below and flagged
(**🔍 REVIEW**) so the review can confirm or redirect them.

## 1. Key decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **Roles-first, shared band stays.** Store `role`; fork the dashboard; do NOT add `device_members` pairing (Option C). | You confirmed role storage + UI fork only. Pairing is a separate, owner-gated rules change (`docs/database-rules.md` §4). |
| D2 | **The DEPENDENT's phone sends the SMS; the GUARDIAN receives it.** (confirmed) | The guardian is the recipient, so the sender cannot be the guardian (it would text itself). The dependent is associated with the band. Trade-off: depends on the dependent's watch running (REQ-SMS-09). |
| D3 | **Resolution status stored at `devices/{id}/status/{seq}`** (confirmed) — persists across reloads. | The band's `history/{seq}` is append-only and band-written; status needs a mutable, app-written node. It is the real-time node, so it survives reloads. Without pairing, rules can only enforce `auth != null`; guardian-only is app-gated until pairing. |
| D4 | **`SEND_SMS` added to the release manifest** with a runtime request, and the Privacy Notice updated in the same change. | You chose SMS-only; this reverses the documented "SMS stays on the band" stance and the notice must change with it (REQ-SMS-08). |
| D5 | **Own phone captured on the Account step** so it is available before the Contacts step comparison. | The wizard is Account → Name → Contacts → Consent; the comparison at Contacts needs own-phone earlier. |
| D6 | **Panic-test/simulation is debug-only; SMS never fires for a simulated panic.** (confirmed) | The Dependent dashboard's panic-test trigger is `BuildConfig.DEBUG`-gated and tags its PANIC payload `simulated: true`. The SMS path skips any `simulated` panic, so simulation (in any build) exercises the map/alert/notification path but can never send a real emergency SMS. Real band panics send SMS, subject to the runtime SEND_SMS grant. |

## 2. Data model & schema changes

### 2.1 New / changed model classes

```
data/model/Role.kt                 NEW   enum Role { GUARDIAN, DEPENDENT }; fromWire/toWire like SignInProvider
data/model/User.kt                 EDIT  + val role: Role? = null   (display view; DB is source of truth)
data/model/AlertStatus.kt          NEW   enum AlertStatus { ACTIVE, ACKNOWLEDGED, RESOLVED }; fromWire, default ACTIVE
data/model/Alert.kt                EDIT  + val status: AlertStatus = AlertStatus.ACTIVE
                                        + val simulated: Boolean = false   (debug panic-test only; band never sets it)
data/model/EmergencyContact.kt     unchanged (phone already E.164)
```

### 2.2 New database paths

| Path | Shape | Written by | Read by |
|------|-------|------------|---------|
| `users/{uid}/role` | `"GUARDIAN"` \| `"DEPENDENT"` | app, at `finalizeSignUp` | gate, SMS role-gate, dashboards |
| `users/{uid}/phone` | `"+639XXXXXXXXX"` (E.164) | app, at `finalizeSignUp` | contact self-validation |
| `devices/{id}/sessions/{panicSeq}` | `{ sessionId, panicSeq, startedAt: ServerValue.TIMESTAMP, active: true }` | app (dependent), on panic | tracking-link resolver (web, out of scope) |
| `devices/{id}/status/{seq}` | `{ status, updatedAt, updatedBy }` | app (guardian), on Acknowledge/Resolve | history rows |

### 2.3 Atomic write (finalizeSignUp)

`FirebaseUserProfileRepository.finalizeSignUp` gains a `role: Role` and an
`ownPhone: String` parameter and adds two absolute paths to the existing
`updateChildren` map (keeping it one commit):

```
"$NODE_USERS/$uid/$FIELD_ROLE"  to role.name
"$NODE_USERS/$uid/$FIELD_PHONE" to ownPhone   // already E.164-normalized
```

`fetchProfile` reads `role` (→ `Role.fromWire`) and `phone`. `saveProfile`
(the thin `ensureProfile` path) is unchanged — it intentionally writes **no**
role/phone, so the gate can detect "profile present but not finished" (§3.3).

## 3. Component design

### 3.1 ROLES — registration & dashboards

**Role capture in the wizard.** Add a `SignUpRoleActivity` as the new first
step (before `SignUpAccountActivity`), or render role selection at the top of
the Account step (D5 places own-phone on the Account step regardless). It
shows two clear choices with the mutual-alignment guidance (REQ-ROLE-07) and
passes the chosen `Role` forward as an Intent extra through Name → Contacts →
Consent. `SignUpConsentViewModel.onCreateAccountClicked` / `finalizeNow`
threads `role` into `finalizeSignUp`. The Google complete-profile path
(`EXTRA_COMPLETE_PROFILE`) shows the same role selection at the start of its
flow, before Contacts, so a first-time Google user also picks a role.

**Gate role resolution.** `HomeViewModel` already runs the gate
(`HomeGateState` + `RequireConsent` / `RequireEmailVerification`). Add: after
the profile is present and consent is accepted, read `role`. If `role` is null,
emit a new `HomeEvent.RequireCompleteProfile` that routes to the sign-up wizard
in complete-profile mode (mirrors the existing consent route). If `role` is
set, the gate opens and exposes `role` on a `HomeUiState.role` field.

**Dashboard fork.** `HomeActivity.createFragment(tag)` and the bottom nav
become role-aware. Confirmed tab sets:

- **Guardian** (`menu_home_bottom_nav_guardian.xml`): **Track** (live map +
  recent alerts, the existing `TrackFragment` enhanced), **Dependent**
  (dependent status, battery, alert timeline), **Contacts** (their own
  emergency contacts). Alert history reached from Track's "See all" as today.
- **Dependent** (`menu_home_bottom_nav_dependent.xml`): **Device** (their band
  status, connection state, panic-test trigger), **Guardian** (their assigned
  guardian contact details), **Contacts** (their emergency contacts incl. the
  guardian).

Implementation: `HomeDestinations` gains role-awareness (a `tabsFor(role)`);
`HomeActivity` selects the nav menu and `createFragment` mapping by
`HomeViewModel.uiState.role`. New fragments: `DependentStatusFragment` (for
the Guardian's "Dependent" tab) reuses `DeviceFragment`'s band-status rendering;
the Dependent's panic-test trigger is a **debug-only** control on the Device tab
(see D6): it reuses the mock sender's `AlertSender.sendAlert(PANIC)` send path
but tags the payload `simulated: true`, so it exercises the map/alert/notification
flow without ever sending an SMS.

**Markers & identity.** `TrackViewModel.markerFace()` already anticipates this
("once roles or pairing exist, the wearer's photo and name belong here"). For a
guardian, the band marker shows the **dependent's** face/name; in this phase the
shared band has no account owner, so the guardian's Track keeps the signed-in
user's face as a placeholder — a known limitation that the future pairing change
(Option C) resolves, not a blocker.

### 3.2 PHONE — own phone & self-validation

**Capture.** `SignUpAccountActivity` gains an own-phone field, validated with
`InputValidator.normalizePhoneToE164` (existing). The normalized value flows
forward to `finalizeSignUp` (§2.3). For the Google complete-profile path,
own-phone is captured alongside role before Contacts.

**Self-validation.** `ContactFields.validated` gains an `ownPhone: String?`
parameter:

```kotlin
fun validated(contact: EmergencyContact, ownPhone: String? = null): EmergencyContact {
    // ...existing name/relationship/phone normalization...
    if (ownPhone != null && phone == ownPhone) throw ContactError.SelfPhone
    return contact.copy(...)
}
```

- New `ContactError.SelfPhone`.
- New string `error_contact_phone_is_self` = "Guardian phone number cannot be
  the same as the dependent's phone number." (REQ-PHONE-05)
- Callers pass own-phone: `SignUpContactsViewModel.onEditorSubmitted` (from
  the wizard's held own-phone) and `ContactsFragment`'s add/edit (reads the
  signed-in user's `users/{uid}/phone` via the profile). Both already route
  through `ContactFields.validated`, so one change covers sign-up + later edits
  (REQ-PHONE-06). The error maps to the string in the respective ViewModel's
  `messageFor`, the way `ContactError.Validation` already does.

### 3.3 SMS — panic sender & live session

**New component `SmsPanicSender`** (`ui/notifications/SmsPanicSender.kt`):

```kotlin
class SmsPanicSender(private val context: Context) {
    fun sendPanicSms(toPhone: String, lat: Double, lng: Double, sessionId: String): Result<Unit> =
        runCatching {
            val body = context.getString(R.string.sms_panic_body,
                "https://guardband.app/track/$sessionId", lat, lng)
            SmsManager.getDefault().sendTextMessage(toPhone, null, body, null, null)
        }
}
```

`SmsManager` is available at minSdk 24 (build is minSdk 24 / targetSdk 34).

**Role-gated hook in `AlertWatchService`.** The watch loop already dispatches
each parsed alert at `plan.toPost.forEach(notifier::post)`. Add, inside that
forEach, a panic branch:

```
if (alert.alertType() == AlertType.PANIC && !alert.simulated) {   // D6: never SMS a simulation
    val role = cachedRole()                       // from profile, see below
    if (role == Role.DEPENDENT) sendPanicSms(alert)
}
```

The `!alert.simulated` guard is the **release-build safety check** (D6): a
simulated panic (from the debug panic-test trigger, or anything tagged
`simulated: true`) updates the map/history/notification path through the normal
`notifier::post` but is never handed to `sendPanicSms`, so it can never send a
real emergency SMS in any build. `cachedRole()`: the service reads the signed-in
user's role once at start (via `RepositoryProvider.userProfileRepository.fetchProfile(uid)` → `role`) and caches it; re-read on a new watch start. The service
already has `RepositoryProvider.authRepository.isLoggedIn()`.
- `sendPanicSms(alert)`: resolve the guardian recipient phone from the
  dependent's `ContactRepository.observeContacts()` snapshot (the `isEmergency`
  contact, or the first contact whose relationship is "Guardian"); if none,
  skip + log (REQ-SMS-07). Then create the session (below) and call
  `SmsPanicSender.sendPanicSms(...)`.
- A GUARDIAN's watch reaches the same branch but `role != DEPENDENT`, so it
  posts the notification only (REQ-SMS-02) — no SMS to self.

**Live session.** On panic, write `devices/{deviceId}/sessions/{panicSeq}`:

```kotlin
database.getReference("devices/$deviceId/sessions/$panicSeq")
    .setValue(mapOf("sessionId" to sessionId, "panicSeq" to panicSeq,
                    "startedAt" to ServerValue.TIMESTAMP, "active" to true))
```

`sessionId` = a short opaque token, e.g. `Base64.urlEncode("$deviceId:$panicSeq")`
(no key in it). Continuous GPS updates need no new write: the band's
post-panic `TRACKING_UPDATE` stream already updates `devices/{id}/latest`
(the mock sender does this for 30 min after a panic; `TrackViewModel` already
reads `latest`). The tracking link resolves (web-side, out of scope) by
reading `latest` under the session's device.

**Permission flow.** Add `SEND_SMS` to `app/src/main/AndroidManifest.xml`. At
runtime, request it when a DEPENDENT enables the alert watch (the existing
`AlertNotificationsActivity` switch) — mirror the existing `POST_NOTIFICATIONS`
request pattern — with a rationale string explaining SMS leaves the phone with
coordinates. On a first-panic path where permission is still missing, request
then; if denied, skip SMS, keep the in-app notification (REQ-SMS-07).

**Privacy notice.** Update the in-app privacy-notice string(s) and
`docs/`/handoff notice in the same change (REQ-SMS-08): panic alerts send an
SMS containing the dependent's coordinates and a tracking link from the
dependent's phone. The CLAUDE.md rule that a notice change ships in the same
commit as the PII-handling change applies.

### 3.4 TL — Older heading & resolution status

**Older heading.** `AlertDays` already emits `Header(Today)` / `Header(Yesterday)`
/ `Header(On(...))`. Change: introduce `AlertDay.Older` (object) and collapse
all pre-yesterday days into it. `group()` emits `Header(Older)` once when the
day drops below Yesterday and never emits another day header after that.
`AlertFormatting.dayLabel` → `label_alert_day_older` = "Older". This is the only
history-grouping delta; Today/Yesterday logic is unchanged (REQ-TL-01).

**Resolution status field.**

- `AlertStatus` enum + `Alert.status` (default ACTIVE) (§2.1).
- `AlertParser.parse` reads `map["status"]` → `AlertStatus.fromWire(it) ?: ACTIVE`.
  The band never sends `status`, so every band-originated row is ACTIVE until a
  guardian acts (REQ-TL-03).
- `AlertParser.parse` also reads `map["simulated"]` → `Boolean` (default
  false). The band never sets it; only the debug panic-test trigger does (D6).
  The SMS path skips simulated panics (§3.3); history rendering ignores it.
- Status node read: `AlertRepository.observeStatuses()` reads
  `devices/{id}/status` and merges by `sequenceId` into the `AlertRow`s.
  `AlertViewModel.render` overlays status onto each row.
- Status write: `AlertRepository.acknowledge(seq)` / `resolve(seq)` write
  `devices/{id}/status/{seq}` = `{ status, updatedAt: ServerValue.TIMESTAMP,
  updatedBy: uid }`. The `AlertViewModel` exposes these for the **guardian**
  only (app-gated by `HomeUiState.role`); the dependent's history shows status
  read-only (REQ-TL-04).
- Row UI: `item_alert.xml` gains a status `Chip`/`TextView`;
  `AlertCardViews.bind` renders it from `AlertRow.status`. Existing type/time/
  location rendering unchanged (REQ-TL-02).

## 4. Security rules

Live rules today: `users/$uid` owner-only with **no `$other:false`**
(deliberate, for thin provisioning + `deletion_pending`); `devices/$deviceId`
world-open (one shared id, no pairing). Changes:

```jsonc
"users": {
  "$uid": {
    // existing children unchanged; ADD:
    "role": { ".validate": "newData.isString() && (newData.val() == 'GUARDIAN' || newData.val() == 'DEPENDENT')" },
    "phone": { ".validate": "newData.isString() && newData.val().matches(/^(\\+639[0-9]{9}|\\+[0-9]{8,15})$/)" }
    // NO $other:false at users/$uid (keeps deletion_pending + thin profile working)
  }
},
"devices": {
  "$deviceId": {
    // existing latest/history open; ADD:
    "sessions": { "$seq": { ".read": "auth != null", ".write": "auth != null" } },
    "status":   { "$seq": { ".read": "auth != null", ".write": "auth != null" } }
  }
}
```

- `role`/`phone` are named children, so no blanket `$other:false` is needed
  (avoids the `deletion_pending` trap documented in `docs/database-rules.md` §3).
- `sessions`/`status` inherit `devices`' open shape for now; `auth != null`
  is the strongest rule available without pairing. Guardian-only status writes
  are app-gated this phase (D3, **🔍 REVIEW**). Tightening to guardian-only
  belongs to the future Option-C pairing change.

## 5. Permissions & manifest

- **ADD** `<uses-permission android:name="android.permission.SEND_SMS" />` to
  `app/src/main/AndroidManifest.xml` (release). Runtime request via the existing
  permission-request pattern used for `POST_NOTIFICATIONS`.
- **No location permission added.** The phone is not a GPS source. Location
  stays debug-only (`app/src/debug/AndroidManifest.xml`) for the mock sender.
- `SmsManager` needs only `SEND_SMS` (no `READ_PHONE_STATE`) on minSdk 24.

## 6. Privacy & compliance

- The SMS body discloses `lat`/`lng` + a tracking link — a stronger PII
  disclosure than any current channel (notifications are `VISIBILITY_PRIVATE`
  and deliberately hide coordinates). This is the approved trade-off (D4); the
  Privacy Notice must reflect it in the same change (REQ-SMS-08).
- The dependent's own phone is new PII stored at `users/{uid}/phone`; the
  notice already discloses the profile contents — add phone to that list.

## 7. Risks & open questions

1. **D2 (SMS sender = dependent)** — SMS reliability is bounded by the
   dependent's phone being on, the watch running, and having signal. The
   band-SMS path remains more reliable and is staged future (REQ-SMS-09).
2. **D3 (status node, guardian-only)** — without pairing, rules can't enforce
   guardian-only status writes; it is app-gated this phase.
3. **Dependent identity on the guardian's map** — the shared band has no
   account owner, so the guardian's `markerFace` is a placeholder until pairing.
4. **Panic-test trigger** — resolved (D6): the Dependent dashboard's panic-test
   trigger is debug-only and tags payloads `simulated: true`; the SMS path
   skips simulated panics, so simulation can never send a real emergency SMS in
   any build.
5. **Tab layout** (§3.1) — confirmed: the per-role Guardian/Dependent tab sets
   above.

## 8. Testing strategy

- **Unit (plain JVM):**
  - `Role.fromWire`/`toWire`; `AlertStatus` default + parsing.
  - `ContactFields.validated(contact, ownPhone)` throws `SelfPhone` when equal,
    passes otherwise; `InputValidator` own-phone normalization.
  - `AlertDays.group` emits Today / Yesterday / Older correctly; no `On`
    headers remain.
  - `SmsPanicSender` body formatting (with a faked `SmsManager`/context via
    Robolectric) and the "no recipient → skip" path.
  - `AlertWatchService` panic branch: sends only when `role == DEPENDENT`;
    guardian posts notification only (use the existing `InMemoryAlertRepository`
    + a fake role provider).
- **Robolectric:** `SmsManager` send + `SEND_SMS` permission grant/deny paths.
- **Instrumented:** the atomic `finalizeSignUp` writes `role` + `phone` in one
  commit (against the Firebase emulator / `InMemory`).
- Existing tests: `MapTilerTileSourceTest`, `TrackViewModelTest`, the alert
  pipeline tests, and `SignUpContactsViewModelTest` must stay green; the
  `finalizeSignUp` / `ContactFields.validated` signature changes ripple through
  their fakes and are updated in the same change.

## 9. Rollout / migration

- Existing accounts have no `role`/`phone`. On next sign-in, the gate sees a
  present profile with `role == null` and routes to complete-profile (D5
  flow), which captures role + own-phone without re-creating the account. No
  data migration script is needed; absence is the signal.
- Existing alert history has no `status` → defaults to ACTIVE (REQ-TL-03), so
  all rows render with a chip immediately.
- The `devices/{id}/sessions` and `/status` nodes are created lazily (on first
  panic / first Acknowledge); absent nodes read as empty.

## 10. File change map (summary)

| Area | File | Change |
|------|------|--------|
| Roles | `data/model/Role.kt` | NEW |
| Roles | `data/model/User.kt` | + role |
| Roles | `ui/signup/SignUpRoleActivity.kt` (+layout) | NEW (role step) |
| Roles | `ui/signup/SignUpConsentViewModel.kt`, `FirebaseUserProfileRepository.kt`, `UserProfileRepository.kt` | thread role into finalizeSignUp; read back in fetchProfile |
| Roles | `ui/home/HomeViewModel.kt`, `HomeActivity.kt`, `HomeDestinations.kt` | role-aware gate + dashboard fork + per-role nav menus |
| Roles | `ui/.../DependentStatusFragment` | NEW (guardian's Dependent tab) |
| Phone | `utils/InputValidator.kt` | (unchanged; reused) |
| Phone | `data/repository/ContactFields.kt`, `ContactError.kt` | + ownPhone param, SelfPhone error |
| Phone | `ui/signup/SignUpAccountActivity.kt`, `SignUpContactsViewModel.kt`, `ui/contacts/ContactsFragment.kt` | capture/pass own-phone; show self-phone error |
| Phone | `res/values/strings.xml` | `error_contact_phone_is_self` |
| SMS | `ui/notifications/SmsPanicSender.kt` | NEW |
| SMS | `ui/notifications/AlertWatchService.kt` | role-gated panic SMS branch + session write |
| SMS | `app/src/main/AndroidManifest.xml` | + SEND_SMS |
| SMS | `res/values/strings.xml` | `sms_panic_body`, SEND_SMS rationale |
| SMS | privacy notice strings + `docs/` | disclose SMS PII |
| TL | `ui/alert/AlertDays.kt`, `AlertFormatting.kt` | Older heading |
| TL | `data/model/AlertStatus.kt` | NEW |
| TL | `data/model/Alert.kt`, `data/repository/AlertParser.kt` | + status |
| TL | `data/repository/AlertRepository.kt`, `FirebaseAlertRepository.kt` | observeStatuses, acknowledge, resolve |
| TL | `ui/alert/AlertViewModel.kt`, `AlertUiState.kt`, `AlertCardViews.kt`, `item_alert.xml` | status chip + guardian actions |
| Rules | `RTDBS-RULES.md` | + role/phone/sessions/status rules |
