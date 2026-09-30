@echo off
echo ====================================================
echo Starting Item Rental Management System (RIMS)...
echo ====================================================

REM Compile Java source files
echo [1/2] Compiling Java files...
if not exist "bin" mkdir bin
javac -cp "lib/*" -d bin src/com/rental/*/*.java src/Main.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed. Please check errors above.
    pause
    exit /b %ERRORLEVEL%
)

REM Run application
echo [2/2] Launching server on http://localhost:8080 ...
java -cp "bin;lib/*" Main
pause
