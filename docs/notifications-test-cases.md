# Alert notifications: device test cases

The manual pass for `feature/alert-notifications` (merged 2026-10-19). The
design and the reasons are in [`notifications.md`](notifications.md). This
file is the checklist: run each case, fill in **Result** (Pass / Fail / Not
run) and **Notes**, and keep a failed case's notes specific (what you saw,
on which phone, how long it took).

## Test run

| | |
|---|---|
| Tester | |
| Date | |
| Build (commit) | |
| Receiver phone (model, Android version) | |
| Sender (mock sender on which phone) | |
| Battery setting on the receiver at the start | |

## Setup

- **Two roles.** The *receiver* is the phone with GuardBand signed in, where
  notifications should appear. The *sender* is the **GuardBand Mock Sender**
  icon, standing in for the band. It can be a second phone, or the same
  phone (switch apps). Two phones are better for the background and sleep
  cases, because opening the mock sender on the receiver brings GuardBand's
  install to the foreground.
- The mock sender is one band button and a battery slider (2026-10-27):
  **hold 1.5 s** sends a PANIC and starts a TRACKING_UPDATE every 5 s;
  **double-press** sends a CHECKIN; during a panic, **hold 5 s** cancels it
  (stops tracking, sends a CHECKIN); dragging the **battery slider** to 20% or
  below sends a LOW_BATTERY. Every message reports the slider's level.
- A debug build from the commit under test (`./gradlew installDebug`).
- `adb` connected to the receiver for the Doze and logcat cases. **If
  someone else's phone is plugged in, tell them before you run adb
  commands on it, and put any setting you change back afterwards.**
- **Fresh install** for the permission cases (TC-01 to TC-04): uninstall
  first, or clear the app's storage, so Android asks again.

**Where to look:**

| What | Where |
|---|---|
| The switch | Track → gear → Settings → **Alert notifications** (its own screen) |
| The channels | Phone settings → Apps → GuardBand → Notifications |
| The lock-screen setting | Phone settings → Notifications (or Lock screen) → "Hide sensitive content" / "Show all content" |
| Battery setting on Realme | Phone settings → Apps → GuardBand → Battery usage → **Allow background activity** (and **Allow auto launch** if shown) |

**Expected wording** (from [`notifications.md`](notifications.md) §2):

| Type | Title | Text | Sound |
|---|---|---|---|
| PANIC | Panic alert sent | Your band sent a panic alert at *date, time*. | **None.** One short buzz, no pop-up banner |
| LOW_BATTERY | Band battery low | Your band is at *N*%. Charge it soon. | Default sound |
| CHECKIN | Check-in received | Your check-in at *date, time* reached GuardBand. | Silent |
| More than 3 at once | GuardBand | *N* more alert(s) from your band. | Default sound |
| The watch | GuardBand | Watching your band for alerts | Silent, can't be swiped away |

No notification should ever show coordinates, a place, a name, a contact,
the profile photo or the device id.

---

## A. Turning it on

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-01 | Fresh install, Android 13+. Sign in and wait on Track for 10 s. | **No** notification permission prompt at launch or on any tab. | | |
| TC-02 | Settings → **Alert notifications**. | Its own screen opens, with a chevron on the row that led there. Switch **off**. Help text: "Shows your band's alerts on this phone, even when GuardBand is closed…". No "Keep watching while the phone sleeps" row. | | |
| TC-03 | Turn the switch on. At the prompt, tap **Don't allow**. | Switch goes back to **off**. Help text changes to "Notifications are blocked for GuardBand on this phone…" and an **Open notification settings** button appears. No watch notification. | | |
| TC-04 | Turn the switch on again (after TC-03). | No prompt loop. Either the prompt appears once more (Android allows one retry) or the switch stays off with the blocked text. Never two prompts in a row from one tap. | | |
| TC-05 | Tap **Open notification settings**. Allow notifications there, then press Back. | The phone's notification page for GuardBand opens. Back on Alert notifications, the blocked text is gone and the help text is back. The switch is still **off** (turning it on is your choice). | | |
| TC-06 | Turn the switch on (permission now allowed, or a fresh install where you tap **Allow**). | Switch **on**. The ongoing "Watching your band for alerts" notification appears. "Keep watching while the phone sleeps" row appears. | | |
| TC-07 | Phone settings → Apps → GuardBand → Notifications. | Five channels: **Panic alerts**, **Band battery**, **Check-ins**, **Other band alerts**, **Alert watch**. Panic alerts has no sound set. | | |
| TC-08 | Tap **Keep watching while the phone sleeps**. | The phone's battery optimisation list (or the closest page this phone has) opens. No crash. If the phone has no such page, a message says so. | | |
| TC-09 | Pull down the shade and tap the "Watching your band" notification. | GuardBand opens on **Alert notifications**; Back goes to Settings. Try to swipe it away: it stays. | | |
| TC-10 | Android 12 or older phone, if one is available. Turn the switch on. | No prompt (older Android has none); the switch turns on and the watch notification appears. | | |

## B. Each alert type, app on screen

Receiver on any GuardBand tab, screen on, switch on.

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-11 | Send **PANIC**. | One notification, wording as in the table. **No sound, no banner** dropping from the top; one short buzz. Status bar icon is the plain bell. | | |
| TC-12 | Send **LOW_BATTERY**. | One notification: "Your band is at *N*%…" where *N* is the sender phone's battery. Default sound. | | |
| TC-13 | Send **CHECKIN**. | One quiet notification, "Check-in received". No sound. | | |
| TC-14 | **Hold 1.5 s** on the sender, let tracking run 30 s, then **hold 5 s** to cancel. | **No** notifications for the tracking updates. The map marker moves as usual. | | |
| TC-15 | Expand each notification from TC-11 to TC-13. | None shows coordinates, a place, your name, a contact, a photo or "guardband-001". The time shown matches the send time. | | |
| TC-16 | Look at the recent-alerts sheet on Track. | The same alerts appear there as before this feature; nothing in the app changed. | | |

## C. Background, locked, asleep

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-17 | Receiver: press Home (GuardBand in the background). Send PANIC. | Notification arrives within a few seconds. | | Delay: |
| TC-18 | Receiver: lock the screen, wait 1 minute. Send PANIC. | Notification arrives; the screen may stay dark (silent channel). Check the shade after unlocking. | | Delay: |
| TC-19 | Receiver: lock, leave untouched for **10+ minutes**, default battery setting. Send PANIC. | Record whether it arrived and how late. | | Arrived? Delay: |
| TC-20 | Same as TC-19 with Realme's **Allow background activity** on (or battery Unrestricted). | Record whether it arrived and how late. | | Arrived? Delay: |
| TC-21 | Force Doze: `adb shell dumpsys deviceidle force-idle`. Send PANIC. Wait 1 minute. Then `adb shell dumpsys deviceidle unforce`. | Record whether it arrived during Doze, after unforce, or not at all. **Always run the unforce line**, even if the case fails. | | During / after / never: |
| TC-22 | Repeat TC-21 with the battery setting from TC-20. | As TC-21. | | |
| TC-23 | Switch on, swipe GuardBand away from Recents. Send PANIC. | Record whether the watch notification survives and the panic arrives. (Expected to survive on stock Android; some phones stop it.) | | |
| TC-24 | Phone settings → Apps → GuardBand → **Force stop**. Send PANIC. | Nothing arrives and the watch notification is gone. Expected: Force stop is the user saying stop. Opening GuardBand starts watching again. | | |

## D. Lock screen

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-25 | Lock-screen setting **Hide sensitive content**. Lock, send PANIC, look at the lock screen without unlocking. | Only "GuardBand / New alert from your band. Unlock to see it." No type, no time, no battery. | | |
| TC-26 | Same, then unlock. | The full text appears in the shade. | | |
| TC-27 | Lock-screen setting **Show all content**. Lock, send PANIC. | Full text shows on the lock screen (Android's choice). Check it still has no location or name. | | |
| TC-28 | Lock-screen setting **Don't show notifications**. Lock, send PANIC. | Nothing on the lock screen; it is in the shade after unlocking. | | |

## E. Tapping a notification

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-29 | GuardBand closed (swiped away, watch still running, or reopen it after TC-24). Tap an alert notification. | GuardBand opens on **Alert history** with that alert in the list. The notification disappears from the shade. Back goes to Track. | | |
| TC-30 | GuardBand open on Contacts. Tap an alert notification from the shade. | Switches to **Alert history** in the same window; no second copy of the app on Back. | | |
| TC-31 | GuardBand open on Account information. Tap an alert notification. | Opens Alert history; Account information closes. | | |
| TC-32 | Log Out, then tap an old alert notification still in the shade. | Goes to **Login**, never into the app. | | |
| TC-33 | Phone offline (airplane mode), GuardBand closed. Tap an alert notification. | The Home cover shows Retry (the gate cannot read the account). Turn airplane mode off, tap Retry: opens **Alert history**. | | |

## F. No repeats, and catching up

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-34 | After TC-11 to TC-13, close and reopen GuardBand. | No old alert is posted again. | | |
| TC-35 | Switch **off**. Send PANIC. Wait 1 minute, switch **on**. | The panic is posted once when the switch comes on (it is under 30 minutes old). | | |
| TC-36 | Receiver in airplane mode. Send 5 alerts (any mix except tracking). Turn airplane mode off. | Up to 3 individual notifications, newest first, plus "*N* more alerts from your band" for the rest. Record what actually arrived: the database may deliver them one at a time, in which case 5 single notifications is also correct. | | |
| TC-37 | **Only on purpose, with the team's agreement:** delete `devices/guardband-001/history` in the Firebase Console. Then send PANIC. | The panic is posted **once**. No flood of old alerts. | | |

## G. Signing out and in

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-38 | Switch on. Settings → **Log Out**. | The watch notification disappears. Send PANIC: nothing arrives. | | |
| TC-39 | Sign back in (same account). | Watching resumes on its own (the switch was left on): the watch notification reappears once Home opens. | | |
| TC-40 | Sign in with a **different** account on the same phone. | Watching resumes for it too (the setting is per phone, and the band is shared until pairing). Expected, not a bug. | | |
| TC-41 | Switch off, Log Out, sign in. | No watch notification. | | |
| TC-42 | Throwaway account: switch on, then Account information → **Delete account**. | The watch stops with the deletion; nothing arrives afterwards. | | |
| TC-43 | Account that meets the consent screen: tap **Decline**. | The watch is not running afterwards. | | |

## H. Restart

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-44 | Switch on. Restart the phone. Don't open GuardBand. Send PANIC. | Nothing arrives and there is no watch notification. **Expected**: the help text says to open GuardBand once after a restart. | | |
| TC-45 | Open GuardBand (past Home). Send PANIC. | The watch notification is back and the panic arrives. | | |

## I. Privacy in the logs

| ID | Steps | Expected | Result | Notes |
|---|---|---|---|---|
| TC-46 | While running sections B and C: `adb logcat -s AlertWatch` | Only "Alert watch started", "Alert watch stopped" or "Alert watch could not start in the foreground". | | |
| TC-47 | `adb logcat` while sending each type, then search the output for the sender's coordinates (shown on the mock sender's status line), your email, and the alert wording. | None of them appears in GuardBand's lines. | | |

## Not testable on a device

- **An unknown alert type** ("New band alert"): the Security Rules only
  accept the four known types, so nothing can write one. Covered by
  `AlertNotifierTest` and `AlertNotificationDeciderTest`.
- **A missed alert older than 30 minutes** after a restart: the mock sender
  always stamps the current time. Covered by `AlertNotificationDeciderTest`.

## Summary

| Section | Pass | Fail | Not run |
|---|---|---|---|
| A. Turning it on | | | |
| B. Each type | | | |
| C. Background and sleep | | | |
| D. Lock screen | | | |
| E. Tapping | | | |
| F. Repeats | | | |
| G. Sign out / in | | | |
| H. Restart | | | |
| I. Logs | | | |

When done, copy the headline results (especially TC-19 to TC-23) into the
table in [`notifications.md`](notifications.md) §7 and the handoff's
"Device checks: alert notifications" row.
