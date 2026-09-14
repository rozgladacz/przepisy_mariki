[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot "gradlew.bat"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

& $gradle assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $adb -e install -r $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $adb -e shell am force-stop pl.local.przepisy.debug
& $adb -e shell monkey -p pl.local.przepisy.debug -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Debug APK zainstalowany i uruchomiony na emulatorze."
