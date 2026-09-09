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

## Receiver validation requirements

- Reject (400) if any required top-level field is missing: schemaVersion,
  deviceId, type, timestamp, battery, sequenceId
- Reject (400) if `type` is not one of the four valid enum values
- On success: write full payload to /devices/{deviceId}/latest (overwrite)
  AND /devices/{deviceId}/history/{sequenceId} (append)