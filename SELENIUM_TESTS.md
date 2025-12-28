# Selenium End-to-End Tests

This document explains the Selenium E2E tests for the RTS Game Lobby System.

## Prerequisites

1. **Java 21** installed
2. **Chrome browser** installed (WebDriverManager will handle ChromeDriver automatically)
3. **Spring Boot application** running on port 8080 (or specify custom port)

## Test Scenarios

The test suite includes 6 comprehensive scenarios:

### Scenario 1: Host Creates Lobby
- Navigates to lobby list page
- Clicks "Create New Lobby" button
- Fills in host name and lobby name
- Submits the form
- Verifies redirection to lobby room
- Validates lobby title, player count, and host badge

### Scenario 2: Multiple Players Join and Ready Up
- Host creates a lobby (default 4 player slots)
- Three additional players join to fill all 4 slots
- Verifies Start button remains disabled until all slots filled
- All four players ready up sequentially
- Verifies that all players show as ready
- Confirms the Start Game button is enabled ONLY when all slots are filled AND all players are ready

### Scenario 3: Config Change Unreadies Players
- Host creates lobby and sets max players to 2
- Second player joins (all slots filled)
- Both players ready up
- Host changes the map configuration
- Verifies all players are automatically unreadied
- Confirms system message about configuration change

### Scenario 4: Chat Functionality
- Host creates lobby and player joins
- Verifies join notification appears
- Host sends chat message
- Player receives message and replies
- Host receives the reply
- Validates real-time WebSocket chat communication

### Scenario 5: Max Players Adjustment
- Host creates lobby with default 4 players
- Host changes max players to 8
- Verifies player slots increase
- Host changes to 2 players
- Confirms slots decrease accordingly

### Scenario 6: Search Lobby
- Creates a lobby with a specific name
- Uses search functionality to find the lobby
- Verifies search results are filtered
- Clears search and confirms all lobbies display

## Running the Tests

### Option 1: Run All Tests
```bash
mvnw.cmd test -Dtest=LobbyE2ETest
```

### Option 2: Run Specific Scenario
```bash
mvnw.cmd test -Dtest=LobbyE2ETest#testHostCreatesLobby
mvnw.cmd test -Dtest=LobbyE2ETest#testMultiplePlayersJoinAndReady
mvnw.cmd test -Dtest=LobbyE2ETest#testConfigChangeUnsreadiesPlayers
mvnw.cmd test -Dtest=LobbyE2ETest#testChatFunctionality
mvnw.cmd test -Dtest=LobbyE2ETest#testMaxPlayersAdjustment
mvnw.cmd test -Dtest=LobbyE2ETest#testSearchLobby
```

### Option 3: Run with Maven in IDE
- Right-click on `LobbyE2ETest.java`
- Select "Run 'LobbyE2ETest'"

## Test Features

- **Automatic Driver Management**: Uses WebDriverManager to automatically download and configure ChromeDriver
- **Spring Boot Integration**: Tests start with the full Spring Boot application context
- **Multi-Browser Simulation**: Creates multiple WebDriver instances to simulate different users
- **Detailed Logging**: Each test prints step-by-step progress to console
- **Wait Strategies**: Uses explicit waits to handle asynchronous operations
- **Cleanup**: Automatically closes all browser instances after tests

## Test Output

Each test provides detailed console output showing each step:

```
=== Scenario 1: Host Creates Lobby ===
1. Navigated to lobby list page
2. Clicked 'Create New Lobby' button
3. Redirected to create lobby page
4. Entered host name: TestHost
5. Entered lobby name: Epic Battle Arena
...
=== Scenario 1: PASSED ===
```

## Troubleshooting

### Chrome Not Found
If you get "Chrome not found" errors:
- Ensure Chrome is installed
- Update WebDriverManager version in pom.xml

### Port Conflicts
If port 8080 is in use:
- Stop other applications using port 8080
- Or modify `application.properties` to use a different port

### Timeout Issues
If tests timeout:
- Increase wait durations in test code
- Check that Spring Boot application is running
- Verify WebSocket connections are working

### Element Not Found
If elements are not found:
- Check that HTML element IDs match between tests and frontend
- Ensure JavaScript has finished loading before interacting

## Test Configuration

The tests use these configurations:

- **Browser**: Chrome (maximized window)
- **Wait Timeout**: 10 seconds for element visibility
- **Test Order**: Sequential execution using `@Order` annotations
- **Spring Boot**: Random port for avoiding conflicts

## Continuous Integration

To run tests in headless mode (for CI/CD):

Modify the `ChromeOptions` in `setup()`:

```java
options.addArguments("--headless");
options.addArguments("--no-sandbox");
options.addArguments("--disable-dev-shm-usage");
```

## Notes

- Tests are designed to run in order due to data dependencies
- Each test creates its own lobby to avoid conflicts
- Multi-player scenarios use separate WebDriver instances
- Chat tests verify real-time WebSocket functionality
- All tests include proper cleanup to close browser windows