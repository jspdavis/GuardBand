# Requirements: Roles, Panic SMS & Alert Timeline

GuardBand today is a single-role app: every account watches one hardcoded band
(`guardband-001`), alerts are band-originated, and alert delivery is in-app
notifications only. This enhancement introduces Guardian/Dependent user roles,
phone-number validation to prevent self-pairing, direct SMS delivery of panic
alerts to the guardian, and a Today/Yesterday/Older timeline with resolution
status for alert history.

## In scope

- Guardian/Dependent role selection at registration, stored at
  `users/{uid}/role`, forking the home dashboard by role.
- Capturing the user's own phone (`users/{uid}/phone`) and blocking an
  emergency (guardian) contact whose phone matches it.
- Phone-side SMS to the guardian's registered phone on a panic from the band,
  with a live-tracking session link.
- Alert history grouped under Today / Yesterday / Older, with an
  Active/Acknowledged/Resolved status on each row.

## Out of scope (staged future, separate owner sign-off)

- **Per-account device pairing** — Option C in `docs/database-rules.md` §4
  (`device_members/{deviceId}/{uid}` gating `devices/` reads). Both roles read
  the shared `guardband-001` band in this phase. Roles + pairing are separate:
  roles gate the UI and the SMS sender; pairing would gate device data.
- **Band-side SMS** — the band's own SIM sending the text. The docs currently
  state "SMS stays on the band"; this spec moves SMS to the phone per the
  owner's decision, which reverses that note and requires a Privacy Notice
  update (REQ-SMS-08).
- **The web page that resolves `https://guardband.app/track/<SESSION_ID>`** —
  the app generates the session token and URL; rendering the live map at that
  URL is a separate web deliverable.

## Confirmed decisions

1. **SMS vs push: SMS only** — direct SMS to the guardian, no FCM/push
   dependency.
2. **Phone validation** — compare `guardian_phone` against `user_phone` during
   setup; block if equal.
3. **Roles** — store `role` (`GUARDIAN` | `DEPENDENT`) at `users/{uid}/role` at
   registration; fork the dashboard.
4. **Timeline** — group under Today / Yesterday / Older.

## Requirements

### ROLES — Role selection & dual dashboards

- **REQ-ROLE-01** — The registration wizard requires the user to select a role
  (GUARDIAN or DEPENDENT) before the account is finalized. No account is
  created without a role.
- **REQ-ROLE-02** — The role is stored at `users/{uid}/role` as the string
  `GUARDIAN` or `DEPENDENT`, written in the same atomic commit as
  name/email/consent/contacts (`finalizeSignUp`).
- **REQ-ROLE-03** — Role capture applies to both sign-up paths: email/password
  and Google complete-profile.
- **REQ-ROLE-04** — The role is read back from the profile and used by the Home
  gate to decide which dashboard to show.
- **REQ-ROLE-05** — A DEPENDENT sees the Dependent dashboard: wristband device
  status, a panic-test trigger (simulation), connection state, and the
  assigned guardian's details.
- **REQ-ROLE-06** — A GUARDIAN sees the Guardian dashboard: real-time map
  tracking, active alerts list, dependent status, battery level, and the
  historical alert timeline.
- **REQ-ROLE-07** — Role-selection UX explains mutual alignment: a Dependent
  pairs with a Guardian and vice versa. Pairing is not enforced at the database
  level in this phase (see Out of scope).
- **REQ-ROLE-08** — A thin/provisioned profile created by
  `ProfileProvisioning.ensureProfile` (before the wizard completes) must not
  break the gate; a present profile with no role routes the user to complete
  the wizard, consistent with the existing consent gate.
- **REQ-ROLE-09** — The existing `contact_relationship_guardian` free-text
  label is unchanged and is NOT a user role.

### PHONE — Guardian phone self-validation

- **REQ-PHONE-01** — The user's own phone number is captured during
  registration and stored at `users/{uid}/phone`, E.164-normalized
  (`+639XXXXXXXXX`), in the same atomic commit.
- **REQ-PHONE-02** — Own-phone capture occurs before the emergency-contacts
  step so the comparison can run there.
- **REQ-PHONE-03** — When an emergency (guardian) contact is added or saved,
  the contact's phone is E.164-normalized and compared against the signed-in
  user's own stored phone.
- **REQ-PHONE-04** — If the normalized contact phone equals the user's own
  phone, the save is blocked.
- **REQ-PHONE-05** — The blocked save shows: "Guardian phone number cannot be
  the same as the dependent's phone number."
- **REQ-PHONE-06** — The check applies in both sign-up (`SignUpContacts`) and
  the Contacts tab add/edit, both routed through `ContactFields.validated`.
- **REQ-PHONE-07** — The check is app-layer (RTDB Security Rules cannot compare
  two nodes), consistent with the existing `MIN_CONTACTS` bound. The rules'
  E.164 regex still validates the stored form of both phones.

### SMS — Panic SMS & live tracking

- **REQ-SMS-01** — When a PANIC alert (type `PANIC`) from the band is received by
  the alert watch on a DEPENDENT's phone, the phone sends an automated SMS to
  the linked guardian's registered phone number.
- **REQ-SMS-02** — SMS sending is role-gated: only a DEPENDENT sends the panic
  SMS. A GUARDIAN's phone, on receiving the same panic, posts an in-app
  notification and does NOT send an SMS to itself.
- **REQ-SMS-03** — The guardian's recipient phone is the dependent's emergency
  contact designated as guardian (the `isEmergency` contact, or a contact
  whose relationship is "Guardian").
- **REQ-SMS-04** — SMS body format:
  `[GuardBand Alert] EMERGENCY: Panic button activated! Track live location here: https://guardband.app/track/<SESSION_ID> (Lat: {lat}, Lng: {lng})`.
- **REQ-SMS-05** — A live-tracking session is created on panic: a session token
  stored at `devices/{deviceId}/sessions/{panicSeq}` so the tracking link can
  be resolved. Continuous GPS updates come from the band's existing
  `TRACKING_UPDATE` stream into `devices/{id}/latest`; no new app-side write is
  required for live coordinates.
- **REQ-SMS-06** — The app adds the `SEND_SMS` permission (release manifest)
  and requests it at runtime with a clear rationale, because it is a dangerous
  permission.
- **REQ-SMS-07** — If the guardian's phone number is unavailable, the SMS is
  skipped and the failure is logged without blocking the in-app alert.
- **REQ-SMS-08** — The Privacy Notice is updated in the same change to
  disclose that panic alerts send an SMS containing the dependent's
  coordinates and a tracking link from the dependent's phone.
- **REQ-SMS-09** — Known limitation (flagged for review): SMS delivery depends
  on the dependent's phone running the alert watch and having signal. The more
  reliable band-SMS path remains a future option.

### TL — History timeline & resolution status

- **REQ-TL-01** — Alert history groups rows under three headings: "Today",
  "Yesterday", "Older". Today and Yesterday already exist; "Older" collapses
  every earlier day into a single heading.
- **REQ-TL-02** — Each history item renders: Alert Type (Panic / Low Battery /
  Check-in), Timestamp, Coordinates or Location name, and Resolution Status
  (Active / Acknowledged / Resolved).
- **REQ-TL-03** — Resolution status is a new field. Existing history with no
  stored status defaults to "Active" so all rows still render.
- **REQ-TL-04** — A GUARDIAN can Acknowledge (Active → Acknowledged) and
  Resolve (→ Resolved) an alert. Dependents do not change status.
- **REQ-TL-05** — Status is persisted at a new node
  `devices/{deviceId}/status/{seq}` (guardian-writable in the app layer; both
  roles readable) so the band's append-only `history` is not mutated. Without
  pairing, Security Rules can only enforce `auth != null`; guardian-only
  enforcement is app-layer until pairing lands. (Design decision, flagged for
  review.)
- **REQ-TL-06** — The status is shown as a chip on each history row; the
  existing filter/sort behavior of history is otherwise unchanged.

## Non-functional / constraints

- No blanket `$other:false` is added at `users/$uid` (it would break
  `deletion_pending` and thin provisioning). New `role`/`phone` children get
  their own named rules.
- No new location permission is added to the release manifest. The phone is
  not a GPS source; the band is. Location stays debug-only for the mock sender.
- Existing behavior preserved: location tracking, device markers, map
  interactions, multi-device selection, the atomic sign-up write, the
  alert-watch foreground service, MapTiler layers, and all existing unit tests.
