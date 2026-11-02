# Device pairing

How a band gets linked to the people who wear and watch it, and how
`devices/` stops being writable by anyone. **Step 1 (the app groundwork) is
built; everything from step 2 on is a proposal.** Each open point says who
decides it.

| | |
|---|---|
| **State** | 2026-11-02: step 1 built (§8). No rules, firmware, mock sender or Privacy Notice change yet: every account still wears `guardband-001`. |
| **Code** | `data/model/PairedBand.kt`, `data/repository/BandRepository.kt` (+ `FixedBandRepository`), `BandSelection.kt`, `WatchedBandAlertRepository.kt`, `RepositoryProvider` |
| **Tests** | `BandSelectionTest`, `WatchedBandAlertRepositoryTest`, `DeviceViewModelTest` (pairing cases) |
| **Related** | [database-rules.md](database-rules.md) §4 (the `devices/` options), [team-checklist.md](team-checklist.md) (SMS), [device-tab.md](device-tab.md) §5, [`../SCHEMA.md`](../SCHEMA.md), [`../README.md`](../README.md#band-firmware-prototype) (the prototype firmware) |

---

## 1. What it is for

**The vision (James, 2026-11-02).**

- Every user can be a **wearer and a guardian at once**: they wear their own
  band, and watch friends' bands.
- **Many-to-many.** One wearer can have many watchers, and one watcher can
  watch many wearers.
- **Track gets a selector:** *My band*, one friend's band, or *All*, with
  every band the account can see on the map at once.

**The goal of pairing itself: only a band can write its own `devices/`
node.** Today anyone with the database URL (it ships in every APK) can write
fake alerts, or, under the deployed rules, erase a panic. Closing reads, so
that only a band's wearer and watchers can see it, comes after.

**Constraints** (handoff §2): the Spark plan, so no server, Cloud Function or
Admin SDK; the main app never writes the band's alerts; no new Android
permission; the Privacy Notice changes in the same commit as any new data
flow; nothing logs a uid or a coordinate.

**Roles are per band, not per account.** There is no stored "guardian" or
"wearer" on `users/{uid}` (rolled back 2026-11-02). Whether someone wears or
watches is a property of their link to one band (`BandRelation`).

---

## 2. Band identity: anonymous auth (decided)

Each band is a **Firebase anonymous user**, and **its uid is its device id**.
Nobody creates accounts by hand, and no server is involved.

1. **First boot.** The band calls the Auth REST API's sign-up with no
   credentials:
   `POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=<web API key>`
   with `{"returnSecureToken": true}`. The answer carries `localId` (the uid,
   which becomes the device id), an `idToken` (valid one hour) and a
   `refreshToken`.
2. **It keeps the identity.** `localId` and `refreshToken` go into the
   ESP32's NVS (the `Preferences` library), so a reboot is the same band. A
   wiped NVS is a **new** band that has to be paired again.
3. **It renews the token** before it expires:
   `POST https://securetoken.googleapis.com/v1/token?key=<web API key>` with
   `grant_type=refresh_token`.
4. **Every write carries it:** `PUT …/devices/<localId>/history/<n>.json?auth=<idToken>`.
5. **The certificate check comes back in the same pass.** The prototype calls
   `setInsecure()`; a band holding a long-lived refresh token must verify the
   server.

**The mock sender does the same** over OkHttp, keeping its identity in its
own SharedPreferences, so a debug phone is one more band.

**Human steps:** enable the **Anonymous** provider (Console → Authentication
→ Sign-in method). The web API key is already public (it is in
`google-services.json`); it identifies the project and protects nothing.

**What anonymous auth lets anyone do:** create an anonymous user and write
its **own** `devices/<their uid>`, which is a fake band nobody watches. It can
never write another band's node. It also gives that user a `users/<uid>`
record it could write, which any email sign-up can already do.

**Open (firmware teammate):** where the web API key lives in the sketch
(`secrets.h`, beside the Wi-Fi password), and what the band does while it has
no valid token (queue and retry, as it does for a dropped connection).

---

## 3. Claiming a band as its wearer (decided: the code goes over Serial)

The prototype has no screen and the real firmware does not exist, so the
pairing code is printed on the **Serial Monitor**.

1. The band makes a **one-time code** (say 6 digits, random) and writes it
   where only it can write and nobody can read:
   `device_secrets/<deviceId>/claim_code`.
2. A new Serial command, **`PAIR`** (beside the sketch's `STATUS`), prints
   the device id and the code. The mock sender shows its own on screen.
3. In the app, **Device tab → Pair a band**: the wearer types both.
4. The app writes the claim:
   `device_members/<deviceId>/<uid>: { relation: "wearer", code: "<code>" }`,
   which a rule accepts only if the code matches `device_secrets` and the band
   has no wearer yet. One write also adds `users/<uid>/bands/<deviceId>: "wearer"`.
5. The band sees itself claimed and **rotates the code**, so the one shown
   can never be used again.

**One wearer per band.** Re-pairing a band to someone else means the wearer
unpairs first (or the band is reset, which makes it a new band).

**Open (James):** code length and expiry; whether the code stays in the
membership row (rules can only compare what is written, and RTDB rules cannot
hash) or a separate claim node; how "the band sees itself claimed" works
without the band reading `device_members`.

---

## 4. Watchers: many-to-many

```
device_members/{deviceId}/{uid}  →  "wearer" | "watcher"     who may see a band
users/{uid}/bands/{deviceId}     →  "wearer" | "watcher"     what an account lists
```

The second node exists because RTDB cannot query `device_members` across
bands by uid; it is the account's own index, owner-only like the rest of
`users/{uid}`. The two must be written together (one multi-path update).

- A band has **one wearer** and **any number of watchers**.
- An account **wears at most one band** in the app today (the Device tab
  shows one) and **watches any number**.
- **The wearer invites.** A watcher sees the wearer's location, so nobody may
  watch without the wearer's say. The wearer creates an invite code in the
  app; the friend redeems it, and a rule accepts the `watcher` row only
  against a live invite.
- **The wearer can remove a watcher**, and a watcher can leave.

**Open (James):** invite expiry and single use; whether a watcher sees the
wearer's **name and photo** (new data shared with another account: notice
first; until then the face marker shows initials or a generic face); whether
an account may wear more than one band.

---

## 5. Track's selector (later step, sketched)

A chip row or menu on Track, fed by `BandRepository.observeBands()`:
**My band**, each watched band, **All**.

- One band: today's map, marker and sheet, for that band.
- **All:** one face per band, each read through
  `RepositoryProvider.alertRepositoryFor(id)`; tapping a face picks it.
- The choice replaces `BandSelection.watchedBandOf`'s fixed rule; where it is
  remembered (per phone like the map layer, or per account) is open.

**Open (James, Jul for the design):** what the alert sheet, Alert history,
the Incident view and alert notifications follow while *All* is selected;
whether notifications cover every watched band or only some.

---

## 6. Rules sketch

Proposal only; [database-rules.md](database-rules.md) §4 stays the decision
record for `devices/`, and the rollout is its §7.

```
devices/$deviceId
  .write   auth != null && auth.uid == $deviceId          ← closes the world writes
           (+ $deviceId == 'guardband-001' during the transition)
  .read    the wearer or a watcher in device_members      ← step 6
device_secrets/$deviceId
  .read    false
  .write   auth.uid == $deviceId
device_members/$deviceId/$uid
  .read    auth.uid == $uid, or the band's wearer
  .write   claim: auth.uid == $uid, code matches, no wearer yet
           invite: auth.uid == $uid, a live invite
           removal: the wearer, or $uid leaving
users/$uid/bands/$deviceId
  (owner-only, as users/$uid)
```

**`guardband-001` stays open until step 6**, so the demo and the prototype
keep working while new bands are closed from the start. History stays
append-only (Option A+) for every band.

---

## 7. What changes with it

| | Change | When |
|---|---|---|
| **Privacy Notice + `CONSENT_VERSION`** | Band records become linked to an account, and friends see a wearer's location. The "one shared band identifier" card and the delete-account page's "not linked to you" paragraph both stop being true. | Step 3 |
| **Delete account** | Remove every membership and the `users/{uid}/bands` index; a band the account wore loses its wearer (its history is the band's, see the open point below). CLAUDE.md's "`devices/` is never touched" changes. | Step 3 |
| **Face marker** | `TrackViewModel.markerFace()` shows the band's wearer, not the viewer. | Step 5 |
| **SMS (FR-05)** | Only the wearer's phone sends, for its own band ([team-checklist.md](team-checklist.md)). | Step 7 |
| **Alert notifications** | The high-water mark is already keyed by device id; the watch follows the selected band today, every watched band later. | Steps 1 and 5 |

**Open (James):** what happens to a band's history when its wearer deletes
their account.

---

## 8. Steps

| # | Step | Touches |
|---|---|---|
| **1** | **Groundwork (built 2026-11-02).** `BandRepository` lists the account's bands (`FixedBandRepository`: everyone wears `guardband-001`); `BandSelection` picks the shown band; `WatchedBandAlertRepository` follows it, so one process can serve several accounts; `AlertError.NoBand`; the alert watch and the Device tab read the band from it. No visible change. | app only |
| 2 | **Band identity.** Anonymous auth in the prototype firmware and the mock sender; writes with `?auth=`; rules close writes on every band id except `guardband-001`. | firmware, mock sender, rules |
| 3 | **Claim over Serial.** `PAIR`, `device_secrets`, `device_members`, `users/{uid}/bands`; `FirebaseBandRepository` replaces `FixedBandRepository`; Pair a band works. Notice and `CONSENT_VERSION`. | app, firmware, rules, notice |
| 4 | **Watcher invites.** Invite, redeem, remove, leave. | app, rules, notice |
| 5 | **Track's selector and All.** | app |
| 6 | **Close reads, retire `guardband-001`.** | rules, notice |
| 7 | **SMS** (FR-05), on the conditions in [team-checklist.md](team-checklist.md). | app, notice, `SEND_SMS` approval |

### Step 1 in detail

- **`BandRepository.observeBands(): Flow<List<PairedBand>>`.** Every reader
  of a band gets it here. `FixedBandRepository` answers at once, with no
  listener, so nothing on screen changed.
- **`BandSelection`** is the one rule for which band is shown:
  `watchedBandOf` (the worn band, else the first watched, else none) for the
  alert screens and the alert watch; `ownBandOf` (the worn band only) for
  the Device tab.
- **`WatchedBandAlertRepository`** is `RepositoryProvider.alertRepository`.
  It never captures a band id: each read follows `watchedBandOf`, and a change
  of band cancels the old band's flow, which removes its listener. No band
  gives `AlertError.NoBand` (worded `MSG_NO_BAND` on Track, Alert history,
  the inbox and the Incident view). ViewModels did not change how they read.
- **`RepositoryProvider.alertRepositoryFor(id)`** reads one named band: the
  alert watch uses it so its high-water mark and its reads can never refer to
  different bands, and *All* will.
- **The Device tab** shows the account's own band: `DevicePairing.Loading`
  until the bands are known, `Unpaired` when it wears none (even if it
  watches friends'), `Paired` otherwise. A different band starts from
  nothing.
- **Still reading `DeviceConstants.DEFAULT_DEVICE_ID`:** `RepositoryProvider`
  (the fixed band), the mock sender and the firmware (they *are* the band),
  and `InMemoryStore`'s test seed.
