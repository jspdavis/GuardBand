# Email verification

How an email/password account proves it owns its address: Firebase sends its
own verification email, the link in it opens GuardBand through the **same**
App Link the password reset uses, and the app applies the code. Built to feel
like [`forgot-password.md`](forgot-password.md), and it leans on that file for
the App Link itself.

| | |
|---|---|
| **Branch** | `mvvm-main` (from `feature/account-safety`, 2026-10-14) |
| **Status** | Unit-tested; **not yet seen on a device** (§7). |
| **Link host / path** | The reset link's: `guardband-aae65.firebaseapp.com` + `/__/auth/action`, Firebase's own default handler. Nothing new is declared. |
| **Screens** | [`ui/verify/`](../app/src/main/java/com/example/guardband/ui/verify/) (the link), [`ui/account/`](../app/src/main/java/com/example/guardband/ui/account/) (status + Resend) |
| **Routing** | [`ui/reset/EmailActionLink.kt`](../app/src/main/java/com/example/guardband/ui/reset/EmailActionLink.kt) |
| **Gating** | **None.** Unverified accounts can use everything. Gating is a separate product decision. |

---

## 1. The flow

```
Sign-up (Consent) ── createAccount ok ──► sendEmailVerification()   best effort,
        │                                                            own coroutine,
        ▼                                                            never blocks
     Welcome ...                                                     sign-up
                                   Firebase's verification email
                                                │ user taps the link
                                                ▼
   https://guardband-aae65.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=…
                                                │
                       ┌────────────────────────┴───────────────────────┐
                       ▼                                                ▼
       App Link verified on this phone                  no app / desktop / unverified
       → ResetPasswordActivity (exported)               → Firebase's own page verifies
         EmailActionLink.routeOf = VERIFY_EMAIL            it in the browser
       → VerifyEmailActivity (not exported)                          │
         checkActionCode (must be VERIFY_EMAIL)                      │
         applyActionCode                                             │
         reload() if someone is signed in                            │
       → "Email verified" → Continue → Splash                        │
                       │                                             │
                       └──────────────┬──────────────────────────────┘
                                      ▼
            Account information, next time it comes to the front:
            reload() → "Verified", Resend gone. Nobody is signed out.
```

**Who gets an email.** Email/password sign-ups only, once, right after
`createAccount` succeeds. A Retry of the profile write resumes past it, so it
is not sent twice. Google accounts are never sent one: Google already proved
the address, and Account information shows them as **Verified** with no
Resend (`EmailVerification`).

**No `ActionCodeSettings`,** exactly like the reset email. Firebase builds the
link from the project's default action URL, which is what the App Link claims;
no Dynamic Links machinery is involved (`forgot-password.md` §11).

---

## 2. One App Link, several kinds of email

Firebase sends **every** email action - `resetPassword`, `verifyEmail`,
`recoverEmail`, `verifyAndChangeEmail` - to the same `/__/auth/action`, and an
intent filter cannot match on a query parameter. So every one of them opens
`ResetPasswordActivity`, the one exported Activity, and
**`EmailActionLink.routeOf` runs first**, before the reset screen exists:

| Link | Route | Ends at |
|---|---|---|
| right host and path, `mode=verifyEmail` | `VERIFY_EMAIL` | `VerifyEmailActivity` |
| right host and path, any other non-blank mode except `resetPassword` (`recoverEmail`, ...) | `UNSUPPORTED` | `VerifyEmailActivity`, "This kind of email link isn't supported in the GuardBand app." |
| `mode=resetPassword` | `RESET_SCREEN` | the reset screen, unchanged |
| anything else: wrong host, wrong path, `http`, no mode, garbage | `RESET_SCREEN` | the reset screen's own "This reset link isn't valid", unchanged |

**The reset path is byte-for-byte what it was.** The scheme, host, path and
query handling moved out of `ResetLinkParser` into `EmailActionLink.paramsFrom`
verbatim, so the verify link is held to the same checks; `ResetLinkParser` now
calls it and keeps its own `mode=resetPassword` + `oobCode` checks.
`ResetLinkParserTest` is **unchanged** and passes; `EmailActionLinkTest` adds
that every reset-shaped or malformed link still routes to the reset screen.
The manifest's App Link entry is untouched.

**First `mode` wins**, as in the reset parser, so a `mode=verifyEmail` appended
to a genuine reset link cannot pull it off the reset screen.

**The verify screen re-checks the link.** It is not exported, but it still
parses the URL itself (`verifyEmailCodeFrom`) rather than trusting that it was
only started by the router.

**The repository checks the code's kind.** `applyEmailVerification` calls
`checkActionCode` first and refuses anything that is not `VERIFY_EMAIL`, so a
crafted `mode=verifyEmail` link carrying, say, a recover-email code is
reported as used rather than applied. The screen can never carry out an action
it does not describe.

---

## 3. Account information

The details card shows **Email verification: Verified / Not verified**.

- **Reload on every return.** `AccountActivity.onStart` calls
  `AccountViewModel.onScreenShown`, which reloads the Firebase user. That is
  what makes a link opened in the browser, or on another device, show up
  without signing out. A failed reload leaves the status as it was.
- **Resend**, for a password account that is not verified yet, with the hint
  "We sent a link to this address. Open it on this phone, then come back here."
- **60 s cooldown**, recomputed from an injected `Clock` on each tick of an
  injected `Flow<Unit>`: the forgot-password Resend pattern, so tests never
  wait on real time. The button reads "Resend in 42s".
- **Firebase's own rate limit** (`ERROR_TOO_MANY_REQUESTS` /
  `FirebaseTooManyRequestsException`, both mapped to
  `AuthError.TooManyRequests`) gets "We've sent several emails already. Wait a
  little, then try again." **and starts the cooldown**, because waiting is the
  only fix. Offline gets "No connection" and **no** cooldown, because nothing
  was sent.
- The cooldown lives in the ViewModel, so leaving the screen resets it.
  Firebase's limit is the backstop.

---

## 4. Security properties

| Property | What enforces it |
|---|---|
| The code never reaches an extra, saved state, a message or a log. | A private field on `VerifyEmailViewModel`. It reaches `VerifyEmailActivity` as the Intent's **data**, the link exactly as it arrived, passed in-process to a non-exported Activity; nothing copies it. |
| Only a verify-email code is ever applied. | `checkActionCode` before `applyActionCode` in `FirebaseAuthRepository`. |
| Nobody is signed in or out by the link. | `applyActionCode` is project-level. A signed-in session is only reloaded. |
| A verify link cannot disturb a reset in progress. | `ResetPasswordActivity.onNewIntent` hands it on and leaves the reset alone. (It used to replace the reset with "this reset link isn't valid".) |
| Verification reveals nothing about other accounts. | It only acts on the signed-in account (Resend) or on a code (the link). |

---

## 5. Traps

- **"Already used" is the common outcome, not an error to chase.** Codes are
  single-use; a second tap, a link opened in the browser first, or a newer
  Resend that superseded it all end at "already been used". The message points
  at Account information, which shows the truth.
- **Verified in Firebase is not verified on screen until a reload.** The
  session caches `isEmailVerified`. That is why Account information reloads on
  every `onStart` and the verify screen reloads after success.
- **The ID token's `email_verified` claim refreshes later still.** Nothing uses
  it today (no rule reads it). If a Security Rule ever gates on
  `auth.token.email_verified`, force a token refresh after verifying.
- **Don't add `ActionCodeSettings` to "fix" deep linking.** It would mint the
  handle-code-in-app link, which needed Dynamic Links (shut down 2025).
- **Don't widen the intent filter to `pathPrefix`.** Same reason as the reset:
  `/__/auth/handler` shares the host.
- **The Console's email templates** (sender name, subject, wording) are a human
  step and were not changed.

---

## 6. Not covered by tests

`sendEmailVerification`, `reload`, `checkActionCode` and `applyActionCode`
themselves (they need a real `FirebaseAuth`), the App Link on a device, and the
view code. The testable parts were lifted out: `EmailActionLinkTest` (routing,
codes, the unchanged reset path), `EmailVerificationTest` (what is shown),
`AccountViewModelTest` (reload, Resend, cooldown, rate limit, offline),
`VerifyEmailViewModelTest` (every outcome) and `SignUpConsentViewModelTest`
(sent once, never blocking, never for Google).

---

## 7. Manual test pass

Uses Gmail addresses as the inbox. Nothing here checks the domain.

1. Sign up a new **email/password** account. A verification email arrives.
2. Settings → **Account information** reads **Not verified** with **Resend email**.
3. Tap **Resend email**: a toast, then the button reads "Resend in 60s" and
   counts down. A second tap does nothing until it reaches zero.
4. Open the newest email **on the phone** and tap the link. GuardBand opens on
   "Email verified" (or, without the App Link, Firebase's page says so).
5. Back in Account information: **Verified**, no Resend. Still signed in.
6. Tap the **same link again**: "already been used".
7. A **Google** account: Account information reads **Verified**, no Resend.
8. **The reset link still works** end to end (`forgot-password.md` §8).
9. Airplane mode, then tap a fresh link: "No connection" with Retry; turn it
   off, Retry verifies.
