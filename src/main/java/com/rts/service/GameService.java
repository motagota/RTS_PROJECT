package com.rts.service;

import com.rts.model.*;
import com.rts.repository.GamePlayerRepository;
import com.rts.repository.GameRepository;
import com.rts.repository.LobbyRepository;
import com.rts.repository.ProductionQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class GameService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private MapService mapService;

    @Autowired
    private ProductionQueueRepository productionQueueRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MovementService movementService;

    // Thread pool for managing game instances
    private final ExecutorService gameExecutor = Executors.newCachedThreadPool();

    // Map to track active game threads
    private final Map<Long, GameInstanceThread> activeGames = new ConcurrentHashMap<>();

    public Game startGame(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        // Create game instance
        Game game = new Game();
        game.setLobbyId(lobbyId);
        game.setGameName(lobby.getName());
        game.setMapName(lobby.getMapName());
        game.setStatus("LOADING");
        game.setStartedAt(LocalDateTime.now());
        game.setMaxPlayers(lobby.getMaxPlayers());
        game.setGameSettings("{}"); // TODO: Add actual settings

        Game savedGame = gameRepository.save(game);

        // Generate map for the game using new RMS-based generator
        try {
            // The lobby.getMapName() should now be the actual template name (e.g., "arabia")
            // not a display name, since we're storing it correctly from the lobby UI
            String mapTemplateName = lobby.getMapName();
            int playerCount = lobby.getPlayers().size();
            int mapSize = 120; // Default medium map size

            System.out.println("=== MAP GENERATION DEBUG ===");
            System.out.println("Lobby ID: " + lobby.getId());
            System.out.println("Lobby players count: " + playerCount);
            System.out.println("Lobby players: " + lobby.getPlayers());
            System.out.println("Map template: " + mapTemplateName);
            System.out.println("Map size: " + mapSize);
            System.out.println("===========================");

            mapService.generateMapFromRMS(mapTemplateName, playerCount, savedGame.getId(), mapSize);
            System.out.println("Generated RMS map for game " + savedGame.getId() + " using template: " + mapTemplateName + " with " + playerCount + " players");
        } catch (Exception e) {
            System.err.println("Failed to generate map for game " + savedGame.getId() + ": " + e.getMessage());
            e.printStackTrace();
            // Continue without map - can be generated later
        }

        // Create game players from lobby players
        List<Player> lobbyPlayers = lobby.getPlayers();
        for (Player lobbyPlayer : lobbyPlayers) {
            GamePlayer gamePlayer = new GamePlayer();
            gamePlayer.setGame(savedGame);
            gamePlayer.setPlayerName(lobbyPlayer.getName());
            gamePlayer.setPlayerSlot(lobbyPlayer.getSlotNumber());
            gamePlayer.setIsHost(lobbyPlayer.getIsHost());
            gamePlayer.setIsAI(lobbyPlayer.getIsAI());
            gamePlayer.setAiDifficulty(lobbyPlayer.getAiDifficulty());
            gamePlayer.setLastHeartbeat(LocalDateTime.now());

            // AI players are automatically ready and connected
            if (lobbyPlayer.getIsAI()) {
                gamePlayer.setIsConnected(true);
                gamePlayer.setPlayerStatus("READY");
            } else {
                gamePlayer.setIsConnected(false); // Will be set to true when they join
                gamePlayer.setPlayerStatus("LOADING");
            }

            // Assign team colors (cycling through colors)
            String[] colors = {"#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500", "#800080"};
            gamePlayer.setTeamColor(colors[(lobbyPlayer.getSlotNumber() - 1) % colors.length]);

            gamePlayerRepository.save(gamePlayer);
        }

        // Start game instance thread
        GameInstanceThread gameThread = new GameInstanceThread(savedGame.getId(), this);
        activeGames.put(savedGame.getId(), gameThread);
        gameExecutor.submit(gameThread);

        return savedGame;
    }

    public Optional<Game> getGameById(Long id) {
        return gameRepository.findById(id);
    }

    public Optional<Game> getGameByLobbyId(Long lobbyId) {
        return gameRepository.findByLobbyId(lobbyId);
    }

    public void updatePlayerHeartbeat(Long gameId, String playerName) {
        Optional<GamePlayer> playerOpt = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName);
        if (playerOpt.isPresent()) {
            GamePlayer player = playerOpt.get();
            player.setLastHeartbeat(LocalDateTime.now());
            player.setIsConnected(true);
            gamePlayerRepository.save(player);
        }
    }

    public void updatePlayerStatus(Long gameId, String playerName, String status) {
        Optional<GamePlayer> playerOpt = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName);
        if (playerOpt.isPresent()) {
            GamePlayer player = playerOpt.get();
            player.setPlayerStatus(status);
            gamePlayerRepository.save(player);
        }
    }

    public List<GamePlayer> getGamePlayers(Long gameId) {
        return gamePlayerRepository.findByGameId(gameId);
    }

    public void updateGameStatus(Long gameId, String status) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            game.setStatus(status);
            gameRepository.save(game);
        }
    }

    public void checkPlayerHeartbeats(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        LocalDateTime timeout = LocalDateTime.now().minusSeconds(10); // 10 second timeout

        for (GamePlayer player : players) {
            // Skip heartbeat check for AI players
            if (player.getIsAI()) {
                continue;
            }

            // Check if player heartbeat has timed out
            LocalDateTime lastHeartbeat = player.getLastHeartbeat();
            if (lastHeartbeat == null || lastHeartbeat.isBefore(timeout)) {
                // Mark as disconnected if not already
                if (player.getIsConnected()) {
                    player.setIsConnected(false);
                    player.setPlayerStatus("DISCONNECTED");
                    player.setDisconnectedAt(LocalDateTime.now());
                    gamePlayerRepository.save(player);
                }
            } else {
                // Player is connected, clear disconnection timestamp
                if (!player.getIsConnected()) {
                    player.setIsConnected(true);
                    player.setDisconnectedAt(null);
                    // Only update status if they were disconnected, preserve LOADING/READY state
                    if ("DISCONNECTED".equals(player.getPlayerStatus())) {
                        player.setPlayerStatus("LOADING");
                    }
                    gamePlayerRepository.save(player);
                }
            }
        }
    }

    public boolean areAllPlayersReady(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        return players.stream().allMatch(p -> "READY".equals(p.getPlayerStatus()));
    }

    public void stopGame(Long gameId) {
        GameInstanceThread gameThread = activeGames.remove(gameId);
        if (gameThread != null) {
            gameThread.stopGame();
        }

        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isPresent()) {
            Game game = gameOpt.get();
            game.setStatus("COMPLETED");
            game.setCompletedAt(LocalDateTime.now());
            gameRepository.save(game);
        }
    }

    public boolean isAnyPlayerDisconnected(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        return players.stream().anyMatch(p -> "DISCONNECTED".equals(p.getPlayerStatus()));
    }

    public boolean isAnyPlayerDisconnectedAndAllOthersReady(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        boolean allOthersReady = players.stream()
                .allMatch(p-> "READY".equals(p.getPlayerStatus()) || (p.getIsConnected() && "LOADING".equals(p.getPlayerStatus())));
        boolean someoneDisconnected = players.stream()
                .anyMatch(p -> !p.getIsConnected() && "DISCONNECTED".equals(p.getPlayerStatus()));
        return someoneDisconnected && allOthersReady;
    }

    public List<GamePlayer> getDisconnectedPlayersInGracePeriod(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        LocalDateTime now = LocalDateTime.now();

        return players.stream()
                .filter(p -> !p.getIsConnected() && "DISCONNECTED".equals(p.getPlayerStatus()))
                .filter(p -> p.getDisconnectedAt() != null)
                .filter(p -> p.getDisconnectedAt().plusSeconds(p.getGracePeriodSeconds()).isAfter(now))
                .toList();
    }

    public List<GamePlayer> getDisconnectedPlayersGracePeriodExpired(Long gameId) {
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        LocalDateTime now = LocalDateTime.now();

        return players.stream()
                .filter(p -> !p.getIsConnected() && "DISCONNECTED".equals(p.getPlayerStatus()))
                .filter(p -> p.getDisconnectedAt() != null)
                .filter(p -> p.getDisconnectedAt().plusSeconds(p.getGracePeriodSeconds()).isBefore(now))
                .toList();
    }

    public ProductionQueueItem produceUnit(Long gameId, String playerName, Integer buildingX, Integer buildingY,
                                            String buildingType, String unitType) throws Exception {
        // Find the player
        GamePlayer player = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        // Parse unit type
        Unit.UnitType type;
        try {
            type = Unit.UnitType.valueOf(unitType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid unit type: " + unitType);
        }

        // Check if player can afford the unit
        int foodCost = type.getFoodCost();
        int woodCost = type.getWoodCost();
        int stoneCost = type.getStoneCost();
        int goldCost = type.getGoldCost();

        if (!player.getResources().canAfford(woodCost, foodCost, stoneCost, goldCost)) {
            throw new IllegalStateException("Insufficient resources");
        }

        // Deduct resources
        player.getResources().deduct(woodCost, foodCost, stoneCost, goldCost);
        gamePlayerRepository.save(player);

        // Check if this building already has items in production
        List<ProductionQueueItem> existingQueue = productionQueueRepository.findByGameIdAndStatus(gameId, "IN_PROGRESS")
                .stream()
                .filter(item -> item.getBuildingX().equals(buildingX) &&
                               item.getBuildingY().equals(buildingY) &&
                               item.getBuildingType().equals(buildingType))
                .toList();

        boolean hasActiveProduction = !existingQueue.isEmpty();

        // Create production queue item
        ProductionQueueItem queueItem = new ProductionQueueItem();
        queueItem.setGame(gameRepository.findById(gameId).orElseThrow());
        queueItem.setPlayerName(playerName);
        queueItem.setPlayerSlot(player.getPlayerSlot());
        queueItem.setBuildingX(buildingX);
        queueItem.setBuildingY(buildingY);
        queueItem.setBuildingType(buildingType);
        queueItem.setUnitType(unitType);

        // Set production time based on unit type (villager = 25 seconds)
        int productionTime = type == Unit.UnitType.VILLAGER ? 25 : 20;
        queueItem.setProductionTimeSeconds(productionTime);

        if (hasActiveProduction) {
            // Building is busy - add to queue as QUEUED
            queueItem.setStatus("QUEUED");
            queueItem.setStartedAt(null);
            queueItem.setCompletesAt(null);
        } else {
            // Building is free - start production immediately
            queueItem.setStatus("IN_PROGRESS");
            queueItem.setStartedAt(LocalDateTime.now());
            queueItem.setCompletesAt(LocalDateTime.now().plusSeconds(productionTime));
        }

        return productionQueueRepository.save(queueItem);
    }

    public List<ProductionQueueItem> getPlayerProductionQueue(Long gameId, String playerName) {
        // Get both IN_PROGRESS and QUEUED items
        List<ProductionQueueItem> inProgress = productionQueueRepository.findByGameIdAndPlayerNameAndStatus(gameId, playerName, "IN_PROGRESS");
        List<ProductionQueueItem> queued = productionQueueRepository.findByGameIdAndPlayerNameAndStatus(gameId, playerName, "QUEUED");

        // Combine and return
        List<ProductionQueueItem> allItems = new java.util.ArrayList<>(inProgress);
        allItems.addAll(queued);
        return allItems;
    }

    public void processProductionQueue(Long gameId) {
        // Get all in-progress production items for this game
        List<ProductionQueueItem> inProgressItems = productionQueueRepository.findByGameIdAndStatus(gameId, "IN_PROGRESS");
        LocalDateTime now = LocalDateTime.now();

        for (ProductionQueueItem item : inProgressItems) {

            if (item.getCompletesAt() != null && (item.getCompletesAt().isBefore(now) || item.getCompletesAt().isEqual(now))) {
                System.out.println(">>> PRODUCTION COMPLETE - Starting spawn process");
                System.out.flush();
                try {
                    // Spawn the unit on the map
                    spawnUnitFromProduction(gameId, item);

                    // Mark as completed
                    item.setStatus("COMPLETED");
                    productionQueueRepository.save(item);

                    System.out.println("Production completed for player " + item.getPlayerName() +
                            ": " + item.getUnitType() + " at building (" + item.getBuildingX() + "," + item.getBuildingY() + ")");

                    // Start the next queued item for this building
                    startNextQueuedItem(gameId, item.getBuildingX(), item.getBuildingY(), item.getBuildingType());

                    // Notify the player that production is complete
                    messagingTemplate.convertAndSend("/topic/game/" + gameId,
                            Map.of("type", "PRODUCTION_QUEUE_UPDATE",
                                    "playerName", item.getPlayerName()));

                    // Notify that units have changed (new unit spawned)
                    // Get updated unit list and broadcast
                    Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
                    if (mapOpt.isPresent()) {
                        GeneratedMap generatedMap = mapOpt.get();
                        String unitsJson = generatedMap.getUnits();
                        if (unitsJson != null && !unitsJson.isEmpty()) {
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                java.util.List<Unit> units = mapper.readValue(
                                        unitsJson,
                                        mapper.getTypeFactory().constructCollectionType(java.util.List.class, Unit.class)
                                );
                                messagingTemplate.convertAndSend("/topic/game/" + gameId,
                                        Map.of("type", "UNITS_UPDATE",
                                                "units", units));
                            } catch (Exception e) {
                                System.err.println("Error deserializing units: " + e.getMessage());
                            }
                        }
                    }

                } catch (Exception e) {
                    System.err.println("ERROR: Failed to complete production for game " + gameId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void spawnUnitFromProduction(Long gameId, ProductionQueueItem item)  {
        System.out.println(">>> spawnUnitFromProduction: START - method entered");
        System.out.flush();

        // Parse unit type
        System.out.println(">>> About to parse unit type: " + item.getUnitType());
        System.out.flush();
        Unit.UnitType unitType = Unit.UnitType.valueOf(item.getUnitType());
        System.out.println(">>> Parsed unit type: " + unitType);
        System.out.flush();

        // Spawn unit next to the building (not at rally point)
        int spawnX = item.getBuildingX() + 2; // Spawn to the right of building
        int spawnY = item.getBuildingY();
        System.out.println(">>> Spawn pos next to building: (" + spawnX + "," + spawnY + ")");

        Integer rallyPointX = null;
        Integer rallyPointY = null;

        // Check if building has a rally point set
        try {
            Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
            if (mapOpt.isPresent()) {
                GeneratedMap generatedMap = mapOpt.get();
                String buildingsJson = generatedMap.getBuildings();

                if (buildingsJson != null && !buildingsJson.isEmpty()) {
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<Building> buildings = objectMapper.readValue(
                            buildingsJson,
                            objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Building.class)
                    );

                    // Find the building that produced this unit
                    for (Building building : buildings) {
                        if (building.getX() == item.getBuildingX() &&
                            building.getY() == item.getBuildingY() &&
                            building.getType().name().equals(item.getBuildingType())) {

                            // Get rally point if set
                            if (building.getRallyPointX() != null && building.getRallyPointY() != null) {
                                rallyPointX = building.getRallyPointX();
                                rallyPointY = building.getRallyPointY();
                                System.out.println("Rally point found: (" + rallyPointX + "," + rallyPointY + ")");
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking rally point: " + e.getMessage());
        }

        // Spawn the unit next to the building
        System.out.println(">>> Before mapService.spawnUnit call");
        System.out.flush();
        System.out.println(">>> Calling mapService.spawnUnit with gameId=" + gameId + ", pos=(" + spawnX + "," + spawnY + "), type=" + unitType + ", player=" + item.getPlayerSlot());
        System.out.flush();

        mapService.spawnUnit(gameId, spawnX, spawnY, unitType, item.getPlayerSlot());

        // If rally point is set, make the unit walk there
        if (rallyPointX != null && rallyPointY != null) {
            System.out.println("Setting unit to walk to rally point: (" + rallyPointX + "," + rallyPointY + ")");
            mapService.setUnitDestination(gameId, spawnX, spawnY, rallyPointX, rallyPointY);
        }

        System.out.println(">>> After mapService.spawnUnit call - completed successfully");
        System.out.flush();
    }

    private void startNextQueuedItem(Long gameId, Integer buildingX, Integer buildingY, String buildingType) {
        // Find the next queued item for this building (oldest first)
        List<ProductionQueueItem> queuedItems = productionQueueRepository.findByGameIdAndStatus(gameId, "QUEUED")
                .stream()
                .filter(item -> item.getBuildingX().equals(buildingX) &&
                               item.getBuildingY().equals(buildingY) &&
                               item.getBuildingType().equals(buildingType))
                .sorted((a, b) -> a.getId().compareTo(b.getId())) // Oldest first (by ID)
                .toList();

        if (!queuedItems.isEmpty()) {
            ProductionQueueItem nextItem = queuedItems.get(0);

            // Start production
            nextItem.setStatus("IN_PROGRESS");
            nextItem.setStartedAt(LocalDateTime.now());
            nextItem.setCompletesAt(LocalDateTime.now().plusSeconds(nextItem.getProductionTimeSeconds()));
            productionQueueRepository.save(nextItem);

            System.out.println("Started next queued item for building (" + buildingX + "," + buildingY + "): " +
                    nextItem.getUnitType() + " for player " + nextItem.getPlayerName());
        }
    }

    public void bootPlayer(Long gameId, String playerName) {
        Optional<GamePlayer> playerOpt = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName);
        if (playerOpt.isPresent()) {
            GamePlayer player = playerOpt.get();
            player.setPlayerStatus("BOOTED");
            gamePlayerRepository.save(player);
            System.out.println("Game " + gameId + " - Player " + playerName + " has been booted");
        }
    }

    public void bootDisconnectedPlayers(Long gameId) {
        List<GamePlayer> expiredPlayers = getDisconnectedPlayersGracePeriodExpired(gameId);
        for (GamePlayer player : expiredPlayers) {
            bootPlayer(gameId, player.getPlayerName());
        }
    }

    public HeartbeatOutcome tickGame(Long gameId){
        checkPlayerHeartbeats(gameId);

        // Boot players whose grace period has expired
        List<GamePlayer> expiredPlayers = getDisconnectedPlayersGracePeriodExpired(gameId);
        if (!expiredPlayers.isEmpty()) {
            bootDisconnectedPlayers(gameId);
            return HeartbeatOutcome.GRACE_PERIOD_OVER;
        }

        // Check if all connected players are ready (excluding booted players)
        List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
        List<GamePlayer> activePlayers = players.stream()
                .filter(p -> !"BOOTED".equals(p.getPlayerStatus()))
                .toList();

        boolean allActivePlayersReady = activePlayers.stream()
                .allMatch(p -> "READY".equals(p.getPlayerStatus()));

        if (allActivePlayersReady && !activePlayers.isEmpty()) {
            updateGameStatus(gameId, "ACTIVE");
            return HeartbeatOutcome.ALL_READY;
        }

        // Check if any player is in disconnected state
        boolean someoneDisconnected = players.stream()
                .anyMatch(p -> "DISCONNECTED".equals(p.getPlayerStatus()));

        return someoneDisconnected ? HeartbeatOutcome.PLAYER_MARKED_DISCONNECTED : HeartbeatOutcome.NONE;
    }

    /**
     * Helper method to convert display name to template name
     * e.g., "Small Grassland" -> "grassland_small"
     */
    private String getMapTemplateNameFromDisplay(String displayName) {
        // Simple mapping - in production, this should query the database
        if (displayName.contains("Grassland") || displayName.contains("grassland")) {
            return "grassland_small";
        } else if (displayName.contains("Desert") || displayName.contains("desert")) {
            return "desert_medium";
        } else if (displayName.contains("Tundra") || displayName.contains("Snow") || displayName.contains("tundra")) {
            return "snow_large";
        }
        // Default fallback
        return "grassland_small";
    }

    // Game instance thread that runs the game loop
    public static class GameInstanceThread implements Runnable {
        private final Long gameId;
        private final GameService gameService;
        private volatile boolean running = true;

        public GameInstanceThread(Long gameId, GameService gameService) {
            this.gameId = gameId;
            this.gameService = gameService;
        }

        @Override
        public void run() {
            System.out.println("Game instance started for game ID: " + gameId);

            try {
                // Wait for all players to be ready
                while (running && !gameService.areAllPlayersReady(gameId)) {
                    Thread.sleep(1000);
                    gameService.checkPlayerHeartbeats(gameId);

                    // grace period to allow disconnected players to reconnect
                    if (gameService.isAnyPlayerDisconnectedAndAllOthersReady(gameId)) {
                        System.out.println("Game " + gameId + " has disconnected players but all others are READY. Cancelling game");                        
                        gameService.updateGameStatus(gameId, "CANCELLED");                       
                        gameService.stopGame(gameId);
                        return;
                    }
                    
                }

                if (!running) {
                    System.out.println("Game " + gameId + " stopped before becoming ACTIVE");
                    return;
                }

                // Transition to ACTIVE
                gameService.updateGameStatus(gameId, "ACTIVE");
                System.out.println("Game " + gameId + " is now ACTIVE");

                // Main game loop
                while (running) {
                    try {
                        // Check heartbeats
                        gameService.checkPlayerHeartbeats(gameId);

                        // Process production queue
                        gameService.processProductionQueue(gameId);

                        // Process unit movement
                        System.out.println("DEBUG: Game loop calling processUnitMovement for game " + gameId);
                        gameService.mapService.processUnitMovement(gameId);

                        // TODO: Additional game logic here
                        // - Update game state
                        // - Process player commands
                        // - Send updates to clients via WebSocket

                    } catch (Exception e) {
                        System.err.println("Error in game loop for game " + gameId + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    Thread.sleep(100); // 10 FPS game tick
                }

                System.out.println("Game loop exited normally for game " + gameId + " (running=" + running + ")");

            } catch (InterruptedException e) {
                System.err.println("INTERRUPTED: Game thread interrupted for game ID: " + gameId);
                e.printStackTrace();
                Thread.currentThread().interrupt(); // Restore interrupt status
            } catch (Exception e) {
                System.err.println("FATAL: Unhandled exception in game thread for game " + gameId);
                e.printStackTrace();
            } finally {
                System.out.println("Game instance stopped for game ID: " + gameId + " (running=" + running + ")");
            }
        }

        public void stopGame() {
            running = false;
        }
    }

    /**
     * Set rally point for a building
     */
    public void setRallyPoint(Long gameId, Integer buildingX, Integer buildingY, String buildingType,
                              Integer rallyPointX, Integer rallyPointY) throws Exception {
        // Get the map for this game
        Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
        if (mapOpt.isEmpty()) {
            throw new IllegalStateException("Map not found for game " + gameId);
        }

        GeneratedMap generatedMap = mapOpt.get();

        // Deserialize the map to get buildings
        String mapDataJson = mapService.decompressAndDeserializeMapGrid(generatedMap.getTerrainData());
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode mapNode = objectMapper.readTree(mapDataJson);

        // Also get buildings from the buildings JSON field
        String buildingsJson = generatedMap.getBuildings();
        java.util.List<Building> buildings = objectMapper.readValue(
                buildingsJson,
                objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Building.class)
        );

        // Find the building and update its rally point
        boolean found = false;
        for (Building building : buildings) {
            if (building.getX() == buildingX &&
                building.getY() == buildingY &&
                building.getType().name().equals(buildingType)) {

                building.setRallyPointX(rallyPointX);
                building.setRallyPointY(rallyPointY);
                found = true;
                System.out.println("Set rally point for building at (" + buildingX + "," + buildingY + ") to (" + rallyPointX + "," + rallyPointY + ")");
                break;
            }
        }

        if (!found) {
            throw new IllegalStateException("Building not found at (" + buildingX + "," + buildingY + ") with type " + buildingType);
        }

        // Serialize buildings back to JSON and save
        String updatedBuildingsJson = objectMapper.writeValueAsString(buildings);
        System.out.println("Updated buildings JSON: " + updatedBuildingsJson);
        generatedMap.setBuildings(updatedBuildingsJson);
        mapService.saveGeneratedMap(generatedMap);
        System.out.println("Rally point saved successfully");
    }
}
