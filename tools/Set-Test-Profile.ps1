param(
    [ValidateSet('Small', 'Standard', 'Large')]
    [string]$Device = 'Standard',
    [ValidateSet('0.85', '1.0', '1.3', '1.5', '2.0')]
    [string]$FontScale = '1.0'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\android-sdk')).Path
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$userHome = [Environment]::GetFolderPath('UserProfile')
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $userHome '.android'
$env:USERPROFILE = $userHome
$density = switch ($Device) {
    'Small' { 480 }
    'Standard' { 440 }
    'Large' { 400 }
}

& $adb shell wm size reset
& $adb shell wm density $density
& $adb shell settings put system font_scale $FontScale
& $adb shell am force-stop tw.knox.dailytasks
& $adb shell am start -n tw.knox.dailytasks/.MainActivity | Out-Null
Write-Host "Applied profile: $Device, font scale $FontScale."
