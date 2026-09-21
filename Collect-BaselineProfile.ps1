param(
    [string]$PackageName = "com.tryniecki.kajutabot"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

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

$sdkText = (& $Adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
if (-not [int]::TryParse($sdkText, [ref]$sdk)) {
    throw "Nie udało się odczytać API level urządzenia: '$sdkText'"
}

if ($sdk -lt 34) {
    throw "Manualne zbieranie bez roota w tym skrypcie wymaga API 34+. Urządzenie ma API $sdk."
}

Write-Host ""
Write-Host "Urządzenie: $Serial"
Write-Host "Android API: $sdk"
Write-Host ""
[void](Read-Host "Przeklikaj aplikację. Gdy skończysz, naciśnij Enter, aby zebrać profil")

Write-Host ""
Write-Host "Czekam 5 sekund, aby ART ustabilizował profil..."
Start-Sleep -Seconds 5

$Receiver = "$PackageName/androidx.profileinstaller.ProfileInstallReceiver"

Write-Host "=== Zapisywanie bieżącego profilu ART ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SAVE_PROFILE",
    $Receiver
)

Start-Sleep -Seconds 1

Write-Host "=== Zatrzymywanie aplikacji ==="
Invoke-Checked $Adb @("-s", $Serial, "shell", "am", "force-stop", $PackageName)

Write-Host "=== Konwersja profilu do human-readable format ==="
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
    Write-Host "Backup starego profilu: $BackupProfile"
}

if (Test-Path $TempProfile) {
    Remove-Item $TempProfile -Force
}

Write-Host "=== Pobieranie profilu z urządzenia ==="
Invoke-Checked $Adb @("-s", $Serial, "pull", $RemoteProfile, $TempProfile)

if (-not (Test-Path $TempProfile)) {
    throw "adb pull zakończył się bez błędu, ale plik nie istnieje: $TempProfile"
}

$fileInfo = Get-Item $TempProfile
if ($fileInfo.Length -eq 0) {
    throw "Zebrany profil jest pusty."
}

Copy-Item $TempProfile $TargetProfile -Force

$lineCount = (Get-Content $TargetProfile | Measure-Object -Line).Lines

Write-Host ""
Write-Host "=== Przywracanie normalnego działania ProfileInstaller ==="
Invoke-Checked $Adb @(
    "-s", $Serial,
    "shell", "am", "broadcast",
    "-a", "androidx.profileinstaller.action.SKIP_FILE",
    "-e", "EXTRA_SKIP_FILE_OPERATION", "DELETE_SKIP_FILE",
    $Receiver
)

Write-Host ""
Write-Host "Gotowe."
Write-Host "Profil: $TargetProfile"
Write-Host "Rozmiar: $($fileInfo.Length) B"
Write-Host "Liczba linii: $lineCount"
Write-Host ""
Write-Host "Możesz teraz zbudować normalny release:"
Write-Host "  .\gradlew.bat :app:assembleRelease"
