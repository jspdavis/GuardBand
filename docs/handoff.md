# Handoff

**As of 2026-10-13 · `mvvm-main` @ `b05d769` (ahead of `main`) ·
393 unit tests, 0 failures · everything pushed. Development paused for the
progress video; pick up from §4, starting with the phone pass.**

The product build is feature-complete for now, and its backend is live:
Hosting, the Security Rules and the password-reset App Link were all deployed
and checked on 2026-10-11. This file is the **backlog**: what is next, what is
waiting on a person, and the traps worth knowing before you start. What is
already built is documented in the files under §2, not here.

---

## 1. Where things stand

| Area | State |
|---|---|
| **Firebase Hosting** | Live at `guardband-aae65.web.app`: a root page, `/privacy`, `/delete-account`, `/.well-known/assetlinks.json`. Deploy from `hosting/` (§6). |
| **Privacy Notice + delete page** | Every decision filled in, **still carrying the DRAFT banner**. The adviser review was parked on 2026-10-11 (§4). |
| **Password reset** | The App Link claims Firebase's own handler, `guardband-aae65.firebaseapp.com/__/auth/action`, because the Console action URL cannot be changed. Verified on a device; manual pass passed. |
| **Security Rules** | Deployed. [`../RTDBS-RULES.md`](../RTDBS-RULES.md), with its `//` keys stripped, *is* the live ruleset. |
| **Legacy nodes** | `password_resets`, `phone_index`, `email_index`: rules removed, data exported and deleted. |
| **Firebase Analytics** | Removed. The notice says the app has no analytics; adding any back means changing the notice in the same commit. |
| **Manual pass, 2026-10-11** | Consent, forgot password, Delete account: **passed**. Alert, Track, mock sender: **with a teammate** (§4). |
| **Teammate UI pass, 2026-10-13** | `f34487b` (Jehan's redesign) did not compile; fixed and built on in `b05d769`, with real GPS in the mock sender from `c045738`. Everything that changed, and what is still open, is in [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md). **None of it has had a phone pass yet** (§4). |

**Branches.** `mvvm-main` is ahead of `main` by the 2026-10-13 work. Branch new work off
`mvvm-main` and merge back with `--no-ff`, as `feature/trust-gaps` was.
CLAUDE.md's branch table does not mention `main` yet.

---

## 2. Read these first

| For | Read |
|---|---|
| The agent's working rules, conventions, gotchas | [`../CLAUDE.md`](../CLAUDE.md) |
| **Running a demo with no hardware** | [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md): both launchers, end to end |
| The map library and its configuration | [`osmdroid.md`](osmdroid.md): **read before touching the map** |
| How alerts are read from RTDB | [`alert-data.md`](alert-data.md) |
| The sign-up wizard | [`signup-flow.md`](signup-flow.md) |
| The post-login host and its tabs | [`home-host-and-bottom-nav.md`](home-host-and-bottom-nav.md) |
| The in-app password reset | [`forgot-password.md`](forgot-password.md) |
| The 2026-10-13 teammate UI pass: what was fixed, what to check | [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md) |
| Privacy Notice, consent versions, hosted pages | [`privacy-and-consent.md`](privacy-and-consent.md) |
| The alert payload contract | [`../SCHEMA.md`](../SCHEMA.md): **authoritative** |
| Security Rules | [`../RTDBS-RULES.md`](../RTDBS-RULES.md): the source of truth |

---

## 3. Done: real GPS in the mock sender

Shipped by jspdavis in `c045738` (2026-10-13). The mock sender reports **Phone
A's own fix** through `LocationManager`, asks for location at runtime and falls
back to the random Cebu City point if refused. The permission is declared only
in `app/src/debug/AndroidManifest.xml`, so release builds have none, and
CLAUDE.md's rule now reads "no phone-location permission in the **main** app"
with that file as the one named exception. The Privacy Notice stays true.

Still open: testing both permission answers on Phone A (§4, "Waiting on a
person"). Details: [`mock-sender-and-receiver.md`](mock-sender-and-receiver.md).

---

## 4. Backlog

### Waiting on a person

| | Who | Notes |
|---|---|---|
| **Phone pass of the 2026-10-13 changes** | anyone with the phone | Section A of [`mvvm-main-cleanup-checklist.md`](mvvm-main-cleanup-checklist.md): tab layouts, back arrows, gear/bell on every tab, the shorter icon-only bar, the Track avatar, the consent step, Welcome (once only), the reset success screen and the launcher icon. Everything built and unit-tested; nothing has been seen on a device. |
| **Mock sender GPS on Phone A** | anyone with two phones | Allow location: alerts carry the phone's real position. Refuse: they fall back to the Cebu City point. |
| **Two app-wide theme changes** | Jehan + Jul | Confirm they are intended: outlined buttons went from 8 dp to 28 dp corners, and every sign-up screen's top padding from 96 dp to 24 dp (`themes.xml`, `f34487b`). |
| **Test the Alert tab, Track tab and mock sender** | teammate | Never had a full human pass. Checklists: [`mock-sender-and-receiver.md` §6](mock-sender-and-receiver.md) and [`alert-data.md` §12](alert-data.md). The staleness badge (stop the walk, wait 3 min) and pan-and-follow matter most. Before 2026-10-11 every history read was denied by the rules, so this has effectively never worked end to end until now. |
| **Adviser review of the hosted pages** | Jul + adviser | Parked on 2026-10-11. Send `/privacy` and `/delete-account`; when approved, remove the DRAFT banner from both and redeploy. A wording change that alters what users agree to bumps `CONSENT_VERSION` with the notice's own version. |
| **Each teammate's fingerprints** | each developer | Debug **SHA-1** in Firebase Console → Project settings (else Google sign-in fails on their build). Debug **SHA-256** added to `hosting/public/.well-known/assetlinks.json` and Hosting redeployed (else the reset link opens the browser). `assetlinks.json` holds **one** developer's today. Release keystore's SHA-1 and SHA-256 before any signed build. |

### Engineering, roughly in order

| | Notes |
|---|---|
| **Close the world-readable, world-writable `devices/`** | Needs device pairing first: a device id per account, an auth token for the band, and `AlertSender` taught to send `?auth=`. Close it before any public demo; the Privacy Notice says plainly that it is open. |
| **A real tile source** | OpenStreetMap's public servers have a usage policy written for light use. Being correctly identified (the user-agent fix) is not the same as being entitled to the traffic. See `osmdroid.md`. |
| **Rename `applicationId` off `com.example.*`** | Only if publishing: Play rejects the prefix and it cannot change after a first release. Means re-registering the Firebase app, every SHA-1, and the `assetlinks.json` package name. |
| **A `users/{uid}` can exist with no `consent`** | `ProfileProvisioning` writes name + email only. **Don't** add a `.validate` requiring `consent`; it would fail that legitimate write. Options in `privacy-and-consent.md`. |
| **Account deletion can come back thin** | If the profile is removed but the Auth delete fails and the user gives up, the next sign-in recreates a minimal record. Needs a "deletion pending" marker that `ProfileProvisioning` honours. CLAUDE.md, "Account deletion". |
| **No re-consent flow** | Users who accepted `1.0` keep `1.0`. Building one means deciding what happens to an account that declines. |
| **Email verification, if ever added** | Every Firebase email action lands on the same `/__/auth/action` path the app claims, so those links will open the app. Decide what it does with them (`forgot-password.md` §3). |
| **The layers button and Check-in pill are stubs** | D7. Check-in is FR-10's band double-press; the app must never send one. |
| **Google profile photo on the Track avatar** | The avatar is the default icon. `FirebaseUser.photoUrl` has the photo for Google accounts; showing it needs an image-loading library (Coil or Glide), which the project does not have. Email accounts keep the default. |
| **A Terms page, only if the course needs one** | The Privacy Notice already covers what users agree to. Adding Terms means a hosted `/terms` page, a `termsPath` + `HostedPages.terms`, and a `CONSENT_VERSION` bump because what is agreed to changes. |

### Small cleanups

- The `label_dashboard_*` / `label_your_contacts` strings are unused since
  `ui/dashboard/` went. (`androidx.cardview` is **in use** again: the
  2026-10-13 redesign wraps its pill buttons in CardViews.)
- `BandStatus` duplicates `AlertFormatting`'s two ISO-8601 patterns; fold
  them into one helper.
- **Button text in capitals inside `strings.xml`**: `btn_login`, `btn_continue`,
  `btn_next`, `btn_submit`, `cd_back`. Put normal case back and let
  `textAllCaps` do it; `cd_back` is a TalkBack label.
- **Hard-coded greens**: `#478831` in about 11 layouts plus drawables, and
  `#3D7D28` on Consent and Welcome. Make them colour tokens.
- **Two oversized images**: `drawable-nodpi/reset_success.png` (3.4 MB) and
  `welcome.png` (2.3 MB), both about 2000 px square. Convert to WebP at screen
  size.
- **Optional**: give the mock sender its own launcher icon; today it inherits
  the app's, so the two differ only by label.
- `SignUpNameViewModel.onGoogleSignInClicked` is unreachable since the
  redesign (flagged in its KDoc).
- `item_contact.xml`'s edit icon is a placeholder; there is no pencil drawable.
- Add `main` to CLAUDE.md's branch table.
- One manual check never done: open the Alert or Track tab, background the
  app, and confirm RTDB traffic stops (`awaitClose` removing the listener).

---

## 5. Needs a human, not an agent

- **Changing the Security Rules.** Edit `RTDBS-RULES.md`, strip every `"//"`
  key, paste the whole file over the Console's, run the nine Playground tests
  (or the emulator recipe in §6), publish. Copy the live rules out first.
- **Registering fingerprints** (§4).
- **Firebase Console settings.** Email templates' sender name and subject can
  be edited; the action URL cannot on this project, so leave it at the default.
- **Reviewing the Privacy Notice** before its banner comes off. It describes
  how real people's data is handled, including emergency contacts who never
  signed up, and it was drafted by an agent from the code: a reason for a
  human to read it, not a reason to trust it.
- **Figma** is the visual source of truth and is Jul's. `DESIGN: Jul` marks the
  spots the agent left as placeholders.

---

## 6. Building, deploying and verifying

**Build and test.**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # JDK 17
./gradlew assembleDebug testDebugUnitTest
./gradlew installDebug                                           # to the USB phone
```

The `java` on PATH is JDK 25, and Gradle 8.4 cannot run on it. Set
`JAVA_HOME` explicitly; it is not reliably inherited. Run **one** Gradle build
at a time: two concurrent builds write the same `app/build/tmp/kotlin-classes/`
and fail with a bare directory listing that looks like a compile error.

To build a commit without disturbing a dirty tree, use a throwaway worktree;
copy `local.properties` in (it is gitignored), and remove it with PowerShell +
`robocopy /MIR` from an empty directory, because `git worktree remove` fails
with "Filename too long" on Gradle output.

**Deploy Hosting.** From `hosting/`, never `firebase init`:

```bash
firebase deploy --only hosting --project guardband-aae65
```

Then confirm `/.well-known/assetlinks.json` returns `application/json` on both
`guardband-aae65.web.app` and `guardband-aae65.firebaseapp.com`.

**Check the App Link on a device.** `adb` is not on PATH; it is in the SDK's
`platform-tools`:

```bash
adb shell pm get-app-links com.example.guardband        # want: verified
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d "'https://guardband-aae65.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=x'"
```

The second should open `ResetPasswordActivity` directly, then report the
dummy link as invalid.

**Test rules without touching production.** The project has no emulator suite,
but the CLI's Database Emulator works ad hoc from a scratch directory: a
`firebase.json` pointing `database.rules` at a stripped copy of
`RTDBS-RULES.md`, then `firebase emulators:exec --only database --project
demo-guardband "node test.mjs"`. Use a `demo-` project id so nothing can
reach the real project. Fake users are unsigned JWTs passed as `?auth=`;
`Authorization: Bearer owner` bypasses rules for seeding. See §7 for its traps.

**Not covered by tests:** `FirebaseAlertRepository`'s listener and whether
`awaitClose` removes it, the Firebase calls in auth and reset, the `MapView`,
and anything in the UI layer. `ExampleInstrumentedTest` is still the template.

---

## 7. Traps that have already cost time

**Firebase**

- **The Console's password-reset action URL cannot be changed** on this
  project: `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`, from the Console and the API
  alike. The app claims the default handler instead, with an exact
  `android:path`, because `/__/auth/handler` shares the host.
- **A child's `.read` never grants the parent.** `history/$sequenceId` alone
  let each entry be read but denied the list, which 401'd the Alert tab and the
  mock sender until 2026-10-11. `history` now has its own `.read`.
- **The Console rejects `RTDBS-RULES.md`'s `"//"` keys.** Strip them before
  pasting.
- **Firebase Hosting's default `ignore` drops `.well-known/`.** The committed
  `firebase.json` omits that glob on purpose; don't tidy it back.
- **Hosting caches a 404 for an hour** (`max-age=3600`). A page that was
  missing on first visit keeps showing "not found" after a deploy; hard-refresh
  or open a private tab.
- **The emulator needs Java 21+**, the opposite of Gradle: unset `JAVA_HOME`
  so the PATH's Java 25 is used. On Windows its `java` process outlives
  `emulators:exec` and holds port 9000. And rules from `firebase.json` apply to
  the `<project>-default-rtdb` namespace, so querying `?ns=<project>` silently
  runs with open rules; load them with a `PUT /.settings/rules.json` instead.

**Android and the build**

- **A view removed from a layout with only its `findViewById` line deleted**
  crashes at runtime with `UninitializedPropertyAccessException`, not at
  compile time. It happened twice. Remove the field and every use of it.
- **An unescaped apostrophe in `strings.xml`** fails `mergeDebugResources`
  naming neither the string nor the line. Write `\'`.
- **A `--` inside an XML comment** fails the same task with a
  `SAXParseException`.
- **A duplicated `<string name="…">`** fails with "Found item String/x more than
  one time". Grep first.
- **`README.md` is UTF-16 LE with CRLF.** A naive script read collapses its
  CRLFs into a whole-file change that git shows only as `Bin … differ`. Read it
  with `newline=''`.
- **A `StateFlow` drops an emission equal to the previous one.**
  `FakeAlertRepository` uses a replaying `MutableSharedFlow` for that reason.
- **Don't "simplify" `AlertParser` into `getValue(Alert::class.java)`.** It
  reintroduces the `isCharging` → `charging` mapper bug, silently.
- **Don't turn `TrackViewModel`'s injected ticker into an internal
  `while (true) { delay(…) }`.** Tests calling `advanceUntilIdle()` then hang
  instead of failing.

**Git**

- **`git merge -F -` does not read stdin** ("could not read file '-'"). Write
  the message to a file. `git commit -F -` does.
