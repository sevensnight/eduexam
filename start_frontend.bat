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

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo Frontend is already running at http://localhost:5173
    powershell -NoProfile -Command "Start-Sleep -Seconds 2"
    exit /b 0
)

echo [2/2] Starting frontend dev server...
echo.
echo ========================================
echo  URL:     http://localhost:5173
echo  Accounts:
echo    Admin:   admin / admin123
echo    Teacher: teacher1 / teacher123
echo    Student: student1 / student123
echo ========================================
echo.
npm run dev
pause
