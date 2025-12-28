package com.rts.controller;

import com.rts.dto.ProduceUnitRequest;
import com.rts.model.ChatMessage;
import com.rts.model.Game;
import com.rts.model.GamePlayer;
import com.rts.model.GeneratedMap;
import com.rts.model.ProductionQueueItem;
import com.rts.service.GameService;
import com.rts.service.HeartbeatOutcome;
import com.rts.service.LobbyService;
import com.rts.service.MapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/games")
@CrossOrigin(origins = "*")
public class GameController {

    @Autowired
    private GameService gameService;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private MapService mapService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/start/{lobbyId}")
    public ResponseEntity<?> startGame(@PathVariable Long lobbyId) {
        try {
            Game game = gameService.startGame(lobbyId);

            // Notify all players in lobby to transition to game
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender("System");
            chatMessage.setType(ChatMessage.MessageType.SYSTEM);
            chatMessage.setLobbyId(lobbyId);
            chatMessage.setContent("GAME_START:" + game.getId());

            messagingTemplate.convertAndSend("/topic/lobby/" + lobbyId, chatMessage);

            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGame(@PathVariable Long id) {
        return gameService.getGameById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/with-map")
    public ResponseEntity<?> getGameWithMap(@PathVariable Long id) {
        Optional<Game> gameOpt = gameService.getGameById(id);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("game", game);

        // Get the generated map if available
        Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(id);
        mapOpt.ifPresent(generatedMap -> response.put("map", generatedMap));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-lobby/{lobbyId}")
    public ResponseEntity<?> getGameByLobby(@PathVariable Long lobbyId) {
        return gameService.getGameByLobbyId(lobbyId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/players")
    public ResponseEntity<List<GamePlayer>> getGamePlayers(@PathVariable Long id) {
        return ResponseEntity.ok(gameService.getGamePlayers(id));
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<?> sendHeartbeat(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");
            gameService.updatePlayerHeartbeat(id, playerName);
            return ResponseEntity.ok(Map.of("status", "heartbeat received"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/loading-tick")
    public ResponseEntity<?> loadingTick(@PathVariable Long id){
        HeartbeatOutcome outcome = gameService.tickGame(id);

        switch(outcome){
            case PLAYER_MARKED_DISCONNECTED -> {
                List<GamePlayer> disconnectedPlayers = gameService.getDisconnectedPlayersInGracePeriod(id);
                messagingTemplate.convertAndSend("/topic/game/" + id,
                        Map.of("type", "PLAYER_DISCONNECTED", "players", disconnectedPlayers));
            }
            case ALL_READY -> messagingTemplate.convertAndSend("/topic/game/" + id,
                    Map.of("type","GAME_STATUS","status","ACTIVE"));
            case GRACE_PERIOD_OVER -> {
                messagingTemplate.convertAndSend("/topic/game/" + id,
                        Map.of("type","PLAYERS_BOOTED","message","Disconnected players have been removed"));
            }
            default->{}
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

    @PostMapping("/{id}/player-status")
    public ResponseEntity<?> updatePlayerStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");
            String status = request.get("status");
            gameService.updatePlayerStatus(id, playerName, status);

            // Broadcast status update to all players in game
            messagingTemplate.convertAndSend("/topic/game/" + id,
                    Map.of("type", "PLAYER_STATUS", "playerName", playerName, "status", status));

            return ResponseEntity.ok(Map.of("status", "updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopGame(@PathVariable Long id) {
        try {
            gameService.stopGame(id);
            return ResponseEntity.ok(Map.of("status", "game stopped"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/produce-unit")
    public ResponseEntity<?> produceUnit(@PathVariable Long id, @RequestBody ProduceUnitRequest request) {
        try {
            ProductionQueueItem queueItem = gameService.produceUnit(
                    id,
                    request.getPlayerName(),
                    request.getBuildingX(),
                    request.getBuildingY(),
                    request.getBuildingType(),
                    request.getUnitType()
            );

            // Get updated player resources to send back
            GamePlayer player = gameService.getGamePlayers(id).stream()
                    .filter(p -> p.getPlayerName().equals(request.getPlayerName()))
                    .findFirst()
                    .orElse(null);

            // Broadcast resource update to the player
            if (player != null) {
                messagingTemplate.convertAndSend("/topic/game/" + id,
                        Map.of("type", "RESOURCES_UPDATE",
                                "playerName", request.getPlayerName(),
                                "resources", player.getResources()));
            }

            return ResponseEntity.ok(Map.of(
                    "queueItem", queueItem,
                    "resources", player != null ? player.getResources() : null
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/production-queue")
    public ResponseEntity<?> getProductionQueue(@PathVariable Long id, @RequestParam String playerName) {
        try {
            List<ProductionQueueItem> queue = gameService.getPlayerProductionQueue(id, playerName);
            return ResponseEntity.ok(queue);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/buildings/rally-point")
    public ResponseEntity<?> setRallyPoint(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            System.out.println("Received rally point request: " + request);

            Integer buildingX = (Integer) request.get("buildingX");
            Integer buildingY = (Integer) request.get("buildingY");
            String buildingType = (String) request.get("buildingType");
            Integer rallyPointX = (Integer) request.get("rallyPointX");
            Integer rallyPointY = (Integer) request.get("rallyPointY");

            System.out.println("Parsed values - buildingX: " + buildingX + ", buildingY: " + buildingY +
                             ", buildingType: " + buildingType + ", rallyPointX: " + rallyPointX +
                             ", rallyPointY: " + rallyPointY);

            gameService.setRallyPoint(id, buildingX, buildingY, buildingType, rallyPointX, rallyPointY);

            return ResponseEntity.ok(Map.of("status", "rally point set"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
