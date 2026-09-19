$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path -LiteralPath $PSScriptRoot).ProviderPath.TrimEnd('\', '/')
$generatedDirectories = @('.gradle', '.kotlin', 'build', 'app/build', 'api/build')

foreach ($relativePath in $generatedDirectories) {
    $target = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $target)) { continue }

    $resolved = (Resolve-Path -LiteralPath $target).ProviderPath.TrimEnd('\', '/')
    if (-not $resolved.StartsWith($repositoryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Cleanup target is outside the repository: $relativePath"
    }

    $item = Get-Item -LiteralPath $target -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        Write-Warning "Skipping linked directory: $relativePath"
        continue
    }

    Write-Host "Removing generated directory: $relativePath"
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

Write-Host 'Cleanup completed. Local configuration and user files were preserved.'
