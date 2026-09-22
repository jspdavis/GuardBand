# GuardBand Cloud Function Test Script
# Run this after deploying the function to test all scenarios

param(
    [Parameter(Mandatory=$true)]
    [string]$FunctionUrl
)

Write-Host "Testing GuardBand ingestAlert Cloud Function" -ForegroundColor Cyan
Write-Host "Function URL: $FunctionUrl" -ForegroundColor Gray
Write-Host ""

$testsPassed = 0
$testsFailed = 0

function Test-Alert {
    param(
        [string]$TestName,
        [string]$Payload,
        [int]$ExpectedStatus,
        [string]$Method = "POST"
    )
    
    Write-Host "Test: $TestName" -ForegroundColor Yellow
    
    try {
        if ($Method -eq "POST") {
            $response = Invoke-WebRequest -Method Post -Uri $FunctionUrl `
                -ContentType "application/json" `
                -Body $Payload `
                -UseBasicParsing `
                -ErrorAction Stop
        } else {
            $response = Invoke-WebRequest -Method Get -Uri $FunctionUrl `
                -UseBasicParsing `
                -ErrorAction Stop
        }
        
        if ($response.StatusCode -eq $ExpectedStatus) {
            Write-Host "✓ PASSED - Status: $($response.StatusCode)" -ForegroundColor Green
            Write-Host "  Response: $($response.Content)" -ForegroundColor Gray
            $script:testsPassed++
        } else {
            Write-Host "✗ FAILED - Expected $ExpectedStatus, got $($response.StatusCode)" -ForegroundColor Red
            Write-Host "  Response: $($response.Content)" -ForegroundColor Gray
            $script:testsFailed++
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq $ExpectedStatus) {
            Write-Host "✓ PASSED - Status: $statusCode (expected error)" -ForegroundColor Green
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                Write-Host "  Response: $responseBody" -ForegroundColor Gray
            } catch {}
            $script:testsPassed++
        } else {
            Write-Host "✗ FAILED - Expected $ExpectedStatus, got $statusCode" -ForegroundColor Red
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
            $script:testsFailed++
        }
    }
    
    Write-Host ""
    Start-Sleep -Milliseconds 500
}

# Test 1: Valid PANIC alert
$panicPayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:00:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 74, "isCharging": false },
  "sequenceId": 1
}
"@
Test-Alert "Valid PANIC Alert" $panicPayload 200

# Test 2: Valid CHECKIN alert
$checkinPayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "CHECKIN",
  "timestamp": "2026-09-09T12:05:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 73, "isCharging": false },
  "sequenceId": 2
}
"@
Test-Alert "Valid CHECKIN Alert" $checkinPayload 200

# Test 3: Valid LOW_BATTERY alert
$lowBatteryPayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "LOW_BATTERY",
  "timestamp": "2026-09-09T12:10:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 15, "isCharging": false },
  "sequenceId": 3
}
"@
Test-Alert "Valid LOW_BATTERY Alert" $lowBatteryPayload 200

# Test 4: Valid TRACKING_UPDATE alert
$trackingPayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "TRACKING_UPDATE",
  "timestamp": "2026-09-09T12:15:00Z",
  "location": { "lat": 10.3160, "lng": 123.8860, "accuracyMeters": 12.0 },
  "battery": { "percent": 14, "isCharging": true },
  "sequenceId": 4
}
"@
Test-Alert "Valid TRACKING_UPDATE Alert" $trackingPayload 200

# Test 5: Invalid - Missing sequenceId
$missingFieldPayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "PANIC",
  "timestamp": "2026-09-09T12:20:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 74, "isCharging": false }
}
"@
Test-Alert "Invalid Alert - Missing sequenceId" $missingFieldPayload 400

# Test 6: Invalid - Bad alert type
$invalidTypePayload = @"
{
  "schemaVersion": "1.0",
  "deviceId": "guardband-001",
  "type": "INVALID_TYPE",
  "timestamp": "2026-09-09T12:25:00Z",
  "location": { "lat": 10.3157, "lng": 123.8854, "accuracyMeters": 8.5 },
  "battery": { "percent": 74, "isCharging": false },
  "sequenceId": 5
}
"@
Test-Alert "Invalid Alert - Bad Type" $invalidTypePayload 400

# Test 7: Invalid - GET request
Test-Alert "Invalid Method - GET Request" "" 405 "GET"

# Summary
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red
Write-Host ""

if ($testsFailed -eq 0) {
    Write-Host "✓ All tests passed! Check Firebase Console to verify data:" -ForegroundColor Green
    Write-Host "  https://console.firebase.google.com/project/guardband-aae65/database" -ForegroundColor Gray
} else {
    Write-Host "✗ Some tests failed. Check function logs:" -ForegroundColor Red
    Write-Host "  firebase functions:log" -ForegroundColor Gray
}
