param(
    [switch]$Headless,
    [switch]$ColdBoot
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = (Resolve-Path (Join-Path $projectRoot '..\StrideRunAndroid\_toolchain\android-sdk')).Path
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$userHome = [Environment]::GetFolderPath('UserProfile')
$androidUserHome = Join-Path $userHome '.android'
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_SDK_HOME = $userHome
$env:ANDROID_USER_HOME = $androidUserHome
$env:ANDROID_EMULATOR_HOME = Join-Path $androidUserHome 'emulator'
$env:ANDROID_AVD_HOME = Join-Path $androidUserHome 'avd'
$env:USERPROFILE = $userHome
New-Item -ItemType Directory -Force -Path @($env:ANDROID_USER_HOME, $env:ANDROID_EMULATOR_HOME, $env:ANDROID_AVD_HOME) | Out-Null
$avdName = 'DailyTasks_QA_AOSP'

function Start-DailyTasksAvd([bool]$SkipSnapshot) {
    $arguments = @(
        '-avd', $avdName,
        '-memory', '2048',
        '-cores', '4',
        '-gpu', 'swiftshader_indirect',
        '-noaudio'
    )
    if ($SkipSnapshot) { $arguments += '-no-snapshot-load' }
    if ($Headless) {
        $arguments += '-no-window'
        Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden | Out-Null
    } else {
        Start-Process -FilePath $emulator -ArgumentList $arguments | Out-Null
    }
}

function Wait-ForHealthyBoot {
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $state = & $adb get-state 2>$null
        if ($state -eq 'device') {
            $booted = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
            if ($booted -eq '1') {
                Start-Sleep -Seconds 20
                $systemUiPid = (& $adb shell pidof com.android.systemui 2>$null).Trim()
                $windowState = (& $adb shell dumpsys window windows 2>$null) -join "`n"
                $hasAnr = $windowState -match 'Application Not Responding|System UI isn.t responding'
                return ($systemUiPid -and -not $hasAnr)
            }
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

$connected = & $adb devices | Select-String '^emulator-\d+\s+device$'
if (-not $connected) {
    Start-DailyTasksAvd -SkipSnapshot:$ColdBoot
}

if (Wait-ForHealthyBoot) {
    Write-Host "$avdName is ready and System UI is healthy."
    exit 0
}

if (-not $ColdBoot) {
    Write-Warning 'Quick Boot was unhealthy. Retrying from a cold boot.'
    & $adb emu kill 2>$null | Out-Null
    Start-Sleep -Seconds 5
    Start-DailyTasksAvd -SkipSnapshot:$true
    if (Wait-ForHealthyBoot) {
        Write-Host "$avdName recovered with a cold boot."
        exit 0
    }
}

throw "$avdName did not reach a healthy boot state."
