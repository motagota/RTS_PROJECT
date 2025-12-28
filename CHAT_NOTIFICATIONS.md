# Chat Notifications for Lobby Events

## Overview

The lobby now broadcasts system notifications via WebSocket chat for key events, ensuring all players are immediately informed of lobby changes.

## Implemented Notifications

### 1. Player Ready Status Changes

**When:** A player clicks "Ready Up" or "Cancel Ready"

**Message Format:**
```
[PlayerName] is ready
[PlayerName] is not ready
```

**Implementation:** [LobbyController.java:67-87](src/main/java/com/rts/controller/LobbyController.java#L67-L87)

**Example:**
```
System: Alice is ready
System: Bob is not ready
System: Charlie is ready
```

### 2. Configuration Changes

**When:** Host changes lobby configuration (max players or map)

**Message Format:**
```
Configuration changed: [details]. All players must ready up again.
```

**Details Include:**
- `Max players set to X` - When max players changed
- `Map changed to [MapName]` - When map changed
- Both if both changed

**Implementation:** [LobbyController.java:89-122](src/main/java/com/rts/controller/LobbyController.java#L89-L122)

**Examples:**
```
System: Configuration changed: Max players set to 8. All players must ready up again.
System: Configuration changed: Map changed to Desert Storm. All players must ready up again.
System: Configuration changed: Max players set to 4, Map changed to Ice World. All players must ready up again.
```

## Technical Implementation

### Backend Changes

**File:** [LobbyController.java](src/main/java/com/rts/controller/LobbyController.java)

1. Added `SimpMessagingTemplate` dependency injection
2. Created `ChatMessage` objects with `SYSTEM` type
3. Broadcast messages to all lobby subscribers via WebSocket

```java
@Autowired
private SimpMessagingTemplate messagingTemplate;

// Send notification
ChatMessage chatMessage = new ChatMessage();
chatMessage.setSender("System");
chatMessage.setType(ChatMessage.MessageType.SYSTEM);
chatMessage.setLobbyId(id);
chatMessage.setContent("...");

messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);
```

### Frontend Changes

**File:** [lobby-room.js](src/main/resources/static/js/lobby-room.js)

Removed local `addSystemMessage()` calls for:
- Ready status changes (line 242)
- Config changes (line 301)
- Max players changes (line 335)

Messages now come from server via WebSocket, ensuring all players see the same notifications simultaneously.

## Benefits

### 1. **Real-time Synchronization**
All players receive notifications at the same time via WebSocket, not just the player who triggered the action.

### 2. **Consistent Messaging**
Server controls message format, ensuring all players see identical messages.

### 3. **Better UX**
Players are immediately aware of:
- When teammates ready up or unready
- When host changes configuration
- What specific settings were changed

### 4. **Reduced Confusion**
Before: Only the player making changes saw local messages
After: All players see the same notifications

## Testing

The notifications can be tested with Selenium:

### Test Scenario: Config Change
```java
// Host changes config
hostDriver.findElement(By.id("mapSelect")).sendKeys("Desert Storm");

// All players (host + guests) should see system message
List<WebElement> messages = player2Driver.findElements(By.className("system-message"));
// Should find: "Configuration changed: Map changed to Desert Storm..."
```

### Test Scenario: Ready Status
```java
// Player readies up
player2Driver.findElement(By.id("readyBtn")).click();

// All players should see system message
List<WebElement> messages = hostDriver.findElements(By.className("system-message"));
// Should find: "Player2 is ready"
```

## Message Flow

```
User Action → REST API → Backend Logic → WebSocket Broadcast
                            ↓
                    Update Database
                            ↓
                    Create ChatMessage
                            ↓
                    Send via WebSocket
                            ↓
            All Connected Clients Receive
                            ↓
                    Display in Chat UI
```

## Future Enhancements

Potential additions:
- Player join/leave notifications (already implemented)
- Game start countdown notifications
- Host transfer notifications
- Kick/ban notifications
- Team assignment notifications

## Related Files

- [LobbyController.java](src/main/java/com/rts/controller/LobbyController.java) - Backend notification logic
- [lobby-room.js](src/main/resources/static/js/lobby-room.js) - Frontend WebSocket handling
- [ChatMessage.java](src/main/java/com/rts/model/ChatMessage.java) - Message model
- [ChatController.java](src/main/java/com/rts/controller/ChatController.java) - WebSocket message routing
