package com.rts.service;

import com.rts.dto.LobbyCreateRequest;
import com.rts.model.Lobby;
import com.rts.model.Player;
import com.rts.repository.LobbyRepository;
import com.rts.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LobbyService {

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private PlayerRepository playerRepository;

    public List<Lobby> getAllPublicLobbies() {
        return lobbyRepository.findByIsPrivateFalseAndStatus("WAITING");
    }

    public List<Lobby> searchLobbies(String searchTerm) {
        return lobbyRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    public Lobby createLobby(LobbyCreateRequest request) {
        Optional<Lobby> existing = lobbyRepository.findByName(request.getName());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Lobby with this name already exists");
        }

        Lobby lobby = new Lobby();
        lobby.setName(request.getName());
        lobby.setHostPlayer(request.getHostPlayer());
        lobby.setMaxPlayers(request.getMaxPlayers());
        lobby.setCurrentPlayers(1);
        lobby.setStatus("WAITING");
        lobby.setMapName(request.getMapName());
        lobby.setCreatedAt(LocalDateTime.now());
        lobby.setPassword(request.getPassword());
        lobby.setIsPrivate(request.getIsPrivate());

        Lobby savedLobby = lobbyRepository.save(lobby);

        // Create host player
        Player hostPlayer = new Player();
        hostPlayer.setName(request.getHostPlayer());
        hostPlayer.setIsHost(true);
        hostPlayer.setLobby(savedLobby);
        hostPlayer.setSlotNumber(1);
        hostPlayer.setIsReady(false);
        playerRepository.save(hostPlayer);

        savedLobby.addPlayer(hostPlayer);

        return savedLobby;
    }

    public Optional<Lobby> getLobbyById(Long id) {
        return lobbyRepository.findById(id);
    }

    public Lobby joinLobby(Long lobbyId, String playerName) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        if (lobby.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        if (!lobby.isWaiting()) {
            throw new IllegalStateException("Lobby game already started");
        }

        // Check if player already in lobby
        Optional<Player> existingPlayer = playerRepository.findByLobbyIdAndName(lobbyId, playerName);
        if (existingPlayer.isPresent()) {
            return lobby; // Player already joined
        }

        // Add new player
        int nextSlot = lobby.getPlayers().size() + 1;
        Player newPlayer = new Player();
        newPlayer.setName(playerName);
        newPlayer.setIsHost(false);
        newPlayer.setLobby(lobby);
        newPlayer.setSlotNumber(nextSlot);
        newPlayer.setIsReady(false);
        playerRepository.save(newPlayer);

        lobby.addPlayer(newPlayer);

        return lobbyRepository.save(lobby);
    }

    public Player togglePlayerReady(Long lobbyId, String playerName) {
        Player player = playerRepository.findByLobbyIdAndName(lobbyId, playerName)
                .orElseThrow(() -> new IllegalArgumentException("Player not found in lobby"));

        player.setIsReady(!player.getIsReady());
        return playerRepository.save(player);
    }

    public void resetAllPlayersReady(Long lobbyId) {
        List<Player> players = playerRepository.findByLobbyId(lobbyId);
        for (Player player : players) {
            player.setIsReady(false);
        }
        playerRepository.saveAll(players);
    }

    public Lobby updateLobbyConfig(Long lobbyId, Integer maxPlayers, String mapName) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        if (maxPlayers != null) {
            lobby.setMaxPlayers(maxPlayers);
        }
        if (mapName != null) {
            lobby.setMapName(mapName);
        }

        // Reset all player ready states when config changes
        resetAllPlayersReady(lobbyId);

        return lobbyRepository.save(lobby);
    }

    public void deleteLobby(Long id) {
        lobbyRepository.deleteById(id);
    }

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

    public Lobby addAIPlayer(Long lobbyId, String difficulty, String customName) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        if (lobby.isFull()) {
            throw new IllegalStateException("Lobby is full");
        }

        if (!lobby.isWaiting()) {
            throw new IllegalStateException("Lobby game already started");
        }

        // Generate AI name if not provided
        int aiCount = (int) lobby.getPlayers().stream()
                .filter(Player::getIsAI)
                .count() + 1;

        String aiName = customName != null && !customName.isEmpty()
                ? customName
                : "AI Bot " + aiCount + " (" + difficulty + ")";

        // Add AI player
        int nextSlot = lobby.getPlayers().size() + 1;
        Player aiPlayer = new Player();
        aiPlayer.setName(aiName);
        aiPlayer.setIsHost(false);
        aiPlayer.setLobby(lobby);
        aiPlayer.setSlotNumber(nextSlot);
        aiPlayer.setIsReady(true); // AI players are always ready
        aiPlayer.setIsAI(true);
        aiPlayer.setAiDifficulty(difficulty);
        playerRepository.save(aiPlayer);

        lobby.addPlayer(aiPlayer);

        return lobbyRepository.save(lobby);
    }

    public void removeAIPlayer(Long lobbyId, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (!player.getIsAI()) {
            throw new IllegalStateException("Cannot remove non-AI player with this method");
        }

        if (!player.getLobby().getId().equals(lobbyId)) {
            throw new IllegalArgumentException("Player not in specified lobby");
        }

        playerRepository.delete(player);

        // Update current player count
        Lobby lobby = lobbyRepository.findById(lobbyId).orElse(null);
        if (lobby != null) {
            lobby.setCurrentPlayers(lobby.getPlayers().size());
            lobbyRepository.save(lobby);
        }
    }
}