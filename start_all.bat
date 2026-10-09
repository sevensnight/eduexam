@echo off
setlocal
title EduExam Launcher

echo ========================================
echo  EduExam - One-click Startup
echo ========================================
echo.
echo Starting backend in a separate window...
start "EduExam Backend" cmd /c call "%~dp0start_backend.bat"

echo Waiting for backend on port 8080...
powershell -NoProfile -Command "$deadline=(Get-Date).AddSeconds(90); while ((Get-Date) -lt $deadline) { $client=[Net.Sockets.TcpClient]::new(); try { $client.Connect('127.0.0.1',8080); $client.Dispose(); exit 0 } catch { $client.Dispose(); Start-Sleep -Milliseconds 500 } }; exit 1"
if errorlevel 1 (
    echo [ERROR] Backend did not become ready within 90 seconds.
    echo Review the EduExam Backend window for details.
    pause
    exit /b 1
)

echo Backend is ready. Starting frontend...
start "EduExam Frontend" cmd /c call "%~dp0start_frontend.bat"

echo Waiting for frontend on port 5173...
powershell -NoProfile -Command "$deadline=(Get-Date).AddSeconds(60); while ((Get-Date) -lt $deadline) { $client=[Net.Sockets.TcpClient]::new(); try { $client.Connect('127.0.0.1',5173); $client.Dispose(); exit 0 } catch { $client.Dispose(); Start-Sleep -Milliseconds 500 } }; exit 1"
if errorlevel 1 (
    echo [ERROR] Frontend did not become ready within 60 seconds.
    echo Review the EduExam Frontend window for details.
    pause
    exit /b 1
)

echo Opening http://localhost:5173 ...
start "" "http://localhost:5173"
echo EduExam is ready.
powershell -NoProfile -Command "Start-Sleep -Seconds 3"
