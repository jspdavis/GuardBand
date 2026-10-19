# Alert notifications

The wearer's phone shows a system notification when the band raises an
alert, including while the app is in the background and the phone is
locked. Opt-in, from **Settings → Alert notifications**. Added 2026-10-19 on
`feature/alert-notifications`.

The code is `ui/notifications/` (`AlertWatchService`, `AlertWatch`,
`AlertNotifier`, `AlertNotificationDecider`, `AlertNotificationPrefs`), the
Settings switch (`ui/settings/`), and the tap route through the Home host
(`HomeLaunchTarget`, `HomeViewModel.onLaunchTargetRequested`).

## 1. Why a foreground service, and not push

There is no server. The Firebase project is on the free Spark plan, which
has no Cloud Functions, and the project is meant to stay free.

| Option | Verdict |
|---|---|
| **Local notifications from the existing RTDB listener, in an opt-in foreground service** | **Chosen.** No server, no secret, no new company, no new dependency, no cost. Works with the app in the background and the phone locked. The cost is three permissions (§5), a persistent "Watching your band" notification, and phones whose own battery manager may still stop it (§6). |
| Local notifications only while the app runs | Rejected. Android freezes a backgrounded app within seconds and Doze cuts its network, so the phone would be silent exactly when it is in a pocket. |
| Firebase Cloud Messaging push | Rejected **for now**. FCM itself is free, but something has to *send* each message, and since July 2024 the only sending API (HTTP v1) needs a Google **service account** key: an admin credential. Free senders would be a laptop script holding that key and running during demos (the laptop becomes part of the system) or the band holding it (an admin key on an ESP32). The clean route, a Cloud Function on `history` writes, needs the **Blaze** plan, which requires a billing account even when usage stays inside its free quota. Revisit when pairing and roles exist, and only if the project accepts Blaze. |
| SMS from the app | Not this design. FR-05 gives SMS to the band's Air780E, so it works when the phone is left behind. Deferred; may change later. |

**Free, checked:** nothing here needs a subscription or a paid plan. The
watch holds one RTDB connection per phone that turns it on (Spark allows 100
at once) and downloads the same 100-entry history window the alert screens
already read, so the 10 GB/month Spark download allowance is not a concern
for a demo. MapTiler is untouched: no map tiles load in the background.

## 2. Who is told, and what they see

**The wearer's phone**, meaning the phone this app is signed in on. There
are no roles yet, so the wording is written to the wearer ("your band").
When roles exist, a guardian's phone would want a different panic channel
(loud, heads-up), which needs a new channel id, because channels belong to
the user once created.

| Type | Channel | Importance | Title | Text (unlocked) |
|---|---|---|---|---|
| PANIC | `alert_panic` Panic alerts | default, **no sound**, one short vibration | Panic alert sent | Your band sent a panic alert at Oct 19, 9:05 PM. |
| LOW_BATTERY | `alert_battery` Band battery | default, default sound | Band battery low | Your band is at 18%. Charge it soon. ("Your band's battery is low." without a percent.) |
| CHECKIN | `alert_checkin` Check-ins | low, silent | Check-in received | Your check-in at Oct 19, 9:05 PM reached GuardBand. |
| unknown type | `alert_other` Other band alerts | default | New band alert | Your band sent an alert at Oct 19, 9:05 PM. Open GuardBand to see it. |
| more than 3 at once | `alert_other` | default | GuardBand | 2 more alerts from your band. |
| the watch itself | `alert_watch` Alert watch | low, silent, ongoing | GuardBand | Watching your band for alerts |

`TRACKING_UPDATE` is never notified.

**Why the panic is silent.** The wearer pressed it, maybe with the person
they are afraid of standing next to them. A phone that rings or shows a
heads-up banner at that moment draws attention to the band. One short buzz
tells the wearer it landed. The user can make it louder in the system's
channel settings; the app cannot change it after the channel exists. This
is a `DESIGN: Jul` decision; changing it means a new channel id.

**Never in a notification:** coordinates, an address, a map link, the
user's or a contact's name, the profile photo (no large icon), the device id
or an email. The claim "your contacts were texted" is not made either: the
app cannot know it until the firmware reports it.

**Lock screen.** Every alert is `VISIBILITY_PRIVATE` with a public version
reading "GuardBand / New alert from your band. Unlock to see it.", and all
of them share one plain bell as the status-bar icon (`ic_stat_alert`), so the
status bar does not show a panic octagon either. The public version only
appears when the phone's lock screen is set to **hide sensitive content**; on
"show all content" Android shows the full text, which is why the full text
is kept free of anything sensitive.

**Tap.** Opens `HomeActivity` at Alert history (the watch's own notification
opens Settings). The target waits for the Home gate: a signed-out,
unverified, consent-less or deletion-pending account meets its gate as
usual, and the target is dropped. A failed gate read keeps it for Retry.

## 3. Which alerts are notified (`AlertNotificationDecider`)

Pure Kotlin, covered by `AlertNotificationDeciderTest`. The phone stores a
high-water mark, the newest `sequenceId` it has handled, per device id.

1. **No mark yet** (first watch on this phone): store the newest and post
   nothing, so an old history is not dumped onto the shade. An empty history
   stores 0, so the band's first ever alert is still posted.
2. **New** means a `sequenceId` above the mark.
3. **The first emission after a (re)start** posts only alerts from the last
   30 minutes (`MISSED_ALERT_WINDOW_MS`, FR-09's tracking window). Older ones
   and unreadable timestamps are absorbed. A timestamp ahead of the phone's
   clock counts as recent.
4. **History reset** (newest below the mark: `history` cleared in the
   Console, or a band that restarted at 1): re-baseline, post nothing.
5. Tracking updates are never posted but advance the mark. An unknown type is
   posted (D1).
6. At most 3 per emission, newest first; the rest become one summary.
7. A failed read changes nothing.

## 4. The service (`AlertWatchService`)

- A plain `Service` with its own coroutine scope; no `lifecycle-service`
  dependency. `foregroundServiceType="specialUse"` with the
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation, not `dataSync`, which gets
  a 6-hour daily limit once the app targets API 35.
- Subscribes with `AlertRepository.DEFAULT_HISTORY_WINDOW`, the alert
  screens' own window, so while both run the SDK serves them from one listen.
- **Started only from foreground code** (`AlertWatch.start`): the Settings
  switch, and `HomeActivity` once its gate opens, if the switch is on, the
  user is signed in and the phone allows the app's notifications
  (`AlertWatch.shouldRun`). Android 12+ refuses a foreground-service start
  from the background.
- **Stopped** by the switch, by `LoginNavigation.clearCredentialsThenGoToLogin`
  (Log Out, a finished Delete account, Decline on the consent screen, Log Out
  on the verification gate), and by the Home gate's `NavigateToLogin`. The
  switch is kept, so the next sign-in on this phone resumes it.
- `START_STICKY`. **No boot receiver**: after the phone restarts, watching
  resumes the next time GuardBand is opened. The Settings help text says so.
  That keeps `RECEIVE_BOOT_COMPLETED` out of the manifest.
- Logs "Alert watch started" / "stopped" / "could not start in the
  foreground" only. Never an alert, a uid or a coordinate.

**Stored on the phone** (`AlertNotificationPrefs`, private SharedPreferences
file `alert_notifications`): whether the switch is on, and the high-water
mark. No alert content. It is per phone, not per account, because the band
is shared until pairing exists.

## 5. Permissions

The release manifest gained exactly three, all from the app's own manifest:

| Permission | Kind | Why |
|---|---|---|
| `POST_NOTIFICATIONS` | runtime, API 33+ | Any notification on Android 13+. Asked for **when the user turns the switch on**, never at launch. A refusal leaves the switch off and offers "Open notification settings"; the app never asks in a loop. |
| `FOREGROUND_SERVICE` | install-time | The watch. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | install-time | The watch's type on API 34+. |

Not added, on purpose: `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`,
`USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `VIBRATE`,
any location or SMS permission. Below API 26 there are no channels, so the
panic is posted fully silent there rather than vibrating (which could need
`VIBRATE`).

**The release manifest needs the owner's approval before it ships.** Check it
with `./gradlew :app:processReleaseManifest` and read the `uses-permission`
lines of `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`.

## 6. Phones that stop it anyway

A foreground service is necessary but not always sufficient. Google's Doze
documentation does not promise network access to a foreground service, and
some manufacturers (Realme/ColorOS, Xiaomi, Samsung, Huawei) add their own
battery manager on top. "Keep watching while the phone sleeps", under the
switch, opens the system's battery optimisation list (no permission needed).
Google lists safety apps as an acceptable reason for that exemption.

**Realme (the test phone, RMX5111, Android 15):** Settings → Apps →
GuardBand → Battery usage → turn on **Allow background activity** (and
**Allow auto launch** if shown), and set battery to **Unrestricted** if the
phone offers it. Then check §7, step 4, both before and after.

## 7. Device checklist

Use the mock sender as the band. Results go in the table below; a check that
was not run says so. The full step-by-step cases, with expected results and
a column to fill in, are [`notifications-test-cases.md`](notifications-test-cases.md).

1. Fresh install, Android 13+: no prompt at launch. Settings → Alert
   notifications → the prompt. Refuse: the switch stays off and shows "Open
   notification settings". Allow: the watch notification appears.
2. App on screen: PANIC, LOW_BATTERY, CHECKIN each post one notification
   (wording, channel and sound as in §2). The panic makes no sound and no
   heads-up banner. Walk mode (TRACKING_UPDATE) posts nothing.
3. App in the background (Home pressed): the same.
4. Screen locked and phone asleep 10+ minutes: send PANIC; note whether and
   how late it arrives. Then `adb shell dumpsys deviceidle force-idle`, send,
   `adb shell dumpsys deviceidle unforce`. Both with the default battery
   setting and with §6's setting.
5. Lock screen with "hide sensitive content": only "New alert from your band.
   Unlock to see it." With "show all": the full text, still with no location.
6. Tap a notification: Home → Alert history. Signed out: Login, not the app.
7. Swipe GuardBand from recents with the switch on: does the watch survive?
8. Reopen the app: old alerts are not posted again.
9. Clear `devices/guardband-001/history` in the Console (only on purpose),
   then send a panic: posted once (rule 4 then rule 1's empty case).
10. Log Out: the watch notification goes and nothing more is posted. Sign back
    in: watching resumes only if the switch is still on.
11. Reboot: no watch until the app is opened (expected).
12. logcat throughout: no coordinates, uid, email or alert text.

| Step | Result (2026-10-19) |
|---|---|
| 1-12 | **Not run.** The implementation pass did not drive the phone. |

## 8. Tests

`AlertNotificationDeciderTest` (every rule in §3), `AlertNotifierTest`
(Robolectric: channels and their importance, the silent panic, content per
type, no coordinates, the private/public split, no large icon, the immutable
tap intent, summary and watch notifications, nothing posted while blocked),
`AlertWatchTest` (`shouldRun`), `SettingsViewModelTest` (the switch: off by
default, the prompt, a refusal, blocked in settings, off) and
`HomeViewModelTest` (a tapped target waits for the gate and is dropped by
every other outcome).

**Not covered by tests:** the service itself, `ServiceCompat.startForeground`,
the permission prompt and the system settings pages. They need a device.
