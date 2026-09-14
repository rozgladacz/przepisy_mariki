[CmdletBinding()]
param(
    [string]$AvdName = "Przepisy_API_33"
)

$ErrorActionPreference = "Stop"
$sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $emulator)) {
    throw "Brak emulatora Android SDK: $emulator"
}
if (-not (Test-Path -LiteralPath $adb)) {
    throw "Brak adb: $adb"
}

& $adb start-server | Out-Null
$running = & $adb devices | Select-String -Pattern '^emulator-\d+\s+device$'
if (-not $running) {
    Write-Host "Uruchamianie widocznego emulatora $AvdName..."
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $AvdName,
        "-gpu", "host",
        "-memory", "4096",
        "-dns-server", "8.8.8.8,1.1.1.1",
        "-no-snapshot-save"
    ) | Out-Null
}

& $adb -e wait-for-device
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if ((& $adb -e shell getprop sys.boot_completed 2>$null).Trim() -eq "1") {
        & $adb -e shell cmd connectivity airplane-mode disable | Out-Null
        & $adb -e shell svc wifi enable | Out-Null
        Write-Host "Emulator Android 13 jest gotowy."
        exit 0
    }
    Start-Sleep -Seconds 2
}

throw "Emulator nie zakonczyl uruchamiania w ciagu 3 minut."
