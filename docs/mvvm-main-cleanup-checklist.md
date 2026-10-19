# mvvm-main cleanup checklist

Started 2026-10-13, after pulling `c045738` (Updated Mock Sender and GPS fix)
and `f34487b` (Updated UI + consent and welcome page) onto `mvvm-main`.

## Already fixed (committed in `b05d769`)

`f34487b` did not compile. These are fixed in the working tree and
`assembleDebug` passes again:

- `activity_consent.xml` and `activity_welcome.xml` used
  `app:layout_setVerticalBias`, which does not exist. Now
  `app:layout_constraintVertical_bias`.
- Settings and Notifications had their back-arrow header replaced by a copy of
  the tabs' gear + bell bar, so the code bound an arrow that no longer existed.
  They were never turned into Activities; they are still Fragments pushed over
  the last tab. The back arrow + title header is restored on both, with the
  rest of the redesign kept.
- `activity_reset_password.xml` lost `progress_reset`, the spinner shown while
  the reset link is checked and the new password saved. Restored as it was.
- Every fragment layout included the whole `activity_home` layout, which drew a
  second bottom nav bar that did nothing. The includes are gone, and content
  that was pinned above that bar now runs to the bottom of the fragment area.
- The gear and bell on Contacts, Alert and Profile did nothing. They now open
  Settings and Notifications through `HomeViewModel`, the same way Track's do.
  The IDs copied from other screens were renamed to each screen's own
  (`ib_contacts_settings`, `ib_contacts_notifications`, `ib_alert_settings`).

## A. Check on the phone what we already changed

Done on the phone, 2026-10-13: everything below checked out. The pass also
covered the `chore/quick-cleanups` merge (button labels, the greens, the
Welcome and reset-success images).

- [x] **Every tab:** content stops just above the bottom nav bar, with nothing
      hidden behind it. Look at Track's Check-in button, the end of the
      Contacts list, Alert history and Settings' scroll.
- [x] **Reset password:** the spinner shows while the email link is being
      checked. After Submit, "Password changed" appears; Go to Login and Back
      both land on Login, and Back from Login does not return to either reset
      screen.
- [x] **Settings and Notifications:** the back arrow and the phone's Back
      button both return to the tab you opened them from.
- [x] **Gear and bell on every tab** (Track, Contacts, Alert, Profile) open
      Settings and Notifications, with **no** bottom bar on either; Back
      brings the bar back with the tab you left still highlighted.
      *(Since 2026-10-19 there is no Alert tab: the bar is Track, Contacts,
      Profile, and Alert history is a pushed screen with a back arrow in
      place of the gear and bell. See `home-host-and-bottom-nav.md` §12.)*
- [x] **Bottom bar:** icons only, noticeably shorter, icons centred. Long-press
      an icon to see its label; TalkBack reads the tab names.
- [x] **Track avatar:** just the profile icon in its circle, no name. TalkBack
      reads "Profile picture: (name)".
- [x] **Launcher icon:** the GuardBand logo on white, uncropped, for both the
      app and "GuardBand Mock Sender" (same icon, different label). Reinstall
      if the old robot icon is still cached by the launcher.
- [x] **Sign-up consent step:** GET STARTED looks dimmed until the box is
      ticked; BACK returns to Contacts; "Read the Privacy Notice" opens the
      hosted page; the spinner appears while the account is created. Check it
      in both modes (email sign-up, and a first-time Google sign-in).
- [x] **Welcome:** appears after GET STARTED in both modes; NEXT and Back both
      go on to Home. Log out and back in (email and Google): it must **not**
      appear again. Killing and reopening the app after NEXT also goes
      straight to Home. (Killed *while* Welcome is on screen, Android restores
      it once more, which is expected.)

## B. Broken or misleading

- [x] **Gear/bell buttons that did nothing**: fixed, see "Already fixed".
- [x] **Copy-pasted button IDs**: renamed, see "Already fixed".
- [x] **The consent text linked to `https://guardband.app/terms`**, a domain
      that isn't ours, for a Terms page that doesn't exist. Removed with the
      mock-up's strings (see below). If the team decides it needs Terms, it is
      a new hosted page plus a `CONSENT_VERSION` bump.
- [x] **The consent mock-up claimed Location and Contacts permissions** the
      app never asks for. Its two rows now say what is true and matches the
      Privacy Notice: the band reports its own location, and contacts are
      typed in, never read from the phone.
- [x] **Button text was made uppercase inside the text itself.** `btn_login`,
      `btn_continue`, `btn_next`, `btn_submit` and `cd_back` are now all caps
      in `strings.xml`. `cd_back` is a screen-reader label, which may read
      "BACK" letter by letter, and these strings also appear in dialogs. Keep
      the text in normal case and set `textAllCaps` in the button style
      instead. Done in `393e373`: on each button, because they share no style.

## C. Leftover screens and files

- [x] **`ConsentActivity`**: Jehan's design now lives on the real consent
      step, `SignUpConsentActivity` (`activity_signup_consent.xml`), which is
      the one that saves consent with `CONSENT_VERSION`. The empty class, its
      layout and its strings are deleted. The consent statement itself is
      unchanged, so no version bump. The main button now reads "Get started"
      rather than "Create account", which was wrong for a new Google sign-in
      where the account already exists.
- [x] **`WelcomeActivity`** is built and in the flow: Consent → Welcome →
      Loading → Home, once per account. Its only route is the consent step's
      success, which happens once per account (email sign-up, or a first-time
      Google sign-in), so it needs no "seen" flag; Login and Splash never pass
      through it.
- [x] **`ForgotSuccessActivity`** was the old "check your email" screen,
      removed on purpose (that confirmation lives in `ForgotRequestActivity`).
      Its design is reused as `ui/reset/ResetSuccessActivity`, a "Password
      changed" screen shown after the new password is set, in place of the old
      Toast. The old class, layout and strings are deleted.
- [x] **Shrink `welcome.png` (2.3 MB) and `reset_success.png` (3.4 MB, was
      `forgot_success.png`).** Both are 2000×2000 or larger; convert to WebP at
      screen size, or a vector. Done in `07a0388`: WebP, 5.8 MB down to 232 KB.

## D. Style and conventions

- [x] **The green `#478831` is typed directly into about 11 layouts and
      several drawables.** Add it as a colour in `colors.xml` and use that
      everywhere, since the convention is theme tokens only. Done in
      `fcfcc86`: `brand_green` and `brand_green_dark` (for `#3D7D28`).
- [ ] **Confirm two app-wide theme changes are intended** (`themes.xml`):
      outlined buttons went from 8 dp to 28 dp corner radius, and the top
      padding on every sign-up screen dropped from 96 dp to 24 dp.

## E. Mock sender GPS (`c045738`)

- [ ] **On Phone A, test both permission answers:** allowed should send the
      phone's real position, refused should fall back to the Cebu City point.
- [x] **Mark the GPS backlog item (§3) done in [handoff.md](handoff.md).** The
      location permission is only in the debug build
      (`app/src/debug/AndroidManifest.xml`) and CLAUDE.md was updated to match,
      so the open question about allowing it is settled.

## F. Wrap-up

- [x] **Commit the fixes listed under "Already fixed"** once section A checks
      out. They went in with `b05d769`.
