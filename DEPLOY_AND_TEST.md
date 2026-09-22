# Deploy and Test Security Rules - Quick Guide

## What Changed?

**Old Approach:** Cloud Function (requires Blaze plan 💰)  
**New Approach:** Direct Database + Security Rules (Spark free tier ✅)

## Deploy Security Rules

### One Command:
```powershell
firebase deploy --only database
```

**Expected output:**
```
✔ Deploy complete!

Project Console: https://console.firebase.google.com/project/guardband-aae65/overview
```

## Test the Rules

### Option 1: Automated Test (Recommended)
```powershell
.\test-database-rules.ps1
```

This runs 14 tests automatically:
- 8 valid writes (4 alert types × 2 paths)
- 6 invalid writes (should be rejected)

### Option 2: Manual Test

**Valid write (should succeed):**
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

**Invalid write (should fail):**
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "INVALID_TYPE",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

## Verify in Firebase Console

1. Open: https://console.firebase.google.com/project/guardband-aae65/database
2. Check **Data tab**: Should see `/devices/guardband-001/latest` and `/history/`
3. Check **Rules tab**: Should see the deployed Security Rules

## What the Rules Validate

✅ All required fields present  
✅ Correct data types (string, number, boolean)  
✅ Alert type is one of: PANIC, CHECKIN, LOW_BATTERY, TRACKING_UPDATE  
✅ Battery percent is 0-100  
✅ DeviceId in payload matches URL path  
✅ SequenceId matches URL path (for history writes)  

## For Your Sender App

Update the endpoint URLs to:

**Write to /latest:**
```
PUT https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json
```

**Write to /history:**
```
PUT https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/history/{sequenceId}.json
```

**Important:** 
- Use PUT method (not POST)
- Add `.json` suffix to URLs
- Make TWO requests per alert (one for latest, one for history)

## Complete Command Sequence

```powershell
# 1. Make sure you're logged in to Firebase
firebase login

# 2. Deploy the rules
cd d:\AndroidStudioProjects\GuardBand
firebase deploy --only database

# 3. Run automated tests
.\test-database-rules.ps1

# 4. Check Firebase Console
# Open: https://console.firebase.google.com/project/guardband-aae65/database
```

## Troubleshooting

**"firebase: command not found"**  
→ Install Firebase CLI: `npm install -g firebase-tools`

**Rules not taking effect**  
→ Wait 10-20 seconds after deploy, then retry
→ Check Rules tab in Firebase Console to confirm they're there

**All writes getting rejected**  
→ Verify `.json` suffix on URL
→ Check all required fields are present
→ Verify deviceId in payload matches URL path

**Need to debug a specific rule**  
→ Use Rules Playground in Firebase Console
→ Test each validation one at a time

## Summary

✅ **Status:** Security Rules created  
✅ **Command:** `firebase deploy --only database`  
✅ **Test:** `.\test-database-rules.ps1`  
✅ **Benefit:** 100% free tier compatible, no Cloud Function needed  

Full details: See `DIRECT_DATABASE_APPROACH.md`
