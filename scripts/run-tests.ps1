[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradle = Join-Path $PSScriptRoot "..\gradlew.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $env:JAVA_HOME)) {
    throw "Brak JDK Android Studio: $env:JAVA_HOME"
}
if (-not (Test-Path -LiteralPath $adb)) {
    throw "Brak adb: $adb"
}

& $gradle testDebugUnitTest lintDebug assembleDebug --continue
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$device = & $adb devices | Select-String -Pattern '\sdevice$'
if (-not $device) {
    throw "Brak uruchomionego emulatora lub telefonu. Najpierw wykonaj .\scripts\start-emulator.ps1"
}

& $gradle connectedDebugAndroidTest --continue
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $gradle assembleRelease verifyReleaseApkSize
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Wszystkie testy, Lint oraz buildy debug/release zakonczone pomyslnie."
