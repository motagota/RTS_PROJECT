package com.rts.controller;

import com.rts.dto.*;
import com.rts.model.Game;
import com.rts.model.GamePlayer;
import com.rts.model.ProductionQueueItem;
import com.rts.service.GameService;
import com.rts.service.GameServiceExtensions;
import com.rts.service.HeartbeatOutcome;
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

    @Autowired
    public GameController(GameService gameService,
                          GameServiceExtensions gameServiceExt,
                          SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.gameServiceExt = gameServiceExt;
        this.messagingTemplate = messagingTemplate;
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
     * Stop a game
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stopGame(@PathVariable Long id) {
        gameService.stopGame(id);
        return ResponseEntity.ok(Map.of("status", "game stopped"));
    }
}
