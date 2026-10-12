@echo off
setlocal
title EduExam Frontend
cd /d "%~dp0frontend"

echo ========================================
echo  EduExam - Online Exam System
echo  Frontend Startup Script
echo ========================================
echo.

echo [1/2] Checking Node.js dependencies...
if not exist node_modules (
    echo First run: installing npm packages, please wait...
    npm install
    if errorlevel 1 (
        echo ERROR: npm install failed
        pause
        exit /b 1
    )
)
echo Dependencies ready.

echo.

powershell -NoProfile -Command "$listening = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue; if (-not $listening) { exit 1 }; try { $page = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/' -TimeoutSec 3; if ($page.Content -match '<title>EduExam') { exit 0 } } catch {}; exit 2"
set "FRONTEND_CHECK=%ERRORLEVEL%"
if "%FRONTEND_CHECK%"=="0" (
    echo Frontend is already running at http://localhost:5173
    powershell -NoProfile -Command "Start-Sleep -Seconds 2"
    exit /b 0
)
if "%FRONTEND_CHECK%"=="2" (
    echo ERROR: Port 5173 is occupied by another service.
    echo Stop that service or change the Vite port before starting.
    pause
    exit /b 1
)

echo [2/2] Starting frontend dev server...
echo.
echo ========================================
echo  URL:     http://localhost:5173
echo  Demo accounts are available only when demo seed data was enabled.
echo ========================================
echo.
npm run dev
pause
