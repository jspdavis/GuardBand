# GuardBand System Architecture

## System Overview

```
┌─────────────────────┐
│  ESP32 + 4G Module  │  (Future - not yet built)
│  Panic Button       │
└──────────┬──────────┘
           │ HTTPS POST
           │ (AT commands)
           ▼
┌─────────────────────┐
│   Mock Sender App   │  (Current - for testing)
│   (Android)         │
└──────────┬──────────┘
           │ HTTPS POST
           │ JSON payload
           │ matching SCHEMA.md
           ▼
┌─────────────────────────────────────────┐
│  Firebase Cloud Function                │
│  ingestAlert                            │
│  Region: asia-southeast1                │
│                                         │
│  1. Validate HTTP method (POST only)   │
│  2. Validate required fields           │
│  3. Validate alert type enum           │
│  4. Write to Realtime Database         │
│  5. Return success/error response      │
└──────────┬──────────────────────────────┘
           │
           │ Admin SDK write
           │ (bypasses security rules)
           ▼
┌─────────────────────────────────────────┐
│  Firebase Realtime Database             │
│  Region: asia-southeast1                │
│  URL: guardband-aae65-default-rtdb...   │
│                                         │
│  /devices/{deviceId}/                   │
│    ├── latest (overwrite)              │
│    └── history/{sequenceId} (append)   │
└──────────┬──────────────────────────────┘
           │
           │ Read via SDK
           │ (requires auth)
           ▼
┌─────────────────────┐
│  Companion App      │  (Existing Android app)
│  (Android)          │
│  - View alerts      │
│  - User profile     │
│  - Home screen      │
└─────────────────────┘
```

## Data Flow Detail

### 1. Alert Transmission

**Sender → Cloud Function**

```http
POST https://asia-southeast1-guardband-aae65.cloudfunctions.net/ingestAlert
Content-Type: application/json

{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {
    "lat": 10.3157,
    "lng": 123.8854,
    "accuracyMeters": 8.5
  },
  "battery": {
    "percent": 74,
    "isCharging": false
  },
  "sequenceId": 1
}
```

### 2. Validation Flow

```
┌─────────────────┐
│  Request In     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     NO      ┌─────────────────┐
│  Method=POST?   ├────────────►│  Return 405     │
└────────┬────────┘              └─────────────────┘
         │ YES
         ▼
┌─────────────────┐     NO      ┌─────────────────┐
│  All required   ├────────────►│  Return 400     │
│  fields present?│              │  + missing list │
└────────┬────────┘              └─────────────────┘
         │ YES
         ▼
┌─────────────────┐     NO      ┌─────────────────┐
│  Valid alert    ├────────────►│  Return 400     │
│  type enum?     │              │  + valid types  │
└────────┬────────┘              └─────────────────┘
         │ YES
         ▼
┌─────────────────┐   ERROR     ┌─────────────────┐
│  Write to DB    ├────────────►│  Return 500     │
│  (both paths)   │              │  + log error    │
└────────┬────────┘              └─────────────────┘
         │ SUCCESS
         ▼
┌─────────────────┐
│  Return 200     │
│  + confirmation │
└─────────────────┘
```

### 3. Database Write Pattern

**Atomic Multi-Path Update:**

```javascript
// Single database operation writes to both locations
{
  "/devices/guardband-001/latest": { full_payload },
  "/devices/guardband-001/history/1": { full_payload }
}
```

**Resulting Structure:**

```
/devices
  /guardband-001
    /latest
      schemaVersion: "1.0"
      deviceId: "guardband-001"
      type: "TRACKING_UPDATE"    ← Most recent type
      timestamp: "...12:15:00Z"   ← Most recent time
      location: {...}
      battery: {...}
      sequenceId: 4               ← Highest sequence
    /history
      /1
        type: "PANIC"
        timestamp: "...12:00:00Z"
        ...
      /2
        type: "CHECKIN"
        timestamp: "...12:05:00Z"
        ...
      /3
        type: "LOW_BATTERY"
        timestamp: "...12:10:00Z"
        ...
      /4
        type: "TRACKING_UPDATE"
        timestamp: "...12:15:00Z"
        ...
```

## Component Responsibilities

### Mock Sender App (Existing)
✅ **Already Built & Tested**
- Generates valid alert payloads
- Sends HTTPS POST requests
- Uses hardcoded deviceId: "guardband-001"
- Increments sequenceId per alert
- Currently points to placeholder URL (needs update)

### Cloud Function (This Task)
✅ **Just Built - Ready to Deploy**
- Receives HTTPS requests
- Validates payload structure
- Validates alert type enum
- Writes to Realtime Database
- Returns appropriate HTTP status codes
- Logs errors server-side

### Realtime Database (Pre-Existing)
✅ **Already Configured**
- Stores alerts in two locations
- `/latest` for quick access to current status
- `/history` for incident logging
- Accessible by companion app with auth

### Companion App (Existing)
✅ **Already Built**
- Reads alerts from database
- Displays alert history
- Shows user profiles
- Handles authentication
- No changes needed for this task

## Network Flow

```
Internet
   ↕
┌──────────────────────────────────────┐
│  Google Cloud Platform               │
│  asia-southeast1 region              │
│                                      │
│  ┌────────────────┐                 │
│  │ Cloud Function │                 │
│  │ ingestAlert    │                 │
│  └────────┬───────┘                 │
│           │                          │
│           │ <10ms latency            │
│           │ (same region)            │
│           ↓                          │
│  ┌────────────────┐                 │
│  │ Realtime DB    │                 │
│  │ guardband-...  │                 │
│  └────────────────┘                 │
│                                      │
└──────────────────────────────────────┘
```

## Security Model

### Current (PoC Phase)
```
Sender App ──► Cloud Function ──► Database
  ┌───┴───┐       ┌───┴───┐      ┌───┴───┐
  │ No    │       │ No    │      │ Admin │
  │ Auth  │       │ Auth  │      │ SDK   │
  └───────┘       └───────┘      └───────┘
                                 (bypasses
                                  rules)
```

**Implications:**
- Anyone with function URL can send alerts
- Acceptable for school project testing
- NOT suitable for production

### Future (Production Phase)
```
Device ──► Cloud Function ──► Database
  │            │                  │
  │ Device     │ Validate         │ Admin
  │ Token      │ Token            │ SDK
  │            │                  │
  └────────────┴──────────────────┘
       Authenticated Pipeline
```

**Options to Consider:**
1. **Shared Secret:** Custom header with pre-shared key
2. **Device Tokens:** Per-device credentials validated server-side
3. **IP Whitelist:** Restrict to known device IPs (if static)
4. **mTLS:** Device certificates for mutual authentication

## Alert Type Semantics

### PANIC
- **Trigger:** User presses panic button
- **Priority:** CRITICAL
- **Action:** Immediate notification to emergency contacts
- **Frequency:** Ad-hoc (emergency only)

### CHECKIN
- **Trigger:** Scheduled or manual check-in
- **Priority:** NORMAL
- **Action:** Update status as "safe"
- **Frequency:** Periodic (e.g., every 4 hours)

### LOW_BATTERY
- **Trigger:** Battery drops below threshold (e.g., 20%)
- **Priority:** WARNING
- **Action:** Notify user to charge device
- **Frequency:** Once per discharge cycle

### TRACKING_UPDATE
- **Trigger:** Location change or periodic update
- **Priority:** INFO
- **Action:** Update location history
- **Frequency:** Periodic (e.g., every 30 minutes)

## Scalability Considerations

### Current Capacity (Spark Plan)
- **Function Invocations:** 125,000/month free
- **Database Operations:** 100,000/day free
- **Database Storage:** 1 GB free
- **Network Egress:** 10 GB/month free

### Estimated Usage (Single Device, Active Day)
- **PANIC:** 0-5 alerts (rare)
- **CHECKIN:** 6 alerts (every 4 hours)
- **LOW_BATTERY:** 0-1 alert
- **TRACKING_UPDATE:** 48 alerts (every 30 min)
- **Total:** ~60 alerts/day = 1,800/month

**Conclusion:** Comfortably within Spark limits for 50+ devices

### Scaling to Production (100 devices)
- **Monthly Invocations:** 180,000 (exceeds Spark)
- **Recommendation:** Upgrade to Blaze plan (~$5-10/month)
- **Database Storage:** ~10 MB/month (well within limits)
- **Optimization:** Implement retention policy (delete old history)

## Error Handling Strategy

### Client-Side (Sender App)
- **200 Response:** Consider alert delivered, increment sequenceId
- **400 Response:** Log validation error, don't retry (bad data)
- **405 Response:** Fatal error (wrong HTTP method), abort
- **500 Response:** Retry with exponential backoff (server issue)
- **Network Error:** Retry immediately, then exponential backoff

### Server-Side (Cloud Function)
- **Validation Errors:** Return 400 immediately, don't write
- **Database Errors:** Log error, return 500, let client retry
- **Malformed JSON:** Return 400 with parsing error details

### Monitoring (Future Enhancement)
- Alert on repeated 500 errors (database issues)
- Alert on unusual 400 rate (schema mismatch?)
- Track alert type distribution (detect anomalies)
- Monitor function execution time (detect slowdowns)

## Testing Strategy

### Unit Tests (Current)
✅ Automated via `test-function.ps1`:
1. Valid PANIC → 200
2. Valid CHECKIN → 200
3. Valid LOW_BATTERY → 200
4. Valid TRACKING_UPDATE → 200
5. Missing field → 400
6. Invalid type → 400
7. Wrong method → 405

### Integration Tests (Next Phase)
- Mock sender app → function → database verification
- Companion app reads alerts successfully
- Multiple devices writing simultaneously
- High-frequency alert bursts (stress test)

### End-to-End Tests (Final Phase)
- Real ESP32 device → cloud function → companion app
- Test all alert types from hardware
- Test GPS coordinates from real movement
- Test battery status from real device

## Deployment Checklist

- [ ] Node.js 18 installed
- [ ] Firebase CLI installed
- [ ] Authenticated to Firebase (`firebase login`)
- [ ] Dependencies installed (`npm install` in functions/)
- [ ] Function deployed (`firebase deploy --only functions:ingestAlert`)
- [ ] Deployment successful (URL received)
- [ ] All 7 tests passed (`test-function.ps1`)
- [ ] Data visible in Firebase Console
- [ ] Mock sender app updated with real URL
- [ ] End-to-end test successful
- [ ] URL shared with firmware developer (future)

## Maintenance Tasks

### Regular
- Monitor function logs for errors
- Check Spark plan usage limits
- Review alert frequency patterns
- Verify database storage growth

### Occasional
- Update Firebase Functions SDK when new version releases
- Review and update database retention policy
- Analyze alert patterns for anomalies
- Performance optimization if latency increases

### Before Production
- Implement authentication layer
- Set up monitoring and alerting
- Load test with expected production volume
- Document runbook for common issues
- Train support team on troubleshooting

---

**Document Version:** 1.0  
**Last Updated:** September 9, 2026  
**Status:** Ready for Deployment
