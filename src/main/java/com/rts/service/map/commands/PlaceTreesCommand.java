package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.Map;
import java.util.Random;

/**
 * RMS Command to place tree objects on forest terrain (Phase 2 of woodline generation)
 *
 * This fills existing FOREST terrain with TREE objects to create the visual woodlines.
 * Trees only grow on FOREST terrain tiles created by CreateForestTerrainCommand.
 *
 * Supports AoE2-style parameters:
 * - number_of_objects: Maximum number of trees to place (default: 999 = fill all forest)
 * - terrain_to_grow_on: Only place trees on this terrain type (default: FOREST)
 * - avoid_player_start_areas: Don't place trees within this radius of player starts
 * - density: Percentage of forest tiles to fill with trees (default: 90)
 */
public class PlaceTreesCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        // Get parameters
        int numberOfObjects = getParameter(parameters, "number_of_objects", 999);
        String terrainToGrowOn = getStringParameter(parameters, "terrain_to_grow_on", "FOREST");
        int avoidPlayerStartAreas = getParameter(parameters, "avoid_player_start_areas", 0);
        int density = getParameter(parameters, "density", 90); // 90% of forest tiles get trees

        int terrainType = getTerrainType(terrainToGrowOn);

        Random random = context.getRandom();
        int treesPlaced = 0;

        // First pass: count available forest tiles
        int availableForestTiles = 0;
        for (int x = 0; x < context.getWidth(); x++) {
            for (int y = 0; y < context.getHeight(); y++) {
                if (context.getTerrainAt(x, y) == terrainType) {
                    // Check distance to player starts
                    boolean tooCloseToPlayer = false;
                    for (MapGenerationContext.PlayerStartPosition playerStart : context.getPlayerStarts()) {
                        double distance = Math.sqrt(Math.pow(x - playerStart.x, 2) + Math.pow(y - playerStart.y, 2));
                        if (distance < avoidPlayerStartAreas) {
                            tooCloseToPlayer = true;
                            break;
                        }
                    }

                    if (!tooCloseToPlayer) {
                        availableForestTiles++;
                    }
                }
            }
        }

        // Calculate how many tiles to fill based on density
        int targetTreesToPlace = Math.min(numberOfObjects, (availableForestTiles * density) / 100);

        // Second pass: place trees on forest terrain
        for (int x = 0; x < context.getWidth() && treesPlaced < targetTreesToPlace; x++) {
            for (int y = 0; y < context.getHeight() && treesPlaced < targetTreesToPlace; y++) {
                if (context.getTerrainAt(x, y) == terrainType) {
                    // Check distance to player starts
                    boolean tooCloseToPlayer = false;
                    for (MapGenerationContext.PlayerStartPosition playerStart : context.getPlayerStarts()) {
                        double distance = Math.sqrt(Math.pow(x - playerStart.x, 2) + Math.pow(y - playerStart.y, 2));
                        if (distance < avoidPlayerStartAreas) {
                            tooCloseToPlayer = true;
                            break;
                        }
                    }

                    if (!tooCloseToPlayer) {
                        // Place tree with density probability
                        if (random.nextInt(100) < density) {
                            context.setTerrainAt(x, y, MapGenerationContext.TERRAIN_TREE);
                            treesPlaced++;
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getCommandName() {
        return "place_trees";
    }

    private int getTerrainType(String terrainName) {
        return switch (terrainName) {
            case "FOREST" -> MapGenerationContext.TERRAIN_FOREST;
            case "GRASS" -> MapGenerationContext.TERRAIN_GRASS;
            case "DESERT" -> MapGenerationContext.TERRAIN_DESERT;
            case "SNOW" -> MapGenerationContext.TERRAIN_SNOW;
            case "WATER" -> MapGenerationContext.TERRAIN_WATER;
            case "DIRT" -> MapGenerationContext.TERRAIN_DIRT;
            default -> MapGenerationContext.TERRAIN_FOREST;
        };
    }

    private int getParameter(Map<String, Object> parameters, String key, int defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }

        Object value = parameters.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    private String getStringParameter(Map<String, Object> parameters, String key, String defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }

        Object value = parameters.get(key);
        if (value instanceof String) {
            return (String) value;
        }

        return defaultValue;
    }
}
