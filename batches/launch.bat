@echo off
setlocal

set "JAVA_EXE="

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  )
)

if defined JAVA_EXE goto :run

for /f "delims=" %%J in ('where java 2^>nul') do (
  set "JAVA_EXE=%%J"
  goto :run
)

echo Java not found. Install a JDK or JRE, set JAVA_HOME, or add java.exe to PATH.
exit /b 1

:run
"%JAVA_EXE%" -jar dsr-uwyg-qol-edition-runner.jar
