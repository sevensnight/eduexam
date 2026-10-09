@echo off
setlocal
title EduExam Backend

if defined EDUEXAM_DB_USER set "DB_USERNAME=%EDUEXAM_DB_USER%"
if defined EDUEXAM_DB_PASSWORD set "DB_PASSWORD=%EDUEXAM_DB_PASSWORD%"

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

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo.
    echo Backend is already running on port 8080.
    powershell -NoProfile -Command "Start-Sleep -Seconds 2"
    exit /b 0
)

cd /d "%~dp0backend"
call mvn clean spring-boot:run
if errorlevel 1 (
    echo.
    echo Backend stopped with an error. Review the log above.
    pause
    exit /b 1
)
