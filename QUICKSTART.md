# GuardBand Cloud Function - Quick Start

## What Was Built

A Firebase Cloud Function (`ingestAlert`) that:
- Accepts HTTPS POST requests from GuardBand devices/sender app
- Validates alert payloads against SCHEMA.md
- Writes to Realtime Database in two locations: `/latest` and `/history/{sequenceId}`
- Returns appropriate HTTP status codes (200/400/405/500)

## Files Created

```
GuardBand/
├── firebase.json              # Firebase project config
├── .firebaserc                # Project ID mapping
├── database.rules.json        # Realtime Database security rules
├── DEPLOYMENT_GUIDE.md        # Full deployment instructions
├── test-function.ps1          # Automated test script
└── functions/
    ├── package.json           # Node.js dependencies
    ├── index.js               # Cloud Function code ⭐
    ├── .eslintrc.js          # Linting config
    ├── .gitignore            # Node modules exclusion
    └── README.md             # Functions documentation
```

## Deployment in 3 Steps

### 1. Install Prerequisites (One-Time Setup)

**Install Node.js 18:**
- Download from https://nodejs.org/
- Verify: `node --version` (should show v18.x.x)

**Install Firebase CLI:**
```powershell
npm install -g firebase-tools
firebase login
```

### 2. Install Dependencies

```powershell
cd d:\AndroidStudioProjects\GuardBand\functions
npm install
```

### 3. Deploy

```powershell
cd d:\AndroidStudioProjects\GuardBand
firebase deploy --only functions:ingestAlert
```

**Copy the URL from the output** - it will look like:
```
https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert
```

## Testing

### Option A: Use the Test Script (Recommended)
```powershell
cd d:\AndroidStudioProjects\GuardBand
.\test-function.ps1 -FunctionUrl "https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert"
```

This runs 7 tests automatically and shows pass/fail for each.

### Option B: Manual curl Test
```bash
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert \
  -H "Content-Type: application/json" \
  -d '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

### Option C: Update Mock Sender App
Replace the placeholder URL in your mock sender app with the deployed function URL and test end-to-end.

## Verify Data in Firebase

1. Go to Firebase Console: https://console.firebase.google.com/project/guardband-aae65/database
2. Select Realtime Database
3. Look for: `/devices/guardband-001/latest` and `/devices/guardband-001/history/`

## Key Implementation Details

### Region
Deployed to `asia-southeast1` to match your Realtime Database region (minimizes latency).

### Validation
- Rejects if required fields missing (returns 400 with list of missing fields)
- Rejects if `type` is not one of: `PANIC`, `CHECKIN`, `LOW_BATTERY`, `TRACKING_UPDATE`
- Rejects non-POST requests with 405

### Database Writes
Uses atomic multi-path update to write both locations simultaneously:
```javascript
updates[`/devices/${deviceId}/latest`] = payload;
updates[`/devices/${deviceId}/history/${sequenceId}`] = payload;
await db.ref().update(updates);
```

### Error Handling
- Returns 500 if database write fails
- Logs errors to Cloud Functions logs
- All responses are JSON (no plain text)

## Important Notes

### Location Field Decision
The SCHEMA.md marks `location` as required, but the requirements doc mentioned uncertainty about whether to strictly enforce it. The function currently:
- Does NOT reject if location is completely missing
- DOES validate location fields if location object is present

**Rationale:** GPS may be unavailable indoors or during signal loss. Better to accept an alert without location than reject a legitimate panic alert.

If you want strict location enforcement, change line 68 in `functions/index.js` from:
```javascript
if (payload.location) {
```
to:
```javascript
if (!payload.location) {
  missingFields.push("location");
} else if (payload.location) {
```

### No Authentication
Per requirements, no auth is implemented (ESP32 + AT commands cannot do OAuth/JWT). For production, consider:
- Shared secret in custom header
- Device certificates
- IP whitelisting

### Spark Plan Compatible
Function uses only Firebase services (no external APIs), so it stays within free tier limits.

## Troubleshooting

**"firebase: command not found"**
→ Install Firebase CLI: `npm install -g firebase-tools`

**"Permission denied" on deploy**
→ Run `firebase login` and use account with access to guardband-aae65

**Function returns 500**
→ Check logs: `firebase functions:log` or in Firebase Console

**Can't reach function URL**
→ Wait 1-2 minutes after deployment for propagation

## Next Steps

1. ✅ Deploy the function
2. ✅ Run the test script
3. ✅ Verify data in Firebase Console
4. 🔄 Update mock sender app with real URL
5. 🔄 Test end-to-end with sender app
6. 🔄 Share URL with firmware developer (when hardware is ready)

## Support

Check these files for more details:
- **DEPLOYMENT_GUIDE.md** - Full step-by-step deployment instructions
- **functions/README.md** - Function-specific documentation
- **SCHEMA.md** - Payload contract (source of truth)
