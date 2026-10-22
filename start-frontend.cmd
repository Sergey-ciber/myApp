@echo off
setlocal
title Task Manager - Frontend (5173)
cd /d "%~dp0frontend"

echo ============================================
echo  Task Manager - Frontend
echo ============================================
echo.

where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Node.js was not found in PATH.
    echo Install Node.js 18 or newer: https://nodejs.org
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -Command "if ((Test-NetConnection -ComputerName localhost -Port 5173 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 1 }"
if errorlevel 1 (
    echo [WARNING] Port 5173 is already in use - the frontend is probably running.
    echo Open http://localhost:5173 or close the previous frontend window.
    echo.
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo First run: installing dependencies ^(npm install^) ...
    echo.
    call npm install
    if errorlevel 1 (
        echo.
        echo [ERROR] Failed to install dependencies.
        echo.
        pause
        exit /b 1
    )
    echo.
)

echo Starting frontend on http://localhost:5173 ...
echo Press Ctrl+C or close this window to stop it.
echo.
call npm run dev

echo.
echo Frontend stopped.
pause
