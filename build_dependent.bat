@echo off

SET BAT_PATH=%~dp0
SET BUILD_PATH=%BAT_PATH%\build

rem Purge any existing build folder
rmdir %BUILD_PATH% /s /q 2> nul
mkdir %BUILD_PATH%

rem Compile the app (run tests alongside)
call %BAT_PATH%\mvnw.cmd clean package

rem Extract full version from jar name
for %%i in (%BAT_PATH%\target\*.jar) do (
    set VERSION=%%~ni
)
set VERSION=%VERSION:dark-souls-remastered-uwyg-qol-edition-=%

rem Copy mandatory resources
xcopy %BAT_PATH%\batches %BUILD_PATH% /E /I /Y

rem Rename files
for %%i in (%BAT_PATH%\target\*.jar) do copy "%%i" %BUILD_PATH%\dsr-uwyg-qol-edition-runner.jar

rem Compress the whole folder
powershell.exe -Command "Compress-Archive -Path %BUILD_PATH%/* -DestinationPath %BUILD_PATH%/dsr-uwyg-qol-edition-runner-%VERSION%.zip -Force"