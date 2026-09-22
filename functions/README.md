# GuardBand Cloud Functions

Firebase Cloud Functions for the GuardBand IoT panic button system.

## Functions

### `ingestAlert`

HTTPS endpoint that receives alert payloads from GuardBand devices (or mock sender app) and writes them to Firebase Realtime Database.

**Region:** `asia-southeast1` (matches database region for minimal latency)

**URL:** `https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert`

**Method:** POST only (405 on GET/PUT/DELETE/etc.)

**Request Body:** JSON matching SCHEMA.md

**Responses:**
- `200` - Alert successfully ingested
- `400` - Validation error (missing fields or invalid type)
- `405` - Method not allowed (non-POST)
- `500` - Database write failure

**Database Structure:**
```
/devices
  /{deviceId}
    /latest          <- Most recent alert (overwritten)
    /history
      /{sequenceId}  <- Historical alerts (append-only)
```

## Schema Validation

The function validates:
1. All required top-level fields present: `schemaVersion`, `deviceId`, `type`, `timestamp`, `battery`, `sequenceId`
2. Nested battery fields: `percent`, `isCharging`
3. Alert type is one of: `PANIC`, `CHECKIN`, `LOW_BATTERY`, `TRACKING_UPDATE`
4. If location is present, validates `lat`, `lng`, `accuracyMeters`

Note: The SCHEMA.md marks location as required, but the function currently allows it to be optional to handle edge cases where GPS may be unavailable. This decision is flagged in the deployment report.

## Development

### Local Testing
```bash
npm run serve
```

### Deploy
```bash
firebase deploy --only functions:ingestAlert
```

### View Logs
```bash
firebase functions:log
```

## Security

⚠️ **No authentication currently implemented** - intentional for PoC phase. The ESP32 with 4G module will use AT commands which cannot handle complex auth handshakes. Future security options:

- Shared secret in custom header
- Device-specific tokens
- IP whitelisting (if static IPs available)
- VPN tunnel for device communication

Discuss with team before production deployment.

## Dependencies

- `firebase-admin` ^12.0.0 - Firebase Admin SDK for database access
- `firebase-functions` ^4.5.0 - Cloud Functions SDK
- Node.js 18 LTS

## Spark Plan Compatibility

This function is designed to work within Firebase Spark (free tier) limits:
- Outbound networking: Uses Firebase services only (no external API calls)
- Invocations: Estimated low volume for school project
- Compute time: Minimal (simple validation + single database write)

If scaling beyond PoC, monitor usage in Firebase Console.
