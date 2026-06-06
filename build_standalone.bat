@echo off
setlocal EnableDelayedExpansion

set "BAT_PATH=%~dp0"
set "BUILD_PATH=%BAT_PATH%build"
set "JPACKAGE_OUT=%BAT_PATH%target\jpackage"
set "APP_NAME=dsr-uwyg-qol-edition"

rem Optional first argument: jpackage type (APP_IMAGE, EXE, MSI). Default: APP_IMAGE.
set "JP_TYPE=%~1"
if "%JP_TYPE%"=="" set "JP_TYPE=APP_IMAGE"

rem Require a JDK with jpackage on PATH or via JAVA_HOME.
set "JPACKAGE_EXE="
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE_EXE=%JAVA_HOME%\bin\jpackage.exe"
)
if not defined JPACKAGE_EXE (
  for /f "delims=" %%J in ('where jpackage 2^>nul') do (
    set "JPACKAGE_EXE=%%J"
    goto :have_jpackage
  )
)
:have_jpackage
if not defined JPACKAGE_EXE (
  echo jpackage not found. Install JDK 14+ ^(JDK 21 recommended^), set JAVA_HOME,
  echo or put jpackage.exe on PATH, then re-run this script.
  exit /b 1
)

rem Warn if a previous test instance of the .exe is still running and would
rem hold runtime\bin\*.dll open, defeating the zip step below.
tasklist /FI "IMAGENAME eq %APP_NAME%.exe" 2>nul | find /I "%APP_NAME%.exe" >nul
if not errorlevel 1 (
  echo.
  echo A previous %APP_NAME%.exe is still running and holds the bundled JRE
  echo DLLs open. Close it ^(or run: taskkill /F /IM %APP_NAME%.exe^) and re-run.
  exit /b 1
)

rem Purge previous outputs.
rmdir "%BUILD_PATH%" /s /q 2> nul
mkdir "%BUILD_PATH%"
rmdir "%JPACKAGE_OUT%" /s /q 2> nul

rem Build the fat jar + run jpackage via the Maven profile.
call "%BAT_PATH%mvnw.cmd" -Pjpackage -Djpackage.type=%JP_TYPE% clean package
if errorlevel 1 (
  echo Maven build failed.
  exit /b 1
)

if /I "%JP_TYPE%"=="APP_IMAGE" (
  set "APP_IMAGE_DIR=%JPACKAGE_OUT%\%APP_NAME%"
  if not exist "!APP_IMAGE_DIR!\%APP_NAME%.exe" (
    echo jpackage did not produce !APP_IMAGE_DIR!\%APP_NAME%.exe
    exit /b 1
  )

  rem OBS overlay template lives next to the launcher .exe (not under app/ with the jar).
  xcopy "%BAT_PATH%templates" "!APP_IMAGE_DIR!\templates" /E /I /Y
  if errorlevel 1 (
    echo Copying templates into the app-image failed.
    exit /b 1
  )

  rem Extract version from the fat jar name for the zip filename.
  set "VERSION="
  for %%i in ("%BAT_PATH%target\*.jar") do set "VERSION=%%~ni"
  set "VERSION=!VERSION:dark-souls-remastered-uwyg-qol-edition-=!"

  rem Zip directly from target\jpackage. Going through an intermediate
  rem build\%APP_NAME%\ copy only doubles disk churn and gives Windows
  rem Defender / Search Indexer twice as many files to scan, which is what
  rem makes Compress-Archive race against AV scans on big bundles (~150 MB
  rem of bundled JRE). System.IO.Compression.ZipFile.CreateFromDirectory is
  rem the same code path used by Explorer's "Send to > Compressed folder":
  rem one process, one pass, far less locking surface.
  set "ZIP_PATH=%BUILD_PATH%\%APP_NAME%-!VERSION!.zip"
  if exist "!ZIP_PATH!" del /f /q "!ZIP_PATH!"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
    "[System.IO.Compression.ZipFile]::CreateFromDirectory('!APP_IMAGE_DIR!','!ZIP_PATH!',[System.IO.Compression.CompressionLevel]::Optimal,$false)"
  if errorlevel 1 (
    echo Zipping the app-image failed.
    exit /b 1
  )

  echo.
  echo Standalone app-image:   !APP_IMAGE_DIR!\%APP_NAME%.exe
  echo Zipped distribution:    !ZIP_PATH!
) else (
  echo.
  echo Installer output is in: %JPACKAGE_OUT%
  dir /b "%JPACKAGE_OUT%"
)

endlocal
