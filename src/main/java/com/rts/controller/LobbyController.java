package com.rts.controller;

import com.rts.dto.AddAIPlayerRequest;
import com.rts.dto.LobbyCreateRequest;
import com.rts.model.ChatMessage;
import com.rts.model.Lobby;
import com.rts.model.Player;
import com.rts.service.LobbyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lobbies")
@CrossOrigin(origins = "*")
public class LobbyController {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<List<Lobby>> getAllLobbies() {
        return ResponseEntity.ok(lobbyService.getAllPublicLobbies());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Lobby>> searchLobbies(@RequestParam String query) {
        return ResponseEntity.ok(lobbyService.searchLobbies(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lobby> getLobby(@PathVariable Long id) {
        return lobbyService.getLobbyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createLobby(@Valid @RequestBody LobbyCreateRequest request) {
        try {
            Lobby lobby = lobbyService.createLobby(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(lobby);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinLobby(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");
            Lobby lobby = lobbyService.joinLobby(id, playerName);
            return ResponseEntity.ok(lobby);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<?> toggleReady(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");
            Player player = lobbyService.togglePlayerReady(id, playerName);
            Lobby lobby = lobbyService.getLobbyById(id).orElseThrow();

            // Send chat notification to all players in lobby
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender(playerName);
            chatMessage.setType(ChatMessage.MessageType.SYSTEM);
            chatMessage.setLobbyId(id);
            chatMessage.setContent(playerName + " is " + (player.getIsReady() ? "ready" : "not ready"));

            messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

            return ResponseEntity.ok(lobby);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/config")
    public ResponseEntity<?> updateConfig(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            Integer maxPlayers = request.get("maxPlayers") != null ?
                Integer.parseInt(request.get("maxPlayers").toString()) : null;
            String mapName = (String) request.get("mapName");

            Lobby lobby = lobbyService.updateLobbyConfig(id, maxPlayers, mapName);

            StringBuilder configMsg = new StringBuilder("Configuration changed: ");
            if (maxPlayers != null) {
                configMsg.append("Max players set to ").append(maxPlayers);
            }
            if (mapName != null) {
                if (maxPlayers != null) configMsg.append(", ");
                configMsg.append("Map changed to ").append(mapName);
            }
            configMsg.append(". All players must ready up again.");

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender("System");
            chatMessage.setType(ChatMessage.MessageType.SYSTEM);
            chatMessage.setLobbyId(id);
            chatMessage.setContent(configMsg.toString());

            messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

            return ResponseEntity.ok(lobby);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leaveLobby(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String playerName = request.get("playerName");

            boolean isHost = lobbyService.isPlayerHost(id, playerName);

            if (isHost) {
       
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setSender("System");
                chatMessage.setType(ChatMessage.MessageType.SYSTEM);
                chatMessage.setLobbyId(id);
                chatMessage.setContent("LOBBY_CLOSED");

                messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

                lobbyService.deleteLobby(id);

                return ResponseEntity.ok(Map.of("lobbyDeleted", true, "message", "Lobby closed"));
            } else {
                lobbyService.removePlayer(id, playerName);

                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setSender(playerName);
                chatMessage.setType(ChatMessage.MessageType.LEAVE);
                chatMessage.setLobbyId(id);
                chatMessage.setContent("");

                messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

                return ResponseEntity.ok(Map.of("lobbyDeleted", false, "message", "Left lobby"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLobby(@PathVariable Long id) {
        lobbyService.deleteLobby(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/add-ai-player")
    public ResponseEntity<?> addAIPlayer(@PathVariable Long id, @Valid @RequestBody AddAIPlayerRequest request) {
        try {
            Lobby lobby = lobbyService.addAIPlayer(id, request.getDifficulty(), request.getAiName());

            String aiName = request.getAiName() != null && !request.getAiName().isEmpty()
                    ? request.getAiName()
                    : "AI Bot (" + request.getDifficulty() + ")";

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender("System");
            chatMessage.setType(ChatMessage.MessageType.SYSTEM);
            chatMessage.setLobbyId(id);
            chatMessage.setContent(aiName + " joined the lobby");

            messagingTemplate.convertAndSend("/topic/lobby/" + id, chatMessage);

            return ResponseEntity.ok(lobby);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{lobbyId}/ai-player/{playerId}")
    public ResponseEntity<?> removeAIPlayer(@PathVariable Long lobbyId, @PathVariable Long playerId) {
        try {
            lobbyService.removeAIPlayer(lobbyId, playerId);

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setSender("System");
            chatMessage.setType(ChatMessage.MessageType.SYSTEM);
            chatMessage.setLobbyId(lobbyId);
            chatMessage.setContent("AI player removed from lobby");

            messagingTemplate.convertAndSend("/topic/lobby/" + lobbyId, chatMessage);

            return ResponseEntity.ok(Map.of("message", "AI player removed"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}