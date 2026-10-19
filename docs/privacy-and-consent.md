# Privacy and consent

The Privacy Notice, the delete-account page, the consent version log, and the
rules that keep them in step with the app.

| | |
|---|---|
| **Branch** | `feature/trust-gaps` |
| **Status** | `assembleDebug` + `testDebugUnitTest` green. **Both pages are DRAFT and nothing is deployed** — see §1. |
| **Consent version** | `1.1` (`UserProfileRepository.CONSENT_VERSION`) |
| **Notice** | [`../hosting/public/privacy/index.html`](../hosting/public/privacy/index.html) → `https://guardband-aae65.web.app/privacy` |
| **Delete page** | [`../hosting/public/delete-account/index.html`](../hosting/public/delete-account/index.html) → `https://guardband-aae65.web.app/delete-account` |
| **URLs built by** | [`utils/HostedPages.kt`](../app/src/main/java/com/example/guardband/utils/HostedPages.kt), from constants in [`../app/build.gradle.kts`](../app/build.gradle.kts) |
| **Related** | [`../RTDBS-RULES.md`](../RTDBS-RULES.md), [`forgot-password.md`](forgot-password.md), [`../CLAUDE.md`](../CLAUDE.md) |

---

## 1. What needs a human

**The pages are drafted, linked and buildable. None of it is live, and the
notice is not finished.**

| # | Step | Status |
|---|---|---|
| 1 | **Fill the placeholders.** `REPLACE_WITH_RESPONSIBLE_PARTY` and `REPLACE_WITH_CONTACT_EMAIL` appear in both pages. Then resolve every `DECISION: Jul` — the list is in §4. | **Done** 2026-10-11 |
| 2 | **Have the notice reviewed** by the project adviser or the university's data privacy contact, then remove the `DRAFT, pending review` banner from both pages. **This text describes how real people's data is handled, including third parties who never signed up. It was drafted by an AI from the code and must not be treated as final because of that.** | Open — Jul + adviser |
| 3 | **Deploy Hosting:** `firebase deploy --only hosting` from `hosting/`. Then open `/privacy` and `/delete-account`, and confirm `/.well-known/assetlinks.json` still returns JSON — §5 explains why that last one is the one to check. | **Done** 2026-10-11 |
| 4 | **Back up, then delete the legacy nodes** in the Console: `password_resets`, `phone_index`, `email_index`. **Export the JSON first.** Removing a rule does not remove stored data; it makes it unreachable instead. The grep that justifies this is in §3. **Only after step 5 is published.** | **Done** 2026-10-11 |
| 5 | **Deploy the Security Rules.** The live rules were merged with `RTDBS-RULES.md` (how is recorded at the top of that file), tested against the local emulator, and published. | **Done** 2026-10-11 |
| 6 | **Manual app test** — §6. | Consent, forgot password and Delete account **passed** 2026-10-11. Alert, Track and mock sender handed to a teammate. |

---

## 2. Consent version log

Bump `UserProfileRepository.CONSENT_VERSION` whenever the consent statement
changes what is being agreed to, and add a row here in the same commit. **The
notice page's own displayed version must equal it** — the point of storing a
version with somebody's consent is being able to say which words they agreed
to, which fails if the number cannot be matched to a document.

| Version | Date | What changed |
|---|---|---|
| `1.0` | before this pass | The original statement: agree to the Privacy Notice, confirm 18-or-older or guardian's permission, confirm the wearer knows and agrees to be tracked. **"Privacy Notice" was plain text — there was no notice to read**, which is what this pass fixes. |
| `1.1` | 2026-10-11 | Wording changed from "I agree to the Privacy Notice" to **"I have read the Privacy Notice and agree to it"**, and the notice became a real, reachable document at `/privacy`. The claim being made is materially different: under 1.0 the user could not have read it. |

**Existing users are not re-prompted.** A record written at `1.0` keeps `1.0`;
nothing rewrites it, and there is no re-consent flow. Building one means
deciding what to do with an account that declines, which is a product
question, not a code one — so it is deliberately not in this pass. The
consent gate (§8) does not change that: it fires only when there is **no**
record, and writes at the current version only for those accounts. It needed
no new row here, because the statement it shows is unchanged.

---

## 3. What was removed from the rules draft, and why

Three blocks came out of `RTDBS-RULES.md`. Each was grepped against `app/src`
for its path string first:

| Node | References in `app/src` | What it was |
|---|---|---|
| `password_resets` | **0** | An abandoned typed-code password reset. It was `".read": true, ".write": true` — **world-readable and world-writable**, on the node that would have held the thing authorising a password change. See `forgot-password.md` §2 for why that design cannot work without a server. |
| `phone_index` | **0** | `".read": true`. An MVP-era lookup. |
| `email_index` | **0** | `".read": true`. Same, and a read of it would have answered exactly the "does this email have an account?" question that Login and forgot-password both deliberately refuse. |

**The rules and the data are separate.** Deleting these rules does not delete
what is stored under those paths — it makes it unreachable. Step 4 in §1 is
export-then-delete for that reason.

---

## 4. Every `DECISION: Jul` left in the pages

| Where | Decision | Status |
|---|---|---|
| Both pages, header | Operator and contact address | **Decided 2026-10-11:** USC Evictees, BS Information Technology, University of Cebu–Banilad; julianmiguelroldan@gmail.com |
| Notice, version line | The notice's publication date | **Decided:** 11 October 2026 |
| Notice, analytics | Whether to keep Firebase Analytics | **Dropped.** The dependency is gone and the notice says the app has no analytics. Adding any back means changing the notice in the same commit. |
| Notice, "How long it is kept" | A retention period for band reports | **None yet**, and the notice says so. Revisit if a schedule is ever built. |
| Notice, "Questions" | A contact to escalate to | **None**, so the line was removed. |
| Notice, "Children" | A minimum age for the **account holder** | **18, or a guardian's permission** - the same as the consent checkbox, so no version bump. An account found to break that is deleted. |
| Delete page, "If you cannot open the app" | Whether a manual deletion process exists | **Yes.** Requests to the contact address, acted on only when sent from the account's own email, within 30 days, with a confirming reply. The deletion is done by hand in the Console: `users/{uid}` first, then the Authentication user, the same order as the app. |

---

## 5. How the URLs are wired

One host, two paths, declared once at the top of `app/build.gradle.kts`:

```kotlin
val hostingHost = "guardband-aae65.web.app"
val privacyPath = "/privacy"
val deleteAccountPath = "/delete-account"
```

| Consumer | Via |
|---|---|
| Consent screen, Settings row | `HostedPages.privacyNotice` / `.deleteAccount`, from `BuildConfig` |

The password-reset App Link used to share this host, at `/reset`. It now has
its own `resetLinkHost`, Firebase's default handler on `firebaseapp.com`,
because the project cannot change the Console's action URL
(`forgot-password.md` §3). The `/reset` page is gone.

**`HostedPages.url` is forgiving about slashes** — a host that picked up a
trailing one or a path that lost its leading one both still produce a working
URL, because those are edits somebody will make in the build file and neither
should 404. `HostedPagesTest` also asserts the two real URLs, so a slip there
fails a test rather than shipping a dead link.

**Hosting serves the pages directly.** `firebase.json` sets `cleanUrls: true`
and `trailingSlash: false`, so `/privacy` and `/delete-account` are
each a 200 rather than a 301 to a trailing-slash form. Neither setting touches
`/.well-known/assetlinks.json` — `cleanUrls` only strips `.html`, and the file
is not a directory index — but **check it after every deploy anyway**, because
a 404 there breaks the password-reset App Link silently. `forgot-password.md`
§5 has the dotfolder trap that causes it.

---

## 6. Manual test pass

Needs §1 steps 1–3 done.

1. **Sign up a new account.** The consent step shows **"Read the Privacy
   Notice"** below the checkbox. Tapping it opens the live notice in a browser.
2. Tapping the **statement text** still toggles the checkbox, and does **not**
   open the browser. (That is why the link is a separate button — §7.)
3. With TalkBack on, the checkbox and its statement still read as **one
   control**, and the link reads as a separate button after it.
4. Finish sign-up. In the Console, the new `users/{uid}/consent` shows
   **`version: "1.1"`**, `accepted: true` and a server `acceptedAt`.
5. **Settings → Privacy Notice** opens the same page.
6. The notice's displayed version reads **1.1**, matching step 4.
7. Open `/delete-account` and follow its in-app steps exactly as written —
   Track → gear → Settings → Account information → Delete account (moved
   one level down on 2026-10-14; the hosted page still names the old path
   until a human updates it). The wording should match what the app actually
   does.
8. An **existing** account that accepted `1.0` still shows `1.0` in the
   Console, and is **not** asked to agree again.

**The consent gate (§8).** Use throwaway accounts.

9. **Console-created account.** Add an email/password user in Firebase
   Console → Authentication and sign in with it on the phone. It is
   unverified, so **Verify your email** comes first: Resend, open the link,
   return. Then **Before you continue** appears before any tab, with sign-up's
   statement and "Read the Privacy Notice". Tap **Agree**: the Console shows
   `users/{uid}/consent` with `version: "1.1"`, `accepted: true` and a server
   `acceptedAt`, and the app opens.
10. **Missing Google profile.** For a Google account, delete only
    `users/{uid}` in the Console (leave its Authentication user) and sign in
    again. The record comes back with a name and email, then the consent
    screen appears. Agree writes consent as in step 9.
11. **Decline.** On the consent screen, tap **Decline**: a message says
    consent is required, Login opens, and the Google chooser is offered again.
    Nothing appears under `consent`; the name, email and any contacts are
    unchanged. Relaunching opens Login, not the app; signing in again shows the
    consent screen again.
12. **Normal accounts.** An account with consent at `1.1`, and one whose
    `version` you set to `"1.0"` in the Console, both go straight in with no
    prompt, and their `acceptedAt` does not change.
13. **New sign-up.** The wizard's consent step is shown once; after Welcome,
    the app opens with no second prompt.
14. **Pending deletion.** Set `users/{uid}/deletion_pending: true` on an
    account with no consent: sign-in goes to Account information to finish
    the deletion, not to the consent screen.
15. **Offline.** Sign-in itself needs a connection, so sign in first, then turn
    on airplane mode and relaunch. A spinner, then after about 10 s "We
    couldn't check your account" with **Try again**: not the consent screen
    and not the tabs. Turn the network back on and tap Try again. Also: on the
    consent screen in airplane mode, Agree shows a no-connection message with
    Try again after about 10 s, and Decline still works.
16. **Back and recents.** On the consent screen, the system Back leaves the
    app, and relaunching shows the consent screen again. Switching away and
    back from recents, and force-stopping then reopening, also land on the
    consent screen. None of them reach the tabs.

**Not testable here:** the Security Rules. There is no emulator suite in this
project, so the nine Rules Playground tests at the top of `RTDBS-RULES.md`
are the test suite for them. Run all nine before publishing.

---

## 7. Decisions worth not reversing

**The consent statement stays plain text; the link is a separate button.** The
statement is the `MaterialCheckBox`'s own `android:text`, deliberately — so
tapping the words toggles the box and TalkBack reads the two as one control. A
`ClickableSpan` inside that label fights the toggle (a tap near the link either
follows it or ticks the box, depending on the pixel) and loses the single-control
reading. The link is therefore `btn_signup_consent_privacy`, directly beneath.

**The notice and the delete page are hosted, not in-app screens.** Someone who
has uninstalled GuardBand, or never had it, still needs to read what they
agreed to and find out how to get their data removed. An in-app-only notice
cannot serve either.

**Neither page runs a script.** No analytics, no fonts, no Firebase web SDK. A
page about data handling should not itself be a data-collection surface.

**The notice states only what the code does.** Where a fact is undecided it
carries a `DECISION: Jul` rather than a plausible-sounding default. A notice
that describes practices the project does not follow is worse than one with a
visible gap, and the gaps are the point of the review in §1 step 2.

**It claims no compliance with anything.** It describes practices, names no
statute, and says in its own opening that it is not a legal opinion. Nobody on
this project is in a position to assert otherwise.

**The rules' phone pattern must stay in step with `InputValidator`.** The app
normalises to E.164 **before** writing (`ContactFields.validated`), so the
`.validate` matches the *stored* form, never what was typed. The pattern is:

```
^\+(639[0-9]{9}|[0-57-9][0-9]{7,14}|6[0-24-9][0-9]{6,13})$
```

The three branches are a PH mobile, then the generic E.164 case split by first
digit — because the app refuses anything starting `+63` that is **not** a PH
mobile, and expressing that needs a negative lookahead, which RTDB's regex
does not have. It was checked against a reimplementation of
`normalizePhoneToE164` over 137 inputs: the pattern accepts exactly the 118
values the app would store and nothing it refuses. **Change one, change the
other, and re-run that check.**

---

## 8. Profiles with no consent record: closed in the app

`ProfileProvisioning.ensureProfile` writes **only `name` and `email`** when a
sign-in finds `users/{uid}` absent. So a profile **can** exist with no
`consent` subtree, by two routes:

- an account created in the Console, on its first sign-in;
- a Google account whose record went missing.

**Both are closed by an in-app consent gate (2026-10-14,
`feature/consent-gate`).** The Home host reads the record before it shows any
tab. An account with no consent record is sent to **Before you continue**,
which is sign-up's consent screen: the same statement on the same checkbox,
the same "Read the Privacy Notice" button, and Agree writes the same three
fields (`accepted: true`, a server `acceptedAt`, `version` =
`CONSENT_VERSION`) through the same code as sign-up. **Decline** signs the
user out and changes nothing: no consent is written and the account and its
data are left as they were. The next sign-in meets the screen again.

What it covers, and what it does not:

| Covered | Not covered |
|---|---|
| A **missing** record, on every way in: sign-in, Google, a restored session, a return from recents. | **Versions.** Someone who agreed to `1.0` is let in; there is still no re-consent flow (§2). `ConsentGate.needsConsent` is where a version rule would go. |
| A record that **cannot be read** (offline, refused, no answer in 10 s): a Retry cover, never the consent screen and never the tabs. | **Contacts.** An account let in with consent may still have no emergency contacts (a Google first-timer who abandoned that step). |
| A pending deletion, which still wins over consent. | **Server-side enforcement.** The rules still do not require consent, and must not (below). |

**No rules change was needed.** The late write was tested in the local
Database Emulator against the stripped `RTDBS-RULES.md`: the owner may add
`consent` to an existing profile, or to a record that does not exist yet; a
stranger, an unauthenticated client, a partial record and `accepted: false`
are all refused.

**Declining has no in-app delete.** Settings is behind the gate, so a user who
declines and wants their data gone uses the hosted `/delete-account` page's
manual route, by email. The hosted pages do not describe the gate yet (a human
decision).

**The third route is closed (2026-10-14, `feature/account-safety`).** An
account whose data was deleted but whose Auth user survived a failed delete
used to have a thin record repopulated on the next sign-in. Delete account now
replaces `users/{uid}` with `{ "deletion_pending": true }` in the same write
that removes the data; `ProfileProvisioning` never re-provisions over it, and
the next sign-in is sent to finish the deletion instead. It needed **no rules
change** (checked in the local emulator). It differs from Option B below in
one respect worth knowing: the marker lives **inside** `users/{uid}`, and after
a finished deletion it stays there, a uid and one boolean with no personal data,
because no session is left that could clear it. CLAUDE.md, "Account
deletion", has the details. The table below is the original analysis.

**The original analysis, kept for the record.** Two options, neither taken at
the time (that pass was scoped to the notice and the rules):

| | Option A — leave it documented | Option B — a deletion-pending marker |
|---|---|---|
| What | Record the gap, change nothing. | Before deleting `users/{uid}`, write a marker outside it; `ProfileProvisioning` refuses to provision when the marker is present, and `finalizeSignUp` clears it. |
| Cost | A deleted-then-recreated account looks active with no consent record. | A new node, a new rule, a change to a provisioning path that two sign-in flows depend on, and a decision about what the marker means for a user who signs up again with the same email. |
| Risk | Low, and visible. | Moderate: `ProfileProvisioning` is the reason a session always has a record behind it, and a marker that fails to clear locks a legitimate user out of having a profile at all. |

**Recommendation: Option A for now, Option B when pairing lands.** The gap is
real but narrow — it needs a failed delete *and* the user giving up *and* a
later sign-in — and nothing leaks: the recreated record holds only the name and
email the session already carries. Option B touches the one path that
guarantees every session has a profile, and doing that properly wants the same
work that device pairing needs anyway. **Do not add
`users/$uid/.validate: newData.hasChildren(['consent'])` as a shortcut** — it
would fail the legitimate provisioning write and leave the user with no profile
at all. There is a note saying so at the top of `RTDBS-RULES.md`.
