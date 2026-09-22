# GuardBand Direct Database Write Approach

## Architecture Change: Why This Is Better

### ❌ Original Plan: Cloud Function
- Requires Blaze (paid) plan
- Adds latency (device → function → database)
- More complex deployment
- Extra moving part to maintain

### ✅ New Plan: Direct Database Writes + Security Rules
- **100% Spark (free) plan compatible** 🎉
- Lower latency (device → database directly)
- Simpler architecture
- Validation enforced by Firebase Security Rules
- REST API works with simple HTTPS PUT (perfect for ESP32 AT commands)

---

## How It Works

```
┌─────────────────────┐
│  Mock Sender App    │
│  (or ESP32 device)  │
└──────────┬──────────┘
           │
           │ HTTPS PUT (no auth needed)
           │ Direct to database REST API
           ▼
┌─────────────────────────────────────────┐
│  Firebase Realtime Database             │
│  Region: asia-southeast1                │
│                                         │
│  Security Rules validate:               │
│  ✓ All required fields present         │
│  ✓ Correct data types                  │
│  ✓ Valid alert type enum               │
│  ✓ Battery percent 0-100               │
│  ✓ DeviceId matches path               │
│  ✓ SequenceId matches path (history)   │
│                                         │
│  ❌ Rejects invalid data with 403       │
└──────────┬──────────────────────────────┘
           │
           │ Read with Firebase SDK
           ▼
┌─────────────────────┐
│  Companion App      │
│  (Android)          │
└─────────────────────┘
```

---

## REST API Endpoints

### Base URL
```
https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app
```

### Write to /latest (Overwrite)
```http
PUT /devices/{deviceId}/latest.json
Content-Type: application/json

{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 1
}
```

### Write to /history (Append)
```http
PUT /devices/{deviceId}/history/{sequenceId}.json
Content-Type: application/json

{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 1
}
```

**Note:** Add `.json` suffix to all REST API URLs - this is required by Firebase RTDB REST API.

---

## Security Rules Validation

The deployed rules validate:

### Required Fields
- ✅ `schemaVersion` must be "1.0"
- ✅ `deviceId` must match the path `$deviceId`
- ✅ `type` must be one of: PANIC, CHECKIN, LOW_BATTERY, TRACKING_UPDATE
- ✅ `timestamp` must be a string
- ✅ `location` must have: lat, lng, accuracyMeters (all numbers)
- ✅ `battery` must have: percent (number, 0-100), isCharging (boolean)
- ✅ `sequenceId` must be a number
- ✅ For history path: `sequenceId` must match path `$sequenceId`
- ✅ For history path: Cannot overwrite existing entries (`!data.exists()`)

### What Happens on Validation Failure
- Returns HTTP 401 Unauthorized or 403 Forbidden (depending on the error)
- Response body contains: `{"error": "Permission denied"}`
- No data is written to the database

---

## Deployment

### 1. Deploy the Security Rules
```powershell
cd d:\AndroidStudioProjects\GuardBand
firebase deploy --only database
```

### 2. Verify in Firebase Console
1. Go to: https://console.firebase.google.com/project/guardband-aae65/database
2. Click "Rules" tab
3. Confirm the rules are visible

---

## Testing

### Valid Alert Test (Should Succeed - 200 OK)

**PowerShell:**
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

**curl (if available):**
```bash
curl -X PUT \
  "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" \
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

**Expected Response:**
```json
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 1
}
```

### Invalid Tests (Should Fail - 401/403)

#### Test 1: Missing Required Field (sequenceId)
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
    "battery": {"percent": 74, "isCharging": false}
  }'
```
**Expected:** Error with "Permission denied"

#### Test 2: Invalid Alert Type
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
**Expected:** Error with "Permission denied"

#### Test 3: Battery Percent Out of Range
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
    "battery": {"percent": 150, "isCharging": false},
    "sequenceId": 1
  }'
```
**Expected:** Error with "Permission denied"

#### Test 4: DeviceId Mismatch
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-999",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```
**Expected:** Error with "Permission denied"

---

## Integration with Sender App

Your mock sender app needs to make **TWO** PUT requests per alert:

### Request 1: Update /latest
```http
PUT https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json
Content-Type: application/json

{full payload}
```

### Request 2: Append to /history
```http
PUT https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/history/{sequenceId}.json
Content-Type: application/json

{full payload}
```

**Important:** Use `.json` suffix on both URLs.

---

## ESP32 AT Commands Integration

When the hardware is ready, the ESP32 can write directly using AT commands:

```
// Write to /latest
AT+HTTPPARA="URL","https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json"
AT+HTTPPARA="CONTENT","application/json"
AT+HTTPDATA=<length>,10000
{send JSON payload}
AT+HTTPACTION=3  // 3 = PUT method

// Write to /history/{sequenceId}
AT+HTTPPARA="URL","https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/history/1.json"
AT+HTTPPARA="CONTENT","application/json"
AT+HTTPDATA=<length>,10000
{send JSON payload}
AT+HTTPACTION=3
```

---

## Advantages Over Cloud Function

| Aspect | Cloud Function | Direct Database |
|--------|---------------|-----------------|
| Firebase Plan | Blaze (paid) required | Spark (free) ✅ |
| Latency | 2 hops (device→func→db) | 1 hop (device→db) ✅ |
| Validation | JavaScript code | Security Rules ✅ |
| Complexity | Deploy function + rules | Deploy rules only ✅ |
| Debugging | Function logs + rules | Rules simulator ✅ |
| ESP32 Compatibility | Perfect (HTTPS POST) | Perfect (HTTPS PUT) ✅ |
| Cost at Scale | Invocation costs | Free tier: 1GB stored, 10GB/month bandwidth ✅ |

---

## Security Rules Testing in Console

Firebase provides a Rules Playground for testing:

1. Go to: https://console.firebase.google.com/project/guardband-aae65/database
2. Click "Rules" tab
3. Click "Rules Playground" button
4. Select "Realtime Database"
5. Test various paths and data

**Example test:**
- **Location:** `/devices/guardband-001/latest`
- **Type:** Write
- **Data:**
```json
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 1
}
```

Should show: ✅ **Allowed**

---

## Limitations & Trade-offs

### ✅ Advantages
- Free tier compatible
- Simpler architecture
- Lower latency
- Less code to maintain

### ⚠️ Considerations
- **No custom error messages** - Security Rules can only allow/deny, they can't return "missing field X" like the Cloud Function could
- **Client makes 2 requests** - Function could write both locations in one request, now sender must write latest + history separately
- **Rules are read-only for debugging** - Can't add console.log() like in functions

### 💡 Mitigation
- For debugging: Use Firebase Rules Playground to test scenarios
- For error messages: Client-side validation before sending (optional)
- For double-writes: Accept the trade-off (still faster overall than function route)

---

## Database Structure (Same as Before)

```
/devices
  /guardband-001
    /latest
      {
        schemaVersion: "1.0",
        deviceId: "guardband-001",
        type: "TRACKING_UPDATE",
        timestamp: "2026-09-09T12:15:00Z",
        location: {...},
        battery: {...},
        sequenceId: 4
      }
    /history
      /1
        {...PANIC alert...}
      /2
        {...CHECKIN alert...}
      /3
        {...LOW_BATTERY alert...}
      /4
        {...TRACKING_UPDATE alert...}
```

---

## Migration from Cloud Function Approach

If you already have the Cloud Function code:

1. ✅ Keep `functions/index.js` as reference for validation logic
2. ❌ Don't deploy the function: `firebase deploy --only database` (not functions)
3. ✅ Update sender app to use REST API instead of function URL
4. ✅ Test with the provided test commands
5. ✅ Remove functions from `.firebaserc` if you want (optional)

---

## Support & Troubleshooting

### Rules not working?
→ Run `firebase deploy --only database` again
→ Check Rules tab in Firebase Console
→ Use Rules Playground to test specific scenarios

### Getting 401/403 on valid data?
→ Check all required fields are present
→ Verify `.json` suffix on URL
→ Ensure deviceId in payload matches URL path
→ For history: ensure sequenceId in payload matches URL path

### Need to debug rules?
→ Use Rules Playground in Firebase Console
→ Test each field validation individually
→ Start with minimal payload and add fields one by one

---

## Summary

**Status:** Rules created and ready to deploy  
**Command:** `firebase deploy --only database`  
**Test:** Use provided PowerShell commands  
**Benefit:** 100% free tier compatible, simpler architecture  

This approach is better suited for a school project prototype that needs to stay within Spark plan limits while still enforcing the same validation that the Cloud Function would have provided.
