@echo off
setlocal
cd /d "%~dp0"
set "MVN=mvn"
where mvn >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2023.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    ) else (
        echo Maven was not found. Open pom.xml in IntelliJ IDEA instead.
        pause
        exit /b 1
    )
)
start "" "http://localhost:8080"
call "%MVN%" spring-boot:run
endlocal
