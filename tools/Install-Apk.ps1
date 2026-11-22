param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\android-sdk')).Path
$jdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\jdk\jdk-17.0.20+8')).Path
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'outputs\TuTuCard-v1.1.41-debug.apk'
$userHome = [Environment]::GetFolderPath('UserProfile')
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $userHome '.android'
$env:USERPROFILE = $userHome

if ($Rebuild) {
    $env:JAVA_HOME = $jdkRoot
    $env:ANDROID_HOME = $sdkRoot
    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' testDebugUnitTest assembleDebug lintDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
        Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apk -Force
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb shell pm grant tw.knox.dailytasks android.permission.POST_NOTIFICATIONS 2>$null
& $adb shell cmd appops set tw.knox.dailytasks SCHEDULE_EXACT_ALARM allow 2>$null
& $adb shell am force-stop tw.knox.dailytasks
& $adb shell am start -n tw.knox.dailytasks/.MainActivity
Start-Sleep -Seconds 4
$appPid = (& $adb shell pidof tw.knox.dailytasks 2>$null).Trim()
$activityState = (& $adb shell dumpsys activity activities 2>$null) -join "`n"
if (-not $appPid -or $activityState -notmatch 'topResumedActivity=.*tw\.knox\.dailytasks') {
    throw 'DailyTasks was installed but did not reach the foreground.'
}

$uiReady = $false
$deadline = (Get-Date).AddSeconds(40)
do {
    & $adb shell uiautomator dump /sdcard/dailytasks-ui.xml 2>$null | Out-Null
    $ui = (& $adb shell cat /sdcard/dailytasks-ui.xml 2>$null) -join "`n"
    if ($ui -match 'androidx\.compose\.ui\.platform\.ComposeView') {
        $uiReady = $true
        break
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
& $adb shell rm -f /sdcard/dailytasks-ui.xml 2>$null | Out-Null
if (-not $uiReady) { throw 'DailyTasks stayed on the launch screen for too long.' }

$windowState = (& $adb shell dumpsys window windows 2>$null) -join "`n"
if ($windowState -match 'Application Not Responding') {
    throw 'Android reported an Application Not Responding dialog.'
}
Write-Host 'DailyTasks installed and launched.'
