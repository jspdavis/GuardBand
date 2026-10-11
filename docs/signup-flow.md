# Sign-up flow

The revamped sign-up wizard: who writes what, the files, the shared design and
the decisions behind it. Moved out of `handoff.md` on 2026-10-11, unchanged.
The rules that govern it are also in [`../CLAUDE.md`](../CLAUDE.md) under
"Contacts and profile data".


## The journey

```
Login ──"Create an Account"──> Account ──> Name ──> Contacts ──> Consent ──> Loading ──> Home
                               step 1/3   step 2/3   step 3/3   (no step indicator)

Login ──"Continue with Google", first-time user──> Contacts ──> Consent ──> Loading ──> Home
                                                   (indicator hidden)
```

## Who writes what — the single most important fact

**Steps 1–3 write nothing.** They collect and pass along. **Consent is the only
screen that touches Firebase.** It calls `createAccount`, then `finalizeSignUp`,
which commits the profile, every contact and the consent record in **one atomic
multi-path write**.

That is why:

- abandoning the wizard at any point leaves **nothing** behind, and never
  occupies an email address;
- `SignUpContactsViewModel` takes **no repositories at all**;
- a **taken email address is not discovered until the final submit**. This is
  deliberate — see §3.

## 1. Files

| Screen | Classes | Layout |
|---|---|---|
| Step 1 Account | `SignUpAccountActivity` / `ViewModel` / `Event` | `activity_signup_account.xml` |
| Step 2 Name | `SignUpNameActivity` / `ViewModel` / `Event` | `activity_signup_name.xml` |
| Step 3 Contacts | `SignUpContactsActivity` / `ViewModel` / `UiState` / `Event` | `activity_signup_contacts.xml` |
| Consent | `SignUpConsentActivity` / `ViewModel` / `UiState` / `Event` | `activity_signup_consent.xml` |
| Shared header | `SignUpStepHeader` | `view_signup_step_header.xml` |

Steps 1 and 2 have **no UiState** — they do no async work, matching the
project's "events only" convention for such screens.

## 2. The shared design

Restyling should touch **one place**, not four layouts:

- `view_signup_step_header.xml` — back arrow, centred "Step n/3", three bars.
  No Skip: every step is required.
- `SignUpStepHeader.bind(activity, step)` fills the right bars. An `<include>`
  cannot vary its children per use site, so this is set in code.
  `bindBackOnly(activity)` hides the indicator entirely.
- `themes.xml` — `Widget.GuardBand.SignUpStep` (the page frame),
  `.SignUpInput`, `.SignUpContinue`, and `TextAppearance.GuardBand.SignUpTitle`
  / `.SignUpSubtitle` / `.SignUpFieldLabel`.

Step 3 **reuses the Contacts tab's** `ContactAdapter`, `item_contact.xml` and
`dialog_contact_editor.xml`. Don't fork them — the two screens drifting is
exactly what the reuse prevents.

## 3. Decisions, and why — please don't silently reverse these

**Location was removed entirely.** `User.location`, `users/{uid}/location`, the
whole `SignUpLocation*` screen, the Profile row and the Track chip's location
line are gone. A city typed once is worse than nothing beside the band's own
GPS, and a stale one on the Track chip actively misleads. CLAUDE.md says not to
reintroduce it without deciding what reads it.

**The account is created at the final submit, not at Step 1.** Trade-off
accepted knowingly: no orphan accounts, at the cost of late "email already in
use".

**Step 1 does not check whether an address is taken.** It would be the kinder
place to find out, but that is precisely the question Firebase's
email-enumeration protection exists to refuse, and Login and forgot-password
both already decline to answer it. A sign-up screen that answered it would undo
that for all three.

**Sign-up requires one contact; the banner asks for three.**
`SignUpContactsViewModel.MIN_AT_SIGNUP = 1`, while the banner counts toward
`InputValidator.MIN_CONTACTS = 3`. A band in use with one contact beats a
sign-up abandoned at the third. The Contacts tab's repository-level minimum of
3-on-delete is unchanged and still protects the **stored** record.

**Consent is recorded, not just ticked.** `finalizeSignUp` writes
`users/{uid}/consent/{accepted, acceptedAt, version}` in the same atomic commit,
so a profile can never exist without one. `acceptedAt` is
`ServerValue.TIMESTAMP`, not the handset clock, because a device clock can be
wrong or deliberately set. **Bump `UserProfileRepository.CONSENT_VERSION`
whenever `label_signup_consent_statement` changes what is being agreed to.**

**No Skip.** Every step is required, so a Skip control would always refuse.

**Registration is two calls, and Retry re-runs only the second.** The
`accountCreated` guard in `SignUpConsentViewModel` is load-bearing: when these
were one call, a failed write left the address unable to finish registering at
all, because the only retry went back through account creation and failed as
"email already in use". **If you refactor Consent, keep that split.**

## 4. How the wizard carries its data

Intent extras, matching the project convention. `EmergencyContact` is
`Parcelable` (via `kotlin-parcelize`) so the staged list can ride along.

This means the password and the staged contacts' names and numbers sit in
Intents between the four screens. Explicit intents to in-app components do not
leave the process, and nothing logs them. **If you want that tightened**, a
small in-memory sign-up session object replaces all five extras — one contained
pass. The one thing it would lose is surviving process death, which extras do.
