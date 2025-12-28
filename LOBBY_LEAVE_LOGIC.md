# Lobby Leave Logic

## Overview

The lobby system handles player departures differently based on whether the leaving player is the host or a regular player.

## Behavior

### When Host Leaves

**Action:** Entire lobby is deleted

**Process:**
1. Host clicks "Leave Lobby"
2. Backend sends `LOBBY_CLOSED` system message via WebSocket to all players
3. Backend deletes the entire lobby (cascade removes all players)
4. All remaining players receive alert: "The host has closed the lobby. You will be redirected to the lobby list."
5. All players are redirected to [lobby.html](src/main/resources/static/lobby.html)

**Why?**
- Host owns the lobby
- Without host, game configuration and start cannot be managed
- Prevents orphaned lobbies

### When Regular Player Leaves

**Action:** Player is removed from lobby

**Process:**
1. Player clicks "Leave Lobby"
2. Backend removes player from lobby
3. Updates current player count
4. Sends LEAVE notification via WebSocket to remaining players
5. Leaving player is redirected to [lobby.html](src/main/resources/static/lobby.html)
6. Other players see: "[PlayerName] left the lobby"

**Why?**
- Game can continue with remaining players
- Host can still manage and start the game
- Open slots for new players to join

## Technical Implementation

### Backend - LobbyService

**File:** [LobbyService.java](src/main/java/com/rts/service/LobbyService.java)

Added two new methods:

```java
public boolean isPlayerHost(Long lobbyId, String playerName) {
    Optional<Player> player = playerRepository.findByLobbyIdAndName(lobbyId, playerName);
    return player.isPresent() && player.get().getIsHost();
}

public void removePlayer(Long lobbyId, String playerName) {
    Optional<Player> player = playerRepository.findByLobbyIdAndName(lobbyId, playerName);
    if (player.isPresent()) {
        playerRepository.delete(player.get());

        // Update current player count
        Lobby lobby = lobbyRepository.findById(lobbyId).orElse(null);
        if (lobby != null) {
            lobby.setCurrentPlayers(lobby.getPlayers().size());
            lobbyRepository.save(lobby);
        }
    }
}
```

### Backend - LobbyController

**File:** [LobbyController.java](src/main/java/com/rts/controller/LobbyController.java)

New endpoint: `POST /api/lobbies/{id}/leave`

```java
@PostMapping("/{id}/leave")
public ResponseEntity<?> leaveLobby(@PathVariable Long id, @RequestBody Map<String, String> request) {
    String playerName = request.get("playerName");
    boolean isHost = lobbyService.isPlayerHost(id, playerName);

    if (isHost) {
        // Send LOBBY_CLOSED message to all players
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender("System");
        chatMessage.setType(ChatMessage.MessageType.SYSTEM);
        chatMessage.setLobbyId(id);
        chatMessage.setContent("LOBBY_CLOSED");

        messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

        // Delete entire lobby
        lobbyService.deleteLobby(id);

        return ResponseEntity.ok(Map.of("lobbyDeleted", true));
    } else {
        // Remove player
        lobbyService.removePlayer(id, playerName);

        // Send LEAVE notification
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender(playerName);
        chatMessage.setType(ChatMessage.MessageType.LEAVE);
        chatMessage.setLobbyId(id);

        messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

        return ResponseEntity.ok(Map.of("lobbyDeleted", false));
    }
}
```

### Frontend - lobby-room.js

**File:** [lobby-room.js](src/main/resources/static/js/lobby-room.js)

#### Leave Button Handler (line 83-87)

```javascript
leaveBtn.addEventListener('click', async () => {
    if (confirm('Are you sure you want to leave the lobby?')) {
        await leaveLobby();
    }
});
```

#### leaveLobby Function (line 404-434)

```javascript
async function leaveLobby() {
    try {
        const response = await fetch(`${API_BASE_URL}/${lobbyId}/leave`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ playerName })
        });

        if (!response.ok) throw new Error('Failed to leave lobby');

        const result = await response.json();
        disconnectWebSocket();

        if (result.lobbyDeleted) {
            alert('Lobby closed successfully.');
        }

        window.location.href = 'lobby.html';

    } catch (error) {
        console.error('Error leaving lobby:', error);
        alert('Failed to leave lobby. Redirecting anyway...');
        disconnectWebSocket();
        window.location.href = 'lobby.html';
    }
}
```

#### LOBBY_CLOSED Handler (line 385-390)

```javascript
else if (chatMessage.type === 'SYSTEM') {
    if (chatMessage.content === 'LOBBY_CLOSED') {
        alert('The host has closed the lobby. You will be redirected to the lobby list.');
        disconnectWebSocket();
        window.location.href = 'lobby.html';
    } else {
        addSystemMessage(chatMessage.content);
    }
}
```

## Flow Diagrams

### Host Leaves Flow

```
Host clicks Leave
    ↓
Confirm dialog
    ↓
POST /api/lobbies/{id}/leave
    ↓
Backend checks: isHost = true
    ↓
Send LOBBY_CLOSED via WebSocket
    ↓
Delete lobby from database
    ↓
All players receive LOBBY_CLOSED
    ↓
Alert shown to all players
    ↓
All players redirected to lobby.html
```

### Regular Player Leaves Flow

```
Player clicks Leave
    ↓
Confirm dialog
    ↓
POST /api/lobbies/{id}/leave
    ↓
Backend checks: isHost = false
    ↓
Remove player from database
    ↓
Update currentPlayers count
    ↓
Send LEAVE via WebSocket
    ↓
Remaining players see "[Name] left the lobby"
    ↓
Leaving player redirected to lobby.html
```

## Database Impact

### Host Leaves
- Lobby row: **DELETED**
- All Player rows: **DELETED** (cascade)
- Chat messages: Lost (not persisted)

### Regular Player Leaves
- Player row: **DELETED**
- Lobby row: **UPDATED** (currentPlayers decremented)
- Other players: **UNCHANGED**

## Edge Cases Handled

### 1. Network Failure During Leave
- Frontend catches errors
- Shows alert: "Failed to leave lobby. Redirecting anyway..."
- Disconnects WebSocket
- Redirects to lobby list anyway

### 2. Browser Close/Refresh
- WebSocket disconnects automatically
- Player remains in database until:
  - Host leaves (lobby deleted)
  - Player explicitly leaves next time
  - Could implement timeout/heartbeat in future

### 3. Last Player Leaves (Non-Host)
- Regular player leave logic applies
- Lobby remains in database with 0 players
- Host could theoretically be the last one
- Empty lobbies could be cleaned up with scheduled task (future enhancement)

## Testing Scenarios

### Manual Test: Host Leaves

1. Host creates lobby
2. Two players join
3. Host clicks "Leave Lobby" → Confirms
4. **Expected:**
   - All players see alert about lobby closure
   - All players redirected to lobby list
   - Lobby disappears from database

### Manual Test: Regular Player Leaves

1. Host creates lobby with 4 slots
2. Three players join (4/4 slots filled)
3. Player 2 clicks "Leave Lobby" → Confirms
4. **Expected:**
   - Player 2 redirected to lobby list
   - Host and remaining players see "Player2 left the lobby"
   - Lobby now shows 3/4 players
   - New players can join the open slot

### Selenium Test Addition

```java
@Test
@DisplayName("Scenario 7: Host leaves, lobby closes for all")
void testHostLeavesClosesLobby() throws InterruptedException {
    // Host creates lobby
    WebDriver hostDriver = driver;
    createLobbyAsHost(hostDriver, "Host", "Test Lobby");

    // Player joins
    WebDriver playerDriver = createNewDriver();
    playerDriver.get(baseUrl + "/lobby.html");
    joinLobby(playerDriver, "Test Lobby", "Player1");

    Thread.sleep(2000);

    // Host leaves
    hostDriver.findElement(By.id("leaveBtn")).click();
    hostDriver.switchTo().alert().accept(); // Confirm dialog

    Thread.sleep(2000);

    // Player should see alert and be redirected
    Alert playerAlert = playerDriver.switchTo().alert();
    Assertions.assertTrue(playerAlert.getText().contains("host has closed"));
    playerAlert.accept();

    // Both should be on lobby list page
    wait.until(ExpectedConditions.urlContains("lobby.html"));

    playerDriver.quit();
}
```

## Future Enhancements

1. **Host Transfer**: Instead of deleting lobby, promote another player to host
2. **Grace Period**: Give players 30 seconds to rejoin if disconnected
3. **Empty Lobby Cleanup**: Scheduled task to delete lobbies with 0 players
4. **Kick Player**: Host can remove individual players
5. **Leave Warning**: Show different warning to host ("This will close the lobby for all players")

## Related Files

- [LobbyService.java](src/main/java/com/rts/service/LobbyService.java) - Leave logic
- [LobbyController.java](src/main/java/com/rts/controller/LobbyController.java) - Leave endpoint
- [lobby-room.js](src/main/resources/static/js/lobby-room.js) - Leave handling and UI
- [Lobby.java](src/main/java/com/rts/model/Lobby.java) - Cascade delete configuration
