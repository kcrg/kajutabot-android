param(
    [string]$PackageName = "com.tryniecki.kajutabot",
    [string]$ActivityName = ".MainActivity"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$Gradle = Join-Path $RepoRoot "gradlew.bat"
if (-not (Test-Path $Gradle)) {
    throw "Nie znaleziono gradlew.bat. Umieść ten skrypt w głównym katalogu repozytorium KajutaBot."
}

$AdbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $AdbCommand) {
    throw "Nie znaleziono adb w PATH."
}
$Adb = $AdbCommand.Source

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Polecenie zakończyło się kodem $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

function Get-SingleDeviceSerial {
    $lines = & $Adb devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices nie powiodło się."
    }

    $devices = @(
        $lines |
            Select-String -Pattern '^\s*(\S+)\s+device\s*$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
    )

    if ($devices.Count -eq 0) {
        throw "Brak podłączonego i autoryzowanego urządzenia ADB."
    }

    if ($devices.Count -gt 1) {
        throw "Podłączono więcej niż jedno urządzenie: $($devices -join ', '). Zostaw jedno urządzenie podłączone."
    }

    return $devices[0]
}

$Serial = Get-SingleDeviceSerial

Write-Host ""
Write-Host "Urządzenie: $Serial"

$sdkText = (& $Adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
if (-not [int]::TryParse($sdkText, [ref]$sdk)) {
    throw "Nie udało się odczytać API level urządzenia: '$sdkText'"
}

if ($sdk -lt 34) {
    throw "Manualne zbieranie bez roota w tym skrypcie wymaga API 34+. Urządzenie ma API $sdk."
}

Write-Host "Android API: $sdk"
Write-Host ""
Write-Host "=== Budowanie i instalacja nonMinifiedRelease ==="
Invoke-Checked $Gradle @(":app:installNonMinifiedRelease")

$Receiver = "$PackageName/androidx.profileinstaller.ProfileInstallReceiver"

Write-Host ""
Write-Host "=== Wyłączenie automatycznej instalacji istniejącego Baseline Profile ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SKIP_FILE",
    "-e", "EXTRA_SKIP_FILE_OPERATION", "WRITE_SKIP_FILE",
    $Receiver
)

Write-Host ""
Write-Host "=== Czyszczenie starego stanu kompilacji/profili ART ==="
Invoke-Checked $Adb @("-s", $Serial, "shell", "am", "force-stop", $PackageName)
Invoke-Checked $Adb @("-s", $Serial, "shell", "cmd", "package", "compile", "-f", "-m", "verify", $PackageName)
Invoke-Checked $Adb @("-s", $Serial, "shell", "pm", "art", "clear-app-profiles", $PackageName)

Write-Host ""
Write-Host "=== Uruchamianie KajutaBota ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "start", "-W",
    "-n", "$PackageName/$ActivityName"
)

Write-Host ""
Write-Host "Gotowe."
Write-Host "Teraz ręcznie przeklikaj najważniejsze ścieżki KajutaBota."
Write-Host "Gdy skończysz, uruchom: .\Collect-BaselineProfile.ps1"
