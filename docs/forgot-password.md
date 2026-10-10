# Forgot password: the in-app reset

How a forgotten password is reset without a server: Firebase sends its own
email, the link in it opens GuardBand through an Android App Link, and the new
password is set inside the app.

| | |
|---|---|
| **Branch** | `mvvm-main` |
| **Status** | `assembleDebug` + `testDebugUnitTest` green, 383 tests, 0 failures. **Nothing is deployed and the App Link cannot work yet** — see §1. |
| **Link host** | `guardband-aae65.web.app` |
| **Link path** | `/reset` |
| **Declared in** | [`../app/build.gradle.kts`](../app/build.gradle.kts) — `hostingHost` / `resetLinkPath`, one place (§3) |
| **Screens** | [`ui/forgot/`](../app/src/main/java/com/example/guardband/ui/forgot/), [`ui/reset/`](../app/src/main/java/com/example/guardband/ui/reset/) |
| **Hosting files** | [`../hosting/`](../hosting/) — created, **not deployed** |
| **Related** | [`../CLAUDE.md`](../CLAUDE.md) ("Forgot password") |

---

## 1. What needs a human

**None of this works until these are done.** The code is complete and the build
is green, but an App Link is a claim about a domain, and the domain has to
agree. Until then the email link opens the fallback web page (§6) instead of
the app — which is a graceful failure, not a crash.

| # | Step | Who |
|---|---|---|
| 1 | **Set up Hosting.** From `hosting/`: `firebase init hosting` if the project has none, then `firebase deploy --only hosting`. Keep the `ignore` list in the committed `firebase.json` — see §5. | Jul |
| 2 | **Put the real SHA-256 fingerprints in `assetlinks.json`**, replacing `REPLACE_WITH_DEBUG_SHA256` and `REPLACE_WITH_RELEASE_SHA256`. Get them with `./gradlew signingReport` (the `SHA-256` line of the `debug` variant). Then **redeploy**. | Jul |
| 3 | **Console → Authentication → Templates → Password reset → the pencil → "customize action URL":** set it to `https://guardband-aae65.web.app/reset`. The subject and sender name are optional. | Jul |
| 4 | **Verify the App Link on a device:** `adb shell pm get-app-links com.example.guardband` should show the host as `verified`. To force a re-check: `adb shell pm verify-app-links --re-verify com.example.guardband`. | anyone |
| 5 | **Manual test pass** (§8). | Jul |

**Every developer needs their own debug SHA-256 in `assetlinks.json`**, the
same way each needs their own debug SHA-1 registered for Google sign-in. A
teammate whose fingerprint is missing gets a build where the link opens the
browser instead of the app, and nothing in the build output says why. The
release keystore's SHA-256 has to be added before any signed build.

---

## 2. The flow

```
Login  →  "Forgot Password?"  →  ForgotRequestActivity
                                   │  email + Request Password Reset Link
                                   ▼
                                 Firebase sends its reset email
                                 (same screen becomes "check your email",
                                  with Resend on a 60 s cooldown)
                                   │
                                   │  user taps the link in the email,
                                   │  on the phone with GuardBand installed
                                   ▼
          https://guardband-aae65.web.app/reset?mode=resetPassword&oobCode=…
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
          App Link verified                App not installed,
          → ResetPasswordActivity          or desktop browser
            (New Credentials)              → the static fallback page (§6)
                    │
                    │  verifyPasswordResetCode(oobCode)  → the account's email
                    │  confirmPasswordReset(oobCode, newPassword)
                    ▼
                 LoginActivity, back stack cleared
```

**Firebase proves the user controls the inbox.** That is the whole security
model, and it is the same one Firebase's own hosted page uses. The app does not
verify anything about the user itself; the `oobCode` in the link *is* the
authorisation, and neither client call needs a signed-in user.

**There is no typed verification code, and there cannot be one without a
server.** The wireframe's five-box "Code Verification" screen with the 2:00
timer is **deliberately not built**. A six-digit code has to be generated
somewhere trusted, stored somewhere the user cannot read or write, compared
server-side, and then exchanged for a password change through the Admin SDK.
On the Spark plan there is no Cloud Function to do any of that. A client-only
version — the code minted in the app, or kept in RTDB — puts the thing that
authorises a password change exactly where an attacker already is: readable
with the app's own credentials, and writable by anyone the rules let write. It
would look like security and provide none. (The draft rules still carry a
world-readable, world-writable `password_resets` node from an older attempt at
this; nothing references it, and it should come out.)

---

## 3. The host and path live in one place

Three files have to agree on them: the manifest's intent filter, the parser
that checks an incoming URL, and `assetlinks.json` on the server. They are
declared **once**, at the top of `app/build.gradle.kts`:

```kotlin
val hostingHost = "guardband-aae65.web.app"
val resetLinkPath = "/reset"
```

and pushed out two ways:

| Consumer | Via |
|---|---|
| `AndroidManifest.xml` intent filter | `manifestPlaceholders["hostingHost"]` / `["resetLinkPath"]` → `${hostingHost}` |
| `ResetPasswordActivity` → `ResetLinkParser` | `buildConfigField` → `BuildConfig.HOSTING_HOST` / `RESET_LINK_PATH` |
| `hosting/public/.well-known/assetlinks.json` | by hand — it is served, not built |

`buildConfigField` needs `buildFeatures { buildConfig = true }`, which AGP 8
does not enable by default. It is set in the same file.

**Never use a path under `/__/auth/`.** Firebase Hosting reserves that prefix
for its own reserved URLs, so an App Link filter on it will not behave.

To change the host or path: edit the two values, redeploy hosting, and update
the Console action URL (§1 step 3). `ResetLinkParserTest` hardcodes the real
values on purpose, so a slip in the build file fails a test rather than
shipping.

---

## 4. The parser is the trust boundary

`ResetPasswordActivity` is **the one `exported="true"` Activity in the app**,
because the point is for an external click to reach it. That means any app or
browser on the device can hand it a URL.

`ui/reset/ResetLinkParser` re-checks everything the intent filter already
narrowed — scheme, host, path — and then `mode=resetPassword` and a non-blank
`oobCode`. **An intent filter is a router, not a validator**, and nothing
downstream should assume the filter was only matched honestly.

Three decisions worth keeping:

- **It takes a `String` and uses `java.net.URI`, not `android.net.Uri`.** That
  is what makes it a plain JVM test in a project with no mocking library. The
  Activity passes `intent.data.toString()`.
- **First occurrence of a query parameter wins.** `?mode=resetPassword&…&mode=verifyEmail`
  keeps the first, so a parameter appended to a real link cannot override the
  one Firebase put there.
- **Failure is a bare `null`, not a reason.** The full URL contains the code,
  so no reject path may produce a message or a log line describing what it
  saw. The user is told "request a new link" either way, which is the only
  useful thing to say.

`ResetLinkParserTest` is mostly rejections for that reason: wrong host, a host
the real one is a prefix of, wrong path, the reserved Firebase path, `http`,
wrong mode, missing code, malformed escapes, nonsense and the empty string.

---

## 5. The hosting files

In `hosting/`, **created but not deployed**:

| File | Why |
|---|---|
| `firebase.json` | Serves `public/`. Its `ignore` list is the point — see below. Sets `Content-Type: application/json` on the assetlinks file. No redirects and no rewrites. |
| `public/.well-known/assetlinks.json` | The Digital Asset Links statement. Names `com.example.guardband` and its certificate fingerprints; this is what makes `autoVerify` succeed. |
| `public/reset/index.html` | The fallback page (§6). |

**The `ignore` list is the trap.** Firebase Hosting's default `ignore` includes
`**/.*`, which silently drops every dotfolder — `.well-known/` among them.
Deploying with the default list gives a 404 on `/.well-known/assetlinks.json`,
an App Link that never verifies, and nothing in the deploy output to explain
it. The committed `firebase.json` omits that glob deliberately. Do not "tidy"
it back in.

**`assetlinks.json` must be served directly**, as `application/json`, at
exactly that path, over HTTPS, with **no redirect**. A redirect fails
verification.

---

## 6. The fallback page

`public/reset/index.html` is what a reset link opens when GuardBand is not
installed — a desktop browser, or a phone without the app. It says to open the
email on the phone that has GuardBand, and nothing else.

**It is deliberately static: no scripts, no Firebase web SDK.** The `oobCode`
is in that page's own URL, and the one thing this page must never become is a
second place that can spend it. Keeping it script-free also means nothing on
it can hand the code to a third party. It carries
`<meta name="referrer" content="no-referrer">` for the same reason.

---

## 7. Security properties, and what holds them up

| Property | What enforces it |
|---|---|
| **The screen cannot be used to find out who has an account.** | `ForgotRequestViewModel.treatAsSent` reports `AuthError.NoSuchUser` and `AuthError.InvalidEmail` as success. The sent wording, the cooldown and the resend confirmation are identical either way, and a test compares the two states field for field. |
| **The `oobCode` never reaches an Intent extra, saved instance state, a log or analytics.** | It is a private field on `ResetPasswordViewModel`. `ResetPasswordActivity` parses it out of the incoming URL and passes it straight in. |
| **Nothing logs the code or the URL.** | `ResetLinkParser` has no log calls and returns `null` rather than a diagnosis. Neither does the ViewModel or the Activity. |
| **The new password is never stored.** | The Activity passes it per call; the ViewModel does not keep it. `AuthError` carries no message, so Firebase text cannot leak either. |
| **A signed-in user is neither signed out nor signed in.** | Both calls are project-level, keyed by the code. The session is not read. Success still ends at Login, because the password they had has just stopped being the password. |

**What that costs:** the code does not survive process death. If Android kills
the app while New Credentials is open, the user comes back to a screen with no
code and has to tap the link in the email again. That is the deliberate
trade — persisting it anywhere would mean writing a password-change credential
to disk.

---

## 8. Manual test pass

Needs §1 complete. Nothing below can be checked by a unit test.

1. Register an account. Sign out.
2. **Forgot Password?** → enter that address → **Request Password Reset Link**.
   The screen becomes "check your email" and Resend counts down from 60.
3. Open the email **on the device**. Tap the link. GuardBand opens on New
   Credentials, and the line under the title names the right address.
4. Watch the criteria list respond as you type. Submit stays dead until both
   rules are green.
5. Submit. It lands on Login with a confirmation. Sign in with the **new**
   password; the old one must fail.
6. **Tap the same link again.** It must say the link has already been used and
   offer a new one.
7. **An expired link:** request one, wait out Firebase's expiry, then open it.
   Same dead end, different wording.
8. **An unregistered address:** request a reset for one. The screen must look
   *identical* to step 2 — same wording, same cooldown, no hint either way.
9. **Airplane mode on the New Credentials screen:** Submit reports no
   connection and keeps what was typed. Turning it back on and submitting again
   works.
10. **A desktop browser** on the same link shows the fallback page.
11. **Already signed in:** open the link while signed in. The reset still runs,
    and the session is not disturbed until success sends you to Login.

**Not covered by tests:** the App Link verification itself, the two Firebase
calls, the fallback page, and `PasswordCriteriaList`'s tinting. The first needs
a real device and a deployed domain; the Firebase calls need a real
`FirebaseAuth`; the last two are view code. The testable parts were lifted out
instead — `ResetLinkParser`, `PasswordCriteria`, and both ViewModels.

---

## 9. Troubleshooting

| Symptom | Cause |
|---|---|
| **The link opens a browser, not the app.** | App Link not verified. Check `adb shell pm get-app-links com.example.guardband`. Usually a missing or wrong SHA-256 in `assetlinks.json` (§1 step 2) — including a teammate's debug keystore that was never added. |
| **`/.well-known/assetlinks.json` returns 404 after a deploy.** | The default Hosting `ignore` list dropped the dotfolder. §5. |
| **The link opens Firebase's own web page.** | The Console action URL is still the default. §1 step 3. |
| **The app opens but says the link isn't valid.** | The URL did not pass `ResetLinkParser`: wrong host or path for this build, or `mode` is not `resetPassword`. Compare the Console action URL against §3's two values. |
| **"This reset link has already been used."** | It has — codes are single-use. Also fires when a *newer* reset request revoked it, so requesting twice and opening the first email does this. |
| **The reset works but the user can still sign in with the old password.** | They did not finish: `confirmPasswordReset` never succeeded. Check the Submit step reported success rather than a Toast. |
| **Resend seems stuck.** | It is on a 60 s cooldown by design, counted on the button. The countdown is recomputed from the clock, so it cannot be stranded by a dropped tick. |
| **No email at all, for a registered address.** | Check the spam folder, then the Console's email activity. The screen cannot tell you whether the address exists — that is deliberate (§7) and not a bug to chase from the UI. |

---

## 10. Reference

- [Create custom email action handlers](https://firebase.google.com/docs/auth/custom-email-handler) — the `mode` / `oobCode` / `apiKey` parameters, and the two client calls
- [Customize account management emails](https://support.google.com/firebase/answer/7000714) — where the action URL is set
- [Dynamic Links deprecation FAQ](https://firebase.google.com/support/dynamic-links-faq) — shut down 25 August 2025; this flow does not use it (§11)
- [Android App Links verification](https://developer.android.com/training/app-links/verify-android-applinks)
- [Digital Asset Links](https://developers.google.com/digital-asset-links/v1/getting-started)

---

## 11. Why this does not depend on Dynamic Links

Firebase Dynamic Links **shut down on 25 August 2025** and its links now 404.
The deprecation notice warns that Firebase Auth's email action links are
affected — but that applies to the *handle-code-in-app* flow, where
`ActionCodeSettings.setHandleCodeInApp(true)` plus `setAndroidPackageName(…)`
mints a `*.page.link` dynamic link.

**This flow passes no `ActionCodeSettings` at all.** `sendPasswordReset` is the
plain one-argument call, and the Console's action URL is what makes the link
point at us. Firebase appends `mode` and `oobCode` to a plain `https` URL on
our own domain, and an ordinary App Link intercepts it. No Dynamic Links
machinery is involved at any point — which is also why the Console action URL
is enough and `ActionCodeSettings` would be actively wrong here.

Newer Firebase Auth SDKs add `ActionCodeSettings.setLinkDomain()` as the
official replacement for the handle-code-in-app path. The project is on
firebase-auth **22.3.1** (BoM 32.8.1), which predates it — a second reason to
stay with the Console URL rather than reach for `ActionCodeSettings`.
