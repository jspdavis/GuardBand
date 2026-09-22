# GuardBand Cloud Function Deployment Guide

## Prerequisites Installation

### 1. Install Node.js (Required)
1. Download Node.js 18.x LTS from: https://nodejs.org/
2. Run the installer and follow the prompts
3. Verify installation by opening a new PowerShell window and running:
   ```
   node --version
   npm --version
   ```
   Both should display version numbers

### 2. Install Firebase CLI (Required)
After Node.js is installed, run:
```powershell
npm install -g firebase-tools
```

Verify installation:
```powershell
firebase --version
```

### 3. Login to Firebase
```powershell
firebase login
```
This will open a browser window for authentication. Use the Google account that has access to the guardband-aae65 project.

## Deployment Steps

### 1. Navigate to the functions directory
```powershell
cd d:\AndroidStudioProjects\GuardBand\functions
```

### 2. Install dependencies
```powershell
npm install
```

### 3. Deploy the function
From the GuardBand root directory:
```powershell
cd d:\AndroidStudioProjects\GuardBand
firebase deploy --only functions:ingestAlert
```

**Expected output:**
- Function will be deployed to: `asia-southeast1` region
- You'll receive a URL like: `https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert`

### 4. Note the deployed URL
Copy the function URL from the deployment output. You'll need it for testing.

## Testing the Function

### Test 1: Valid PANIC Alert
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"PANIC\",
    \"timestamp\": \"2026-09-09T12:00:00Z\",
    \"location\": { \"lat\": 10.3157, \"lng\": 123.8854, \"accuracyMeters\": 8.5 },
    \"battery\": { \"percent\": 74, \"isCharging\": false },
    \"sequenceId\": 1
  }'
```

### Test 2: Valid CHECKIN Alert
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"CHECKIN\",
    \"timestamp\": \"2026-09-09T12:05:00Z\",
    \"location\": { \"lat\": 10.3157, \"lng\": 123.8854, \"accuracyMeters\": 8.5 },
    \"battery\": { \"percent\": 73, \"isCharging\": false },
    \"sequenceId\": 2
  }'
```

### Test 3: Valid LOW_BATTERY Alert
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"LOW_BATTERY\",
    \"timestamp\": \"2026-09-09T12:10:00Z\",
    \"location\": { \"lat\": 10.3157, \"lng\": 123.8854, \"accuracyMeters\": 8.5 },
    \"battery\": { \"percent\": 15, \"isCharging\": false },
    \"sequenceId\": 3
  }'
```

### Test 4: Valid TRACKING_UPDATE Alert
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"TRACKING_UPDATE\",
    \"timestamp\": \"2026-09-09T12:15:00Z\",
    \"location\": { \"lat\": 10.3160, \"lng\": 123.8860, \"accuracyMeters\": 12.0 },
    \"battery\": { \"percent\": 14, \"isCharging\": true },
    \"sequenceId\": 4
  }'
```

### Test 5: Invalid Alert - Missing Field
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"PANIC\",
    \"timestamp\": \"2026-09-09T12:20:00Z\",
    \"location\": { \"lat\": 10.3157, \"lng\": 123.8854, \"accuracyMeters\": 8.5 },
    \"battery\": { \"percent\": 74, \"isCharging\": false }
  }'
```
**Expected:** 400 error with message about missing `sequenceId`

### Test 6: Invalid Alert - Bad Type
```powershell
curl -X POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert `
  -H "Content-Type: application/json" `
  -d '{
    \"schemaVersion\": \"1.0\",
    \"deviceId\": \"guardband-001\",
    \"type\": \"INVALID_TYPE\",
    \"timestamp\": \"2026-09-09T12:25:00Z\",
    \"location\": { \"lat\": 10.3157, \"lng\": 123.8854, \"accuracyMeters\": 8.5 },
    \"battery\": { \"percent\": 74, \"isCharging\": false },
    \"sequenceId\": 5
  }'
```
**Expected:** 400 error with message about invalid alert type

### Test 7: Invalid Method - GET Request
```powershell
curl -X GET https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert
```
**Expected:** 405 Method Not Allowed

## Verify in Firebase Console

After successful tests, verify the data in Firebase Console:

1. Go to: https://console.firebase.google.com/project/guardband-aae65/database
2. Navigate to Realtime Database
3. Check the structure:
   ```
   /devices
     /guardband-001
       /latest         <- Should contain the most recent alert (sequenceId: 4)
       /history
         /1            <- PANIC alert
         /2            <- CHECKIN alert
         /3            <- LOW_BATTERY alert
         /4            <- TRACKING_UPDATE alert
   ```

## Troubleshooting

### Issue: "Permission denied" when deploying
**Solution:** Run `firebase login` again and ensure you're using the correct Google account

### Issue: Function times out
**Solution:** Check Firebase Console logs at https://console.firebase.google.com/project/guardband-aae65/functions/logs

### Issue: Database writes fail
**Solution:** 
1. Verify database URL in functions/index.js matches your project
2. Check database rules allow writes (they currently require auth, but the function uses Admin SDK which bypasses rules)

### Issue: curl not found on Windows
**Solution:** Use PowerShell's `Invoke-RestMethod` instead:
```powershell
Invoke-RestMethod -Method Post -Uri "https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert" `
  -ContentType "application/json" `
  -Body '{\"schemaVersion\":\"1.0\",\"deviceId\":\"guardband-001\",\"type\":\"PANIC\",\"timestamp\":\"2026-09-09T12:00:00Z\",\"location\":{\"lat\":10.3157,\"lng\":123.8854,\"accuracyMeters\":8.5},\"battery\":{\"percent\":74,\"isCharging\":false},\"sequenceId\":1}'
```

## Next Steps

Once the function is deployed and tested:

1. Update the mock sender app with the real function URL
2. Test end-to-end with the sender app on a separate device/emulator
3. Monitor function logs for any issues: `firebase functions:log`
4. Consider setting up Firebase Analytics to track alert volumes

## Security Note

Currently, the function has NO authentication. Anyone with the URL can send alerts. This is intentional per project requirements (ESP32 with AT commands cannot do complex auth). For production:

- Consider IP whitelisting if device IPs are static
- Add a simple shared secret in headers
- Or implement device-specific tokens validated server-side

Flag this for team discussion when moving beyond proof-of-concept.
