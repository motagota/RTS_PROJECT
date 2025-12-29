package com.rts.service;

import com.rts.model.*;
import com.rts.repository.GamePlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extension methods for GameService with better separation of concerns
 * This service provides cleaner API methods that work with the refactored controller
 */
@Service
public class GameServiceExtensions {

    @Autowired
    private GameService gameService;

    @Autowired
    private MapService mapService;

    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Start game from lobby - cleaner method signature
     */
    public Game startGameFromLobby(Long lobbyId) {
        return gameService.startGame(lobbyId);
    }

    /**
     * Notify game start via WebSocket
     */
    public void notifyGameStart(Long lobbyId, Long gameId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender("System");
        chatMessage.setType(ChatMessage.MessageType.SYSTEM);
        chatMessage.setLobbyId(lobbyId);
        chatMessage.setContent("GAME_START:" + gameId);

        messagingTemplate.convertAndSend("/topic/lobby/" + lobbyId, chatMessage);
    }

    /**
     * Get player by name
     */
    public Optional<GamePlayer> getPlayerByName(Long gameId, String playerName) {
        return gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName);
    }

    /**
     * Enqueue unit production - simplified signature using just unit type
     */
    public ProductionQueueItem enqueueUnitProduction(Long gameId, String playerName, String unitType) {
        try {
            // Get the player's headquarters to use as the production building
            GamePlayer player = getPlayerByName(gameId, playerName)
                    .orElseThrow(() -> new IllegalArgumentException("Player not found"));

            // Get player's headquarters
            Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                throw new IllegalStateException("Map not found");
            }

            GeneratedMap generatedMap = mapOpt.get();
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String buildingsJson = generatedMap.getBuildings();
            List<Building> buildings = objectMapper.readValue(
                    buildingsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Building.class)
            );

            // Find player's headquarters
            Building headquarters = buildings.stream()
                    .filter(b -> b.getType() == Building.BuildingType.HEADQUARTERS
                            && b.getPlayerNumber() == player.getPlayerSlot())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Player headquarters not found"));

            // Use existing produceUnit method
            return gameService.produceUnit(
                    gameId,
                    playerName,
                    headquarters.getX(),
                    headquarters.getY(),
                    headquarters.getType().name(),
                    unitType
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue production: " + e.getMessage(), e);
        }
    }

    /**
     * Set rally point using building ID
     */
    public void setRallyPoint(Long gameId, Long buildingId, Integer rallyX, Integer rallyY) {
        try {
            // Get the building from the map
            Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                throw new IllegalStateException("Map not found");
            }

            GeneratedMap generatedMap = mapOpt.get();
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String buildingsJson = generatedMap.getBuildings();
            List<Building> buildings = objectMapper.readValue(
                    buildingsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Building.class)
            );

            // Find building by ID (using buildingId as index for now since Building doesn't have a DB ID)
            if (buildingId < 0 || buildingId >= buildings.size()) {
                throw new IllegalArgumentException("Building not found");
            }
            Building building = buildings.get(buildingId.intValue());

            // Use existing setRallyPoint method
            gameService.setRallyPoint(
                    gameId,
                    building.getX(),
                    building.getY(),
                    building.getType().name(),
                    rallyX,
                    rallyY
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rally point: " + e.getMessage(), e);
        }
    }

    /**
     * Move multiple units to a target location
     */
    public void moveUnits(Long gameId, List<Integer> unitIds, Integer targetX, Integer targetY) {
        try {
            for (Integer unitId : unitIds) {
                mapService.setUnitDestinationById(gameId, unitId, targetX, targetY);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to move units: " + e.getMessage(), e);
        }
    }
}
