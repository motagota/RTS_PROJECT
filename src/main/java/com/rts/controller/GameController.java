package com.rts.controller;

import com.rts.dto.*;
import com.rts.model.Game;
import com.rts.model.GamePlayer;
import com.rts.model.ProductionQueueItem;
import com.rts.model.ResourceNode;
import com.rts.service.GameService;
import com.rts.service.GameServiceExtensions;
import com.rts.service.HeartbeatOutcome;
import com.rts.service.ResourceNodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GameController - Handles game-related HTTP endpoints
 *
 * REFACTORED with proper DTOs and separation of concerns:
 * - All request bodies use DTOs with validation
 * - Business logic delegated to services
 * - Clean separation between API layer and business layer
 */
@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;
    private final GameServiceExtensions gameServiceExt;
    private final SimpMessagingTemplate messagingTemplate;
    private final ResourceNodeService resourceNodeService;

    @Autowired
    public GameController(GameService gameService,
                          GameServiceExtensions gameServiceExt,
                          SimpMessagingTemplate messagingTemplate,
                          ResourceNodeService resourceNodeService) {
        this.gameService = gameService;
        this.gameServiceExt = gameServiceExt;
        this.messagingTemplate = messagingTemplate;
        this.resourceNodeService = resourceNodeService;
    }

    /**
     * Start a new game from a lobby
     */
    @PostMapping("/start/{lobbyId}")
    public ResponseEntity<Game> startGame(@PathVariable Long lobbyId) {
        Game game = gameServiceExt.startGameFromLobby(lobbyId);

        // Notify lobby players via WebSocket
        gameServiceExt.notifyGameStart(lobbyId, game.getId());

        return ResponseEntity.ok(game);
    }

    /**
     * Get game by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Game> getGame(@PathVariable Long id) {
        return gameService.getGameById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get game players
     */
    @GetMapping("/{id}/players")
    public ResponseEntity<List<GamePlayer>> getGamePlayers(@PathVariable Long id) {
        List<GamePlayer> players = gameService.getGamePlayers(id);
        return ResponseEntity.ok(players);
    }

    /**
     * Send heartbeat
     */
    @PostMapping("/{gameId}/players/{playerName}/heartbeat")
    public ResponseEntity<Map<String, String>> sendHeartbeat(
            @PathVariable Long gameId,
            @PathVariable String playerName) {
        gameService.updatePlayerHeartbeat(gameId, playerName);
        return ResponseEntity.ok(Map.of("status", "heartbeat received"));
    }

    /**
     * Update player status (LOADING, READY, etc.)
     */
    @PutMapping("/{gameId}/players/{playerName}/status")
    public ResponseEntity<Map<String, String>> updatePlayerStatus(
            @PathVariable Long gameId,
            @PathVariable String playerName,
            @Valid @RequestBody PlayerStatusRequest request) {
        gameService.updatePlayerStatus(gameId, playerName, request.getStatus());

        // Broadcast status update
        messagingTemplate.convertAndSend("/topic/game/" + gameId,
                Map.of("type", "PLAYER_STATUS", "playerName", playerName, "status", request.getStatus()));

        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    /**
     * Loading tick - polls game status during loading phase
     */
    @GetMapping("/{id}/loading-tick")
    public ResponseEntity<Map<String, Object>> loadingTick(@PathVariable Long id) {
        HeartbeatOutcome outcome = gameService.tickGame(id);

        // Handle different outcomes
        switch (outcome) {
            case PLAYER_MARKED_DISCONNECTED -> {
                List<GamePlayer> disconnectedPlayers = gameService.getDisconnectedPlayersInGracePeriod(id);
                messagingTemplate.convertAndSend("/topic/game/" + id,
                        Map.of("type", "PLAYER_DISCONNECTED", "players", disconnectedPlayers));
            }
            case ALL_READY -> messagingTemplate.convertAndSend("/topic/game/" + id,
                    Map.of("type", "GAME_STATUS", "status", "ACTIVE"));
            case GRACE_PERIOD_OVER -> messagingTemplate.convertAndSend("/topic/game/" + id,
                    Map.of("type", "PLAYERS_BOOTED", "message", "Disconnected players have been removed"));
        }

        String gameStatus = gameService.getGameById(id)
                .map(Game::getStatus)
                .orElse("UNKNOWN");

        List<GamePlayer> disconnectedInGrace = gameService.getDisconnectedPlayersInGracePeriod(id);

        return ResponseEntity.ok(Map.of(
                "gameStatus", gameStatus,
                "outcome", outcome.toString(),
                "disconnectedPlayers", disconnectedInGrace
        ));
    }

    /**
     * Enqueue unit production
     */
    @PostMapping("/{gameId}/players/{playerName}/queue")
    public ResponseEntity<Map<String, Object>> enqueueProduction(
            @PathVariable Long gameId,
            @PathVariable String playerName,
            @Valid @RequestBody EnqueueProductionRequest request) {
        ProductionQueueItem queueItem = gameServiceExt.enqueueUnitProduction(
                gameId, playerName, request.getUnitType());

        // Get updated resources
        GamePlayer player = gameServiceExt.getPlayerByName(gameId, playerName)
                .orElse(null);

        // Broadcast updates
        if (player != null) {
            messagingTemplate.convertAndSend("/topic/game/" + gameId,
                    Map.of("type", "RESOURCES_UPDATE",
                            "playerName", playerName,
                            "resources", player.getResources()));
        }

        return ResponseEntity.ok(Map.of(
                "queueItem", queueItem,
                "resources", player != null ? player.getResources() : null
        ));
    }

    /**
     * Get production queue for a player
     */
    @GetMapping("/{gameId}/players/{playerName}/queue")
    public ResponseEntity<List<ProductionQueueItem>> getProductionQueue(
            @PathVariable Long gameId,
            @PathVariable String playerName) {
        List<ProductionQueueItem> queue = gameService.getPlayerProductionQueue(gameId, playerName);
        return ResponseEntity.ok(queue);
    }

    /**
     * Set rally point for a building
     */
    @PutMapping("/{gameId}/buildings/{buildingId}/rally-point")
    public ResponseEntity<Map<String, String>> setRallyPoint(
            @PathVariable Long gameId,
            @PathVariable Long buildingId,
            @Valid @RequestBody SetRallyPointRequest request) {
        gameServiceExt.setRallyPoint(gameId, buildingId, request.getRallyX(), request.getRallyY());
        return ResponseEntity.ok(Map.of("status", "rally point set"));
    }

    /**
     * Move units to a target location
     */
    @PostMapping("/{gameId}/units/move")
    public ResponseEntity<Map<String, String>> moveUnits(
            @PathVariable Long gameId,
            @Valid @RequestBody MoveUnitsRequest request) {
        gameServiceExt.moveUnits(gameId, request.getUnitIds(), request.getTargetX(), request.getTargetY());
        return ResponseEntity.ok(Map.of("status", "units movement queued"));
    }

    /**
     * Command units to gather from a resource node
     */
    @PostMapping("/{gameId}/units/gather")
    public ResponseEntity<Map<String, String>> gatherResource(
            @PathVariable Long gameId,
            @Valid @RequestBody GatherResourceRequest request) {
        System.out.println("=== GATHER ENDPOINT CALLED ===");
        System.out.println("Game ID: " + gameId);
        System.out.println("Unit IDs: " + request.getUnitIds());
        System.out.println("Resource at: (" + request.getResourceX() + "," + request.getResourceY() + ")");
        gameServiceExt.gatherResource(gameId, request.getUnitIds(), request.getResourceX(), request.getResourceY());
        return ResponseEntity.ok(Map.of("status", "units gathering queued"));
    }

    /**
     * Stop a game
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stopGame(@PathVariable Long id) {
        gameService.stopGame(id);
        return ResponseEntity.ok(Map.of("status", "game stopped"));
    }

    /**
     * Get all resource nodes for a game
     */
    @GetMapping("/{gameId}/resources")
    public ResponseEntity<List<ResourceNodeDTO>> getResourceNodes(@PathVariable Long gameId) {
        System.out.println("GET /api/games/" + gameId + "/resources called");
        List<ResourceNode> nodes = resourceNodeService.getResourceNodes(gameId);
        System.out.println("Found " + nodes.size() + " resource nodes for game " + gameId);
        List<ResourceNodeDTO> dtos = nodes.stream()
                .map(ResourceNodeDTO::new)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get resource node at specific coordinates
     */
    @GetMapping("/{gameId}/resources/{x}/{y}")
    public ResponseEntity<ResourceNodeDTO> getResourceNodeAt(
            @PathVariable Long gameId,
            @PathVariable int x,
            @PathVariable int y) {
        return resourceNodeService.getResourceNodeAt(gameId, x, y)
                .map(node -> ResponseEntity.ok(new ResourceNodeDTO(node)))
                .orElse(ResponseEntity.notFound().build());
    }
}
