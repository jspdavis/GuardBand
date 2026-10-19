# GuardBand Alert Payload Schema — v1.0

Single source of truth for the sender ↔ receiver contract. Both AlertSender.kt
(sender) and ingestAlert (receiver, Cloud Function) must match this exactly.
If this changes, update both sides and this file together.

## Payload shape

{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T00:00:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 74, "isCharging": false },
  "sequenceId": 1
}

## Field rules

| Field | Type | Required | Notes |
|---|---|---|---|
| schemaVersion | string | yes | Always "1.0" for now |
| deviceId | string | yes | Currently hardcoded "guardband-001" |
| type | string enum | yes | One of: PANIC, CHECKIN, LOW_BATTERY, TRACKING_UPDATE |
| timestamp | string | yes | ISO 8601 UTC |
| location.lat | number | yes | |
| location.lng | number | yes | |
| location.accuracyMeters | number | yes | |
| battery.percent | number | yes | 0-100 |
| battery.isCharging | boolean | yes | |
| sequenceId | number | yes | Incrementing integer per device |

## Check-ins (FR-10)

A check-in is the wearer double-pressing the band's button within 2 seconds:
a quiet, non-emergency "I'm safe". It uses this same payload with
`"type": "CHECKIN"` and is written the same way as every other type, to
`/devices/{deviceId}/history/{sequenceId}` and `/devices/{deviceId}/latest`.
There is no separate check-in node or payload.

- **Band-originated only.** The band is the only thing that creates a
  check-in. The main app reads check-ins and never writes one; the debug-only
  mock sender writes them because it stands in for the band.
- **Not an emergency.** FR-10: a check-in does not trigger the emergency SMS.
  Receivers must not treat `CHECKIN` as an alarm.
- **How the app reads it.** The latest check-in is the `CHECKIN` with the
  highest `sequenceId` in the history window the app already reads (the last
  100 raw entries). `latest` alone cannot answer it, because every later
  payload, tracking updates included, overwrites that node.

## Receiver validation requirements

- Reject (400) if any required top-level field is missing: schemaVersion,
  deviceId, type, timestamp, battery, sequenceId
- Reject (400) if `type` is not one of the four valid enum values
- On success: write full payload to /devices/{deviceId}/latest (overwrite)
  AND /devices/{deviceId}/history/{sequenceId} (append)