# Game Start and Instance Management System

## Overview

The game start system creates a separate threaded game instance when the host starts the game, displays a loading splash screen to all players, and implements a heartbeat system to track player connections in real-time.

## Architecture

### Components

1. **Game Entity** - Database representation of a game instance
2. **GamePlayer Entity** - Tracks players in the game with heartbeat data
3. **GameService** - Manages game instances and threads
4. **GameController** - REST API for game operations
5. **Game Frontend** - Loading screen and game UI with heartbeat indicators

## Database Schema

### Game Table
```sql
CREATE TABLE games (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lobby_id BIGINT NOT NULL,
    game_name VARCHAR(255) NOT NULL,
    map_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL, -- LOADING, ACTIVE, PAUSED, COMPLETED
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    max_players INT NOT NULL,
    game_settings TEXT
);
```

### GamePlayer Table
```sql
CREATE TABLE game_players (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    game_id BIGINT NOT NULL,
    player_name VARCHAR(255) NOT NULL,
    player_slot INT NOT NULL,
    is_connected BOOLEAN NOT NULL DEFAULT TRUE,
    last_heartbeat TIMESTAMP,
    player_status VARCHAR(50), -- LOADING, READY, PLAYING, DISCONNECTED
    team_color VARCHAR(7),
    is_host BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE
);
```

## Flow Diagram

```
Host Clicks "Start Game"
        ↓
POST /api/games/start/{lobbyId}
        ↓
GameService.startGame()
        ↓
┌─────────────────────────────┐
│ 1. Create Game record       │
│ 2. Create GamePlayer records│
│ 3. Start GameInstanceThread │
│ 4. Send GAME_START message  │
└─────────────────────────────┘
        ↓
WebSocket broadcast: "GAME_START:{gameId}"
        ↓
All players receive message
        ↓
Redirect to game.html?id={gameId}
        ↓
┌─────────────────────────────┐
│  Loading Splash Screen      │
│  - Show map name            │
│  - List all players         │
│  - Heartbeat indicators     │
│  - Loading progress bar     │
│  - Random game tips         │
└─────────────────────────────┘
        ↓
Each player:
1. Connects to WebSocket
2. Starts 3-second heartbeat
3. Updates status to LOADING
4. Simulates loading (auto-progress)
5. Updates status to READY
        ↓
GameInstanceThread monitors:
- Waits for all players READY
- Checks heartbeats every 1s
        ↓
All players ready?
        ↓
Update game status: ACTIVE
        ↓
┌─────────────────────────────┐
│   Transition to Game        │
│   - Hide loading screen     │
│   - Show game canvas        │
│   - Display player list     │
│   - Show mini-map           │
│   - Start game timer        │
│   - Continue heartbeats     │
└─────────────────────────────┘
```

## Backend Implementation

### GameService.java

**Thread Management:**
```java
private final ExecutorService gameExecutor = Executors.newCachedThreadPool();
private final Map<Long, GameInstanceThread> activeGames = new ConcurrentHashMap<>();
```

**Starting a Game:**
```java
public Game startGame(Long lobbyId) {
    // Create game instance
    Game game = new Game();
    game.setStatus("LOADING");

    // Create game players from lobby
    for (Player lobbyPlayer : lobbyPlayers) {
        GamePlayer gamePlayer = new GamePlayer();
        gamePlayer.setPlayerStatus("LOADING");
        gamePlayer.setLastHeartbeat(LocalDateTime.now());
        // Assign color from palette
    }

    // Start game thread
    GameInstanceThread gameThread = new GameInstanceThread(gameId, this);
    activeGames.put(gameId, gameThread);
    gameExecutor.submit(gameThread);
}
```

**Game Instance Thread:**
```java
public static class GameInstanceThread implements Runnable {
    @Override
    public void run() {
        // Wait for all players to be READY
        while (running && !gameService.areAllPlayersReady(gameId)) {
            Thread.sleep(1000);
            gameService.checkPlayerHeartbeats(gameId);
        }

        // Transition to ACTIVE
        gameService.updateGameStatus(gameId, "ACTIVE");

        // Main game loop (10 FPS)
        while (running) {
            gameService.checkPlayerHeartbeats(gameId);
            // TODO: Game logic
            Thread.sleep(100);
        }
    }
}
```

**Heartbeat System:**
```java
public void checkPlayerHeartbeats(Long gameId) {
    List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
    LocalDateTime timeout = LocalDateTime.now().minusSeconds(10);

    for (GamePlayer player : players) {
        if (player.getLastHeartbeat().isBefore(timeout)) {
            player.setIsConnected(false);
            player.setPlayerStatus("DISCONNECTED");
            gamePlayerRepository.save(player);
        }
    }
}
```

### GameController.java

**REST Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/games/start/{lobbyId}` | Start game from lobby |
| GET | `/api/games/{id}` | Get game details |
| GET | `/api/games/by-lobby/{lobbyId}` | Get game by lobby ID |
| GET | `/api/games/{id}/players` | Get all game players |
| POST | `/api/games/{id}/heartbeat` | Send player heartbeat |
| POST | `/api/games/{id}/player-status` | Update player status |
| POST | `/api/games/{id}/stop` | Stop game instance |

**Heartbeat Endpoint:**
```java
@PostMapping("/{id}/heartbeat")
public ResponseEntity<?> sendHeartbeat(@PathVariable Long id,
                                       @RequestBody Map<String, String> request) {
    String playerName = request.get("playerName");
    gameService.updatePlayerHeartbeat(id, playerName);
    return ResponseEntity.ok(Map.of("status", "heartbeat received"));
}
```

## Frontend Implementation

### game.html Structure

```html
<!-- Loading Splash Screen -->
<div id="loadingScreen">
    - Game title with pulse animation
    - Map name display
    - Player loading list with:
      - Color indicator
      - Player name
      - Status (Loading/Ready)
      - Heartbeat indicator
    - Progress bar with shine effect
    - Random gameplay tip
</div>

<!-- Main Game Screen (hidden initially) -->
<div id="gameScreen">
    - Header: Game title, timer, resources
    - Canvas: Main game area
    - Sidebar: Mini-map, player list with heartbeats
    - Footer: Action buttons
</div>
```

### game.js Logic

**Initialization:**
```javascript
function init() {
    gameId = urlParams.get('id');
    playerName = sessionStorage.getItem('playerName');

    connectToGame();
}
```

**Heartbeat System:**
```javascript
function startHeartbeat() {
    heartbeatInterval = setInterval(async () => {
        await fetch(`${API_BASE_URL}/${gameId}/heartbeat`, {
            method: 'POST',
            body: JSON.stringify({ playerName })
        });
    }, 3000); // Every 3 seconds
}
```

**Loading Simulation:**
```javascript
function simulateLoading() {
    const loadingSteps = [
        { percent: 20, text: 'Loading map assets...' },
        { percent: 40, text: 'Initializing game engine...' },
        { percent: 60, text: 'Syncing game state...' },
        { percent: 80, text: 'Waiting for all players...' },
        { percent: 100, text: 'Ready to start!' }
    ];

    // Progress through steps every 1.5 seconds
    // At 100%, update status to READY
}
```

**Player Status Update:**
```javascript
async function updatePlayerStatus(status) {
    await fetch(`${API_BASE_URL}/${gameId}/player-status`, {
        method: 'POST',
        body: JSON.stringify({ playerName, status })
    });
}
```

### CSS Animations

**Heartbeat Animation:**
```css
@keyframes heartbeat {
    0%, 100% {
        transform: scale(1);
        opacity: 1;
    }
    50% {
        transform: scale(1.3);
        opacity: 0.7;
    }
}

@keyframes heartbeat-ripple {
    0% {
        transform: translate(-50%, -50%) scale(1);
        opacity: 1;
    }
    100% {
        transform: translate(-50%, -50%) scale(2.5);
        opacity: 0;
    }
}
```

**Loading Bar Shine:**
```css
@keyframes loading-shine {
    0% { left: -100%; }
    100% { left: 200%; }
}
```

## Player Status Lifecycle

```
LOADING → READY → PLAYING
    ↓
DISCONNECTED (if heartbeat timeout)
```

## Heartbeat Indicators

### Visual States

**Connected (Green, Animated):**
- Heartbeat indicator pulses
- Ripple effect emanates
- Updates every 3 seconds

**Disconnected (Red, Static):**
- No animation
- Triggered if no heartbeat for 10 seconds

## Color Assignment

Players are automatically assigned team colors:
```javascript
const colors = [
    "#FF0000", // Red
    "#00FF00", // Green
    "#0000FF", // Blue
    "#FFFF00", // Yellow
    "#FF00FF", // Magenta
    "#00FFFF", // Cyan
    "#FFA500", // Orange
    "#800080"  // Purple
];
```

Colors cycle based on player slot number.

## Game Tips System

Random tips displayed during loading:
```javascript
const tips = [
    "Build your base quickly to gain an early advantage!",
    "Scout the map to find resource deposits early.",
    "Don't forget to upgrade your units for better combat effectiveness.",
    "A strong economy is the foundation of a powerful army.",
    "Use terrain to your advantage in battles.",
    "Keep your units grouped for better tactical control.",
    "Expand your base to multiple locations for resource diversity.",
    "Tech rushing can catch opponents off guard!"
];
```

## WebSocket Communication

### Lobby → Game Transition
```javascript
// Sent from GameController
ChatMessage: {
    sender: "System",
    type: "SYSTEM",
    lobbyId: lobbyId,
    content: "GAME_START:{gameId}"
}

// Received in lobby-room.js
if (chatMessage.content.startsWith('GAME_START:')) {
    const gameId = chatMessage.content.split(':')[1];
    window.location.href = `game.html?id=${gameId}`;
}
```

### Game Updates
```javascript
// Subscribe to game channel
stompClient.subscribe(`/topic/game/${gameId}`, handleGameUpdate);

// Message types:
{
    type: "PLAYER_STATUS",
    playerName: "Alice",
    status: "READY"
}

{
    type: "GAME_STATUS",
    status: "ACTIVE"
}
```

## Thread Safety

- `ConcurrentHashMap` for active games tracking
- `volatile boolean running` for thread control
- Database transactions for player status updates
- Executor service manages thread pool

## Cleanup

**On Game Completion:**
```java
public void stopGame(Long gameId) {
    GameInstanceThread gameThread = activeGames.remove(gameId);
    if (gameThread != null) {
        gameThread.stopGame();
    }

    game.setStatus("COMPLETED");
    game.setCompletedAt(LocalDateTime.now());
    gameRepository.save(game);
}
```

**On Page Unload:**
```javascript
window.addEventListener('beforeunload', () => {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
    }
    if (stompClient) {
        stompClient.disconnect();
    }
});
```

## Performance Considerations

- **Thread Pool:** Cached thread pool scales with concurrent games
- **Heartbeat Interval:** 3-second client send, 10-second server timeout
- **Game Loop:** 10 FPS (100ms tick) for game logic
- **WebSocket:** Efficient real-time updates vs HTTP polling

## Future Enhancements

1. **Reconnection Logic:** Allow players to reconnect if disconnected
2. **Spectator Mode:** Join game as observer
3. **Game Recording:** Save game state for replay
4. **AI Players:** Fill empty slots with bots
5. **Pause/Resume:** Host can pause the game
6. **Team Assignment:** Pre-assign players to teams
7. **Match Making:** Auto-match players by skill level

## Related Files

- [Game.java](src/main/java/com/rts/model/Game.java)
- [GamePlayer.java](src/main/java/com/rts/model/GamePlayer.java)
- [GameService.java](src/main/java/com/rts/service/GameService.java)
- [GameController.java](src/main/java/com/rts/controller/GameController.java)
- [game.html](src/main/resources/static/game.html)
- [game.css](src/main/resources/static/css/game.css)
- [game.js](src/main/resources/static/js/game.js)
