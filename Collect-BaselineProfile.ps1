param(
    [string]$PackageName = "com.tryniecki.kajutabot"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $AdbCommand) {
    throw "adb was not found in PATH."
}

$Adb = $AdbCommand.Source

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        $argumentText = $Arguments -join " "
        throw "Command failed with exit code $exitCode`: $FilePath $argumentText"
    }
}

function Get-SingleDeviceSerial {
    $lines = & $Adb devices
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "adb devices failed with exit code $exitCode."
    }

    $devices = @(
        $lines |
            Select-String -Pattern '^\s*(\S+)\s+device\s*$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
    )

    if ($devices.Count -eq 0) {
        throw "No connected and authorized ADB device was found."
    }

    if ($devices.Count -gt 1) {
        throw "More than one ADB device is connected: $($devices -join ', '). Leave only one device connected."
    }

    return $devices[0]
}

$Serial = Get-SingleDeviceSerial

$sdkText = (& $Adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
$sdkReadExitCode = $LASTEXITCODE

if ($sdkReadExitCode -ne 0) {
    throw "Failed to read Android API level. adb exited with code $sdkReadExitCode."
}

$sdk = 0
if (-not [int]::TryParse($sdkText, [ref]$sdk)) {
    throw "Failed to parse Android API level: '$sdkText'"
}

if ($sdk -lt 34) {
    throw "This non-root collection script requires Android API 34 or newer. Device API: $sdk."
}

Write-Host ""
Write-Host "Device: $Serial"
Write-Host "Android API: $sdk"
Write-Host "Package: $PackageName"
Write-Host ""
[void](Read-Host "Use the app and exercise the important user journeys. Press Enter when finished")

Write-Host ""
Write-Host "Waiting 5 seconds for ART profile data to stabilize..."
Start-Sleep -Seconds 5

$Receiver = "$PackageName/androidx.profileinstaller.ProfileInstallReceiver"

Write-Host "=== Saving current ART profile ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SAVE_PROFILE",
    $Receiver
)

Start-Sleep -Seconds 1

Write-Host "=== Force-stopping app ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "force-stop",
    $PackageName
)

Write-Host "=== Converting profile to human-readable format ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "pm", "dump-profiles",
    "--dump-classes-and-methods",
    $PackageName
)

$RemoteProfile = "/data/misc/profman/$PackageName-primary.prof.txt"

$WorkDir = Join-Path $RepoRoot "app\build\manual-baseline-profile"
$BackupDir = Join-Path $WorkDir "backups"
$TargetDir = Join-Path $RepoRoot "app\src\main"
$TargetProfile = Join-Path $TargetDir "baseline-prof.txt"
$TempProfile = Join-Path $WorkDir "$PackageName-primary.prof.txt"

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

if (Test-Path $TargetProfile) {
    $Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $BackupProfile = Join-Path $BackupDir "baseline-prof-$Timestamp.txt"
    Copy-Item $TargetProfile $BackupProfile -Force
    Write-Host "Existing profile backup: $BackupProfile"
}

if (Test-Path $TempProfile) {
    Remove-Item $TempProfile -Force
}

Write-Host "=== Pulling profile from device ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "pull",
    $RemoteProfile,
    $TempProfile
)

if (-not (Test-Path $TempProfile)) {
    throw "adb pull completed, but the profile file does not exist: $TempProfile"
}

$fileInfo = Get-Item $TempProfile
if ($fileInfo.Length -eq 0) {
    throw "Collected profile is empty."
}

Copy-Item $TempProfile $TargetProfile -Force

$lineCount = (Get-Content $TargetProfile | Measure-Object -Line).Lines

Write-Host ""
Write-Host "=== Re-enabling normal ProfileInstaller behavior ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SKIP_FILE",
    "-e", "EXTRA_SKIP_FILE_OPERATION", "DELETE_SKIP_FILE",
    $Receiver
)

Write-Host ""
Write-Host "Done."
Write-Host "Profile: $TargetProfile"
Write-Host "Size: $($fileInfo.Length) B"
Write-Host "Lines: $lineCount"
Write-Host ""
Write-Host "You can now build the normal release:"
Write-Host "  .\gradlew.bat :app:assembleRelease"
