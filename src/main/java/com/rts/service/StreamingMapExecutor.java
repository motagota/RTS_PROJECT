package com.rts.service;

import com.rts.config.BinaryWebSocketHandler;
import com.rts.dto.AstNode;
import com.rts.model.MapCell;
import com.rts.model.MapGrid;
import com.rts.model.Terrain;
import com.rts.service.map.StreamingLandGenerator;
import com.rts.service.map.StreamingLandGenerator.TileUpdate;
import com.rts.utils.BinaryMapEncoder;
import com.rts.utils.GridInitializer;
import com.rts.utils.RNG;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Streaming version of MapExecutor that executes RMS commands
 * and sends real-time updates via WebSocket for each step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingMapExecutor {

    private final StreamingLandGenerator landGenerator;
    private final BinaryWebSocketHandler webSocketHandler;

    // Map of session ID to latch for step-by-step mode
    private final ConcurrentHashMap<String, CountDownLatch> stepLatches = new ConcurrentHashMap<>();

    /**
     * Execute RMS script with streaming updates for each command.
     *
     * @param ast parsed RMS commands
     * @param mapSize map size
     * @param seed random seed
     * @param sessionId WebSocket session ID
     * @param updateInterval how often to send updates during land generation
     * @param stepByStep if true, pause after each step waiting for next-step command
     * @param playerCount number of players (for multiplayer games)
     * @return the final grid
     */
    public MapGrid executeStreaming(List<AstNode> ast, int mapSize, long seed,
                                    String sessionId, int updateInterval, boolean stepByStep, int playerCount) {

        log.info("Executing {} RMS commands for session {} with streaming (stepByStep={})",
                ast.size(), sessionId, stepByStep);

        RNG rng = new RNG(seed);
        MapGrid grid = GridInitializer.createEmptyGrid(mapSize);

        try {
            // Send initial state
            sendStepUpdate(sessionId, 0, ast.size(), "Initialized empty grid", "init");

            // If step-by-step mode, wait for first "next step" command
            if (stepByStep) {
                waitForNextStep(sessionId);
            }

            int landIdCounter = 1;
            List<PlayerOrigin> playerOrigins = new ArrayList<>();

            for (int i = 0; i < ast.size(); i++) {
            AstNode node = ast.get(i);
            String command = node.getType().toLowerCase();
            int provenanceIndex = i;

            log.info("Executing command {}/{}: {}", i + 1, ast.size(), command);

            // Send step start notification
            sendStepUpdate(sessionId, i + 1, ast.size(),
                    "Starting: " + node.getType(), "step_start");

            try {
                switch (command) {
                    case "base_terrain" -> {
                        // Try _arg first (for "base_terrain GRASS"), then "terrain" attribute
                        String terrainType = (String) node.getAttribute("_arg");
                        if (terrainType == null) {
                            terrainType = (String) node.getAttribute("terrain");
                        }
                        Terrain terrain = terrainType != null ?
                                Terrain.fromString(terrainType) : Terrain.GRASS;
                        executeBaseTerrain(grid, terrain, sessionId);
                    }

                    case "create_player_lands" -> {
                        // Get number of players - use from RMS if specified, otherwise use the game's player count
                        Integer numPlayers = node.getAttributeAsInt("number_of_players");
                        if (numPlayers == null) numPlayers = playerCount;

                        log.info("Creating player lands for {} players", numPlayers);

                        executeCreatePlayerLands(grid, node, rng, mapSize,
                                landIdCounter, playerOrigins, sessionId, updateInterval, numPlayers);
                        landIdCounter += numPlayers;
                    }

                    case "create_land" -> {
                        executeCreateLand(grid, node, rng, mapSize,
                                landIdCounter, sessionId, updateInterval);
                        landIdCounter++;
                    }

                    case "create_object" ->{
                        executeCreateObject( grid, node, rng, mapSize, playerOrigins, provenanceIndex, sessionId);
                    }

                    case "create_terrain" -> {
                        executeCreateTerrain(grid, node, rng, mapSize, playerOrigins, provenanceIndex, sessionId, updateInterval);
                    }

                    case "place_headquarters" -> {
                        executePlaceHeadquarters(grid, playerOrigins, sessionId);
                    }

                    default -> {
                        log.warn("Unknown command: {}", command);
                        sendStepUpdate(sessionId, i + 1, ast.size(),
                                "Skipped unknown command: " + command, "step_skip");
                    }
                }

                // Send step completion
                sendStepUpdate(sessionId, i + 1, ast.size(),
                        "Completed: " + node.getType(), "step_complete");

                // If step-by-step mode, wait for next command
                if (stepByStep && i < ast.size() - 1) {
                    waitForNextStep(sessionId);
                }

            } catch (Exception e) {
                log.error("Error executing command {}: {}", command, e.getMessage(), e);
                sendStepUpdate(sessionId, i + 1, ast.size(),
                        "Error: " + e.getMessage(), "step_error");
                throw new RuntimeException("Error in command " + (i + 1) + " (" + command + "): " + e.getMessage(), e);
            }
        }

            // Send final completion
            sendStepUpdate(sessionId, ast.size(), ast.size(),
                    "All commands executed successfully", "complete");

            log.info("Streaming execution complete for session {}", sessionId);
            return grid;

        } finally {
            // Clean up latch for this session (only if sessionId is not null)
            if (sessionId != null) {
                stepLatches.remove(sessionId);
            }
        }
    }

    private void executeBaseTerrain(MapGrid grid, Terrain terrain, String sessionId) {
        log.debug("Setting base terrain to {}", terrain);

        int totalCells = grid.getWidth() * grid.getHeight();
        int batchSize = 1000; // Send updates in batches of 1000 tiles
        List<TileUpdate> tileUpdates = new ArrayList<>();

        // Fill entire grid with base terrain and send updates
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.getCell(x, y).setTerrain(terrain);

                // Add to batch
                tileUpdates.add(new TileUpdate(x, y, terrain, 0, null));

                // Send batch when full
                if (tileUpdates.size() >= batchSize) {
                    sendBaseTerrainUpdate(sessionId, new ArrayList<>(tileUpdates), totalCells);
                    tileUpdates.clear();
                }
            }
        }

        // Send remaining tiles
        if (!tileUpdates.isEmpty()) {
            sendBaseTerrainUpdate(sessionId,new ArrayList<>(tileUpdates), totalCells);
        }
    }

    private void sendBaseTerrainUpdate(String sessionId, List<TileUpdate> tiles, int totalCells) {
        if (sessionId != null && webSocketHandler != null) {
            BaseTerrainUpdate update = new BaseTerrainUpdate(tiles, totalCells);

            // Encode to binary format
            byte[] binaryData = BinaryMapEncoder.encodeBaseTerrainUpdate(update);

            // Send as binary message via native WebSocket
            webSocketHandler.sendBinaryMessage(sessionId, binaryData);

            log.debug("Sent binary base terrain update: {} tiles, {} bytes", tiles.size(), binaryData.length);
        }
    }

    private void executeCreatePlayerLands(MapGrid grid, AstNode node, RNG rng,
                                         int mapSize, int landId,
                                         List<PlayerOrigin> playerOrigins,
                                         String sessionId, int updateInterval, int numPlayers) {

        log.debug("Creating player lands for {} players", numPlayers);
        String terrainType = (String) node.getAttribute("terrain_type");
        Terrain terrain = terrainType != null ? Terrain.fromString(terrainType) : Terrain.DIRT;

        Integer landPercent = node.getAttributeAsInt("land_percent");
        if (landPercent == null) landPercent = 15;

        // AOE-style spawn: scattered circle with randomization
        // Instead of perfect circle, add random offset and vary the radius
        double centerX = mapSize / 2.0;
        double centerY = mapSize / 2.0;
        double baseRadius = mapSize * 0.35;

        for (int i = 0; i < numPlayers; i++) {
            // Player IDs are 1-indexed to match playerSlot numbering in lobby/game
            int playerId = i + 1;

            // Base angle for circular distribution
            double baseAngle = (2 * Math.PI * i) / numPlayers;

            // Add random angle variation (±15 degrees)
            double angleVariation = (rng.nextDouble() - 0.5) * (Math.PI / 6);
            double angle = baseAngle + angleVariation;

            // Vary the radius (90-110% of base radius) for less perfect circle
            double radiusVariation = 0.9 + (rng.nextDouble() * 0.2);
            double radius = baseRadius * radiusVariation;

            // Calculate position with randomization
            int ox = (int) (centerX + radius * Math.cos(angle));
            int oy = (int) (centerY + radius * Math.sin(angle));

            // Add small random offset (±3 tiles)
            ox += (int) ((rng.nextDouble() - 0.5) * 6);
            oy += (int) ((rng.nextDouble() - 0.5) * 6);

            // Ensure within bounds with safety margin
            ox = Math.max(10, Math.min(mapSize - 10, ox));
            oy = Math.max(10, Math.min(mapSize - 10, oy));

            // Calculate tiles per player (divide total land_percent by number of players)
            int targetTiles = (mapSize * mapSize * landPercent) / (100 * numPlayers);

            log.debug("Player {} land at ({},{}) with {} tiles (angle: {}, radius: {})",
                     playerId, ox, oy, targetTiles, Math.toDegrees(angle), radius);

            // Store player origin for later use (e.g., object placement)
            playerOrigins.add(new PlayerOrigin(ox, oy, playerId));

            // Use streaming land generator
            landGenerator.growLandStreaming(grid, rng, ox, oy, targetTiles,
                    terrain, landId + i, playerId, 0,
                    sessionId, updateInterval);
        }
    }

    private void executeCreateLand(MapGrid grid, AstNode node, RNG rng,
                                  int mapSize, int landId,
                                  String sessionId, int updateInterval) {

        log.debug("Creating land");
        String terrainType = (String) node.getAttribute("terrain_type");
        Terrain terrain = terrainType != null ? Terrain.fromString(terrainType) : Terrain.GRASS;

        Integer landPercent = node.getAttributeAsInt("land_percent");
        if (landPercent == null) landPercent = 10;

        // Random position for land
        int ox = (int) (rng.nextDouble() * mapSize);
        int oy = (int) (rng.nextDouble() * mapSize);

        int targetTiles = (mapSize * mapSize * landPercent) / 100;

        log.debug("Creating land at ({},{}) with {} tiles", ox, oy, targetTiles);

        landGenerator.growLandStreaming(grid, rng, ox, oy, targetTiles,
                terrain, landId, null, 0,
                sessionId, updateInterval);
    }

    /**
     * Execute create_object command
     * Places objects (gold, stone, trees) on the map with streaming updates.
     * Supports AOE2-style placement with grouping and distance constraints.
     * @param grid the map grid
     * @param node the AST node containing object placement parameters
     * @param rng random number generator
     * @param mapSize size of the map
     * @param playerOrigins list of player starting positions
     * @param provenanceIndex command index for provenance tracking
     * @param sessionId WebSocket session ID for streaming updates
     */
    private void executeCreateObject(MapGrid grid, AstNode node, RNG rng, int mapSize,
                                     List<PlayerOrigin> playerOrigins,
                                     int provenanceIndex, String sessionId) {
        log.debug("Creating objects");

        // Parse object attributes
        String objectType = (String) node.getAttribute("_arg");
        if (objectType == null) objectType = "GOLD";

        Integer numObjects = node.getAttributeAsInt("number_of_objects");
        if (numObjects == null) numObjects = 5;

        Integer minDistanceToPlayers = node.getAttributeAsInt("min_distance_to_players");
        if (minDistanceToPlayers == null) minDistanceToPlayers = 8;

        Integer maxDistanceToPlayers = node.getAttributeAsInt("max_distance_to_players");
        // If not specified, no max distance constraint

        Boolean placeForEveryPlayer = node.getAttributeAsBoolean("set_place_for_every_player");
        boolean perPlayer = placeForEveryPlayer != null && placeForEveryPlayer;

        Boolean tightGrouping = node.getAttributeAsBoolean("set_tight_grouping");
        boolean useTightGrouping = tightGrouping != null && tightGrouping;

        Integer groupRadius = node.getAttributeAsInt("group_placement_radius");
        if (groupRadius == null) groupRadius = 2;

        log.debug("Placing {} objects (per player: {}, tight grouping: {}, min dist: {}, max dist: {}, group radius: {})",
                objectType, perPlayer, useTightGrouping, minDistanceToPlayers, maxDistanceToPlayers, groupRadius);

        // Stream updates in batches
        List<TileUpdate> placedObjects = new ArrayList<>();
        int updateInterval = 5; // Send update every 5 objects
        int successfulPlacements = 0;

        if (perPlayer && !playerOrigins.isEmpty()) {
            // Place objects for each player
            for (PlayerOrigin playerOrigin : playerOrigins) {
                if (useTightGrouping) {
                    // Place all objects as a tight group near this player
                    successfulPlacements += placeObjectGroup(grid, rng, mapSize, objectType,
                            numObjects, groupRadius, playerOrigin,
                            minDistanceToPlayers, maxDistanceToPlayers,
                            provenanceIndex, placedObjects, sessionId, updateInterval,
                            successfulPlacements, numObjects * playerOrigins.size());
                } else {
                    // Place objects individually near this player
                    for (int i = 0; i < numObjects; i++) {
                        if (placeSingleObject(grid, rng, mapSize, objectType,
                                playerOrigin, minDistanceToPlayers, maxDistanceToPlayers,
                                provenanceIndex, placedObjects)) {
                            successfulPlacements++;

                            // Send batch update when interval reached
                            if (placedObjects.size() >= updateInterval) {
                                sendObjectPlacementUpdate(sessionId, new ArrayList<>(placedObjects),
                                        successfulPlacements, numObjects * playerOrigins.size(), objectType);
                                placedObjects.clear();
                            }
                        }
                    }
                }
            }
        } else {
            // Place objects randomly on map (not per player)
            int objectsToPlace = numObjects;
            for (int i = 0; i < objectsToPlace; i++) {
                if (placeSingleObject(grid, rng, mapSize, objectType,
                        null, minDistanceToPlayers, maxDistanceToPlayers,
                        provenanceIndex, placedObjects)) {
                    successfulPlacements++;

                    // Send batch update when interval reached
                    if (placedObjects.size() >= updateInterval) {
                        sendObjectPlacementUpdate(sessionId, new ArrayList<>(placedObjects),
                                successfulPlacements, objectsToPlace, objectType);
                        placedObjects.clear();
                    }
                }
            }
        }

        // Send final batch if any objects remaining
        if (!placedObjects.isEmpty()) {
            int totalObjects = perPlayer ? numObjects * playerOrigins.size() : numObjects;
            sendObjectPlacementUpdate(sessionId, placedObjects,
                    successfulPlacements, totalObjects, objectType);
        }

        int totalExpected = perPlayer ? numObjects * playerOrigins.size() : numObjects;
        log.debug("Successfully placed {}/{} {} objects", successfulPlacements, totalExpected, objectType);
    }

    /**
     * Place a single object on the map.
     * @param grid the map grid
     * @param rng random number generator
     * @param mapSize size of the map
     * @param objectType type of object to place
     * @param nearPlayer if not null, place near this player within distance constraints
     * @param minDistance minimum distance from players
     * @param maxDistance maximum distance from players (null = no limit)
     * @param provenanceIndex provenance index
     * @param placedObjects list to add placed object to
     * @return true if object was placed successfully
     */
    private boolean placeSingleObject(MapGrid grid, RNG rng, int mapSize, String objectType,
                                      PlayerOrigin nearPlayer, int minDistance, Integer maxDistance,
                                      int provenanceIndex, List<TileUpdate> placedObjects) {
        int maxAttempts = 100;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x, y;

            if (nearPlayer != null && maxDistance != null) {
                // Place within ring around player (between min and max distance)
                double angle = rng.nextDouble() * 2 * Math.PI;
                double distance = minDistance + rng.nextDouble() * (maxDistance - minDistance);
                x = (int) (nearPlayer.x + distance * Math.cos(angle));
                y = (int) (nearPlayer.y + distance * Math.sin(angle));
            } else if (nearPlayer != null) {
                // Place randomly but respect min distance
                x = rng.nextInt(0, mapSize - 1);
                y = rng.nextInt(0, mapSize - 1);
            } else {
                // Place completely randomly
                x = rng.nextInt(0, mapSize - 1);
                y = rng.nextInt(0, mapSize - 1);
            }

            // Ensure within bounds
            if (x < 0 || x >= mapSize || y < 0 || y >= mapSize) continue;

            // Check distance constraints
            if (!checkDistanceConstraints(x, y, nearPlayer, minDistance, maxDistance)) {
                continue;
            }

            // Check if cell is valid for placement
            MapCell cell = grid.getCell(x, y);
            if (cell != null && cell.getTerrain().isWalkable() && cell.getObject() == null) {
                // Place the object
                cell.setObject(objectType);
                cell.setProvenance("create_object:" + provenanceIndex);

                // Add to update batch
                placedObjects.add(new TileUpdate(
                        x, y,
                        cell.getTerrain(),
                        cell.getLandId(),
                        cell.getOwner(),
                        objectType
                ));
                return true;
            }
        }

        log.warn("Failed to place {} object after {} attempts", objectType, maxAttempts);
        return false;
    }

    /**
     * Place a group of objects tightly clustered together.
     * @return number of objects successfully placed
     */
    private int placeObjectGroup(MapGrid grid, RNG rng, int mapSize, String objectType,
                                 int numObjects, int groupRadius, PlayerOrigin nearPlayer,
                                 int minDistance, Integer maxDistance, int provenanceIndex,
                                 List<TileUpdate> placedObjects, String sessionId,
                                 int updateInterval, int currentCount, int totalCount) {
        int maxAttempts = 100;
        int placed = 0;

        // First, find a valid center point for the group
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int centerX, centerY;

            if (maxDistance != null) {
                // Place within ring around player
                double angle = rng.nextDouble() * 2 * Math.PI;
                double distance = minDistance + rng.nextDouble() * (maxDistance - minDistance);
                centerX = (int) (nearPlayer.x + distance * Math.cos(angle));
                centerY = (int) (nearPlayer.y + distance * Math.sin(angle));
            } else {
                // Random center that respects min distance
                centerX = rng.nextInt(groupRadius, mapSize - groupRadius - 1);
                centerY = rng.nextInt(groupRadius, mapSize - groupRadius - 1);
            }

            // Check if center is valid
            if (!checkDistanceConstraints(centerX, centerY, nearPlayer, minDistance, maxDistance)) {
                continue;
            }

            // Try to place objects in a tight cluster around this center
            int groupPlaced = 0;
            List<TileUpdate> groupTiles = new ArrayList<>();

            for (int i = 0; i < numObjects && groupPlaced < numObjects; i++) {
                // Try to place within groupRadius of center
                for (int r = 0; r <= groupRadius && groupPlaced < numObjects; r++) {
                    for (int dx = -r; dx <= r && groupPlaced < numObjects; dx++) {
                        for (int dy = -r; dy <= r && groupPlaced < numObjects; dy++) {
                            if (Math.abs(dx) + Math.abs(dy) > groupRadius) continue;

                            int x = centerX + dx;
                            int y = centerY + dy;

                            if (x < 0 || x >= mapSize || y < 0 || y >= mapSize) continue;

                            MapCell cell = grid.getCell(x, y);
                            if (cell != null && cell.getTerrain().isWalkable() && cell.getObject() == null) {
                                cell.setObject(objectType);
                                cell.setProvenance("create_object:" + provenanceIndex);

                                groupTiles.add(new TileUpdate(
                                        x, y,
                                        cell.getTerrain(),
                                        cell.getLandId(),
                                        cell.getOwner(),
                                        objectType
                                ));
                                groupPlaced++;

                                if (groupPlaced >= numObjects) break;
                            }
                        }
                    }
                }
            }

            // If we placed at least some objects, consider it successful
            if (groupPlaced > 0) {
                placedObjects.addAll(groupTiles);
                placed = groupPlaced;

                // Send updates if needed
                if (placedObjects.size() >= updateInterval) {
                    sendObjectPlacementUpdate(sessionId, new ArrayList<>(placedObjects),
                            currentCount + placed, totalCount, objectType);
                    placedObjects.clear();
                }

                log.debug("Placed tight group of {}/{} {} objects at ({}, {})",
                        groupPlaced, numObjects, objectType, centerX, centerY);
                return placed;
            }
        }

        log.warn("Failed to place tight group of {} objects after {} attempts", objectType, maxAttempts);
        return placed;
    }

    /**
     * Check if a position satisfies distance constraints relative to a player.
     */
    private boolean checkDistanceConstraints(int x, int y, PlayerOrigin nearPlayer,
                                             int minDistance, Integer maxDistance) {
        if (nearPlayer == null) return true;

        int dist = Math.abs(x - nearPlayer.x) + Math.abs(y - nearPlayer.y);

        if (dist < minDistance) return false;
        if (maxDistance != null && dist > maxDistance) return false;

        return true;
    }

    /**
     * Execute create_terrain command
     * Places terrain clumps (forests, etc.) on the map with streaming updates.
     * Supports AOE2-style woodlines with clumping and player avoidance.
     * @param grid the map grid
     * @param node the AST node containing terrain placement parameters
     * @param rng random number generator
     * @param mapSize size of the map
     * @param playerOrigins list of player starting positions
     * @param provenanceIndex command index for provenance tracking
     * @param sessionId WebSocket session ID for streaming updates
     * @param updateInterval how often to send updates
     */
    private void executeCreateTerrain(MapGrid grid, AstNode node, RNG rng, int mapSize,
                                      List<PlayerOrigin> playerOrigins,
                                      int provenanceIndex, String sessionId, int updateInterval) {
        log.debug("Creating terrain clumps");

        // Parse terrain attributes
        String terrainType = (String) node.getAttribute("_arg");
        if (terrainType == null) terrainType = "FOREST";
        Terrain terrain = Terrain.fromString(terrainType);

        String baseTerrainType = (String) node.getAttribute("base_terrain");
        Terrain baseTerrain = baseTerrainType != null ? Terrain.fromString(baseTerrainType) : Terrain.GRASS;

        Integer numClumps = node.getAttributeAsInt("number_of_clumps");
        if (numClumps == null) numClumps = 2;

        Integer tilesPerClump = node.getAttributeAsInt("number_of_tiles");
        if (tilesPerClump == null) tilesPerClump = 25;

        Integer clumpingFactor = node.getAttributeAsInt("clumping_factor");
        if (clumpingFactor == null) clumpingFactor = 20;

        Integer spacingToOtherTerrain = node.getAttributeAsInt("spacing_to_other_terrain_types");
        if (spacingToOtherTerrain == null) spacingToOtherTerrain = 2;

        Boolean avoidPlayerStarts = node.getAttributeAsBoolean("set_avoid_player_start_areas");
        boolean shouldAvoidPlayers = avoidPlayerStarts != null && avoidPlayerStarts;

        Boolean placeForEveryPlayer = node.getAttributeAsBoolean("set_place_for_every_player");
        boolean perPlayer = placeForEveryPlayer != null && placeForEveryPlayer;

        Integer minDistanceToPlayers = node.getAttributeAsInt("min_distance_to_players");
        Integer maxDistanceToPlayers = node.getAttributeAsInt("max_distance_to_players");

        // Determine placement strategy
        int minDistance, maxDistance;
        if (perPlayer) {
            // Place near players (e.g., woodlines near TC)
            minDistance = minDistanceToPlayers != null ? minDistanceToPlayers : 6;
            maxDistance = maxDistanceToPlayers != null ? maxDistanceToPlayers : 12;
        } else if (shouldAvoidPlayers) {
            // Avoid player start areas (e.g., center forests)
            minDistance = 13;
            maxDistance = -1; // No max limit
        } else {
            // Random placement anywhere
            minDistance = 0;
            maxDistance = -1;
        }

        log.debug("Placing {} terrain: {} clumps of {} tiles each (clumping: {}, per player: {}, avoid players: {}, min dist: {}, max dist: {}, spacing: {})",
                terrainType, numClumps, tilesPerClump, clumpingFactor, perPlayer, shouldAvoidPlayers, minDistance, maxDistance, spacingToOtherTerrain);

        List<TileUpdate> placedTiles = new ArrayList<>();
        int totalTilesToPlace = numClumps * (perPlayer && !playerOrigins.isEmpty() ? playerOrigins.size() : 1) * tilesPerClump;
        int successfulPlacements = 0;

        // Place clumps for each player if set_place_for_every_player is true
        if (perPlayer && !playerOrigins.isEmpty()) {
            for (PlayerOrigin playerOrigin : playerOrigins) {
                for (int clump = 0; clump < numClumps; clump++) {
                    int placed = placeTerrainClump(grid, rng, mapSize, terrain, baseTerrain,
                            tilesPerClump, clumpingFactor, spacingToOtherTerrain,
                            playerOrigin, minDistance, maxDistance, provenanceIndex, placedTiles,
                            sessionId, updateInterval, successfulPlacements, totalTilesToPlace);
                    successfulPlacements += placed;
                }
            }
        } else {
            // Place clumps randomly on map (or avoiding players if set)
            for (int clump = 0; clump < numClumps; clump++) {
                PlayerOrigin referencePlayer = (shouldAvoidPlayers && !playerOrigins.isEmpty())
                        ? playerOrigins.get(rng.nextInt(playerOrigins.size()))
                        : null;
                int placed = placeTerrainClump(grid, rng, mapSize, terrain, baseTerrain,
                        tilesPerClump, clumpingFactor, spacingToOtherTerrain,
                        referencePlayer, minDistance, maxDistance, provenanceIndex, placedTiles,
                        sessionId, updateInterval, successfulPlacements, totalTilesToPlace);
                successfulPlacements += placed;
            }
        }

        // Send final batch
        if (!placedTiles.isEmpty()) {
            sendTerrainPlacementUpdate(sessionId, placedTiles,
                    successfulPlacements, totalTilesToPlace, terrainType);
        }

        log.debug("Successfully placed {}/{} {} terrain tiles", successfulPlacements, totalTilesToPlace, terrainType);
    }

    /**
     * Place a single clump of terrain tiles.
     * @param minDistance minimum distance from player (0 = no minimum)
     * @param maxDistance maximum distance from player (-1 = no maximum)
     * @return number of tiles successfully placed
     */
    private int placeTerrainClump(MapGrid grid, RNG rng, int mapSize, Terrain terrain,
                                  Terrain baseTerrain, int targetTiles, int clumpingFactor,
                                  int spacing, PlayerOrigin nearPlayer, int minDistance, int maxDistance,
                                  int provenanceIndex, List<TileUpdate> placedTiles,
                                  String sessionId, int updateInterval,
                                  int currentCount, int totalCount) {
        int maxAttempts = 100;

        // Try to find a valid center point for the clump
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int centerX, centerY;

            if (nearPlayer != null && maxDistance > 0) {
                // Place within ring around player (between min and max distance)
                double angle = rng.nextDouble() * 2 * Math.PI;
                double distance = minDistance + rng.nextDouble() * (maxDistance - minDistance);
                centerX = (int) (nearPlayer.x + distance * Math.cos(angle));
                centerY = (int) (nearPlayer.y + distance * Math.sin(angle));
            } else if (nearPlayer != null && minDistance > 0) {
                // Place randomly but avoid player start area (no max distance)
                double angle = rng.nextDouble() * 2 * Math.PI;
                double distance = minDistance + rng.nextDouble() * (mapSize / 3.0);
                centerX = (int) (nearPlayer.x + distance * Math.cos(angle));
                centerY = (int) (nearPlayer.y + distance * Math.sin(angle));
            } else {
                // Completely random placement
                centerX = rng.nextInt(10, mapSize - 10);
                centerY = rng.nextInt(10, mapSize - 10);
            }

            // Ensure within bounds
            if (centerX < 5 || centerX >= mapSize - 5 || centerY < 5 || centerY >= mapSize - 5) {
                continue;
            }

            // Check if center is on valid base terrain
            MapCell centerCell = grid.getCell(centerX, centerY);
            if (centerCell == null || centerCell.getTerrain() != baseTerrain) {
                continue;
            }

            // Check distance constraints
            if (nearPlayer != null) {
                int dist = Math.abs(centerX - nearPlayer.x) + Math.abs(centerY - nearPlayer.y);
                if (dist < minDistance) continue;
                if (maxDistance > 0 && dist > maxDistance) continue;
            }

            // Check spacing from other terrain types (initial placement only)
            if (!checkTerrainSpacing(grid, centerX, centerY, spacing, baseTerrain, terrain)) {
                continue;
            }

            // Grow the clump from this center using probability-based spreading
            int placed = growTerrainClump(grid, rng, centerX, centerY, targetTiles,
                    terrain, baseTerrain, clumpingFactor, spacing, provenanceIndex,
                    placedTiles, sessionId, updateInterval, currentCount, totalCount);

            if (placed > 0) {
                log.debug("Placed terrain clump of {} tiles at ({}, {})", placed, centerX, centerY);
                return placed;
            }
        }

        log.warn("Failed to place terrain clump after {} attempts", maxAttempts);
        return 0;
    }

    /**
     * Grow a terrain clump from a center point using probability-based spreading.
     * Higher clumping factor = more circular/dense.
     */
    private int growTerrainClump(MapGrid grid, RNG rng, int startX, int startY,
                                 int targetTiles, Terrain terrain, Terrain baseTerrain,
                                 int clumpingFactor, int spacing, int provenanceIndex,
                                 List<TileUpdate> placedTiles, String sessionId,
                                 int updateInterval, int currentCount, int totalCount) {
        List<int[]> frontier = new ArrayList<>();        
        java.util.Set<String> visited = new java.util.HashSet<>();

        // Start with center
        frontier.add(new int[]{startX, startY});
        visited.add(startX + "," + startY);
        int placedCount = 0;

        while (!frontier.isEmpty() && placedCount < targetTiles) {
            int idx;
            if (clumpingFactor > 10 && frontier.size() > 1) {
                    // nextInt(min, max) is inclusive on both ends, so use max = size - 1
                    idx = rng.nextInt(0, Math.min(2, frontier.size() - 1));
            } else {
                idx = rng.nextInt(frontier.size());
            }

            int[] current = frontier.remove(idx);
            if (current == null || current.length < 2) continue;

            int x = current[0];
            int y = current[1];

            

            MapCell cell = grid.getCell(x, y);
        if (cell == null || cell.getTerrain() != baseTerrain) continue;
        if (!checkTerrainSpacing(grid, x, y, spacing, baseTerrain, terrain)) continue;

        // Place terrain
        cell.setTerrain(terrain);
        cell.setProvenance("create_terrain:" + provenanceIndex);
        placedCount++;

        placedTiles.add(new TileUpdate(x, y, terrain, cell.getLandId(), cell.getOwner()));

        if (placedTiles.size() >= updateInterval) {
            sendTerrainPlacementUpdate(sessionId, new ArrayList<>(placedTiles),
                    currentCount + placedCount, totalCount, terrain.name());
            placedTiles.clear();
        }

        // Add neighbors
        int[][] directions = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
        for (int[] next : directions) {
            int nx = next[0];
            int ny = next[1];

            if (nx >= 0 && nx < grid.getWidth() && ny >= 0 && ny < grid.getHeight()) {
                String key = nx + "," + ny;
                if (!visited.contains(key)) {
                    visited.add(key);
                    frontier.add(new int[]{nx, ny});
                }
            }
        }
    }
    return placedCount;
    }

    /**
     * Execute place_headquarters command
     * Places headquarters buildings at player start positions
     */
    private void executePlaceHeadquarters(MapGrid grid, List<PlayerOrigin> playerOrigins, String sessionId) {
        log.debug("Placing headquarters for {} players", playerOrigins.size());

        for (PlayerOrigin origin : playerOrigins) {
            placeHeadquartersAt(grid, origin.x, origin.y, origin.playerId);
        }

        log.info("Placed {} headquarters buildings", grid.getBuildings().size());
    }

    /**
     * Place a headquarters building at the specified position
     * The headquarters is a 2x2 building represented by a single Building object
     * at the top-left corner. The frontend will render it as a 2x2 area.
     *
     *   X X
     *   X X
     *
     * Where X = building cells covering a 2x2 area
     * The building position (x,y) represents the top-left corner
     */
    private void placeHeadquartersAt(MapGrid grid, int centerX, int centerY, int playerNumber) {
        // Place a single 2x2 headquarters building
        // Position it at the top-left of where we want the 2x2 to be
        // Offset by -1, -1 to center it around the start position
        int buildingX = centerX - 1;
        int buildingY = centerY - 1;

        grid.addBuilding(new com.rts.model.Building(buildingX, buildingY,
                com.rts.model.Building.BuildingType.TOWN_CENTER, playerNumber));

        log.debug("Placed headquarters for player {} at ({},{}) covering 2x2 area", playerNumber, buildingX, buildingY);
    }

    /**
     * Check if a position has enough spacing from other terrain types.
     * Allows the terrain being placed to be next to itself (for clump growth).
     */
    private boolean checkTerrainSpacing(MapGrid grid, int x, int y, int spacing, Terrain baseTerrain, Terrain terrainBeingPlaced) {
        if (spacing <= 0) return true; // No spacing required

        for (int dx = -spacing; dx <= spacing; dx++) {
            for (int dy = -spacing; dy <= spacing; dy++) {
                int nx = x + dx;
                int ny = y + dy;

                if (nx >= 0 && nx < grid.getWidth() && ny >= 0 && ny < grid.getHeight()) {
                    MapCell neighbor = grid.getCell(nx, ny);
                    if (neighbor != null && neighbor.getTerrain() != null) {
                        Terrain neighborTerrain = neighbor.getTerrain();
                        // Allow base terrain and the terrain being placed, reject others
                        if (neighborTerrain != baseTerrain && neighborTerrain != terrainBeingPlaced) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void sendTerrainPlacementUpdate(String sessionId, List<TileUpdate> tiles,
                                            int placedCount, int totalCount, String terrainType) {
        if (sessionId != null && webSocketHandler != null) {
            StreamingLandGenerator.StreamUpdate update =
                    new StreamingLandGenerator.StreamUpdate(
                            placedCount < totalCount ? "progress" : "complete",
                            placedCount,
                            totalCount,
                            0,
                            null,
                            tiles,
                            new ArrayList<>()
                    );

            byte[] binaryData = BinaryMapEncoder.encodeStreamUpdate(update);
            webSocketHandler.sendBinaryMessage(sessionId, binaryData);
            log.debug("Sent terrain placement update: {}/{} {} tiles", placedCount, totalCount, terrainType);
        }
    }

    private void sendObjectPlacementUpdate(String sessionId, List<TileUpdate> tiles, int placedCount, int totalCount, String objectType) {
        if( sessionId!=null && webSocketHandler!=null){
            StreamingLandGenerator.StreamUpdate update =
                    new StreamingLandGenerator.StreamUpdate(
                            placedCount< totalCount ? "progress":"complete",
                            placedCount,
                            totalCount,
                            0,
                            null,
                            tiles,
                            new ArrayList<>()
                    );

            byte[] binaryData = BinaryMapEncoder.encodeStreamUpdate(update);
            webSocketHandler.sendBinaryMessage(sessionId, binaryData);
            log.debug("Sent object placement update: {}/{} {} objects", placedCount, totalCount, objectType);
        }
    }
    private void sendStepUpdate(String sessionId, int currentStep, int totalSteps,
                               String message, String status) {
        if (sessionId != null && webSocketHandler != null) {
            StepUpdate update = new StepUpdate(currentStep, totalSteps, message, status);

            // Encode to binary format
            byte[] binaryData = BinaryMapEncoder.encodeStepUpdate(update);

            // Send as binary message via native WebSocket
            webSocketHandler.sendBinaryMessage(sessionId, binaryData);

            log.debug("Sent binary step update: {}/{} - {} ({} bytes)", currentStep, totalSteps, message, binaryData.length);
        }
    }

    /**
     * Wait for the client to send a "next step" command.
     * Used in step-by-step mode.
     */
    private void waitForNextStep(String sessionId) {
        try {
            log.debug("Waiting for next step command for session {}", sessionId);
            CountDownLatch latch = new CountDownLatch(1);
            stepLatches.put(sessionId, latch);

            // Wait for up to 5 minutes (timeout to prevent hanging forever)
            boolean received = latch.await(5, TimeUnit.MINUTES);
            if (!received) {
                log.warn("Timeout waiting for next step command for session {}", sessionId);
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for next step: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Called when client sends "next step" command.
     * Releases the waiting thread to continue to next RMS command.
     */
    public void nextStep(String sessionId) {
        log.debug("Received next step command for session {}", sessionId);
        CountDownLatch latch = stepLatches.get(sessionId);
        if (latch != null) {
            latch.countDown();
        } else {
            log.warn("No latch found for session {} - not in step mode or already completed", sessionId);
        }
    }

    // Helper classes
    private static class PlayerOrigin {
        int x;
        int y;
        int playerId;

        PlayerOrigin(int x, int y, int playerId) {
            this.x = x;
            this.y = y;
            this.playerId = playerId;
        }
    }

    public static class StepUpdate {
        public final int currentStep;
        public final int totalSteps;
        public final String message;
        public final String status;

        public StepUpdate(int currentStep, int totalSteps, String message, String status) {
            this.currentStep = currentStep;
            this.totalSteps = totalSteps;
            this.message = message;
            this.status = status;
        }
    }

    public static class BaseTerrainUpdate {
        public final String type = "base_terrain";
        public final List<TileUpdate> tiles;
        public final int totalCells;

        public BaseTerrainUpdate(List<TileUpdate> tiles, int totalCells) {
            this.tiles = tiles;
            this.totalCells = totalCells;
        }
    }
}
