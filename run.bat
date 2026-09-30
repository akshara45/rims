@echo off
echo ====================================================
echo Starting Item Rental Management System (RIMS)...
echo ====================================================

where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven 3.9+ is required. Install Maven and configure the RIMS_DB_* environment variables.
    pause
    exit /b 1
)

echo Launching RIMS with the configured PostgreSQL database...
call mvn -B compile dependency:copy-dependencies -DoutputDirectory=target/dependency exec:java
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Application startup failed. Check PostgreSQL settings and credentials.
    pause
    exit /b %ERRORLEVEL%
)
pause
