package com.rts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rts.dto.AstNode;
import com.rts.model.Building;
import com.rts.model.GeneratedMap;
import com.rts.model.MapGrid;
import com.rts.model.MapTemplate;
import com.rts.model.Unit;
import com.rts.repository.GeneratedMapRepository;
import com.rts.repository.MapTemplateRepository;
import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;
import com.rts.service.map.RMSParser;
import com.rts.service.map.commands.BaseTerrainCommand;
import com.rts.service.map.commands.PlayerStartsCommand;
import com.rts.service.map.commands.ShouldGenerateHeadquartersCommand;
import com.rts.service.map.commands.PlaceHeadquartersCommand;
import com.rts.service.map.commands.PlaceResourcesForEveryPlayerCommand;
import com.rts.service.map.commands.PlaceNeutralResourcesCommand;
import com.rts.service.map.commands.CreateForestTerrainCommand;
import com.rts.service.map.commands.PlaceTreesCommand;
import com.rts.service.map.commands.PlayerWoodlinesCommand;
import com.rts.utils.RNG;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
public class MapService {

    @Autowired
    private MapTemplateRepository mapTemplateRepository;

    @Autowired
    private GeneratedMapRepository generatedMapRepository;

    @Autowired
    private RMSParser rmsParser;

    @Autowired
    private StreamingMapExecutor streamingMapExecutor;

    @Autowired
    private MovementService movementService;

    @Autowired
    private PathfindingService pathfindingService;

    @Autowired
    private ResourceGatheringService resourceGatheringService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RMSCommand> rmsCommands = new HashMap<>();

    @PostConstruct
    public void init() {
        // Register RMS commands
        registerCommand(new BaseTerrainCommand());
        registerCommand(new PlayerStartsCommand());
        registerCommand(new ShouldGenerateHeadquartersCommand());
        registerCommand(new PlaceHeadquartersCommand());
        registerCommand(new PlaceResourcesForEveryPlayerCommand());
        registerCommand(new PlaceNeutralResourcesCommand());
        registerCommand(new CreateForestTerrainCommand());
        registerCommand(new PlaceTreesCommand());
        registerCommand(new PlayerWoodlinesCommand());

        // Create default map templates if they don't exist
        createDefaultMapTemplates();
    }

    private void registerCommand(RMSCommand command) {
        rmsCommands.put(command.getCommandName(), command);
    }

    /**
     * Load map templates from RMS files in resources/maps directory
     * Always reloads templates to ensure RMS script changes are picked up during development
     */
    private void createDefaultMapTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:maps/*.rms");

            for (Resource resource : resources) {
                try {
                    String rmsScript = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String filename = resource.getFilename();

                    if (filename == null) continue;

                    // Extract map name from filename (e.g., "arabia.rms" -> "arabia")
                    String templateName = filename.replace(".rms", "");

                    // Extract display name and description from RMS comments
                    String displayName = extractDisplayName(rmsScript, templateName);
                    String description = extractDescription(rmsScript);

                    // Check if template already exists
                    Optional<MapTemplate> existingTemplate = mapTemplateRepository.findByName(templateName);
                    MapTemplate template;

                    if (existingTemplate.isPresent()) {
                        // Update existing template
                        template = existingTemplate.get();
                    } else {
                        // Create new template
                        template = new MapTemplate();
                        template.setName(templateName);
                    }

                    // Update template fields
                    template.setDisplayName(displayName);
                    template.setDescription(description);
                    template.setMinPlayers(2);
                    template.setMaxPlayers(8);
                    template.setMapSize("MEDIUM");
                    template.setBaseTerrain("GRASS");
                    template.setRmsScript(rmsScript);
                    template.setIsDefault(templateName.equals("arabia"));

                    mapTemplateRepository.save(template);

                } catch (Exception e) {
                    System.err.println("Failed to load map template from " + resource.getFilename() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to load map templates from resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractDisplayName(String rmsScript, String fallback) {
        // Look for first comment line with format: /* MapName - Description */
        String[] lines = rmsScript.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("/*") && line.contains("-")) {
                String content = line.replace("/*", "").replace("*/", "").trim();
                String[] parts = content.split("-");
                if (parts.length > 0) {
                    return parts[0].trim();
                }
            }
        }
        // Capitalize fallback
        return fallback.substring(0, 1).toUpperCase() + fallback.substring(1).replace("_", " ");
    }

    private String extractDescription(String rmsScript) {
        // Extract full description from first comment block
        String[] lines = rmsScript.split("\n");
        StringBuilder description = new StringBuilder();
        boolean inComment = false;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("/*")) {
                inComment = true;
                // Skip first line if it has the title
                if (!line.contains("-")) {
                    String content = line.replace("/*", "").replace("*/", "").trim();
                    if (!content.isEmpty()) description.append(content).append(" ");
                }
                continue;
            }
            if (line.contains("*/")) {
                inComment = false;
                break;
            }
            if (inComment && line.startsWith("*")) {
                String content = line.substring(1).trim();
                if (!content.isEmpty()) description.append(content).append(" ");
            }
        }

        return description.toString().trim();
    }

    public List<MapTemplate> getAllMapTemplates() {
        return mapTemplateRepository.findAll();
    }

    public Optional<MapTemplate> getMapTemplateByName(String name) {
        return mapTemplateRepository.findByName(name);
    }

    /**
     * Generate a map using the new RMS-based streaming generator
     * This is a synchronous version for game start (no WebSocket streaming)
     */
    public GeneratedMap generateMapFromRMS(String templateName, int playerCount, Long gameId, int mapSize) {
        Optional<MapTemplate> templateOpt = mapTemplateRepository.findByName(templateName);
        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("Map template not found: " + templateName);
        }

        MapTemplate template = templateOpt.get();

        // Validate player count
        if (playerCount < template.getMinPlayers() || playerCount > template.getMaxPlayers()) {
            throw new IllegalArgumentException("Player count " + playerCount + " not supported for this map");
        }

        String rmsScript = template.getRmsScript();
        if (rmsScript == null || rmsScript.isEmpty()) {
            throw new IllegalArgumentException("Map template has no RMS script: " + templateName);
        }

        // Parse RMS script
        List<AstNode> ast = rmsParser.parse(rmsScript);

        // Generate map using RMS executor (no WebSocket streaming)
        long seed = System.currentTimeMillis();
        MapGrid grid = streamingMapExecutor.executeStreaming(
                ast,
                mapSize,
                seed,
                null, // No session ID - no streaming
                10,   // Update interval (doesn't matter without session)
                false, // Not step-by-step
                playerCount // Pass the actual player count from the lobby
        );

        // Create GeneratedMap entity
        GeneratedMap generatedMap = new GeneratedMap();
        generatedMap.setGameId(gameId);
        generatedMap.setMapTemplateName(templateName);
        generatedMap.setWidth(mapSize);
        generatedMap.setHeight(mapSize);
        generatedMap.setPlayerCount(playerCount);
        generatedMap.setGeneratedAt(LocalDateTime.now());

        // Serialize map data to compressed binary format
        try {
            byte[] terrainData = serializeAndCompressMapGrid(grid);
            generatedMap.setTerrainData(terrainData);

            // Extract and serialize buildings
            String buildingsJson = serializeBuildingsToJson(grid.getBuildings());
            generatedMap.setBuildings(buildingsJson);

            // Spawn initial villagers for each player near their headquarters
            List<Unit> initialUnits = spawnInitialVillagers(grid.getBuildings(), playerCount);
            String unitsJson = objectMapper.writeValueAsString(initialUnits);
            generatedMap.setUnits(unitsJson);

            generatedMap.setPlayerStarts("[]"); // TODO: Extract player starts from grid
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize map data", e);
        }

        return generatedMapRepository.save(generatedMap);
    }

    /**
     * Serialize MapGrid to JSON and compress with GZIP
     */
    private byte[] serializeAndCompressMapGrid(MapGrid grid) throws IOException {
        // First serialize to JSON string
        String jsonString = serializeMapGridToJson(grid);

        // Then compress with GZIP
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
        }

        return byteStream.toByteArray();
    }

    /**
     * Decompress and deserialize map data from binary format
     */
    public String decompressAndDeserializeMapGrid(byte[] compressedData) throws IOException {
        if (compressedData == null) {
            return null;
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedData);
        StringBuilder result = new StringBuilder();

        try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) > 0) {
                result.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
        }

        return result.toString();
    }

    /**
     * Serialize buildings to JSON string
     */
    private String serializeBuildingsToJson(List<Building> buildings) throws JsonProcessingException {
        List<Map<String, Object>> buildingData = new ArrayList<>();

        for (Building building : buildings) {
            Map<String, Object> data = new HashMap<>();
            data.put("x", building.getX());
            data.put("y", building.getY());
            data.put("type", building.getType().name());
            data.put("playerNumber", building.getPlayerNumber());
            buildingData.add(data);
        }

        return objectMapper.writeValueAsString(buildingData);
    }

    /**
     * Spawn initial villagers for each player near their headquarters
     * @param buildings List of buildings (including headquarters)
     * @param playerCount Number of players
     * @return List of initial villager units
     */
    private List<Unit> spawnInitialVillagers(List<Building> buildings, int playerCount) {
        List<Unit> villagers = new ArrayList<>();
        int villagersPerPlayer = 3; // Spawn 3 villagers per player

        // Find each player's headquarters
        for (int playerNum = 1; playerNum <= playerCount; playerNum++) {
            final int currentPlayer = playerNum;
            Building headquarters = buildings.stream()
                    .filter(b -> b.getType() == Building.BuildingType.TOWN_CENTER
                              && b.getPlayerNumber() == currentPlayer)
                    .findFirst()
                    .orElse(null);

            if (headquarters != null) {
                // Spawn villagers around the headquarters
                int hqX = headquarters.getX();
                int hqY = headquarters.getY();
                int hqWidth = headquarters.getWidth();
                int hqHeight = headquarters.getHeight();

                // Spawn positions: to the right and below the headquarters
                int[][] spawnOffsets = {
                    {hqWidth, 0},           // Right of HQ
                    {hqWidth + 1, 0},       // Further right
                    {hqWidth, 1}            // Right and down
                };

                for (int i = 0; i < villagersPerPlayer && i < spawnOffsets.length; i++) {
                    int spawnX = hqX + spawnOffsets[i][0];
                    int spawnY = hqY + spawnOffsets[i][1];

                    Unit villager = new Unit(spawnX, spawnY, Unit.UnitType.VILLAGER, playerNum);
                    villagers.add(villager);
                }
            } else {
                System.err.println("Warning: No headquarters found for player " + playerNum);
            }
        }

        return villagers;
    }

    /**
     * Serialize MapGrid to JSON string
     */
    private String serializeMapGridToJson(MapGrid grid) throws JsonProcessingException {
        Map<String, Object> mapData = new HashMap<>();
        mapData.put("width", grid.getWidth());
        mapData.put("height", grid.getHeight());

        // Serialize cells
        List<Map<String, Object>> cells = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                var cell = grid.getCell(x, y);
                if (cell != null) {
                    Map<String, Object> cellData = new HashMap<>();
                    cellData.put("x", x);
                    cellData.put("y", y);

                    // Safety check for null terrain
                    if (cell.getTerrain() != null) {
                        cellData.put("terrain", cell.getTerrain().name());
                    } else {
                        System.err.println("WARNING: Cell at (" + x + "," + y + ") has null terrain");
                        cellData.put("terrain", "GRASS"); // Default fallback
                    }

                    cellData.put("landId", cell.getLandId());
                    if (cell.getOwner() != null) {
                        cellData.put("owner", cell.getOwner());
                    }
                    if (cell.getObject() != null) {
                        cellData.put("object", cell.getObject());
                    }
                    cells.add(cellData);
                }
            }
        }
        mapData.put("cells", cells);

        return objectMapper.writeValueAsString(mapData);
    }

    public GeneratedMap generateMap(String templateName, int playerCount, Long gameId) {
        Optional<MapTemplate> templateOpt = mapTemplateRepository.findByName(templateName);
        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("Map template not found: " + templateName);
        }

        MapTemplate template = templateOpt.get();

        // Validate player count
        if (playerCount < template.getMinPlayers() || playerCount > template.getMaxPlayers()) {
            throw new IllegalArgumentException("Player count " + playerCount + " not supported for this map");
        }

        // Determine map dimensions based on size
        int[] dimensions = getMapDimensions(template.getMapSize());
        int width = dimensions[0];
        int height = dimensions[1];

        // Create generation context
        long seed = System.currentTimeMillis(); // Use timestamp as seed for randomness
        MapGenerationContext context = new MapGenerationContext(width, height, playerCount, seed);
        context.setEdgeDistanceMin(template.getEdgeDistanceMin());
        context.setStartingAreaRadius(template.getStartingAreaRadius());
        context.setPlayerLandRadius(template.getPlayerLandRadius());

        // Execute RMS commands
        try {
            List<Map<String, Object>> commands = objectMapper.readValue(
                template.getRmsCommands(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            for (Map<String, Object> commandData : commands) {
                String commandName = (String) commandData.get("command");
                RMSCommand command = rmsCommands.get(commandName);

                if (command != null) {
                    command.execute(context, commandData);
                } else {
                    System.err.println("Unknown RMS command: " + commandName);
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse RMS commands", e);
        }

        // Create GeneratedMap entity
        GeneratedMap generatedMap = new GeneratedMap();
        generatedMap.setGameId(gameId);
        generatedMap.setMapTemplateName(templateName);
        generatedMap.setWidth(width);
        generatedMap.setHeight(height);
        generatedMap.setPlayerCount(playerCount);
        generatedMap.setGeneratedAt(LocalDateTime.now());

        // Serialize terrain data (legacy method - not currently used)
        try {
            // Convert terrain map to compressed format
            String terrainJson = objectMapper.writeValueAsString(context.getTerrainMap());
            byte[] compressedTerrain = terrainJson.getBytes(StandardCharsets.UTF_8);
            // Note: This old method doesn't use GZIP compression, but we need byte[] now
            generatedMap.setTerrainData(compressedTerrain);
            generatedMap.setPlayerStarts(objectMapper.writeValueAsString(context.getPlayerStarts()));
            generatedMap.setBuildings(objectMapper.writeValueAsString(context.getBuildings()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize map data", e);
        }

        return generatedMapRepository.save(generatedMap);
    }

    public Optional<GeneratedMap> getGeneratedMapByGameId(Long gameId) {
        return generatedMapRepository.findByGameId(gameId);
    }

    /**
     * Get all units for a game
     */
    public List<Unit> getUnitsForGame(Long gameId) {
        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                return new ArrayList<>();
            }

            GeneratedMap generatedMap = mapOpt.get();
            String unitsJson = generatedMap.getUnits();

            if (unitsJson == null || unitsJson.isEmpty() || unitsJson.equals("[]")) {
                return new ArrayList<>();
            }

            return objectMapper.readValue(
                    unitsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Unit.class)
            );
        } catch (Exception e) {
            System.err.println("Error getting units for game: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void spawnUnit(Long gameId, int x, int y, com.rts.model.Unit.UnitType unitType, int playerNumber)  {

        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);

            if (mapOpt.isEmpty()) {
                throw new IllegalStateException("Map not found for game " + gameId);
            }

            GeneratedMap generatedMap = mapOpt.get();

            // Get existing units or create empty list
            String unitsJson = generatedMap.getUnits();
            java.util.List<com.rts.model.Unit> units;

            if (unitsJson == null || unitsJson.isEmpty() || unitsJson.equals("[]")) {
                units = new java.util.ArrayList<>();
            } else {
                units = objectMapper.readValue(
                        unitsJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, com.rts.model.Unit.class)
                );
            }

            // Create the unit
            com.rts.model.Unit unit = new com.rts.model.Unit(x, y, unitType, playerNumber);
            units.add(unit);

            // Serialize units back to JSON and save
            String updatedUnitsJson = serializeUnitsToJson(units);
            generatedMap.setUnits(updatedUnitsJson);
            generatedMapRepository.save(generatedMap);
        }
        catch (Exception e) {
            System.err.println("MapService.spawnUnit: ERROR - " + e.getMessage());
        }
    }

    /**
     * Serialize units to JSON string
     */
    private String serializeUnitsToJson(java.util.List<com.rts.model.Unit> units) throws JsonProcessingException {
        java.util.List<Map<String, Object>> unitData = new java.util.ArrayList<>();

        for (com.rts.model.Unit unit : units) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", unit.getId());
            data.put("x", unit.getX());
            data.put("y", unit.getY());
            data.put("type", unit.getType().name());
            data.put("playerNumber", unit.getPlayerNumber());
            data.put("health", unit.getHealth());
            data.put("maxHealth", unit.getMaxHealth());

            // Preserve movement state
            data.put("targetX", unit.getTargetX());
            data.put("targetY", unit.getTargetY());
            data.put("movementSpeed", unit.getMovementSpeed());

            unitData.add(data);
        }

        return objectMapper.writeValueAsString(unitData);
    }

    private int[] getMapDimensions(String mapSize) {
        return switch (mapSize) {
            case "SMALL" -> new int[]{100, 100};
            case "MEDIUM" -> new int[]{150, 150};
            case "LARGE" -> new int[]{200, 200};
            case "HUGE" -> new int[]{300, 300};
            default -> new int[]{100, 100};
        };
    }

    /**
     * Save an updated GeneratedMap entity
     */
    public void saveGeneratedMap(GeneratedMap generatedMap) {
        generatedMapRepository.save(generatedMap);
    }

    /**
     * Create a simple byte array for pathfinding from the compressed JSON terrain data.
     * Each byte represents the terrain code at that position.
     * Cells with resource objects (GOLD, STONE, BERRIES, TREE) are marked as blocked terrain.
     *
     * @param generatedMap The map to extract terrain data from
     * @return byte array where terrainData[y * width + x] = terrain code (0-11)
     */
    public byte[] createPathfindingTerrainArray(GeneratedMap generatedMap) {
        try {
            // Decompress and parse the terrain JSON
            String terrainJson = decompressAndDeserializeMapGrid(generatedMap.getTerrainData());
            com.fasterxml.jackson.databind.JsonNode mapNode = objectMapper.readTree(terrainJson);
            com.fasterxml.jackson.databind.JsonNode cellsNode = mapNode.get("cells");

            int width = generatedMap.getWidth();
            int height = generatedMap.getHeight();

            // Initialize array with default terrain (GRASS = 0)
            byte[] terrainArray = new byte[width * height];

            if (cellsNode != null && cellsNode.isArray()) {
                // Parse cells
                for (com.fasterxml.jackson.databind.JsonNode cellNode : cellsNode) {
                    int x = cellNode.get("x").asInt();
                    int y = cellNode.get("y").asInt();

                    int index = y * width + x;
                    if (index < 0 || index >= terrainArray.length) continue;

                    // Check if cell has a resource object
                    com.fasterxml.jackson.databind.JsonNode objectNode = cellNode.get("object");
                    if (objectNode != null && !objectNode.isNull()) {
                        String object = objectNode.asText();
                        // Map resource objects to terrain codes that are blocked in pathfinding
                        byte resourceTerrain = mapObjectToTerrainCode(object);
                        if (resourceTerrain != -1) {
                            terrainArray[index] = resourceTerrain;
                            continue;
                        }
                    }

                    // No resource object, use the cell's terrain
                    com.fasterxml.jackson.databind.JsonNode terrainNode = cellNode.get("terrain");
                    if (terrainNode != null && !terrainNode.isNull()) {
                        String terrain = terrainNode.asText();
                        terrainArray[index] = mapTerrainStringToCode(terrain);
                    }
                }
            }

            return terrainArray;

        } catch (Exception e) {
            System.err.println("Error creating pathfinding terrain array: " + e.getMessage());
            e.printStackTrace();
            // Return empty array on error
            return new byte[generatedMap.getWidth() * generatedMap.getHeight()];
        }
    }

    /**
     * Map resource object names to terrain codes for pathfinding
     * Returns -1 if not a resource object
     */
    private byte mapObjectToTerrainCode(String object) {
        return switch (object.toUpperCase()) {
            case "GOLD" -> 7;           // GOLD terrain code
            case "STONE" -> 5;          // STONE terrain code
            case "BERRIES", "FORAGE", "BERRY_BUSH" -> 8;  // FOOD terrain code
            case "TREE" -> 11;          // TREE terrain code
            default -> -1;              // Not a blocking resource
        };
    }

    /**
     * Map terrain string names to terrain codes
     */
    private byte mapTerrainStringToCode(String terrain) {
        return switch (terrain.toUpperCase()) {
            case "GRASS" -> 0;
            case "DESERT" -> 1;
            case "SNOW" -> 2;
            case "LAVA" -> 3;
            case "WATER" -> 4;
            case "STONE" -> 5;
            case "WOOD", "DIRT" -> 6;   // DEPRECATED
            case "GOLD" -> 7;
            case "FOOD", "BERRIES" -> 8;
            case "HUNT" -> 9;
            case "FOREST" -> 10;
            case "TREE" -> 11;
            default -> 0;  // Default to GRASS
        };
    }

    /**
     * Set a unit's destination for pathfinding by unit ID
     */
    public void setUnitDestinationById(Long gameId, int unitId, int targetX, int targetY) {
        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                System.err.println("Map not found for game " + gameId);
                return;
            }

            GeneratedMap generatedMap = mapOpt.get();

            // Get units
            String unitsJson = generatedMap.getUnits();
            if (unitsJson == null || unitsJson.isEmpty()) {
                System.err.println("No units found on map");
                return;
            }

            java.util.List<com.rts.model.Unit> units = objectMapper.readValue(
                    unitsJson,
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, com.rts.model.Unit.class)
            );

            // Get buildings
            String buildingsJson = generatedMap.getBuildings();
            java.util.List<Building> buildings = null;
            if (buildingsJson != null && !buildingsJson.isEmpty()) {
                buildings = objectMapper.readValue(
                        buildingsJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Building.class)
                );
            }

            // Get terrain data for pathfinding (converts JSON to simple byte array)
            byte[] terrainData = createPathfindingTerrainArray(generatedMap);

            // Find the unit by ID
            com.rts.model.Unit targetUnit = null;
            for (com.rts.model.Unit unit : units) {
                if (unit.getId() == unitId) {
                    targetUnit = unit;
                    break;
                }
            }

            if (targetUnit == null) {
                System.err.println("Unit not found with ID: " + unitId);
                return;
            }

            // Release gather slot if unit is currently gathering
            if (targetUnit.getGatherState() != Unit.GatherState.IDLE) {
                resourceGatheringService.releaseGatherSlot(targetUnit);
                targetUnit.setGatherState(Unit.GatherState.IDLE);
                targetUnit.setTargetResourceNodeId(null);
            }

            // Set destination using movement service
            movementService.setUnitDestination(
                    targetUnit, targetX, targetY,
                    terrainData, buildings, units,
                    generatedMap.getWidth(), generatedMap.getHeight()
            );

            // Save updated units
            unitsJson = objectMapper.writeValueAsString(units);
            generatedMap.setUnits(unitsJson);
            saveGeneratedMap(generatedMap);

            // Immediately broadcast the updated units so clients see the movement start
            broadcastUnitUpdate(gameId, units);

        } catch (Exception e) {
            System.err.println("Error setting unit destination by ID: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Command units to gather from a resource node
     */
    public void commandGatherResource(Long gameId, List<Integer> unitIds, int resourceX, int resourceY) {

        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {

                return;
            }

            GeneratedMap generatedMap = mapOpt.get();

            // Get units
            String unitsJson = generatedMap.getUnits();
            if (unitsJson == null || unitsJson.isEmpty()) {
                return;
            }

            List<Unit> units = objectMapper.readValue(
                    unitsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Unit.class)
            );

            // Get buildings
            String buildingsJson = generatedMap.getBuildings();
            List<Building> buildings = new ArrayList<>();
            if (buildingsJson != null && !buildingsJson.isEmpty()) {
                buildings = objectMapper.readValue(
                        buildingsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Building.class)
                );
            }

            // Get terrain data for pathfinding (converts JSON to simple byte array)
            byte[] terrainData = createPathfindingTerrainArray(generatedMap);

            // Find units by IDs
            List<Unit> targetUnits = new ArrayList<>();
            for (Integer unitId : unitIds) {
                for (Unit unit : units) {
                    if (unit.getId() == unitId) {
                        targetUnits.add(unit);
                        break;
                    }
                }
            }

            if (targetUnits.isEmpty()) {
                return;
            }

            // Command gathering using resource gathering service
            resourceGatheringService.commandGatherResource(
                    targetUnits,
                    gameId,
                    resourceX,
                    resourceY,
                    terrainData,
                    buildings,
                    units,
                    generatedMap.getWidth(),
                    generatedMap.getHeight()
            );

            // Save updated units
            unitsJson = objectMapper.writeValueAsString(units);
            generatedMap.setUnits(unitsJson);
            saveGeneratedMap(generatedMap);
            
            broadcastUnitUpdate(gameId, units);


        } catch (Exception e) {
            System.err.println("Error commanding resource gathering: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Set a unit's destination for pathfinding by coordinates (deprecated, use setUnitDestinationById)
     */
    public void setUnitDestination(Long gameId, int unitX, int unitY, int targetX, int targetY) {
        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                System.err.println("Map not found for game " + gameId);
                return;
            }

            GeneratedMap generatedMap = mapOpt.get();

            // Get units
            String unitsJson = generatedMap.getUnits();
            if (unitsJson == null || unitsJson.isEmpty()) {
                System.err.println("No units found on map");
                return;
            }

            java.util.List<com.rts.model.Unit> units = objectMapper.readValue(
                    unitsJson,
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, com.rts.model.Unit.class)
            );

            // Get buildings
            String buildingsJson = generatedMap.getBuildings();
            java.util.List<Building> buildings = null;
            if (buildingsJson != null && !buildingsJson.isEmpty()) {
                buildings = objectMapper.readValue(
                        buildingsJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Building.class)
                );
            }

            // Get terrain data for pathfinding (converts JSON to simple byte array)
            byte[] terrainData = createPathfindingTerrainArray(generatedMap);

            // Find the unit at the specified position
            com.rts.model.Unit targetUnit = null;
            for (com.rts.model.Unit unit : units) {
                if (unit.getX() == unitX && unit.getY() == unitY) {
                    targetUnit = unit;
                    break;
                }
            }

            if (targetUnit == null) {
                System.err.println("Unit not found at position (" + unitX + "," + unitY + ")");
                return;
            }

            // Set destination using movement service
            movementService.setUnitDestination(
                    targetUnit, targetX, targetY,
                    terrainData, buildings, units,
                    generatedMap.getWidth(), generatedMap.getHeight()
            );

            // Save updated units
            unitsJson = objectMapper.writeValueAsString(units);
             generatedMap.setUnits(unitsJson);
            saveGeneratedMap(generatedMap);

            // Immediately broadcast the updated units so clients see the movement start
            broadcastUnitUpdate(gameId, units);

        } catch (Exception e) {
            System.err.println("Error setting unit destination: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process movement for all units in a game
     */
    public void processUnitMovement(Long gameId) {
        try {
            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            if (mapOpt.isEmpty()) {
                return;
            }

            GeneratedMap generatedMap = mapOpt.get();

            // Get units
            String unitsJson = generatedMap.getUnits();
            if (unitsJson == null || unitsJson.isEmpty()) {
                return;
            }

            java.util.List<com.rts.model.Unit> units = objectMapper.readValue(
                    unitsJson,
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, com.rts.model.Unit.class)
            );

            // Check if any units are moving or gathering
            boolean anyMoving = false;
            boolean anyGathering = false;
            for (com.rts.model.Unit unit : units) {
                if (unit.isMoving()) {
                    anyMoving = true;
                }
                if (unit.isGathering()) {
                    anyGathering = true;
                }
            }

            // Skip if no units are moving or gathering
            if (!anyMoving && !anyGathering) {
                return;
            }

            // Get buildings
            String buildingsJson = generatedMap.getBuildings();
            java.util.List<Building> buildings = null;
            if (buildingsJson != null && !buildingsJson.isEmpty()) {
                buildings = objectMapper.readValue(
                        buildingsJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Building.class)
                );
            }

            // Get terrain data for pathfinding (converts JSON to simple byte array)
            byte[] terrainData = createPathfindingTerrainArray(generatedMap);

            // Process movement
            movementService.processUnitMovement(
                    units, terrainData, buildings,
                    generatedMap.getWidth(), generatedMap.getHeight()
            );

            // Process resource gathering
            resourceGatheringService.processGathering(
                    units, generatedMap.getGameId(), buildings,
                    terrainData, units,
                    generatedMap.getWidth(), generatedMap.getHeight()
            );

            // Save updated units
            String updatedUnitsJson = objectMapper.writeValueAsString(units);
            generatedMap.setUnits(updatedUnitsJson);
            saveGeneratedMap(generatedMap);

            // Broadcast unit updates to all players via WebSocket
            broadcastUnitUpdate(gameId, units);

        } catch (Exception e) {
            System.err.println("Error processing unit movement for game " + gameId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcast unit position updates to all players in a game
     */
    private void broadcastUnitUpdate(Long gameId, java.util.List<com.rts.model.Unit> units) {
        try {
            // Create update message
            java.util.Map<String, Object> update = new java.util.HashMap<>();
            update.put("type", "UNITS_UPDATE");
            update.put("units", units);

            // Send to all players in this game
            messagingTemplate.convertAndSend("/topic/game/" + gameId, update);

        } catch (Exception e) {
            System.err.println("Error broadcasting unit update: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
