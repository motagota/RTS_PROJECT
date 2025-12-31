package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.PlayerLand;
import com.rts.service.map.RMSCommand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RMS Command to place resources for every player
 * Ensures fair and symmetric resource distribution by placing
 * resources within each player's land zone
 *
 * Supports advanced placement features:
 * - set_min_distance_to_players / set_max_distance_to_players
 * - set_min_distance_group_placement / set_max_distance_group_placement
 * - set_group_placement_radius
 */
public class PlaceResourcesForEveryPlayerCommand implements RMSCommand {

    // Track placed resource locations for group distance constraints
    private static class ResourcePatch {
        int centerX;
        int centerY;
        int playerNumber;

        ResourcePatch(int centerX, int centerY, int playerNumber) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.playerNumber = playerNumber;
        }
    }

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        // Get parameters
        int resourceType = getResourceType(parameters);
        int count = getParameter(parameters, "count", 3); // How many resource patches per player
        int minDistanceToPlayers = getParameter(parameters, "min_distance_to_players", 5); // Min distance from player start
        int maxDistanceToPlayers = getParameter(parameters, "max_distance_to_players", 15); // Max distance from player start
        int groupPlacementRadius = getParameter(parameters, "group_placement_radius", 3); // Size of each resource patch (cluster tightness)
        int numberOfObjects  = getParameter(parameters, "number_of_objects", 3); 
        boolean tightGrouping = getParameter(parameters, "tight_grouping", 0) != 0; // Tight grouping flag
        int minDistanceGroupPlacement = getParameter(parameters, "min_distance_group_placement", 0); // Min distance between resource groups
        int maxDistanceGroupPlacement = getParameter(parameters, "max_distance_group_placement", 999); // Max distance between resource groups

        // Track all placed patches for group distance constraints
        List<ResourcePatch> placedPatches = new ArrayList<>();

        // Place resources for each player
        for (PlayerLand playerLand : context.getPlayerLands()) {
            placeResourcesForPlayer(context, playerLand, resourceType, count, minDistanceToPlayers,
                                   maxDistanceToPlayers, groupPlacementRadius,numberOfObjects,tightGrouping,
                                    minDistanceGroupPlacement,
                                   maxDistanceGroupPlacement, placedPatches);
        }
    }

    @Override
    public String getCommandName() {
        return "place_resources_for_every_player";
    }

    /**
     * Place resources within a specific player's land with advanced placement constraints
     */
    private void placeResourcesForPlayer(MapGenerationContext context, PlayerLand playerLand,
                                        int resourceType, int count, int minDistanceToPlayers,
                                        int maxDistanceToPlayers, int groupPlacementRadius, int numberOfObjects,
                                        boolean tightGrouping,
                                        int minDistanceGroupPlacement, int maxDistanceGroupPlacement,
                                        List<ResourcePatch> placedPatches) {

        for (int i = 0; i < count; i++) {
            // Try up to 50 times to find a valid location (more attempts due to group distance constraints)
            boolean placed = false;
            for (int attempt = 0; attempt < 50 && !placed; attempt++) {
                // Get random point within the player's land at specified distance from player start
                int[] point = playerLand.getRandomPointInRadius(context.getRandom(),
                                                                minDistanceToPlayers,
                                                                maxDistanceToPlayers);
                int x = point[0];
                int y = point[1];

                // Check if this point is valid
                if (isValidResourceLocation(context, x, y, groupPlacementRadius, playerLand.getPlayerNumber(),
                                           minDistanceGroupPlacement, maxDistanceGroupPlacement, placedPatches)) {
                    placeResourcePatch(context, x, y, resourceType, groupPlacementRadius, numberOfObjects, tightGrouping);

                    // Track this patch for group distance constraints
                    placedPatches.add(new ResourcePatch(x, y, playerLand.getPlayerNumber()));

                    placed = true;
                }
            }
        }
    }

    /**
     * Check if a location is valid for placing a resource patch with group distance constraints
     */
    private boolean isValidResourceLocation(MapGenerationContext context, int centerX, int centerY,
                                           int patchSize, int playerNumber,
                                           int minDistanceGroupPlacement, int maxDistanceGroupPlacement,
                                           List<ResourcePatch> placedPatches) {
        int radius = patchSize / 2;

        // Check if all tiles in the patch area are within map bounds
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                if (x < 0 || x >= context.getWidth() || y < 0 || y >= context.getHeight()) {
                    return false;
                }
            }
        }

        // Check group distance constraints (distance to other resource patches of same player)
        for (ResourcePatch patch : placedPatches) {
            // Only check distance to patches from the same player
            if (patch.playerNumber == playerNumber) {
                double distance = Math.sqrt(Math.pow(centerX - patch.centerX, 2) + Math.pow(centerY - patch.centerY, 2));

                // Must be at least minDistanceGroupPlacement away
                if (distance < minDistanceGroupPlacement) {
                    return false;
                }

                // Must be at most maxDistanceGroupPlacement away (keeps resources grouped)
                if (maxDistanceGroupPlacement < 999 && distance > maxDistanceGroupPlacement) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Place a square patch of resources
     */
    private void placeResourcePatch(MapGenerationContext context, 
                                    int centerX, int centerY,
                                    int resourceType, 
                                    int patchSize, 
                                    int numberOfResources, 
                                    boolean tightGrouping){
        int half = patchSize / 2;

        Random random = context.getRandom();
        Set<Long> usedPositions = new HashSet<>();
        
        if ( tightGrouping){
            // --------- Tight Grouping ( connected clustered ) ---------

            List<int[]> frontier = new ArrayList<>();
            frontier.add(new int[]{centerX, centerY});
            usedPositions.add(key(centerX, centerY));
            context.setTerrainAt(centerX, centerY, resourceType);

            int placed = 1;

            while(placed < numberOfResources && !frontier.isEmpty()){               
                int[] base = frontier.get(random.nextInt(frontier.size()));   
                int bx = base[0];
                int by = base[1];

                int[][] dirs = {
                    {1,0}, {-1,0}, {0,1}, {0,-1}
                };

                int[] dir = dirs[random.nextInt(dirs.length)];

                int nx = bx + dir[0];
                int ny = by + dir[1];

                if(nx < centerX - half || nx > centerX + half ||
                   ny < centerY - half || ny > centerY + half){
                 
                    continue;                
                }

                long k = key(nx, ny);
                if ( usedPositions.contains(k)){
                    continue;
                }   

                usedPositions.add(k);
                context.setTerrainAt(nx, ny, resourceType);
                frontier.add(new int[]{nx, ny});
                placed++;
            }
        }  else {
            // --------- Loose Grouping ( scattered ) ---------

            int placed = 0;
            while (placed < numberOfResources) {
                int x = centerX - half + random.nextInt(patchSize)-half;
                int y = centerY - half + random.nextInt(patchSize)-half;

                long k = key(x, y);
                if ( usedPositions.contains(k)){
                    continue;
                }   

                usedPositions.add(k);
                context.setTerrainAt(x, y, resourceType);
                placed++;
            }
        }      
        
    }

    private long key(int x, int y){
        return (((long)x) << 32) | (y & 0xffffffffL);
    }

    /**
     * Get resource type from parameters
     */
    private int getResourceType(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("resource_type")) {
            return MapGenerationContext.TERRAIN_STONE; // Default to stone
        }

        Object resourceTypeParam = parameters.get("resource_type");

        if (resourceTypeParam instanceof Integer) {
            return (Integer) resourceTypeParam;
        } else if (resourceTypeParam instanceof String) {
            return switch ((String) resourceTypeParam) {
                // RTS Resources
                case "STONE" -> MapGenerationContext.TERRAIN_STONE;
                case "GOLD" -> MapGenerationContext.TERRAIN_GOLD;
                case "WOOD", "FOREST", "DIRT" -> MapGenerationContext.TERRAIN_DIRT;
                case "FOOD", "BERRIES", "FARM" -> MapGenerationContext.TERRAIN_FOOD;
                case "HUNT", "BOAR", "DEER" -> MapGenerationContext.TERRAIN_HUNT;
                // Base Terrains
                case "WATER" -> MapGenerationContext.TERRAIN_WATER;
                case "DESERT" -> MapGenerationContext.TERRAIN_DESERT;
                case "SNOW" -> MapGenerationContext.TERRAIN_SNOW;
                case "GRASS" -> MapGenerationContext.TERRAIN_GRASS;
                case "LAVA" -> MapGenerationContext.TERRAIN_LAVA;
                default -> MapGenerationContext.TERRAIN_STONE;
            };
        }

        return MapGenerationContext.TERRAIN_STONE;
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
