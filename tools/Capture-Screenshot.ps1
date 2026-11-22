param([string]$Name = "screen-$(Get-Date -Format 'yyyyMMdd-HHmmss')")

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\android-sdk')).Path
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$userHome = [Environment]::GetFolderPath('UserProfile')
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $userHome '.android'
$env:USERPROFILE = $userHome
$outputDir = Join-Path $projectRoot 'qa\screenshots'
$safeName = $Name -replace '[^a-zA-Z0-9_-]', '-'
$remote = "/sdcard/$safeName.png"
$local = Join-Path $outputDir "$safeName.png"

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $adb shell screencap -p $remote
& $adb pull $remote $local
if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed.' }
& $adb shell rm $remote
Write-Host $local
