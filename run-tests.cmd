@echo off
echo ============================================
echo Running RTS Lobby Selenium E2E Tests
echo ============================================
echo.

REM Check if Spring Boot is running
echo [1/3] Checking if application is running...
curl -s http://localhost:8080/lobby.html > nul 2>&1
if errorlevel 1 (
    echo WARNING: Spring Boot application may not be running on port 8080
    echo Please start it with: mvnw.cmd spring-boot:run
    echo.
    pause
    exit /b 1
)
echo Application is running!
echo.

REM Run the tests
echo [2/3] Running Selenium tests...
echo.
call mvnw.cmd test -Dtest=LobbyE2ETest

REM Show results
echo.
echo [3/3] Tests completed!
echo Check the output above for results.
echo.
pause
