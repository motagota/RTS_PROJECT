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

    @Autowired
    private ResourceNodeService resourceNodeService;

    private final ExecutorService gameExecutor = Executors.newCachedThreadPool();
    private final Map<Long, GameInstanceThread> activeGames = new ConcurrentHashMap<>();

    public Game startGame(Long lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));

        Game game = new Game();
        game.setLobbyId(lobbyId);
        game.setGameName(lobby.getName());
        game.setMapName(lobby.getMapName());
        game.setStatus("LOADING");
        game.setStartedAt(LocalDateTime.now());
        game.setMaxPlayers(lobby.getMaxPlayers());
        game.setGameSettings("{}"); // TODO: Add actual settings

        Game savedGame = gameRepository.save(game);

        try {
            String mapTemplateName = lobby.getMapName();
            int playerCount = lobby.getPlayers().size();
            int mapSize = 120; // Default medium map size

            mapService.generateMapFromRMS(mapTemplateName, playerCount, savedGame.getId(), mapSize);

            Optional<GeneratedMap> generatedMapOpt = mapService.getGeneratedMapByGameId(savedGame.getId());
            if (generatedMapOpt.isPresent()) {
                GeneratedMap generatedMap = generatedMapOpt.get();
                String terrainJson = mapService.decompressAndDeserializeMapGrid(generatedMap.getTerrainData());
                resourceNodeService.initializeResourceNodesFromMap(savedGame, terrainJson);
            }
        } catch (Exception e) {
            System.err.println("Failed to generate map for game " + savedGame.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }

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

            if (lobbyPlayer.getIsAI()) {
                gamePlayer.setIsConnected(true);
                gamePlayer.setPlayerStatus("READY");
            } else {
                gamePlayer.setIsConnected(false);
                gamePlayer.setPlayerStatus("LOADING");
            }

            String[] colors = {"#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500", "#800080"};
            gamePlayer.setTeamColor(colors[(lobbyPlayer.getSlotNumber() - 1) % colors.length]);

            gamePlayerRepository.save(gamePlayer);
        }

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
        LocalDateTime timeout = LocalDateTime.now().minusSeconds(10);

        for (GamePlayer player : players) {
            if (player.getIsAI()) {
                continue;
            }

            LocalDateTime lastHeartbeat = player.getLastHeartbeat();
            if (lastHeartbeat == null || lastHeartbeat.isBefore(timeout)) {
                if (player.getIsConnected()) {
                    player.setIsConnected(false);
                    player.setPlayerStatus("DISCONNECTED");
                    player.setDisconnectedAt(LocalDateTime.now());
                    gamePlayerRepository.save(player);
                }
            } else {
                if (!player.getIsConnected()) {
                    player.setIsConnected(true);
                    player.setDisconnectedAt(null);
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
        GamePlayer player = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        Unit.UnitType type;
        try {
            type = Unit.UnitType.valueOf(unitType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid unit type: " + unitType);
        }

        int foodCost = type.getFoodCost();
        int woodCost = type.getWoodCost();
        int stoneCost = type.getStoneCost();
        int goldCost = type.getGoldCost();

        if (!player.getResources().canAfford(woodCost, foodCost, stoneCost, goldCost)) {
            String errorMsg = String.format(
                "Insufficient resources. Need: %d wood, %d food, %d stone, %d gold. Have: %d wood, %d food, %d stone, %d gold",
                woodCost, foodCost, stoneCost, goldCost,
                player.getResources().getWood(),
                player.getResources().getFood(),
                player.getResources().getStone(),
                player.getResources().getGold()
            );
            throw new IllegalStateException(errorMsg);
        }

        player.getResources().deduct(woodCost, foodCost, stoneCost, goldCost);
        gamePlayerRepository.save(player);

        List<ProductionQueueItem> existingQueue = productionQueueRepository.findByGameIdAndStatus(gameId, "IN_PROGRESS")
                .stream()
                .filter(item -> item.getBuildingX().equals(buildingX) &&
                               item.getBuildingY().equals(buildingY) &&
                               item.getBuildingType().equals(buildingType))
                .toList();

        boolean hasActiveProduction = !existingQueue.isEmpty();

        ProductionQueueItem queueItem = new ProductionQueueItem();
        queueItem.setGame(gameRepository.findById(gameId).orElseThrow());
        queueItem.setPlayerName(playerName);
        queueItem.setPlayerSlot(player.getPlayerSlot());
        queueItem.setBuildingX(buildingX);
        queueItem.setBuildingY(buildingY);
        queueItem.setBuildingType(buildingType);
        queueItem.setUnitType(unitType);

        int productionTime = type == Unit.UnitType.VILLAGER ? 25 : 20;
        queueItem.setProductionTimeSeconds(productionTime);

        if (hasActiveProduction) {
            queueItem.setStatus("QUEUED");
            queueItem.setStartedAt(null);
            queueItem.setCompletesAt(null);
        } else {
            queueItem.setStatus("IN_PROGRESS");
            queueItem.setStartedAt(LocalDateTime.now());
            queueItem.setCompletesAt(LocalDateTime.now().plusSeconds(productionTime));
        }

        return productionQueueRepository.save(queueItem);
    }

    public List<ProductionQueueItem> getPlayerProductionQueue(Long gameId, String playerName) {
        List<ProductionQueueItem> inProgress = productionQueueRepository.findByGameIdAndPlayerNameAndStatus(gameId, playerName, "IN_PROGRESS");
        List<ProductionQueueItem> queued = productionQueueRepository.findByGameIdAndPlayerNameAndStatus(gameId, playerName, "QUEUED");

        List<ProductionQueueItem> allItems = new java.util.ArrayList<>(inProgress);
        allItems.addAll(queued);
        return allItems;
    }

    public void processProductionQueue(Long gameId) {
        List<ProductionQueueItem> inProgressItems = productionQueueRepository.findByGameIdAndStatus(gameId, "IN_PROGRESS");
        LocalDateTime now = LocalDateTime.now();

        for (ProductionQueueItem item : inProgressItems) {

            if (item.getCompletesAt() != null && (item.getCompletesAt().isBefore(now) || item.getCompletesAt().isEqual(now))) {
                try {
                    spawnUnitFromProduction(gameId, item);

                    item.setStatus("COMPLETED");
                    productionQueueRepository.save(item);

                    startNextQueuedItem(gameId, item.getBuildingX(), item.getBuildingY(), item.getBuildingType());

                    messagingTemplate.convertAndSend("/topic/game/" + gameId,
                            Map.of("type", "PRODUCTION_QUEUE_UPDATE",
                                    "playerName", item.getPlayerName()));

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
        Unit.UnitType unitType = Unit.UnitType.valueOf(item.getUnitType());

        int[] spawnPos = findEmptySpawnLocation(gameId, item.getBuildingX(), item.getBuildingY(), item.getBuildingType());
        int spawnX = spawnPos[0];
        int spawnY = spawnPos[1];

        Integer rallyPointX = null;
        Integer rallyPointY = null;

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

                    for (Building building : buildings) {
                        if (building.getX() == item.getBuildingX() &&
                            building.getY() == item.getBuildingY() &&
                            building.getType().name().equals(item.getBuildingType())) {

                            if (building.getRallyPointX() != null && building.getRallyPointY() != null) {
                                rallyPointX = building.getRallyPointX();
                                rallyPointY = building.getRallyPointY();
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking rally point: " + e.getMessage());
        }

        mapService.spawnUnit(gameId, spawnX, spawnY, unitType, item.getPlayerSlot());

        if (rallyPointX != null && rallyPointY != null) {
            if (unitType == Unit.UnitType.VILLAGER) {
                try {
                    Optional<com.rts.model.ResourceNode> resourceOpt =
                        resourceNodeService.getResourceNodeAt(gameId, rallyPointX, rallyPointY);

                    if (resourceOpt.isPresent() && !resourceOpt.get().isDepleted()) {
                        Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
                        if (mapOpt.isPresent()) {
                            GeneratedMap generatedMap = mapOpt.get();
                            String unitsJson = generatedMap.getUnits();

                            if (unitsJson != null && !unitsJson.isEmpty()) {
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                                    new com.fasterxml.jackson.databind.ObjectMapper();
                                java.util.List<Unit> units = objectMapper.readValue(
                                    unitsJson,
                                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Unit.class)
                                );

                                Unit spawnedUnit = null;
                                for (Unit u : units) {
                                    if (u.getX() == spawnX && u.getY() == spawnY) {
                                        spawnedUnit = u;
                                        break;
                                    }
                                }

                                if (spawnedUnit != null) {
                                    java.util.List<Integer> unitIdList = java.util.List.of(spawnedUnit.getId());
                                    mapService.commandGatherResource(gameId, unitIdList, rallyPointX, rallyPointY);
                                }
                            }
                        }
                    } else {
                        mapService.setUnitDestination(gameId, spawnX, spawnY, rallyPointX, rallyPointY);
                    }
                } catch (Exception e) {
                    System.err.println("Error checking rally point resource: " + e.getMessage());
                    e.printStackTrace();
                    mapService.setUnitDestination(gameId, spawnX, spawnY, rallyPointX, rallyPointY);
                }
            } else {
                mapService.setUnitDestination(gameId, spawnX, spawnY, rallyPointX, rallyPointY);
            }
        }
    }

    private int[] findEmptySpawnLocation(Long gameId, int buildingX, int buildingY, String buildingType) {
        try {
            Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                return new int[]{buildingX + 2, buildingY};
            }

            GeneratedMap generatedMap = mapOpt.get();
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

            java.util.List<Unit> units = new java.util.ArrayList<>();
            String unitsJson = generatedMap.getUnits();
            if (unitsJson != null && !unitsJson.isEmpty()) {
                units = objectMapper.readValue(
                        unitsJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Unit.class)
                );
            }

            int buildingWidth = buildingType.equals("TOWN_CENTER") ? 2 : 1;
            int buildingHeight = buildingType.equals("TOWN_CENTER") ? 2 : 1;

            int[][] offsets = {
                {buildingWidth, 0}, {buildingWidth, 1}, {buildingWidth, -1},
                {buildingWidth + 1, 0}, {buildingWidth + 1, 1}, {buildingWidth + 1, -1},
                {0, buildingHeight}, {1, buildingHeight}, {-1, buildingHeight},
                {-1, 0}, {-1, 1}, {-1, -1},
                {0, -1}, {1, -1}, {-1, -1},
                {buildingWidth + 2, 0}, {0, buildingHeight + 1}, {-2, 0}, {0, -2}
            };

            for (int[] offset : offsets) {
                int spawnX = buildingX + offset[0];
                int spawnY = buildingY + offset[1];

                if (spawnX < 0 || spawnX >= generatedMap.getWidth() ||
                    spawnY < 0 || spawnY >= generatedMap.getHeight()) {
                    continue;
                }

                boolean occupied = false;
                for (Unit unit : units) {
                    if (unit.getX() == spawnX && unit.getY() == spawnY) {
                        occupied = true;
                        break;
                    }
                }

                if (!occupied) {
                    return new int[]{spawnX, spawnY};
                }
            }

            return new int[]{buildingX + buildingWidth, buildingY};

        } catch (Exception e) {
            System.err.println("Error finding spawn location: " + e.getMessage());
            return new int[]{buildingX + 2, buildingY};
        }
    }

    private void startNextQueuedItem(Long gameId, Integer buildingX, Integer buildingY, String buildingType) {
        List<ProductionQueueItem> queuedItems = productionQueueRepository.findByGameIdAndStatus(gameId, "QUEUED")
                .stream()
                .filter(item -> item.getBuildingX().equals(buildingX) &&
                               item.getBuildingY().equals(buildingY) &&
                               item.getBuildingType().equals(buildingType))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();

        if (!queuedItems.isEmpty()) {
            ProductionQueueItem nextItem = queuedItems.get(0);

            nextItem.setStatus("IN_PROGRESS");
            nextItem.setStartedAt(LocalDateTime.now());
            nextItem.setCompletesAt(LocalDateTime.now().plusSeconds(nextItem.getProductionTimeSeconds()));
            productionQueueRepository.save(nextItem);
        }
    }

    public void bootPlayer(Long gameId, String playerName) {
        Optional<GamePlayer> playerOpt = gamePlayerRepository.findByGameIdAndPlayerName(gameId, playerName);
        if (playerOpt.isPresent()) {
            GamePlayer player = playerOpt.get();
            player.setPlayerStatus("BOOTED");
            gamePlayerRepository.save(player);
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

        List<GamePlayer> expiredPlayers = getDisconnectedPlayersGracePeriodExpired(gameId);
        if (!expiredPlayers.isEmpty()) {
            bootDisconnectedPlayers(gameId);
            return HeartbeatOutcome.GRACE_PERIOD_OVER;
        }

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
            try {
                // Wait for all players to be ready
                while (running && !gameService.areAllPlayersReady(gameId)) {
                    Thread.sleep(1000);
                    gameService.checkPlayerHeartbeats(gameId);

                    // grace period to allow disconnected players to reconnect
                    if (gameService.isAnyPlayerDisconnectedAndAllOthersReady(gameId)) {
                        gameService.updateGameStatus(gameId, "CANCELLED");
                        gameService.stopGame(gameId);
                        return;
                    }

                }

                if (!running) {
                    return;
                }

                // Transition to ACTIVE
                gameService.updateGameStatus(gameId, "ACTIVE");

                // Main game loop
                while (running) {
                    try {
                        // Check heartbeats
                        gameService.checkPlayerHeartbeats(gameId);

                        // Process production queue
                        gameService.processProductionQueue(gameId);

                        // Process unit movement
                        gameService.mapService.processUnitMovement(gameId);

                        // Send villager stats update (every tick)
                        gameService.sendVillagerStatsUpdate(gameId);

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

            } catch (InterruptedException e) {
                System.err.println("INTERRUPTED: Game thread interrupted for game ID: " + gameId);
                e.printStackTrace();
                Thread.currentThread().interrupt(); // Restore interrupt status
            } catch (Exception e) {
                System.err.println("FATAL: Unhandled exception in game thread for game " + gameId);
                e.printStackTrace();
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
                break;
            }
        }

        if (!found) {
            throw new IllegalStateException("Building not found at (" + buildingX + "," + buildingY + ") with type " + buildingType);
        }

        // Serialize buildings back to JSON and save
        String updatedBuildingsJson = objectMapper.writeValueAsString(buildings);
        generatedMap.setBuildings(updatedBuildingsJson);
        mapService.saveGeneratedMap(generatedMap);
    }

    /**
     * Send villager statistics update to all players
     * Called from game loop every tick
     */
    public void sendVillagerStatsUpdate(Long gameId) {
        try {
            List<GamePlayer> players = gamePlayerRepository.findByGameId(gameId);
            Map<String, Object> allPlayerStats = new java.util.HashMap<>();

            for (GamePlayer player : players) {
                Map<String, Object> playerStats = getVillagerStats(gameId, player.getPlayerSlot());
                allPlayerStats.put("player" + player.getPlayerSlot(), playerStats);
            }

            // Send to all clients
            messagingTemplate.convertAndSend("/topic/game/" + gameId,
                Map.of("type", "VILLAGER_STATS_UPDATE",
                       "stats", allPlayerStats));

        } catch (Exception e) {
            // Don't spam logs - this runs every tick
            // Only log first occurrence or significant errors
        }
    }

    /**
     * Get villager statistics for a specific player
     * Returns counts of idle villagers and villagers working on each resource type
     */
    public Map<String, Object> getVillagerStats(Long gameId, Integer playerId) {
        Map<String, Object> stats = new java.util.HashMap<>();

        try {
            Optional<GeneratedMap> mapOpt = mapService.getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                return stats;
            }

            GeneratedMap generatedMap = mapOpt.get();
            String unitsJson = generatedMap.getUnits();

            if (unitsJson == null || unitsJson.isEmpty()) {
                stats.put("idle", 0);
                stats.put("gold", 0);
                stats.put("stone", 0);
                stats.put("food", 0);
                stats.put("wood", 0);
                return stats;
            }

            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<Unit> units = objectMapper.readValue(
                unitsJson,
                objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Unit.class)
            );

            int idleCount = 0;
            int goldCount = 0;
            int stoneCount = 0;
            int foodCount = 0;
            int woodCount = 0;

            for (Unit unit : units) {
                // Only count villagers for this player
                if (unit.getPlayerNumber() != playerId || unit.getType() != Unit.UnitType.VILLAGER) {
                    continue;
                }

                // Check if idle (not moving, not gathering)
                if (!unit.isMoving() && unit.getGatherState() == Unit.GatherState.IDLE) {
                    idleCount++;
                } else if (unit.getGatherState() == Unit.GatherState.GATHERING ||
                           unit.getGatherState() == Unit.GatherState.MOVING_TO_RESOURCE) {
                    // Unit is actively gathering or moving to gather
                    Long resourceNodeId = unit.getTargetResourceNodeId();
                    if (resourceNodeId != null) {
                        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(resourceNodeId);
                        if (nodeOpt.isPresent()) {
                            ResourceNode node = nodeOpt.get();
                            switch (node.getType()) {
                                case GOLD:
                                    goldCount++;
                                    break;
                                case STONE:
                                    stoneCount++;
                                    break;
                                case BERRIES:
                                case FORAGE:  // Include both BERRIES and FORAGE as food
                                    foodCount++;
                                    break;
                                case TREE:
                                    woodCount++;
                                    break;
                            }
                        }
                    }
                }
            }

            stats.put("idle", idleCount);
            stats.put("gold", goldCount);
            stats.put("stone", stoneCount);
            stats.put("food", foodCount);
            stats.put("wood", woodCount);

        } catch (Exception e) {
            System.err.println("Error calculating villager stats: " + e.getMessage());
            e.printStackTrace();
        }

        return stats;
    }
}
