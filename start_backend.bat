@echo off
setlocal
title EduExam Backend

set "_CLI_DB_USER=%EDUEXAM_DB_USER%"
set "_CLI_DB_PASSWORD=%EDUEXAM_DB_PASSWORD%"
set "_CLI_DB_HOST=%EDUEXAM_DB_HOST%"
set "_CLI_DB_PORT=%EDUEXAM_DB_PORT%"
set "_CLI_DB_NAME=%EDUEXAM_DB_NAME%"
set "_CLI_JWT=%APP_JWT_SECRET%"
set "_CLI_SEED_DEMO=%EDUEXAM_SEED_DEMO%"
set "_CLI_DB_USERNAME_ALT=%DB_USERNAME%"
set "_CLI_DB_PASSWORD_ALT=%DB_PASSWORD%"
set "_CLI_DB_HOST_ALT=%DB_HOST%"
set "_CLI_DB_PORT_ALT=%DB_PORT%"
set "_CLI_DB_NAME_ALT=%DB_NAME%"
if exist "%~dp0.eduexam.local.bat" call "%~dp0.eduexam.local.bat"
if defined _CLI_DB_USER set "EDUEXAM_DB_USER=%_CLI_DB_USER%"
if defined _CLI_DB_PASSWORD set "EDUEXAM_DB_PASSWORD=%_CLI_DB_PASSWORD%"
if defined _CLI_DB_HOST set "EDUEXAM_DB_HOST=%_CLI_DB_HOST%"
if defined _CLI_DB_PORT set "EDUEXAM_DB_PORT=%_CLI_DB_PORT%"
if defined _CLI_DB_NAME set "EDUEXAM_DB_NAME=%_CLI_DB_NAME%"
if defined _CLI_JWT set "APP_JWT_SECRET=%_CLI_JWT%"
if defined _CLI_SEED_DEMO set "EDUEXAM_SEED_DEMO=%_CLI_SEED_DEMO%"
if not defined _CLI_DB_USER if defined _CLI_DB_USERNAME_ALT set "EDUEXAM_DB_USER=%_CLI_DB_USERNAME_ALT%"
if not defined _CLI_DB_PASSWORD if defined _CLI_DB_PASSWORD_ALT set "EDUEXAM_DB_PASSWORD=%_CLI_DB_PASSWORD_ALT%"
if not defined _CLI_DB_HOST if defined _CLI_DB_HOST_ALT set "EDUEXAM_DB_HOST=%_CLI_DB_HOST_ALT%"
if not defined _CLI_DB_PORT if defined _CLI_DB_PORT_ALT set "EDUEXAM_DB_PORT=%_CLI_DB_PORT_ALT%"
if not defined _CLI_DB_NAME if defined _CLI_DB_NAME_ALT set "EDUEXAM_DB_NAME=%_CLI_DB_NAME_ALT%"

if not defined EDUEXAM_DB_USER if defined DB_USERNAME set "EDUEXAM_DB_USER=%DB_USERNAME%"
if not defined EDUEXAM_DB_PASSWORD if defined DB_PASSWORD set "EDUEXAM_DB_PASSWORD=%DB_PASSWORD%"

if defined EDUEXAM_DB_USER set "DB_USERNAME=%EDUEXAM_DB_USER%"
if defined EDUEXAM_DB_PASSWORD set "DB_PASSWORD=%EDUEXAM_DB_PASSWORD%"
if defined EDUEXAM_DB_HOST set "DB_HOST=%EDUEXAM_DB_HOST%"
if defined EDUEXAM_DB_PORT set "DB_PORT=%EDUEXAM_DB_PORT%"
if defined EDUEXAM_DB_NAME set "DB_NAME=%EDUEXAM_DB_NAME%"

if not defined APP_JWT_SECRET (
    echo [ERROR] APP_JWT_SECRET is not configured.
    echo Copy eduexam.local.example.bat to .eduexam.local.bat and set a local secret.
    pause
    exit /b 1
)
powershell -NoProfile -Command "if ([Text.Encoding]::UTF8.GetByteCount($env:APP_JWT_SECRET) -lt 32 -or @('CHANGE_ME','replace-with-a-random-secret-of-at-least-32-characters','eduexam-local-development-secret-change-me-32-chars') -contains $env:APP_JWT_SECRET) { exit 1 }"
if errorlevel 1 (
    echo [ERROR] APP_JWT_SECRET must be a private random value with at least 32 UTF-8 bytes.
    echo Generate a random secret and put it in .eduexam.local.bat.
    pause
    exit /b 1
)

powershell -NoProfile -Command "$listening = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue; if (-not $listening) { exit 1 }; try { $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 3; if ($health.service -eq 'eduexam' -and $health.status -eq 'UP') { exit 0 } } catch {}; exit 2"
set "HEALTH_CHECK=%ERRORLEVEL%"
if "%HEALTH_CHECK%"=="0" (
    echo.
    echo Backend is already running on port 8080.
    powershell -NoProfile -Command "Start-Sleep -Seconds 2"
    exit /b 0
)
if "%HEALTH_CHECK%"=="2" (
    echo [ERROR] Port 8080 is occupied by another service.
    echo Stop that service or change the EduExam backend port before starting.
    pause
    exit /b 1
)

echo Starting EduExam Backend Server...
echo.
echo API will be available at: http://localhost:8080
echo Press Ctrl+C to stop the server
echo ========================================
echo.

call "%~dp0init_database.bat"
if errorlevel 1 (
    echo.
    echo Backend startup cancelled because database initialization failed.
    pause
    exit /b 1
)

cd /d "%~dp0backend"
call mvn clean spring-boot:run
if errorlevel 1 (
    echo.
    echo Backend stopped with an error. Review the log above.
    pause
    exit /b 1
)
