package com.rts.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LobbyE2ETest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setup() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        baseUrl = "http://localhost:" + port;
    }

    @AfterEach
    void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Host creates lobby and views it")
    void testHostCreatesLobby() throws InterruptedException {
        System.out.println("=== Scenario 1: Host Creates Lobby ===");

        // Navigate to lobby page
        driver.get(baseUrl + "/lobby.html");
        System.out.println("1. Navigated to lobby list page");

        // Click create new lobby
        WebElement createBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("createLobbyBtn")));
        createBtn.click();
        System.out.println("2. Clicked 'Create New Lobby' button");

        // Wait for redirect to create-lobby page
        wait.until(ExpectedConditions.urlContains("create-lobby.html"));
        System.out.println("3. Redirected to create lobby page");

        // Fill in lobby details
        WebElement hostNameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("hostName")));
        hostNameInput.sendKeys("TestHost");
        System.out.println("4. Entered host name: TestHost");

        WebElement lobbyNameInput = driver.findElement(By.id("lobbyName"));
        lobbyNameInput.sendKeys("Epic Battle Arena");
        System.out.println("5. Entered lobby name: Epic Battle Arena");

        // Submit form
        WebElement submitBtn = driver.findElement(By.cssSelector("button[type='submit']"));
        submitBtn.click();
        System.out.println("6. Submitted lobby creation form");

        // Wait for redirect to lobby room
        wait.until(ExpectedConditions.urlContains("lobby-room.html"));
        System.out.println("7. Redirected to lobby room");

        // Verify lobby title
        WebElement lobbyTitle = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("lobbyTitle")));
        Assertions.assertEquals("Epic Battle Arena", lobbyTitle.getText());
        System.out.println("8. Verified lobby title: " + lobbyTitle.getText());

        // Verify player count
        WebElement playerCount = driver.findElement(By.id("playerCount"));
        Assertions.assertEquals("1", playerCount.getText());
        System.out.println("9. Verified player count: 1");

        // Verify host is in slot 1
        Thread.sleep(1000); // Wait for UI to update
        List<WebElement> playerSlots = driver.findElements(By.className("player-slot"));
        Assertions.assertTrue(playerSlots.get(0).getText().contains("TestHost"));
        Assertions.assertTrue(playerSlots.get(0).getText().contains("HOST"));
        System.out.println("10. Verified TestHost is in slot 1 with HOST badge");

        System.out.println("=== Scenario 1: PASSED ===\n");
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Multiple players join and ready up")
    void testMultiplePlayersJoinAndReady() throws InterruptedException {
        System.out.println("=== Scenario 2: Multiple Players Join and Ready ===");

        // Host creates lobby
        WebDriver hostDriver = driver;
        createLobbyAsHost(hostDriver, "Player1", "Battle Royale");
        System.out.println("1. Host (Player1) created lobby: Battle Royale");

        // Get lobby ID from URL
        String lobbyUrl = hostDriver.getCurrentUrl();
        String lobbyId = lobbyUrl.substring(lobbyUrl.indexOf("id=") + 3);
        System.out.println("2. Lobby ID: " + lobbyId);

        // Player 2 joins
        WebDriver player2Driver = createNewDriver();
        player2Driver.get(baseUrl + "/lobby.html");
        joinLobby(player2Driver, "Battle Royale", "Player2");
        System.out.println("3. Player2 joined the lobby");

        // Player 3 joins
        WebDriver player3Driver = createNewDriver();
        player3Driver.get(baseUrl + "/lobby.html");
        joinLobby(player3Driver, "Battle Royale", "Player3");
        System.out.println("4. Player3 joined the lobby");

        // Player 4 joins to fill all slots (default maxPlayers = 4)
        WebDriver player4Driver = createNewDriver();
        player4Driver.get(baseUrl + "/lobby.html");
        joinLobby(player4Driver, "Battle Royale", "Player4");
        System.out.println("5. Player4 joined the lobby - all slots filled!");

        // Wait for all players to see each other
        Thread.sleep(3000);

        // Verify player count on host screen
        WebElement hostPlayerCount = hostDriver.findElement(By.id("playerCount"));
        wait.until(ExpectedConditions.textToBePresentInElement(hostPlayerCount, "4"));
        System.out.println("6. Host sees 4 players in lobby (all slots filled)");

        // Verify start button is still disabled (not all ready yet)
        WebElement startBtn = hostDriver.findElement(By.id("startGameBtn"));
        Assertions.assertTrue(startBtn.getAttribute("disabled") != null);
        System.out.println("7. Start button disabled - waiting for all players to ready");

        // Player 2 readies up
        WebElement player2ReadyBtn = player2Driver.findElement(By.id("readyBtn"));
        player2ReadyBtn.click();
        System.out.println("8. Player2 clicked Ready");
        Thread.sleep(1000);

        // Player 3 readies up
        WebElement player3ReadyBtn = player3Driver.findElement(By.id("readyBtn"));
        player3ReadyBtn.click();
        System.out.println("9. Player3 clicked Ready");
        Thread.sleep(1000);

        // Player 4 readies up
        WebElement player4ReadyBtn = player4Driver.findElement(By.id("readyBtn"));
        player4ReadyBtn.click();
        System.out.println("10. Player4 clicked Ready");
        Thread.sleep(1000);

        // Host readies up (last one)
        WebElement hostReadyBtn = hostDriver.findElement(By.id("readyBtn"));
        hostReadyBtn.click();
        System.out.println("11. Host (Player1) clicked Ready - all players ready!");
        Thread.sleep(2000);

        // Verify all players are ready
        List<WebElement> hostSlots = hostDriver.findElements(By.className("player-slot"));
        int readyCount = 0;
        for (WebElement slot : hostSlots) {
            if (slot.getAttribute("class").contains("ready")) {
                readyCount++;
            }
        }
        Assertions.assertEquals(4, readyCount);
        System.out.println("12. Verified all 4 players are ready");

        // Verify start button is NOW enabled (all slots filled AND all ready)
        startBtn = hostDriver.findElement(By.id("startGameBtn"));
        Assertions.assertFalse(startBtn.getAttribute("disabled") != null);
        System.out.println("13. Start Game button is NOW enabled!");

        // Cleanup
        player2Driver.quit();
        player3Driver.quit();
        player4Driver.quit();

        System.out.println("=== Scenario 2: PASSED ===\n");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Host changes configuration, players unready")
    void testConfigChangeUnsreadiesPlayers() throws InterruptedException {
        System.out.println("=== Scenario 3: Config Change Unreadies Players ===");

        // Host creates lobby
        WebDriver hostDriver = driver;
        createLobbyAsHost(hostDriver, "Host", "Config Test Lobby");
        System.out.println("1. Host created lobby: Config Test Lobby");

        // Host sets max players to 2 for easier testing
        Thread.sleep(1000);
        List<WebElement> playerButtons = hostDriver.findElements(By.className("player-btn"));
        for (WebElement btn : playerButtons) {
            if (btn.getAttribute("data-players").equals("2")) {
                btn.click();
                break;
            }
        }
        System.out.println("2. Host set max players to 2");
        Thread.sleep(2000);

        // Player 2 joins (filling all slots)
        WebDriver player2Driver = createNewDriver();
        player2Driver.get(baseUrl + "/lobby.html");
        joinLobby(player2Driver, "Config Test Lobby", "Player2");
        System.out.println("3. Player2 joined - all slots filled!");

        Thread.sleep(2000);

        // Both players ready up
        WebElement hostReadyBtn = hostDriver.findElement(By.id("readyBtn"));
        hostReadyBtn.click();
        System.out.println("4. Host readied up");

        Thread.sleep(1000);

        WebElement player2ReadyBtn = player2Driver.findElement(By.id("readyBtn"));
        player2ReadyBtn.click();
        System.out.println("5. Player2 readied up");

        Thread.sleep(2000);

        // Verify both are ready
        List<WebElement> hostSlots = hostDriver.findElements(By.className("player-slot"));
        int readyCountBefore = 0;
        for (WebElement slot : hostSlots) {
            if (slot.getAttribute("class").contains("ready")) {
                readyCountBefore++;
            }
        }
        Assertions.assertEquals(2, readyCountBefore);
        System.out.println("6. Both players are ready (all slots filled)");

        // Host changes map
        WebElement mapSelect = hostDriver.findElement(By.id("mapSelect"));
        mapSelect.sendKeys("Desert Storm");
        System.out.println("7. Host changed map to Desert Storm");

        // Wait for config update
        Thread.sleep(3000);

        // Verify players are now unready
        hostSlots = hostDriver.findElements(By.className("player-slot"));
        int readyCountAfter = 0;
        for (WebElement slot : hostSlots) {
            if (slot.getAttribute("class").contains("ready")) {
                readyCountAfter++;
            }
        }
        Assertions.assertEquals(0, readyCountAfter);
        System.out.println("8. Both players are now unready after config change");

        // Verify system message about config change
        Thread.sleep(1000);
        List<WebElement> systemMessages = hostDriver.findElements(By.className("system-message"));
        boolean foundConfigMessage = false;
        for (WebElement msg : systemMessages) {
            if (msg.getText().contains("Configuration changed")) {
                foundConfigMessage = true;
                break;
            }
        }
        Assertions.assertTrue(foundConfigMessage);
        System.out.println("9. System message about config change displayed");

        // Cleanup
        player2Driver.quit();

        System.out.println("=== Scenario 3: PASSED ===\n");
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Chat functionality works")
    void testChatFunctionality() throws InterruptedException {
        System.out.println("=== Scenario 4: Chat Functionality ===");

        // Host creates lobby
        WebDriver hostDriver = driver;
        createLobbyAsHost(hostDriver, "ChatHost", "Chat Test Room");
        System.out.println("1. Host created lobby: Chat Test Room");

        // Player 2 joins
        WebDriver player2Driver = createNewDriver();
        player2Driver.get(baseUrl + "/lobby.html");
        joinLobby(player2Driver, "Chat Test Room", "ChatPlayer");
        System.out.println("2. ChatPlayer joined");

        Thread.sleep(3000);

        // Verify join message appears for host
        List<WebElement> hostMessages = hostDriver.findElements(By.className("system-message"));
        boolean foundJoinMessage = false;
        for (WebElement msg : hostMessages) {
            if (msg.getText().contains("ChatPlayer joined")) {
                foundJoinMessage = true;
                System.out.println("3. Host sees join message: " + msg.getText());
                break;
            }
        }
        Assertions.assertTrue(foundJoinMessage);

        // Host sends chat message
        WebElement hostChatInput = hostDriver.findElement(By.id("chatInput"));
        hostChatInput.sendKeys("Welcome to the lobby!");
        WebElement hostSendBtn = hostDriver.findElement(By.id("sendBtn"));
        hostSendBtn.click();
        System.out.println("4. Host sent message: 'Welcome to the lobby!'");

        Thread.sleep(2000);

        // Player 2 receives and replies
        List<WebElement> player2ChatMessages = player2Driver.findElements(By.className("chat-message"));
        boolean foundHostMessage = false;
        for (WebElement msg : player2ChatMessages) {
            if (msg.getText().contains("ChatHost") && msg.getText().contains("Welcome")) {
                foundHostMessage = true;
                System.out.println("5. Player2 received host message: " + msg.getText());
                break;
            }
        }
        Assertions.assertTrue(foundHostMessage);

        // Player 2 replies
        WebElement player2ChatInput = player2Driver.findElement(By.id("chatInput"));
        player2ChatInput.sendKeys("Thanks! Ready to play!");
        WebElement player2SendBtn = player2Driver.findElement(By.id("sendBtn"));
        player2SendBtn.click();
        System.out.println("6. Player2 sent message: 'Thanks! Ready to play!'");

        Thread.sleep(2000);

        // Host receives reply
        List<WebElement> hostChatMessages = hostDriver.findElements(By.className("chat-message"));
        boolean foundPlayerMessage = false;
        for (WebElement msg : hostChatMessages) {
            if (msg.getText().contains("ChatPlayer") && msg.getText().contains("Ready to play")) {
                foundPlayerMessage = true;
                System.out.println("7. Host received player message: " + msg.getText());
                break;
            }
        }
        Assertions.assertTrue(foundPlayerMessage);

        // Cleanup
        player2Driver.quit();

        System.out.println("=== Scenario 4: PASSED ===\n");
    }

    @Test
    @Order(5)
    @DisplayName("Scenario 5: Host adjusts max players")
    void testMaxPlayersAdjustment() throws InterruptedException {
        System.out.println("=== Scenario 5: Max Players Adjustment ===");

        // Host creates lobby
        createLobbyAsHost(driver, "Host", "Player Limit Test");
        System.out.println("1. Host created lobby: Player Limit Test");

        Thread.sleep(1000);

        // Check initial player count (should be 4 by default)
        WebElement maxPlayersSpan = driver.findElement(By.id("maxPlayers"));
        Assertions.assertEquals("4", maxPlayersSpan.getText());
        System.out.println("2. Initial max players: 4");

        // Count initial empty slots
        List<WebElement> initialSlots = driver.findElements(By.className("player-slot"));
        Assertions.assertEquals(4, initialSlots.size());
        System.out.println("3. Initial slot count: 4");

        // Host clicks 8 players button
        List<WebElement> playerButtons = driver.findElements(By.className("player-btn"));
        WebElement eightPlayerBtn = null;
        for (WebElement btn : playerButtons) {
            if (btn.getAttribute("data-players").equals("8")) {
                eightPlayerBtn = btn;
                break;
            }
        }
        Assertions.assertNotNull(eightPlayerBtn);
        eightPlayerBtn.click();
        System.out.println("4. Host clicked 8 players button");

        Thread.sleep(2000);

        // Verify max players updated
        maxPlayersSpan = driver.findElement(By.id("maxPlayers"));
        wait.until(ExpectedConditions.textToBePresentInElement(maxPlayersSpan, "8"));
        System.out.println("5. Max players updated to: 8");

        // Verify slot count increased
        List<WebElement> updatedSlots = driver.findElements(By.className("player-slot"));
        Assertions.assertEquals(8, updatedSlots.size());
        System.out.println("6. Slot count increased to: 8");

        // Change to 2 players
        WebElement twoPlayerBtn = null;
        playerButtons = driver.findElements(By.className("player-btn"));
        for (WebElement btn : playerButtons) {
            if (btn.getAttribute("data-players").equals("2")) {
                twoPlayerBtn = btn;
                break;
            }
        }
        Assertions.assertNotNull(twoPlayerBtn);
        twoPlayerBtn.click();
        System.out.println("7. Host clicked 2 players button");

        Thread.sleep(2000);

        // Verify decreased to 2
        maxPlayersSpan = driver.findElement(By.id("maxPlayers"));
        wait.until(ExpectedConditions.textToBePresentInElement(maxPlayersSpan, "2"));
        System.out.println("8. Max players decreased to: 2");

        updatedSlots = driver.findElements(By.className("player-slot"));
        Assertions.assertEquals(2, updatedSlots.size());
        System.out.println("9. Slot count decreased to: 2");

        System.out.println("=== Scenario 5: PASSED ===\n");
    }

    @Test
    @Order(6)
    @DisplayName("Scenario 6: Search lobby functionality")
    void testSearchLobby() throws InterruptedException {
        System.out.println("=== Scenario 6: Search Lobby ===");

        // Create lobby first
        WebDriver hostDriver = createNewDriver();
        createLobbyAsHost(hostDriver, "SearchHost", "Searchable Lobby Name");
        System.out.println("1. Created lobby: Searchable Lobby Name");

        // New player searches for lobby
        driver.get(baseUrl + "/lobby.html");
        Thread.sleep(2000);
        System.out.println("2. Opened lobby list page");

        // Enter search term
        WebElement searchInput = driver.findElement(By.id("searchInput"));
        searchInput.clear();
        searchInput.sendKeys("Searchable");
        System.out.println("3. Entered search term: Searchable");

        Thread.sleep(2000);

        // Verify lobby appears in results
        List<WebElement> lobbyCards = driver.findElements(By.className("lobby-card"));
        boolean foundLobby = false;
        for (WebElement card : lobbyCards) {
            if (card.getText().contains("Searchable Lobby Name")) {
                foundLobby = true;
                System.out.println("4. Found lobby in search results");
                break;
            }
        }
        Assertions.assertTrue(foundLobby);

        // Clear search and verify all lobbies show
        searchInput.clear();
        Thread.sleep(2000);
        System.out.println("5. Cleared search");

        lobbyCards = driver.findElements(By.className("lobby-card"));
        Assertions.assertTrue(lobbyCards.size() > 0);
        System.out.println("6. All lobbies displayed: " + lobbyCards.size());

        // Cleanup
        hostDriver.quit();

        System.out.println("=== Scenario 6: PASSED ===\n");
    }

    // Helper Methods

    private void createLobbyAsHost(WebDriver driver, String hostName, String lobbyName) throws InterruptedException {
        driver.get(baseUrl + "/lobby.html");
        WebDriverWait driverWait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement createBtn = driverWait.until(ExpectedConditions.elementToBeClickable(By.id("createLobbyBtn")));
        createBtn.click();

        driverWait.until(ExpectedConditions.urlContains("create-lobby.html"));

        WebElement hostNameInput = driverWait.until(ExpectedConditions.presenceOfElementLocated(By.id("hostName")));
        hostNameInput.sendKeys(hostName);

        WebElement lobbyNameInput = driver.findElement(By.id("lobbyName"));
        lobbyNameInput.sendKeys(lobbyName);

        WebElement submitBtn = driver.findElement(By.cssSelector("button[type='submit']"));
        submitBtn.click();

        driverWait.until(ExpectedConditions.urlContains("lobby-room.html"));
        Thread.sleep(1000);
    }

    private void joinLobby(WebDriver driver, String lobbyName, String playerName) throws InterruptedException {
        WebDriverWait driverWait = new WebDriverWait(driver, Duration.ofSeconds(10));

        Thread.sleep(2000); // Wait for lobbies to load

        // Find and click on the lobby
        List<WebElement> lobbyCards = driver.findElements(By.className("lobby-card"));
        WebElement targetLobby = null;
        for (WebElement card : lobbyCards) {
            if (card.getText().contains(lobbyName)) {
                targetLobby = card;
                break;
            }
        }

        Assertions.assertNotNull(targetLobby, "Lobby not found: " + lobbyName);
        targetLobby.click();
        Thread.sleep(1000);

        // Enter player name in modal
        WebElement playerNameInput = driverWait.until(ExpectedConditions.presenceOfElementLocated(By.id("playerName")));
        playerNameInput.sendKeys(playerName);

        WebElement joinBtn = driver.findElement(By.id("confirmJoinBtn"));
        joinBtn.click();

        driverWait.until(ExpectedConditions.urlContains("lobby-room.html"));
        Thread.sleep(1000);
    }

    private WebDriver createNewDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        return new ChromeDriver(options);
    }
}
