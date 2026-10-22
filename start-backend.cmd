@echo off
setlocal
title Task Manager - Backend (8080)
cd /d "%~dp0backend"

echo ============================================
echo  Task Manager - Backend
echo ============================================
echo.

echo Checking PostgreSQL on localhost:5433 ...
powershell -NoProfile -Command "if (-not (Test-NetConnection -ComputerName localhost -Port 5433 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 1 }"
if errorlevel 1 (
    echo.
    echo [ERROR] PostgreSQL is not reachable on localhost:5433.
    echo Start the PostgreSQL service and try again.
    echo If your database uses another host/port, override DB_URL, for example:
    echo     set DB_URL=jdbc:postgresql://localhost:5433/taskmanager
    echo     set DB_PASSWORD=your_password
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -Command "if ((Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 1 }"
if errorlevel 1 (
    echo.
    echo [WARNING] Port 8080 is already in use - the backend is probably running.
    echo Open http://localhost:8080 or close the previous backend window.
    echo.
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo.
    echo [ERROR] Maven ^(mvn^) was not found in PATH.
    echo.
    pause
    exit /b 1
)

echo Starting backend on http://localhost:8080 ...
echo Press Ctrl+C or close this window to stop it.
echo.
call mvn spring-boot:run

echo.
echo Backend stopped.
pause
