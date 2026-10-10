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

| # | Step | Who |
|---|---|---|
| 1 | **Fill the placeholders.** `REPLACE_WITH_RESPONSIBLE_PARTY` and `REPLACE_WITH_CONTACT_EMAIL` appear in both pages. Then resolve every `DECISION: Jul` — the list is in §4. | Jul |
| 2 | **Have the notice reviewed** by the project adviser or the university's data privacy contact, then remove the `DRAFT, pending review` banner from both pages. **This text describes how real people's data is handled, including third parties who never signed up. It was drafted by an AI from the code and must not be treated as final because of that.** | Jul + adviser |
| 3 | **Deploy Hosting:** `firebase deploy --only hosting` from `hosting/`. Then open `/privacy`, `/delete-account` **and `/reset`**, and confirm `/.well-known/assetlinks.json` still returns JSON — §5 explains why that last one is the one to check. | Jul |
| 4 | **Back up, then delete the legacy nodes** in the Console: `password_resets`, `phone_index`, `email_index`. **Export the JSON first.** Removing a rule does not remove stored data; it makes it unreachable instead. The grep that justifies this is in §3. | Jul |
| 5 | **Merge the draft rules into the live rules by hand**, following "Merging into the Console" at the top of `RTDBS-RULES.md`, and run all eight Rules Playground tests. **Copy the live rules out before you touch them** — nobody has a record of them. | Jul |
| 6 | **Manual app test** — §6. | anyone |

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
| `1.1` | `DECISION: Jul` — publication date | Wording changed from "I agree to the Privacy Notice" to **"I have read the Privacy Notice and agree to it"**, and the notice became a real, reachable document at `/privacy`. The claim being made is materially different: under 1.0 the user could not have read it. |

**Existing users are not re-prompted.** A record written at `1.0` keeps `1.0`;
nothing rewrites it, and there is no re-consent flow. Building one means
deciding what to do with an account that declines, which is a product
question, not a code one — so it is deliberately not in this pass.

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

| Where | Decision needed |
|---|---|
| Both pages, header | `REPLACE_WITH_RESPONSIBLE_PARTY`, `REPLACE_WITH_CONTACT_EMAIL` |
| Notice, version line | The notice's **publication date** |
| Notice, "Automatically, by Google" | **Whether to keep Firebase Analytics at all.** It is on the classpath and **no app code calls it**, yet it collects an app-instance id, device model, OS version, region and an advertising id on its own. Dropping the dependency would stop that collection entirely. If it stays, the notice has to say what the project uses it for. |
| Notice, "How long it is kept" | **A retention period for band reports.** There is none today and nothing deletes on a schedule. The notice says exactly that rather than inventing one. |
| Notice, "Children" | Whether the project sets a minimum age for the **account holder** beyond the self-declaration, and what it does on learning an account belongs to a child. |
| Notice, "Questions" | A university contact or data privacy office to escalate to, if there is one. |
| Delete page, "If you cannot open the app" | **Whether a manual deletion process exists.** Today it does not: nobody owns these requests, there is no way to verify the asker owns the account, and no response time is set. The page says so rather than promising a route that is not there. |

---

## 5. How the URLs are wired

One host, three paths, declared once at the top of `app/build.gradle.kts`:

```kotlin
val hostingHost = "guardband-aae65.web.app"
val resetLinkPath = "/reset"
val privacyPath = "/privacy"
val deleteAccountPath = "/delete-account"
```

| Consumer | Via |
|---|---|
| App Link intent filter | `manifestPlaceholders["hostingHost"]` / `["resetLinkPath"]` |
| `ResetLinkParser` | `BuildConfig.HOSTING_HOST` / `RESET_LINK_PATH` |
| Consent screen, Settings row | `HostedPages.privacyNotice` / `.deleteAccount`, from `BuildConfig` |

`HOSTING_HOST` used to be `RESET_LINK_HOST`. It was renamed when the notice
started sharing the domain: one host constant with an accurate name beats two
with the same value.

**`HostedPages.url` is forgiving about slashes** — a host that picked up a
trailing one or a path that lost its leading one both still produce a working
URL, because those are edits somebody will make in the build file and neither
should 404. `HostedPagesTest` also asserts the two real URLs, so a slip there
fails a test rather than shipping a dead link.

**Hosting serves all three directly.** `firebase.json` sets `cleanUrls: true`
and `trailingSlash: false`, so `/privacy`, `/delete-account` and `/reset` are
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
   Track → gear → Settings → Delete account. The wording should match what the
   app actually does.
8. An **existing** account that accepted `1.0` still shows `1.0` in the
   Console, and is **not** asked to agree again.

**Not testable here:** the Security Rules. There is no emulator suite in this
project, so the eight Rules Playground tests at the top of `RTDBS-RULES.md`
are the test suite for them. Run all eight after merging.

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
page about data handling should not itself be a data-collection surface, and
`/reset` has a stronger version of the same rule (`forgot-password.md` §6).

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

## 8. The gap this pass did not close

`ProfileProvisioning.ensureProfile` writes **only `name` and `email`** when a
sign-in finds `users/{uid}` absent. So a profile **can** exist with no
`consent` subtree, by at least three routes:

- an account created in the Console, on its first sign-in;
- a Google account whose record went missing;
- **an account whose data was deleted but whose Auth user survived a failed
  delete** — the next sign-in repopulates a thin record.

The last one matters most: a user who asked to be deleted ends up with an
account again, minus their contacts and consent.

**Two options, neither taken here** (this pass was scoped to the notice and the
rules):

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
