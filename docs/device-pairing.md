# Device pairing

How a band gets linked to the people who wear and watch it, and how
`devices/` stops being writable by anyone. **Steps 1 and 2 are built.
Watchers and a Track switcher were built on 2026-11-10 in a different
shape from steps 4 and 5 below, without step 3 (§9).** Everything else is a
proposal. Each open point says who decides it.

| | |
|---|---|
| **State** | 2026-11-02: step 1 (app groundwork) and step 2 (band identity) built (§8). Its rules were published on 2026-11-04 (Jul); it still waits on Anonymous sign-in being turned on in the Console. **2026-11-10 (jspdavis, `5f0b254`): account-to-account watchers by invite code, a Track switcher, and `FirebaseBandRepository` in place of the fixed one (§9).** Since then an account reads `devices/<its own uid>` by default, not `guardband-001`, so the app shows an empty band until §9's open points are settled. No Privacy Notice change yet. |
| **Code** | App: `data/model/PairedBand.kt`, `ConnectedWearer.kt`, `data/repository/BandRepository.kt` (`FirebaseBandRepository`; `FixedBandRepository` kept, unwired), `BandSelection.kt`, `BandSelectionStore.kt`, `WatchedBandAlertRepository.kt`, `WearerConnectionRepository.kt` (+ `Firebase…`), `RepositoryProvider`, `ui/device/InviteCode*`, the switcher in `ui/track/`. Mock sender: `BandIdentity.kt`, `AlertSender.kt`. Firmware: `guardband_band.ino`, `gts_roots.h`. Rules: `RTDBS-RULES.md`'s `devices/` writes and its three watcher nodes |
| **Tests** | `BandSelectionTest`, `WatchedBandAlertRepositoryTest`, `DeviceViewModelTest` (pairing cases), `InviteCodeViewModelTest`, `BandAuthRepliesTest` (testDebug); the rules: 18 emulator cases (§6), none yet for the watcher nodes. `FirebaseBandRepository`, `FirebaseWearerConnectionRepository` and the switcher's selection logic in `TrackViewModel` have no tests |
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
5. **It checks the server's certificate.** Every HTTPS call is verified
   against Google's four GTS root certificates, embedded in `gts_roots.h`
   (valid until 2036), instead of `setInsecure()`. On 2026-11-02 the database
   chained to GTS Root R1 and the two Auth hosts to R4; all three verified
   against those four roots alone with `openssl s_client`.

### As built (step 2, 2026-11-02)

**Where it writes.** Both bands still write to **`guardband-001` by
default**, so the app and the demo are unchanged; there the sign-in is best
effort, because the rules leave that id open. A switch writes to
`devices/<band id>` instead, which needs the sign-in and which the app does
not read until a band can be claimed (step 3): `WRITE_TO_OWN_ID` in the
sketch, the "own id" switch in the mock sender. Changing the band id re-reads
that id's history keys, so each band numbers its own history.

**Firmware.** The web API key goes in the gitignored `secrets.h`
(`#define FIREBASE_API_KEY`); without it the band does not sign in and works
as before. The identity lives in NVS namespace `gb_identity` (`uid`,
`refresh`). The band signs in as soon as Wi-Fi and the clock are ready, so
its id is printed before the first alert; `STATUS` shows it; `RESETID`
forgets it and makes a new band. A failed sign-in is not retried for 60 s,
so alerts to the shared id never wait on it, and a 401 drops the ID token so
the next attempt signs in afresh. Tokens are never printed.

**Mock sender.** `BandIdentity` (REST, OkHttp) keeps the uid and refresh
token in SharedPreferences `mock_sender_band` and the ID token in memory. It
never touches the app's `FirebaseAuth` session. The screen shows the band
id, the switch and **New band id**; the status line says `signed in` or not.
The key comes from the Firebase app's own options. `BandAuthReplies` (pure,
`BandAuthRepliesTest`) reads the replies.

**Human steps:** turn on the **Anonymous** provider (Console →
Authentication → Sign-in method → Anonymous). Until then both bands say so
and send unsigned to `guardband-001`. **Do not turn on automatic clean-up of
anonymous accounts** (an Identity Platform option): it would delete a band's
identity after 30 days. The web API key identifies the project and protects
nothing; it ships in every APK.

**What anonymous auth lets anyone do:** create an anonymous user and write
its **own** `devices/<their uid>`, which is a fake band nobody watches. Any
signed-in app account can do the same under its own uid. Neither can write
another band's node, and nobody sees such a band until it is claimed with the
band's code (step 3). It also gives that user a `users/<uid>` record it could
write, which any email sign-up can already do.

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

[database-rules.md](database-rules.md) §4 stays the decision record for
`devices/`, and the rollout is its §7.

**The writes are in `RTDBS-RULES.md` since step 2 (2026-11-02), not yet
published:** `latest` and new `history` rows may be written by anyone under
`guardband-001`, and under any other id only by `auth.uid == $deviceId`.
They passed 18 cases in the local Database Emulator (`demo-guardband`, never
the real project): the shared id open but still append-only; a band writing
its own `latest` and new rows; its own rows still not rewritable or
deletable; unsigned writes, another band's, and an app account's all refused
on a band's id; payload validation unchanged; reads open; `users/`
unchanged. The rest is a sketch:

```
devices/$deviceId
  .write   auth != null && auth.uid == $deviceId          ← closes the world writes (step 2)
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
| **2** | **Band identity (built 2026-11-02; §2 "As built").** Anonymous auth in the prototype firmware and the mock sender; every write with `?auth=`; both still write to `guardband-001` unless switched to their own id; the firmware checks certificates. The rules close writes on every band id except `guardband-001` (emulator-tested; published 2026-11-04). Waits on: Anonymous sign-in in the Console. | firmware, mock sender, rules |
| 3 | **Claim over Serial.** `PAIR`, `device_secrets`, `device_members`, `users/{uid}/bands`; Pair a band works. Notice and `CONSENT_VERSION`. (`FirebaseBandRepository` is already wired, 2026-11-10, reading a `users/{uid}/bands` that nothing writes yet; §9.) | app, firmware, rules, notice |
| 4 | **Watcher invites.** Invite, redeem, remove, leave. **Partly built 2026-11-10, per account rather than per band (§9);** remove and leave have repository methods but no screen. | app, rules, notice |
| 5 | **Track's selector and All.** **Partly built 2026-11-10:** "My Band" or one connected wearer, no All (§9). | app |
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

---

## 9. As built: connected accounts (2026-11-10)

What `5f0b254` (jspdavis) added, how it reads the database, where it departs
from §3 to §5, and what is open. Read from the code on 2026-11-11.

### What a user sees

- **Device tab → Pair a band** is enabled (its "Coming soon" is hidden) and
  opens **Connect** (`ui/device/InviteCodeDialogFragment`,
  `dialog_invite_code.xml`, `InviteCodeViewModel`), with two tabs:
  - **Share my code:** Generate code makes a 6-character code (A-Z and 0-9,
    no character twice), valid for an hour; tap it or Copy code to copy it.
  - **Watch someone:** type the code and Connect. Failures: not found,
    expired, your own code, network, and "permission denied", worded for a
    developer ("Have the invite_codes rules been published?").
- **Track** has a pill at the top (`track_wearer_switcher`): the shown
  wearer's name ("My Band" by default) and a status: **Safe** (green),
  **Panic Alert** (red, while `TrackUiState.bandPanic` is set) or
  **Offline** (grey, not reporting within 180 s). Tapping it opens
  **Switch Wearer**: My Band, each connected wearer by name ("Wearer" when the
  name is blank), and **+ Connect New Wearer**, which opens Connect.
- When Track first loads with connected wearers, or a new one appears while
  it is open, it **switches to that wearer by itself** and toasts "Now
  watching: <name>".

### Data

```
invite_codes/{code}                         { wearer_uid, created_at (server), expires_at, used: false }
wearer_watchers/{wearer_uid}/{watcher_uid}  true
watcher_wearers/{watcher_uid}/{wearer_uid}  true
users/{uid}/bands/{deviceId}                read, never written
```

- **Redeem** reads the code, checks `expires_at` (the generating phone's clock
  plus one hour) against the redeeming phone's clock, refuses your own code,
  then writes both link rows in one multi-path update. `used` is never set,
  so a code works for anyone, any number of times, within its hour. Codes are
  never deleted.
- `removeWatcher` and `stopWatching` delete both rows. **Nothing calls them
  yet**: there is no screen to remove a watcher or stop watching.

### Which band an account reads

`FirebaseBandRepository` is now `RepositoryProvider.bandRepository`. It lists:

- **Own bands (WEARER):** the keys under `users/{uid}/bands`. When there are
  none, which is always today, **two: the account's own uid, then
  `guardband-001`.**
- **Watched bands (WATCHER):** for each wearer in `watcher_wearers/{uid}`,
  their `users/{wearer}/bands` keys plus the wearer's uid itself.

`BandSelection.watchedBandOf` takes the first WEARER band, so **every
account's default band is `devices/<its own uid>`**, and a connected
wearer's is `devices/<their uid>` (`ConnectedWearer.deviceId` falls back to
the uid). **Nothing writes to either.** The mock sender and the firmware write
to `guardband-001` or to their own *anonymous* band uid, never to an
account's uid. So by default Track, Alert history, the inbox, the Device tab
(which also prints the account's uid as the band id) and alert notifications
all show an empty band, and `guardband-001` cannot be chosen in the switcher.
The unit tests use fakes, so they cannot see this.

**The switcher's choice** is `BandSelectionStore`: SharedPreferences
`band_selection`, key `selected_band_id`, per phone, not cleared on sign-out.
`RepositoryProvider.init` creates it from `GuardBandApplication`.
`WatchedBandAlertRepository` combines it with the band list through
`BandSelection.watchedBandOf(bands, explicit)`, which uses the choice when it
is in the list. **`AlertWatchService` still uses the one-argument rule**, so
notifications follow My Band whatever Track shows.

### Where it departs from the plan

| Plan (§3 to §5) | Built |
|---|---|
| Claim a band first (step 3) | No claim. `users/{uid}/bands` is read, never written. |
| A watcher watches a **band** (`device_members/{deviceId}/{uid}`) | A watcher watches an **account** (`wearer_watchers`), then that account's bands |
| A rule accepts a watcher row only against a live invite | Any signed-in account may write `wearer_watchers/{anyone}/{itself}`. The code is checked only in the app. |
| Watchers see the band | The rules also let a watcher read the wearer's whole `users/{uid}`: name, email, **emergency contacts**, consent |
| Invite expiry and single use: open (James) | Multi-use for an hour, on the phones' clocks |
| The wearer's name and photo: notice first | The name shows in the switcher; no notice change |
| My band, a friend, **All** | My band or one friend |

### Open before it can be demoed

1. **What an account with no claimed band reads.** Put `guardband-001` first
   (or alone) in the fallback, so the shared demo band is back by default;
   or build the claim (step 3) first. *(James)*
2. **Rules:** accept a watcher row only against a live invite, and narrow
   what a watcher may read to what the switcher needs (the name and the bands
   index). [database-rules.md](database-rules.md) §10. *(Jul)*
3. **Which watcher rules are live:** `RTDBS-RULES.md` and
   `RTDBS-RULES-CONSOLE.json` differ ([database-rules.md](database-rules.md)
   §10). *(Jul)*
4. **Privacy Notice and `CONSENT_VERSION`:** a wearer's name and alerts are
   now shared with other accounts, and the rules expose their contacts. *(James)*
5. **Screens to remove a watcher and to stop watching**; the alert watch
   following the switcher.
6. **Smaller:** `FirebaseBandRepository`'s `readOnce` never completes on a
   read error, so the band list can stall instead of falling back;
   `FirebaseWearerConnectionRepository` maps unknown failures by matching
   message text (CLAUDE.md: map by code only) and imports `android.util.Log`
   without using it; the two "permission denied" messages are developer
   wording on a user's screen; no tests cover the two Firebase repositories or
   `TrackViewModel`'s switcher logic.
