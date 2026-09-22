# Manual Testing Guide for Security Rules

## Prerequisites

Make sure the Security Rules are published in Firebase Console:
1. Go to: https://console.firebase.google.com/project/guardband-aae65/database
2. Click "Rules" tab
3. Verify the rules are published

---

## Test 1: Valid Alert (Should Succeed - 200)

### PowerShell Command:
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

### curl Command (if available):
```bash
curl -X PUT "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" \
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

### Expected Result:
✅ **Success (200 OK)**
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

---

## Test 2: Missing Required Field (Should Fail - 403)

### PowerShell Command:
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

### curl Command:
```bash
curl -X PUT "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" \
  -H "Content-Type: application/json" \
  -d '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "PANIC",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false}
  }'
```

### Expected Result:
❌ **Permission Denied**
```json
{
  "error": "Permission denied"
}
```

**Note:** PowerShell will show this as an error. That's expected!

---

## Test 3: Invalid Alert Type (Should Fail - 403)

### PowerShell Command:
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

### curl Command:
```bash
curl -X PUT "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" \
  -H "Content-Type: application/json" \
  -d '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "INVALID_TYPE",
    "timestamp": "2026-09-09T12:00:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 74, "isCharging": false},
    "sequenceId": 1
  }'
```

### Expected Result:
❌ **Permission Denied**
```json
{
  "error": "Permission denied"
}
```

---

## Test 4: Battery Percent Out of Range (Should Fail - 403)

### PowerShell Command:
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

### Expected Result:
❌ **Permission Denied**

---

## Test 5: Write to History (Should Succeed - 200)

### PowerShell Command:
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/history/1.json" `
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

### Expected Result:
✅ **Success (200 OK)**

---

## Verify in Firebase Console

After successful tests, verify the data:

1. Go to: https://console.firebase.google.com/project/guardband-aae65/database
2. Click "Data" tab
3. You should see:
   ```
   /devices
     /guardband-001
       /latest
         schemaVersion: "1.0"
         deviceId: "guardband-001"
         type: "PANIC"
         timestamp: "2026-09-09T12:00:00Z"
         location: {...}
         battery: {...}
         sequenceId: 1
       /history
         /1
           {...same data...}
   ```

---

## Test All 4 Alert Types

### CHECKIN:
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "CHECKIN",
    "timestamp": "2026-09-09T12:05:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 73, "isCharging": false},
    "sequenceId": 2
  }'
```

### LOW_BATTERY:
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "LOW_BATTERY",
    "timestamp": "2026-09-09T12:10:00Z",
    "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
    "battery": {"percent": 15, "isCharging": false},
    "sequenceId": 3
  }'
```

### TRACKING_UPDATE:
```powershell
Invoke-RestMethod -Method Put `
  -Uri "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app/devices/guardband-001/latest.json" `
  -ContentType "application/json" `
  -Body '{
    "schemaVersion": "1.0",
    "deviceId": "guardband-001",
    "type": "TRACKING_UPDATE",
    "timestamp": "2026-09-09T12:15:00Z",
    "location": {"lat": 10.3160, "lng": 123.8860, "accuracyMeters": 12.0},
    "battery": {"percent": 14, "isCharging": true},
    "sequenceId": 4
  }'
```

All should succeed (200 OK).

---

## Quick Test Checklist

- [ ] Test 1: Valid PANIC alert → ✅ Success (200)
- [ ] Test 2: Missing sequenceId → ❌ Permission denied (403)
- [ ] Test 3: Invalid type → ❌ Permission denied (403)
- [ ] Test 4: Battery out of range → ❌ Permission denied (403)
- [ ] Test 5: Write to history/1 → ✅ Success (200)
- [ ] Verify data in Firebase Console Data tab
- [ ] Verify rules in Firebase Console Rules tab

---

## Troubleshooting

**Problem:** All writes succeed (even invalid ones)  
**Solution:** Rules not deployed. Go to Firebase Console → Rules tab → Click "Publish"

**Problem:** All writes fail (even valid ones)  
**Solution:** 
- Check `.json` suffix on URL
- Verify all required fields present
- Wait 10-20 seconds after publishing rules

**Problem:** Can't determine success/failure in PowerShell  
**Solution:** 
- Success: Returns the JSON data
- Failure: Shows red error message with "Permission denied"

---

## Summary

✅ **Valid alerts with all required fields** → Should succeed  
❌ **Missing required fields** → Should be rejected  
❌ **Invalid alert type** → Should be rejected  
❌ **Battery percent < 0 or > 100** → Should be rejected  

The Security Rules enforce the same validation that the Cloud Function would have, but work on the free Spark plan!
