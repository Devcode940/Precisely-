@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
) else (
  echo Please install Gradle 9.1.0+ or generate the official Gradle wrapper with: gradle wrapper
  exit /b 1
)
