$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\android-sdk')).Path
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$userHome = [Environment]::GetFolderPath('UserProfile')
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $userHome '.android'
$env:USERPROFILE = $userHome

& $adb emu kill
Write-Host 'DailyTasks_QA_AOSP stopped.'
