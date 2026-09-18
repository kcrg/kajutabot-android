@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

echo.
echo ==========================================
echo   KajutaBot Android - repository cleanup
echo ==========================================
echo.

call :RemoveDir ".gradle"
call :RemoveDir ".kotlin"

call :RemoveDir ".idea\caches"
call :RemoveDir ".idea\shelf"
call :RemoveDir ".idea\httpRequests"
call :RemoveFile ".idea\workspace.xml"
call :RemoveFile ".idea\deploymentTargetSelector.xml"

call :RemoveFile "local.properties"

for /d /r %%D in (build) do (
    if exist "%%D" (
        echo [DIR ] %%D
        rd /s /q "%%D" 2>nul
    )
)

for /d /r %%D in (.cxx) do (
    if exist "%%D" (
        echo [DIR ] %%D
        rd /s /q "%%D" 2>nul
    )
)

for /d /r %%D in (.externalNativeBuild) do (
    if exist "%%D" (
        echo [DIR ] %%D
        rd /s /q "%%D" 2>nul
    )
)

for /r %%F in (*.iml) do (
    if exist "%%F" (
        echo [FILE] %%F
        del /f /q "%%F" 2>nul
    )
)

for /r %%F in (*.tmp *.temp *.log) do (
    if exist "%%F" (
        echo [FILE] %%F
        del /f /q "%%F" 2>nul
    )
)

echo.
echo Cleanup completed.
echo.
exit /b 0

:RemoveDir
if exist "%~1" (
    echo [DIR ] %~1
    rd /s /q "%~1" 2>nul
)
exit /b 0

:RemoveFile
if exist "%~1" (
    echo [FILE] %~1
    del /f /q "%~1" 2>nul
)
exit /b 0
