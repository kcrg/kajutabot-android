param(
    [string]$PackageName = "com.tryniecki.kajutabot",
    [string]$ActivityName = ".MainActivity"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$Gradle = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $Gradle)) {
    throw "gradlew.bat was not found. Put this script in the repository root."
}

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
        $commandLine = "$FilePath $($Arguments -join ' ')"
        throw ("Command failed with exit code {0}: {1}" -f $exitCode, $commandLine)
    }
}

function Get-SingleDeviceSerial {
    $lines = & $Adb devices
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw ("adb devices failed with exit code {0}." -f $exitCode)
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
        throw ("More than one ADB device is connected: {0}. Leave only one device connected." -f ($devices -join ', '))
    }

    return $devices[0]
}

$Serial = Get-SingleDeviceSerial

Write-Host ""
Write-Host "Device: $Serial"

$sdkText = (& $Adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read Android API level through adb."
}

$sdk = 0
if (-not [int]::TryParse($sdkText, [ref]$sdk)) {
    throw ("Failed to parse Android API level: '{0}'" -f $sdkText)
}

if ($sdk -lt 34) {
    throw ("This manual Baseline Profile workflow requires Android API 34 or newer. Device API: {0}." -f $sdk)
}

Write-Host "Android API: $sdk"

Write-Host ""
Write-Host "=== Build and install nonMinifiedRelease ==="
Invoke-Checked $Gradle @(
    ":app:installNonMinifiedRelease"
)

$Receiver = "$PackageName/androidx.profileinstaller.ProfileInstallReceiver"

Write-Host ""
Write-Host "=== Disable automatic installation of the bundled Baseline Profile ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SKIP_FILE",
    "WRITE_SKIP_FILE",
    "-n", $Receiver
)

Write-Host ""
Write-Host "=== Clear previous ART compilation/profile state ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "force-stop",
    $PackageName
)

Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "cmd", "package", "compile",
    "-f", "-m", "verify",
    $PackageName
)

Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "pm", "art", "clear-app-profiles",
    $PackageName
)

Write-Host ""
Write-Host "=== Start KajutaBot ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "start", "-W",
    "-n", "$PackageName/$ActivityName"
)

Write-Host ""
Write-Host "Ready."
Write-Host "Now run .\Collect-BaselineProfile.ps1, exercise the important app flows, then confirm profile collection when prompted."
