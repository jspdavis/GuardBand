<#
.SYNOPSIS
  Feeds this laptop's Windows location to the GuardBand prototype band as
  GPS lines, and stands in for the Arduino Serial Monitor meanwhile.

.DESCRIPTION
  Every few seconds it reads the laptop's position from Windows Location
  and sends "GPS,lat,lng,accuracy" to the band over the serial port, so
  alerts carry the laptop's real position instead of a simulated one.
  Most laptops have no GPS chip, so Windows estimates the position from
  nearby Wi-Fi: expect tens to a few hundred metres of accuracy, which
  the band passes on as accuracyMeters.

  Only one program can hold a COM port, so close the Arduino Serial
  Monitor first. The board's output is shown here instead, and anything
  you type (BATT,15  STATUS  NOFIX) followed by Enter goes to the board.
  Ctrl+C quits.

  The coordinates themselves are never printed.

  Needs Windows PowerShell 5.1 (powershell.exe), not PowerShell 7:
  System.Device.Location is .NET Framework only. Location must be on in
  Settings > Privacy & security > Location, including "Let desktop apps
  access your location".

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File firmware\tools\laptop-gps.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File firmware\tools\laptop-gps.ps1 -Port COM9 -IntervalSeconds 10
#>
param(
    # The board's port. Picked automatically when there is only one.
    [string]$Port,
    [int]$Baud = 115200,
    # How often the position is sent. The band's tracking sends every 5 s.
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSEdition -eq 'Core') {
    throw 'Run this with Windows PowerShell (powershell.exe): System.Device.Location is not in PowerShell 7.'
}
Add-Type -AssemblyName System.Device

# ---------- Serial port ----------
if (-not $Port) {
    $ports = @([System.IO.Ports.SerialPort]::GetPortNames())
    if ($ports.Count -eq 0) { throw 'No COM port found. Is the ESP32 plugged in?' }
    if ($ports.Count -gt 1) { throw "Several COM ports ($($ports -join ', ')): pass one with -Port." }
    $Port = $ports[0]
}
$serial = New-Object System.IO.Ports.SerialPort $Port, $Baud
$serial.NewLine = "`n"
# DTR and RTS stay low, so opening the port does not reset the board.
$serial.DtrEnable = $false
$serial.RtsEnable = $false
try {
    $serial.Open()
} catch {
    throw "Could not open $Port. Close the Arduino Serial Monitor (and anything else using the port) first."
}

# ---------- Laptop location ----------
$watcher = New-Object System.Device.Location.GeoCoordinateWatcher ([System.Device.Location.GeoPositionAccuracy]::High)
$watcher.MovementThreshold = 1
[void]$watcher.TryStart($false, [TimeSpan]::FromSeconds(10))
if ($watcher.Permission -eq 'Denied') {
    Write-Warning ('Windows refused the location. Settings > Privacy & security > Location: turn on Location ' +
        'services and "Let desktop apps access your location". The band will use simulated positions meanwhile.')
}

$invariant = [Globalization.CultureInfo]::InvariantCulture
Write-Host "[laptop-gps] Connected to $Port at $Baud baud. Type a command for the board and press Enter; Ctrl+C quits."

$received = ''
$typed = ''
$nextSendAt = [DateTime]::MinValue
$lastStatus = $null
$lastAccuracy = $null
$shownFixSet = $false

try {
    while ($true) {
        # Board -> screen, a whole line at a time.
        $received += $serial.ReadExisting()
        while (($newline = $received.IndexOf("`n")) -ge 0) {
            $line = $received.Substring(0, $newline).TrimEnd("`r")
            $received = $received.Substring($newline + 1)
            # The board acknowledges every GPS line; show only the first.
            if ($line -like 'GPS fix set*') {
                if ($shownFixSet) { continue }
                $shownFixSet = $true
            }
            Write-Host $line
        }

        # Keyboard -> board.
        while ([Console]::KeyAvailable) {
            $key = [Console]::ReadKey($true)
            if ($key.Key -eq 'Enter') {
                Write-Host ''
                if ($typed) { $serial.WriteLine($typed) }
                $typed = ''
            } elseif ($key.Key -eq 'Backspace') {
                if ($typed.Length -gt 0) {
                    $typed = $typed.Substring(0, $typed.Length - 1)
                    Write-Host -NoNewline "`b `b"
                }
            } elseif ($key.KeyChar -ne [char]0) {
                $typed += $key.KeyChar
                Write-Host -NoNewline $key.KeyChar
            }
        }

        # Laptop location -> board.
        if ((Get-Date) -ge $nextSendAt) {
            $nextSendAt = (Get-Date).AddSeconds($IntervalSeconds)
            if ($watcher.Status -ne $lastStatus) {
                Write-Host "[laptop-gps] Windows location: $($watcher.Status)"
                $lastStatus = $watcher.Status
            }
            $location = $watcher.Position.Location
            if (-not $location.IsUnknown) {
                $accuracy = $location.HorizontalAccuracy
                if ([double]::IsNaN($accuracy) -or $accuracy -le 0) { $accuracy = 100 }
                $serial.WriteLine([string]::Format($invariant, 'GPS,{0:F6},{1:F6},{2:F1}',
                    $location.Latitude, $location.Longitude, $accuracy))
                # Say so when the accuracy changes by a quarter or more, not on every send.
                if ($null -eq $lastAccuracy -or [Math]::Abs($accuracy - $lastAccuracy) -ge 0.25 * $lastAccuracy) {
                    Write-Host ([string]::Format($invariant,
                        '[laptop-gps] Sending this laptop''s position every {0} s (accuracy {1:F0} m)', $IntervalSeconds, $accuracy))
                    $lastAccuracy = $accuracy
                }
            }
        }

        Start-Sleep -Milliseconds 50
    }
} finally {
    $watcher.Stop()
    $watcher.Dispose()
    if ($serial.IsOpen) { $serial.Close() }
    Write-Host '[laptop-gps] Port closed.'
}
