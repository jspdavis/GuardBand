# Realtime Database rules

Every path the app touches, what the rules allow on each, the open decision on
`devices/`, the Playground tests, and how to publish and roll back.

| | |
|---|---|
| **Rules file** | [`../RTDBS-RULES.md`](../RTDBS-RULES.md): JSON with `"//"` comment keys; stripped, it is the ruleset |
| **Live rules** | Deployed **2026-10-11**. Their text is §9, which is also the rollback |
| **Proposed** | 2026-10-19 (`feature/rules-and-logo`): `users/` **unchanged**; `devices/` is **Option A+, pending Jul's decision** (§4). **Not deployed** |
| **Tested** | All four `devices/` options against the test list in §5, in the local Database Emulator under a `demo-` project id (§6). Never against the real project |
| **Related** | [`privacy-and-consent.md`](privacy-and-consent.md) §3 (the legacy nodes), [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md), [`../SCHEMA.md`](../SCHEMA.md) (the payload), [`../CLAUDE.md`](../CLAUDE.md) |

**Nobody but a human deploys rules.** An agent edits `RTDBS-RULES.md` and
this file; publishing is §7, by hand, in the Console.

---

## 1. In one paragraph

The rules already match what the app does: everything the app and the mock
sender do is allowed, and nothing else under `users/` is. Root, `users` as a
list, and the three legacy nodes are all denied by default. The one real gap
is `devices/`, which is world-readable and world-writable by design until
device pairing exists. The proposal leaves it open, but makes `history`
**append-only**: anyone can still add an entry, but nobody can rewrite or
delete one, which today anyone can. Whether to go further is Jul's call (§4).

---

## 2. Inventory: every path the app reads or writes

Found by searching `app/src/main` and `app/src/debug` for every
`getReference`, `child(...)` and REST path, and every caller of the
repository methods behind them. `{uid}` is always the signed-in user's own
uid; nothing reads or writes another user's record.

| Path | Read by | Written by | Auth | Class, screen |
|---|---|---|---|---|
| `users/{uid}` (whole record) | `FirebaseUserProfileRepository.fetchProfile` (Profile tab); `.profileState` (`ProfileProvisioning` on every email or Google sign-in, `HomeViewModel`'s gate, `DeleteAccountViewModel` on start) | `.markDeletionPending`: one `setValue` replacing the record with `{ "deletion_pending": true }` (Delete account, step 2) | owner | Profile, Home gate, Account information |
| `users/{uid}/name`, `/email` | (inside the record reads above) | `.saveProfile` (`updateChildren`, from `ProfileProvisioning` when the record is absent); `.finalizeSignUp` (one multi-path update from the root) | owner | Sign-in, sign-up Consent step |
| `users/{uid}/emergency_contacts` | `FirebaseContactRepository.observeContacts` (a live listener) | `addContact`, `updateContact`, `deleteContact`: each a **transaction on the whole node** | owner | Contacts tab |
| `users/{uid}/emergency_contacts/{pushId}/{name, phone, relationship}` | (inside the node above) | `.finalizeSignUp`, one path per contact | owner | Sign-up Consent step |
| `users/{uid}/consent/{accepted, acceptedAt, version}` | `.consentRecord` (`HomeViewModel`'s consent gate) | `.finalizeSignUp`; `.recordConsent` (`ConsentRequiredViewModel`). Same three paths, `acceptedAt` a server timestamp | owner | Home gate, Before you continue |
| `users/{uid}/deletion_pending` | `.profileState` | `.markDeletionPending` | owner | Account information |
| `devices/guardband-001/latest` | `FirebaseAlertRepository.observeLatestAlert` (`TrackViewModel`: the map marker) | **mock sender** `AlertSender.send`: `PUT latest.json`, **second**; the band, once firmware exists | read: a signed-in receiver, though the rules ask for none. Write: **unauthenticated** REST | Track tab; Mock Sender (debug only) |
| `devices/guardband-001/history` (the list) | `.observeAlertHistory(100)`: `orderByKey().limitToLast(100)`, shared by Track's recent-alerts panel, Alert history (`AlertViewModel`) and the Check-in pill (`observeLatestCheckIn`); **mock sender** `readHighestHistoryKey`: `GET history.json?shallow=true` | (none at list level) | read: receiver signed in; mock sender **unauthenticated** | Track, Alert history, Mock Sender |
| `devices/guardband-001/history/{sequenceId}` | (inside the list) | **mock sender** `PUT history/{n}.json`, **first**; the band | **unauthenticated** | Mock Sender |
| `devices/{id}/command`, `devices/{id}/status` | **nobody** | **nobody** | | No reference in `app/`, `SCHEMA.md` or the README |
| `password_resets`, `phone_index`, `email_index` | **nobody** | **nobody** | | Removed 2026-10-11 ([`privacy-and-consent.md`](privacy-and-consent.md) §3) |

The device id is always `DeviceConstants.DEFAULT_DEVICE_ID`, supplied through
`RepositoryProvider`. Firebase Auth and Hosting are not part of the database
and are not covered here.

---

## 3. The rules against the inventory

**Paths the app uses with no rule:** none. `deletion_pending` has no rule of
its own, which is deliberate: `users/$uid`'s `.write` covers it and there is
no `$other` at that level (CLAUDE.md, "Account deletion").

**Rules for paths nothing uses:**

| Rule | Note |
|---|---|
| `devices/$deviceId/command` (`led`) and `.../status` (`led`, `online`, `lastUpdate`) | World-readable and world-writable, used by nothing in the repo. They look like an early ESP32 LED test. **Kept in every option**, because a teammate's firmware sketch might still use them. **Ask the firmware teammate; if nobody does, delete both blocks** (one edit, any option). |
| `password_resets`, `phone_index`, `email_index` | **No rules at all**, confirmed: no key in the file, and tests 22a to 22f are denied under every option. |

**Rules looser than the app needs:**

| | Today | Proposed |
|---|---|---|
| 1 | **Anyone can overwrite or delete a history entry** (tests 24, 25): an unauthenticated client can erase a panic. The app never does either; `SCHEMA.md` calls history append-only. | **Fixed in A+**: `".write": "!data.exists()"`. |
| 2 | History entries are checked less than `latest`: no check on `sequenceId` or `battery` (test 27 lets a 150% battery through). | **Fixed in A+**: the same checks as `latest`. |
| 3 | A history key can be anything (test 26). The app orders by key and relies on whole-number keys. | **Fixed in A+**: `$sequenceId.matches(/^[0-9]+$/)`. |
| 4 | **Anyone can read the wearer's position history.** | **The decision in §4.** |
| 5 | Any device id can be written (test 33), so anyone can create `devices/<anything>`. | Not changed. Pinning `$deviceId` to `guardband-001` would hard-code the one id that pairing removes. Listed so it is a choice. |
| 6 | `users/{uid}` accepts any extra child from its owner (no `$other`); `name` may be an empty string; `email` has no format check. | Not changed: owner-only, so the owner can only clutter their own record. **Adding a `$other` at `users/$uid` needs a rule for `deletion_pending` first**, or Delete account fails at step 2. |
| 7 | `emergency_contacts/$contactId` repeats its parent's `.read` and `.write`. | Not changed. Redundant and harmless; RTDB grants cascade down. |

**Things the rules cannot enforce:** the contact minimum (1) and maximum (3).
RTDB rules cannot count children, so a 4th contact (test 16) and the last
contact's deletion (test 21) are both allowed. The repository's transaction
is the last line (CLAUDE.md, "Contacts and profile data").

**Anything the app does that the rules would block:** nothing, under the
deployed rules or A+. Every app operation in §2 passed in the emulator.
Under B and C the mock sender breaks, and under C the receivers do too (§4).

---

## 4. `devices/`: the decision (DECISION: Jul)

The tradeoff: today, anyone who has the database URL (it is inside every APK,
in `google-services.json`) can read the band's reported positions and write
fake alerts. Every option that closes the read breaks the mock sender
or the receivers without app changes, and app changes are out of scope for
this pass.

| | A: as deployed | **A+: append-only history (recommended)** | B: sign-in to read | C: paired reads |
|---|---|---|---|---|
| Read `latest`, `history` | anyone | anyone | any signed-in account | a signed-in account listed under `device_members/{deviceId}/{uid}` |
| Write `latest` | anyone, validated | same | same | same |
| Write `history/{n}` | anyone, **including overwrite and delete** | anyone, **new entries only**; whole-number key; `latest`'s checks | as A+ | as A+ |
| **Receiver** (Track, Alert history, Check-in pill) | works | works | works: every receiver is signed in | **broken for everyone** until each account's uid is added by hand in the Console. There is no pairing flow, and no client may write `device_members` (test 34) |
| **Mock sender** | works | works. **One new failure mode:** if a history PUT lands but its response is lost, the retry reuses the id and gets `HTTP 401` until the harness is relaunched (relaunching re-reads the keys). Two senders at once refuse each other's ids the same way | **broken**: its seed read (`GET history.json?shallow=true`) is unauthenticated, so every send fails with "HTTP 401 reading the history keys". Fixing it means teaching `AlertSender` to send `?auth=` (an app change) | **broken**, same as B |
| **Band firmware** (future) | PUTs work | PUTs work; must never reuse a sequence id, and must treat a 401 on a retried PUT as "already stored" | as A+ (it only writes) | as A+ |
| **Privacy Notice** | accurate ("can be read, and written, without signing in") | accurate | **overstates the exposure**: reads would need an account. Reword in the next deploy | needs rewording |
| Real protection | none | stops anyone erasing or rewriting the history | little: sign-up is open, so anyone can make an account and read | real, for reads; writes stay open until the band has a token |

**Recommendation: A+, pending Jul's decision.** It breaks neither end of the
demo, keeps the notice accurate, and closes the worst write (erasing a panic).
B costs the mock sender for little gain while anyone can sign up. C is the
right end state, but it belongs with device pairing: a pairing record the
app writes, a token for the band, and `AlertSender` sending `?auth=`. A+
leaves B and C as one-block swaps.

**Switching is one block.** Replace the whole `"devices"` block in
`RTDBS-RULES.md` with the option's block below; for C, also add the
`"device_members"` block beside `"users"` and `"devices"`. Option A's block is
the `"devices"` block of §9. A+ is what `RTDBS-RULES.md` holds now. The
`"//"` comments inside the file's A+ block go with it.

<details>
<summary>Option B: the <code>devices</code> block</summary>

A+ with every `".read": true` under `devices/` changed to `"auth != null"`.

```json
"devices": {
  "$deviceId": {
    "latest": {
      ".read": "auth != null",
      ".write": true,
      ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
      "type": {
        ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
      },
      "sequenceId": { ".validate": "newData.isNumber()" },
      "battery": {
        "percent": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100" },
        "isCharging": { ".validate": "newData.isBoolean()" }
      }
    },
    "history": {
      ".read": "auth != null",
      "$sequenceId": {
        ".read": "auth != null",
        ".write": "!data.exists()",
        ".validate": "$sequenceId.matches(/^[0-9]+$/) && newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
        "type": {
          ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
        },
        "sequenceId": { ".validate": "newData.isNumber()" },
        "battery": {
          "percent": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100" },
          "isCharging": { ".validate": "newData.isBoolean()" }
        }
      }
    },
    "command": {
      ".read": "auth != null",
      ".write": true,
      "led": { ".validate": "newData.isBoolean()" }
    },
    "status": {
      ".read": "auth != null",
      ".write": true,
      "led": { ".validate": "newData.isBoolean()" },
      "online": { ".validate": "newData.isBoolean()" },
      "lastUpdate": { ".validate": "newData.isNumber()" }
    }
  }
}
```

</details>

<details>
<summary>Option C: the <code>devices</code> block, plus <code>device_members</code></summary>

A+ with every `".read": true` under `devices/` changed to the membership
check. `device_members` has no `.write` anywhere, so only the Console (or a
future server) can pair an account; a user may read only their own row.

```json
"devices": {
  "$deviceId": {
    "latest": {
      ".read": "auth != null && root.child('device_members').child($deviceId).child(auth.uid).val() === true",
      ".write": true,
      ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
      "type": {
        ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
      },
      "sequenceId": { ".validate": "newData.isNumber()" },
      "battery": {
        "percent": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100" },
        "isCharging": { ".validate": "newData.isBoolean()" }
      }
    },
    "history": {
      ".read": "auth != null && root.child('device_members').child($deviceId).child(auth.uid).val() === true",
      "$sequenceId": {
        ".read": "auth != null && root.child('device_members').child($deviceId).child(auth.uid).val() === true",
        ".write": "!data.exists()",
        ".validate": "$sequenceId.matches(/^[0-9]+$/) && newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
        "type": {
          ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
        },
        "sequenceId": { ".validate": "newData.isNumber()" },
        "battery": {
          "percent": { ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100" },
          "isCharging": { ".validate": "newData.isBoolean()" }
        }
      }
    },
    "command": {
      ".read": "auth != null && root.child('device_members').child($deviceId).child(auth.uid).val() === true",
      ".write": true,
      "led": { ".validate": "newData.isBoolean()" }
    },
    "status": {
      ".read": "auth != null && root.child('device_members').child($deviceId).child(auth.uid).val() === true",
      ".write": true,
      "led": { ".validate": "newData.isBoolean()" },
      "online": { ".validate": "newData.isBoolean()" },
      "lastUpdate": { ".validate": "newData.isNumber()" }
    }
  }
},
"device_members": {
  "$deviceId": {
    "$uid": {
      ".read": "auth != null && auth.uid == $uid"
    }
  }
}
```

To pair an account by hand under C: Console → Data → add
`device_members/guardband-001/<uid>: true`.

</details>

---

## 5. Rules Playground tests

Console → Realtime Database → Rules → **Rules Playground**. Choose the
simulation type (read or write), the location, and "Authenticated" with a
custom uid where a row says so. Use a real uid for `AUTH_UID` and a different
one for `OTHER_UID`. A simulated write is **not** stored.

**The Playground simulates against the live data,** which matters for the
append-only rule: test 8 needs a sequence id that does **not** exist yet
(`999999`), and tests 24 and 25 one that **does** (look in Data, under
`devices/guardband-001/history`, and use any key you see; `1` below). For
`acceptedAt = now`, use the current time in milliseconds (or a few seconds
ago); a future value must be denied.

Payload `P` (a valid alert, per `SCHEMA.md`):

```json
{"schemaVersion":"1.0","deviceId":"guardband-001","type":"PANIC","timestamp":"2026-10-19T08:00:00.000Z","location":{"lat":10.3157,"lng":123.8854,"accuracyMeters":8.5},"battery":{"percent":80,"isCharging":false},"sequenceId":999999}
```

Tests 1 to 9 are the nine that used to be listed in `RTDBS-RULES.md`. "As" is
who is authenticated; "unauth" means unauthenticated. The four right-hand
columns are the expected result under each `devices/` option; for `users/` and
the legacy nodes they are the same under all four.

| # | Op | Path | As | Data | A | **A+** | B | C |
|---|---|---|---|---|---|---|---|---|
| 1 | read | `/users/AUTH_UID` | AUTH_UID | | allow | **allow** | allow | allow |
| 2 | write | `/users/AUTH_UID` | AUTH_UID | `null` | allow | **allow** | allow | allow |
| 3 | read | `/users/AUTH_UID` | OTHER_UID | | deny | **deny** | deny | deny |
| 4 | read | `/users/AUTH_UID` | unauth | | deny | **deny** | deny | deny |
| 5a | write | `/users/AUTH_UID/emergency_contacts/c1` | AUTH_UID | `{"name":"A","phone":"0917123456","relationship":""}` | deny | **deny** | deny | deny |
| 5b | write | same | AUTH_UID | same, phone `"+639171234567"` | allow | **allow** | allow | allow |
| 6a | write | `/users/AUTH_UID/consent` | AUTH_UID | `{"accepted":false,"acceptedAt":0,"version":"1.1"}` | deny | **deny** | deny | deny |
| 6b | write | same | AUTH_UID | `{"accepted":true,"version":"1.1"}` | deny | **deny** | deny | deny |
| 6c | write | same | AUTH_UID | all three, `acceptedAt` in the future | deny | **deny** | deny | deny |
| 6d | write | same | AUTH_UID | all three, `acceptedAt` = now | allow | **allow** | allow | allow |
| 7 | read | `/devices/guardband-001/latest` | unauth | | allow | **allow** | deny | deny |
| 8 | write | `/devices/guardband-001/history/999999` | unauth | `P` | allow | **allow** | allow | allow |
| 9 | read | `/devices/guardband-001/history` | unauth | | allow | **allow** | deny | deny |
| 10 | read | `/` | AUTH_UID | | deny | **deny** | deny | deny |
| 11 | read | `/users` | AUTH_UID | | deny | **deny** | deny | deny |
| 12 | write | `/users/AUTH_UID` | AUTH_UID | `{"deletion_pending":true}` | allow | **allow** | allow | allow |
| 13 | write | `/users/OTHER_UID/name` | AUTH_UID | `"X"` | deny | **deny** | deny | deny |
| 14 | write | `/users/AUTH_UID/name` | unauth | `"X"` | deny | **deny** | deny | deny |
| 16 | write | `/users/AUTH_UID/emergency_contacts` | AUTH_UID | four valid contacts | allow ¹ | **allow ¹** | allow ¹ | allow ¹ |
| 17 | write | `/users/AUTH_UID/emergency_contacts/c1` | AUTH_UID | a valid contact plus `"note":"x"` | deny | **deny** | deny | deny |
| 18 | write | same | AUTH_UID | `{"name":"A","relationship":""}` (no phone) | deny | **deny** | deny | deny |
| 19 | write | same | AUTH_UID | phone `"+6321234567"` (PH landline) | deny | **deny** | deny | deny |
| 20 | write | same | AUTH_UID | phone `"+14155550123"` | allow | **allow** | allow | allow |
| 22a | read | `/password_resets` | unauth | | deny | **deny** | deny | deny |
| 22b | write | `/password_resets/x` | AUTH_UID | `{"code":"1"}` | deny | **deny** | deny | deny |
| 22c | read | `/phone_index` | AUTH_UID | | deny | **deny** | deny | deny |
| 22d | write | `/phone_index/x` | unauth | `"x"` | deny | **deny** | deny | deny |
| 22e | read | `/email_index` | AUTH_UID | | deny | **deny** | deny | deny |
| 22f | write | `/email_index/x` | unauth | `"x"` | deny | **deny** | deny | deny |
| 23 | write | `/devices/guardband-001/latest` | unauth | `P` | allow | **allow** | allow | allow |
| 24 | write | `/devices/guardband-001/history/1` (exists) | unauth | `P` with `"sequenceId":1` | allow | **deny** | deny | deny |
| 25 | write | same | unauth | `null` (a delete) | allow | **deny** | deny | deny |
| 26 | write | `/devices/guardband-001/history/abc` | unauth | `P` | allow | **deny** | deny | deny |
| 27 | write | `/devices/guardband-001/history/999999` | unauth | `P` with `"percent":150` | allow | **deny** | deny | deny |
| 28 | write | `/devices/guardband-001/latest` | unauth | `P` with `"type":"FOO"` | deny | **deny** | deny | deny |
| 30 | read | `/devices/guardband-001/history` | AUTH_UID | | allow | **allow** | allow | allow ² |
| 31 | read | `/devices/guardband-001/latest` | OTHER_UID | | allow | **allow** | allow | deny ² |
| 34 | write | `/device_members/guardband-001/OTHER_UID` | OTHER_UID | `true` | deny | **deny** | deny | deny |

¹ RTDB rules cannot count children; the repository refuses the 4th contact
(and the last one's deletion). This row shows the rule does not, so nobody
relies on it.
² Under C, only if `device_members/guardband-001/AUTH_UID` is `true` and
`OTHER_UID`'s is not.

The gaps in the numbering are emulator-only checks (§6) that the Playground
cannot express: a `PATCH` merge (15), a single contact delete (21), the
`shallow` and ordered queries (29, 30 as a query, 32), a made-up device id
(33) and `command/led` (35).

---

## 6. What was tested, and how

On 2026-10-19 every row above, plus the emulator-only ones, ran against all
four options in the local Database Emulator (firebase-tools 15.33.0, project
id `demo-guardband`), with the rules loaded by `PUT /.settings/rules.json`
into the `demo-guardband-default-rtdb` namespace, fresh seed data before each
test, and unsigned test tokens for the users. **Every result matched the
table**, and `users/` and the legacy nodes gave identical results under all
four. The extra checks: a `PATCH` of `name` and `email` (saveProfile) and a
single contact delete are allowed; the mock sender's
`history.json?shallow=true` read is allowed under A and A+ and denied under B
and C; the receiver's `orderBy="$key"&limitToLast=100` query is allowed for
a signed-in user under A, A+ and B, and under C only for a paired one; a write
to `devices/not-a-band/latest` and to `command/led` are allowed under all four.

The recipe is in [`handoff.md`](handoff.md) §6 ("Test rules without touching
production") and its traps in §7. The harness itself was a scratch script and
is not in the repo.

---

## 7. Rollout (by hand)

1. **Decide the `devices/` option** (§4). If it is not A+, swap the block in
   `RTDBS-RULES.md` first (for C, add `device_members` and pair the accounts
   in the Console **before** publishing, or every receiver goes blank).
2. **Save the live rules.** Console → Realtime Database → Rules: copy the
   whole text somewhere safe. It should equal §9. **If it does not, stop**:
   someone changed the Console without changing the file, and that needs
   merging first.
3. **Strip the comment keys.** From the repo root, in PowerShell:

   ```powershell
   python -c "import json;s=lambda o:{k:s(v) for k,v in o.items() if not k.startswith('//')} if isinstance(o,dict) else o;print(json.dumps(s(json.load(open('RTDBS-RULES.md',encoding='utf-8'))),indent=2))" | Set-Clipboard
   ```

   The clipboard now holds the ruleset (ASCII only, 115 lines for A+).
4. **Paste it over the editor's text. Do not publish yet.**
5. **Run §5 in the Playground** against the pasted text. Every row must match
   your option's column. If one does not, do not publish.
6. **Publish.**
7. **Check the app** on a phone, signed in: Profile shows your name; Contacts
   lists, adds and edits a contact; Track shows the marker, the Check-in pill
   and the recent-alerts sheet; See all opens Alert history. Then the **Mock
   Sender**: Simulate Panic reads "✓ Sent PANIC as #n" and the receiver shows
   it; a few Walk steps move the marker; relaunch the sender and send again
   (the seed read). Under B or C, expect the sender to fail with HTTP 401.
8. **Back in the repo:** delete the STATUS paragraph at the top of
   `RTDBS-RULES.md`, replace §9 below with the newly live text, and update the
   status rows here and in `handoff.md` §1. Commit.

**Rolling back:** paste §9 (the text saved in step 2, if it differs) and
publish. Nothing in the app needs to change back, because the app never
depended on the old looseness.

---

## 8. Leftover data

**Removing a rule never removes data.** It only makes the data unreachable
from clients.

- **`password_resets`, `phone_index`, `email_index`: should already be
  gone.** The whole database was exported and the three nodes deleted in the
  Data tab on 2026-10-11, after the rules that served them were removed
  (`privacy-and-consent.md` §1, step 4). With no rule, no client can have
  written them since; only the Console or an Admin SDK could. **Check once:**
  Console → Data, and look at the root. Expect only `devices` and `users`.
- **If any of them is there:**
  1. Export it first: hover the node, ⋮ → **Export JSON**, and keep the
     file with the 2026-10-11 export.
  2. Delete it: hover the node, click the **✕** (or ⋮ → Delete), confirm.
  3. Reload the Data tab to make sure it is gone. Rules need no change.
- **Other leftovers worth a look while you are there**, none urgent:
  `devices/guardband-001/command` and `status` (if anything ever wrote them);
  any device id other than `guardband-001` (open writes let anyone create
  one, §3 row 5); a history key that is not a whole number (A+ refuses new
  ones but leaves stored ones alone); and finished deletions'
  `users/{uid}: { deletion_pending: true }` markers (harmless, `handoff.md`
  §4). Export before deleting any of them, too.

---

## 9. The live rules (deployed 2026-10-11): rollback text

This is `RTDBS-RULES.md` as committed before 2026-10-19 (`6ade00c`), with its
`"//"` keys stripped. Its `"devices"` block is **Option A**.

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid",
        "name": {
          ".validate": "newData.isString() && newData.val().length <= 100"
        },
        "email": {
          ".validate": "newData.isString() && newData.val().length <= 254"
        },
        "emergency_contacts": {
          "$contactId": {
            ".read": "auth != null && auth.uid == $uid",
            ".write": "auth != null && auth.uid == $uid",
            ".validate": "newData.hasChildren(['name','phone'])",
            "name": {
              ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 100"
            },
            "phone": {
              ".validate": "newData.isString() && newData.val().matches(/^\\+(639[0-9]{9}|[0-57-9][0-9]{7,14}|6[0-24-9][0-9]{6,13})$/)"
            },
            "relationship": {
              ".validate": "newData.isString() && newData.val().length <= 50"
            },
            "$other": {
              ".validate": false
            }
          }
        },
        "consent": {
          ".validate": "newData.hasChildren(['accepted','acceptedAt','version'])",
          "accepted": {
            ".validate": "newData.isBoolean() && newData.val() == true"
          },
          "acceptedAt": {
            ".validate": "newData.isNumber() && newData.val() <= now"
          },
          "version": {
            ".validate": "newData.isString() && newData.val().matches(/^[0-9]{1,3}\\.[0-9]{1,3}$/)"
          },
          "$other": {
            ".validate": false
          }
        }
      }
    },
    "devices": {
      "$deviceId": {
        "latest": {
          ".read": true,
          ".write": true,
          ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
          "type": {
            ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
          },
          "sequenceId": {
            ".validate": "newData.isNumber()"
          },
          "battery": {
            "percent": {
              ".validate": "newData.isNumber() && newData.val() >= 0 && newData.val() <= 100"
            },
            "isCharging": {
              ".validate": "newData.isBoolean()"
            }
          }
        },
        "history": {
          ".read": true,
          "$sequenceId": {
            ".read": true,
            ".write": true,
            ".validate": "newData.hasChildren(['schemaVersion','deviceId','type','timestamp','battery','sequenceId'])",
            "type": {
              ".validate": "newData.val() === 'PANIC' || newData.val() === 'CHECKIN' || newData.val() === 'LOW_BATTERY' || newData.val() === 'TRACKING_UPDATE'"
            }
          }
        },
        "command": {
          ".read": true,
          ".write": true,
          "led": {
            ".validate": "newData.isBoolean()"
          }
        },
        "status": {
          ".read": true,
          ".write": true,
          "led": {
            ".validate": "newData.isBoolean()"
          },
          "online": {
            ".validate": "newData.isBoolean()"
          },
          "lastUpdate": {
            ".validate": "newData.isNumber()"
          }
        }
      }
    }
  }
}
```
