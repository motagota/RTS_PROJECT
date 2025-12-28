package com.rts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rts.dto.AstNode;
import com.rts.model.Building;
import com.rts.model.GeneratedMap;
import com.rts.model.MapGrid;
import com.rts.model.MapTemplate;
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

            System.out.println("Loading/updating " + resources.length + " map templates from RMS files...");

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
                        System.out.println("  Updating map template: " + templateName);
                    } else {
                        // Create new template
                        template = new MapTemplate();
                        template.setName(templateName);
                        System.out.println("  Creating map template: " + templateName);
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

            System.out.println("Successfully loaded/updated " + mapTemplateRepository.count() + " map templates");

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
        System.out.println("Parsed " + ast.size() + " RMS commands for template: " + templateName);

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

        System.out.println("Generated map grid: " + mapSize + "x" + mapSize);

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
            System.out.println("Compressed map data size: " + terrainData.length + " bytes");

            // Extract and serialize buildings
            String buildingsJson = serializeBuildingsToJson(grid.getBuildings());
            generatedMap.setBuildings(buildingsJson);
            System.out.println("Serialized " + grid.getBuildings().size() + " buildings");

            generatedMap.setPlayerStarts("[]"); // TODO: Extract player starts from grid
            generatedMap.setUnits("[]"); // Initialize empty units array
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
                    System.out.println("Executing RMS command: " + commandName);
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

    public void spawnUnit(Long gameId, int x, int y, com.rts.model.Unit.UnitType unitType, int playerNumber)  {

        try {
            System.out.println(">>> MapService.spawnUnit: ENTERED - gameId=" + gameId + ", pos=(" + x + "," + y + "), type=" + unitType + ", player=" + playerNumber);
            System.out.flush();

            Optional<GeneratedMap> mapOpt = getGeneratedMapByGameId(gameId);
            System.out.println(">>> MapService.spawnUnit: Got map optional: " + (mapOpt.isPresent() ? "present" : "empty"));
            System.out.flush();

            if (mapOpt.isEmpty()) {
                throw new IllegalStateException("Map not found for game " + gameId);
            }

            GeneratedMap generatedMap = mapOpt.get();
            System.out.println(">>> MapService.spawnUnit: Retrieved GeneratedMap");
            System.out.flush();

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

            System.out.println("Spawned " + unitType + " at (" + x + "," + y + ") for player " + playerNumber + ". Total units: " + units.size());
        }
        catch (Exception e) {
            System.out.println(">>> MapService.spawnUnit: ERROR - " + e.getMessage());
        }
    }

    /**
     * Serialize units to JSON string
     */
    private String serializeUnitsToJson(java.util.List<com.rts.model.Unit> units) throws JsonProcessingException {
        java.util.List<Map<String, Object>> unitData = new java.util.ArrayList<>();

        for (com.rts.model.Unit unit : units) {
            Map<String, Object> data = new HashMap<>();
            data.put("x", unit.getX());
            data.put("y", unit.getY());
            data.put("type", unit.getType().name());
            data.put("playerNumber", unit.getPlayerNumber());
            data.put("health", unit.getHealth());
            data.put("maxHealth", unit.getMaxHealth());
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
}
