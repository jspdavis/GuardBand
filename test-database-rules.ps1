# GuardBand Database Security Rules Test Script
# Tests direct database writes with Security Rules validation

param(
    [string]$BaseUrl = "https://guardband-aae65-default-rtdb.asia-southeast1.firebasedatabase.app",
    [string]$DeviceId = "guardband-001"
)

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "GuardBand Security Rules Test Suite" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Database: $BaseUrl" -ForegroundColor Gray
Write-Host "Device ID: $DeviceId" -ForegroundColor Gray
Write-Host ""

$testsPassed = 0
$testsFailed = 0

function Test-DatabaseWrite {
    param(
        [string]$TestName,
        [string]$Path,
        [string]$Payload,
        [bool]$ShouldSucceed
    )
    
    Write-Host "Test: $TestName" -ForegroundColor Yellow
    Write-Host "  Path: $Path" -ForegroundColor Gray
    
    $fullUrl = "$BaseUrl$Path"
    
    try {
        $response = Invoke-WebRequest -Method Put -Uri $fullUrl `
            -ContentType "application/json" `
            -Body $Payload `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($ShouldSucceed) {
            Write-Host "  ✓ PASSED - Write succeeded (200)" -ForegroundColor Green
            Write-Host "  Response: $($response.Content)" -ForegroundColor Gray
            $script:testsPassed++
        } else {
            Write-Host "  ✗ FAILED - Write should have been rejected but succeeded" -ForegroundColor Red
            Write-Host "  Response: $($response.Content)" -ForegroundColor Gray
            $script:testsFailed++
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        
        if (-not $ShouldSucceed) {
            if ($statusCode -eq 401 -or $statusCode -eq 403 -or $statusCode -eq 400) {
                Write-Host "  ✓ PASSED - Write rejected ($statusCode)" -ForegroundColor Green
                try {
                    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                    $responseBody = $reader.ReadToEnd()
                    Write-Host "  Response: $responseBody" -ForegroundColor Gray
                } catch {}
                $script:testsPassed++
            } else {
                Write-Host "  ✗ FAILED - Expected rejection (401/403) but got $statusCode" -ForegroundColor Red
                Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
                $script:testsFailed++
            }
        } else {
            Write-Host "  ✗ FAILED - Write should have succeeded but was rejected ($statusCode)" -ForegroundColor Red
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                Write-Host "  Response: $responseBody" -ForegroundColor Gray
            } catch {}
            $script:testsFailed++
        }
    }
    
    Write-Host ""
    Start-Sleep -Milliseconds 500
}

# Valid payloads for each alert type
$validPanic = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 1
}
"@

$validCheckin = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "CHECKIN",
  "timestamp": "2026-09-09T12:05:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 73, "isCharging": false},
  "sequenceId": 2
}
"@

$validLowBattery = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "LOW_BATTERY",
  "timestamp": "2026-09-09T12:10:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 15, "isCharging": false},
  "sequenceId": 3
}
"@

$validTracking = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "TRACKING_UPDATE",
  "timestamp": "2026-09-09T12:15:00Z",
  "location": {"lat": 10.3160, "lng": 123.8860, "accuracyMeters": 12.0},
  "battery": {"percent": 14, "isCharging": true},
  "sequenceId": 4
}
"@

# Invalid payloads
$missingSequenceId = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:20:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false}
}
"@

$invalidType = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "INVALID_TYPE",
  "timestamp": "2026-09-09T12:25:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 5
}
"@

$batteryOutOfRange = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:30:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 150, "isCharging": false},
  "sequenceId": 6
}
"@

$deviceIdMismatch = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-999",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:35:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 7
}
"@

$missingLocation = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:40:00Z",
  "battery": {"percent": 74, "isCharging": false},
  "sequenceId": 8
}
"@

$missingBattery = @"
{
  "schemaVersion": "1.0",
  "deviceId": "$DeviceId",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:45:00Z",
  "location": {"lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5},
  "sequenceId": 9
}
"@

# Run tests
Write-Host "===== VALID WRITES (Should Succeed) =====" -ForegroundColor Cyan
Write-Host ""

Test-DatabaseWrite "Valid PANIC to /latest" "/devices/$DeviceId/latest.json" $validPanic $true
Test-DatabaseWrite "Valid PANIC to /history/1" "/devices/$DeviceId/history/1.json" $validPanic $true

Test-DatabaseWrite "Valid CHECKIN to /latest" "/devices/$DeviceId/latest.json" $validCheckin $true
Test-DatabaseWrite "Valid CHECKIN to /history/2" "/devices/$DeviceId/history/2.json" $validCheckin $true

Test-DatabaseWrite "Valid LOW_BATTERY to /latest" "/devices/$DeviceId/latest.json" $validLowBattery $true
Test-DatabaseWrite "Valid LOW_BATTERY to /history/3" "/devices/$DeviceId/history/3.json" $validLowBattery $true

Test-DatabaseWrite "Valid TRACKING_UPDATE to /latest" "/devices/$DeviceId/latest.json" $validTracking $true
Test-DatabaseWrite "Valid TRACKING_UPDATE to /history/4" "/devices/$DeviceId/history/4.json" $validTracking $true

Write-Host "===== INVALID WRITES (Should Be Rejected) =====" -ForegroundColor Cyan
Write-Host ""

Test-DatabaseWrite "Missing sequenceId" "/devices/$DeviceId/latest.json" $missingSequenceId $false
Test-DatabaseWrite "Invalid alert type" "/devices/$DeviceId/latest.json" $invalidType $false
Test-DatabaseWrite "Battery percent out of range" "/devices/$DeviceId/latest.json" $batteryOutOfRange $false
Test-DatabaseWrite "DeviceId mismatch" "/devices/$DeviceId/latest.json" $deviceIdMismatch $false
Test-DatabaseWrite "Missing location object" "/devices/$DeviceId/latest.json" $missingLocation $false
Test-DatabaseWrite "Missing battery object" "/devices/$DeviceId/latest.json" $missingBattery $false

# Summary
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red
Write-Host ""

if ($testsFailed -eq 0) {
    Write-Host "✓ All tests passed! Security Rules are working correctly." -ForegroundColor Green
    Write-Host ""
    Write-Host "Verify the data in Firebase Console:" -ForegroundColor Gray
    Write-Host "  https://console.firebase.google.com/project/guardband-aae65/database" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Expected structure:" -ForegroundColor Gray
    Write-Host "  /devices/$DeviceId/latest (sequenceId: 4, type: TRACKING_UPDATE)" -ForegroundColor Gray
    Write-Host "  /devices/$DeviceId/history/1 (PANIC)" -ForegroundColor Gray
    Write-Host "  /devices/$DeviceId/history/2 (CHECKIN)" -ForegroundColor Gray
    Write-Host "  /devices/$DeviceId/history/3 (LOW_BATTERY)" -ForegroundColor Gray
    Write-Host "  /devices/$DeviceId/history/4 (TRACKING_UPDATE)" -ForegroundColor Gray
} else {
    Write-Host "✗ Some tests failed. Check the output above for details." -ForegroundColor Red
    Write-Host ""
    Write-Host "Common issues:" -ForegroundColor Yellow
    Write-Host "  - Rules not deployed: Run 'firebase deploy --only database'" -ForegroundColor Gray
    Write-Host "  - Rules syntax error: Check Firebase Console Rules tab" -ForegroundColor Gray
    Write-Host "  - Network issues: Check internet connection" -ForegroundColor Gray
}
