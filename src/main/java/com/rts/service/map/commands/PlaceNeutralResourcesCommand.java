package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RMS Command to place neutral (map-wide) resources
 * Places resources anywhere on the map, not restricted to player lands
 * Allows forests and resources to join naturally across the map
 *
 * Supports:
 * - number_of_groups: How many separate resource clusters to place
 * - number_of_objects: Size of each cluster (tiles per group)
 * - set_group_placement_radius: Cluster tightness
 * - min_distance_group_placement: Minimum spacing between groups
 */
public class PlaceNeutralResourcesCommand implements RMSCommand {

    private static class ResourcePatch {
        int centerX;
        int centerY;

        ResourcePatch(int centerX, int centerY) {
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        // Get parameters
        int resourceType = getResourceType(parameters);
        int numberOfGroups = getParameter(parameters, "number_of_groups", 10);
        int numberOfObjects = getParameter(parameters, "number_of_objects", 20);
        int groupPlacementRadius = getParameter(parameters, "group_placement_radius", 5);
        int minDistanceGroupPlacement = getParameter(parameters, "min_distance_group_placement", 0);
        int minDistanceToPlayers = getParameter(parameters, "min_distance_to_players", 0);

        System.out.println("Placing neutral resources:");
        System.out.println("  Resource type: " + resourceType);
        System.out.println("  Number of groups: " + numberOfGroups);
        System.out.println("  Objects per group: " + numberOfObjects);
        System.out.println("  Group placement radius: " + groupPlacementRadius);
        System.out.println("  Min distance between groups: " + minDistanceGroupPlacement);

        // Track placed patches
        List<ResourcePatch> placedPatches = new ArrayList<>();

        // Place resource groups
        for (int i = 0; i < numberOfGroups; i++) {
            boolean placed = false;

            // Try up to 100 times to find a valid location
            for (int attempt = 0; attempt < 100 && !placed; attempt++) {
                // Get random point anywhere on the map
                int x = context.getRandom().nextInt(context.getWidth());
                int y = context.getRandom().nextInt(context.getHeight());

                // Check if this location is valid
                if (isValidResourceLocation(context, x, y, groupPlacementRadius,
                                           minDistanceGroupPlacement, minDistanceToPlayers, placedPatches)) {
                    // Place the resource patch
                    placeResourcePatch(context, x, y, resourceType, groupPlacementRadius, numberOfObjects);

                    // Track this patch
                    placedPatches.add(new ResourcePatch(x, y));

                    placed = true;
                    System.out.println("  Placed neutral resource group " + (i + 1) + " at (" + x + "," + y + ")");
                }
            }

            if (!placed) {
                System.out.println("  Warning - Could not find valid location for neutral resource group " + (i + 1));
            }
        }
    }

    @Override
    public String getCommandName() {
        return "place_neutral_resources";
    }

    /**
     * Check if a location is valid for placing a neutral resource patch
     */
    private boolean isValidResourceLocation(MapGenerationContext context, int centerX, int centerY,
                                           int patchSize, int minDistanceGroupPlacement,
                                           int minDistanceToPlayers, List<ResourcePatch> placedPatches) {
        int radius = patchSize / 2;

        // Check if all tiles in the patch area are within map bounds
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                if (x < 0 || x >= context.getWidth() || y < 0 || y >= context.getHeight()) {
                    return false;
                }
            }
        }

        // Check minimum distance to player starts if specified
        if (minDistanceToPlayers > 0) {
            for (MapGenerationContext.PlayerStartPosition playerStart : context.getPlayerStarts()) {
                double distance = Math.sqrt(Math.pow(centerX - playerStart.x, 2) + Math.pow(centerY - playerStart.y, 2));
                if (distance < minDistanceToPlayers) {
                    return false;
                }
            }
        }

        // Check group distance constraints
        for (ResourcePatch patch : placedPatches) {
            double distance = Math.sqrt(Math.pow(centerX - patch.centerX, 2) + Math.pow(centerY - patch.centerY, 2));

            if (distance < minDistanceGroupPlacement) {
                return false;
            }
        }

        return true;
    }

    /**
     * Place a circular patch of resources with specified size
     */
    private void placeResourcePatch(MapGenerationContext context, int centerX, int centerY,
                                   int resourceType, int patchSize, int numberOfObjects) {
        // Calculate how many tiles to fill based on numberOfObjects
        int radius = Math.max(1, (int) Math.sqrt(numberOfObjects / Math.PI));

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

                if (distance <= radius) {
                    context.setTerrainAt(x, y, resourceType);
                }
            }
        }
    }

    /**
     * Get resource type from parameters
     */
    private int getResourceType(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("resource_type")) {
            return MapGenerationContext.TERRAIN_DIRT; // Default to wood
        }

        Object resourceTypeParam = parameters.get("resource_type");

        if (resourceTypeParam instanceof Integer) {
            return (Integer) resourceTypeParam;
        } else if (resourceTypeParam instanceof String) {
            return switch ((String) resourceTypeParam) {
                case "STONE" -> MapGenerationContext.TERRAIN_STONE;
                case "GOLD" -> MapGenerationContext.TERRAIN_GOLD;
                case "WOOD", "FOREST", "DIRT", "OAKS" -> MapGenerationContext.TERRAIN_DIRT;
                case "FOOD", "BERRIES", "FARM" -> MapGenerationContext.TERRAIN_FOOD;
                case "HUNT", "BOAR", "DEER" -> MapGenerationContext.TERRAIN_HUNT;
                case "WATER" -> MapGenerationContext.TERRAIN_WATER;
                case "DESERT" -> MapGenerationContext.TERRAIN_DESERT;
                case "SNOW" -> MapGenerationContext.TERRAIN_SNOW;
                case "GRASS" -> MapGenerationContext.TERRAIN_GRASS;
                case "LAVA" -> MapGenerationContext.TERRAIN_LAVA;
                default -> MapGenerationContext.TERRAIN_DIRT;
            };
        }

        return MapGenerationContext.TERRAIN_DIRT;
    }

    /**
     * Get integer parameter with default value
     */
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
}
