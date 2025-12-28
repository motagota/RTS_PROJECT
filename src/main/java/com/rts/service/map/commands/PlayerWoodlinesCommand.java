package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.PlayerLand;
import com.rts.service.map.RMSCommand;

import java.util.*;

/**
 * RMS Command to create forced player woodlines (AoE2 Arabia-style)
 *
 * Creates guaranteed woodlines for each player at specific distances:
 * - Back woodline: Close to TC, safe wood (8-12 tiles)
 * - Side woodlines: Medium distance (13-17 tiles)
 * - Front woodline: Forward position, risky (18-24 tiles)
 *
 * These are placed as FOREST terrain blobs first, then filled with trees.
 * This ensures consistent, balanced woodlines for all players.
 *
 * Supports AoE2-style parameters:
 * - min_distance_to_players: Minimum distance from player start
 * - max_distance_to_players: Maximum distance from player start
 * - number_of_objects: Size of the woodline (number of tree tiles)
 * - set_tight_grouping: Creates solid, connected woodline
 * - set_loose_grouping: Creates scattered trees
 */
public class PlayerWoodlinesCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        // Get parameters
        int minDistanceToPlayers = getParameter(parameters, "min_distance_to_players", 8);
        int maxDistanceToPlayers = getParameter(parameters, "max_distance_to_players", 12);
        int numberOfObjects = getParameter(parameters, "number_of_objects", 100);
        boolean setTightGrouping = getParameter(parameters, "set_tight_grouping", 1) != 0;
        boolean setLooseGrouping = getParameter(parameters, "set_loose_grouping", 0) != 0;

        System.out.println("Creating player woodlines:");
        System.out.println("  Distance to players: " + minDistanceToPlayers + "-" + maxDistanceToPlayers);
        System.out.println("  Trees per woodline: " + numberOfObjects);
        System.out.println("  Tight grouping: " + setTightGrouping);
        System.out.println("  Loose grouping: " + setLooseGrouping);

        // Place woodline for each player
        for (PlayerLand playerLand : context.getPlayerLands()) {
            placePlayerWoodline(context, playerLand, minDistanceToPlayers, maxDistanceToPlayers,
                              numberOfObjects, setTightGrouping, setLooseGrouping);
        }
    }

    @Override
    public String getCommandName() {
        return "player_woodlines";
    }

    /**
     * Place a forced woodline for a specific player
     */
    private void placePlayerWoodline(MapGenerationContext context, PlayerLand playerLand,
                                    int minDistance, int maxDistance, int numberOfObjects,
                                    boolean tightGrouping, boolean looseGrouping) {
        Random random = context.getRandom();

        // Try to find a good location for the woodline
        boolean placed = false;
        for (int attempt = 0; attempt < 50 && !placed; attempt++) {
            // Get random point at specified distance from player start
            int[] point = playerLand.getRandomPointInRadius(random, minDistance, maxDistance);
            int centerX = point[0];
            int centerY = point[1];

            // Check if location is valid
            if (centerX >= 0 && centerX < context.getWidth() &&
                centerY >= 0 && centerY < context.getHeight()) {

                // Create woodline as forest terrain first
                if (tightGrouping) {
                    createTightWoodline(context, centerX, centerY, numberOfObjects, random);
                } else if (looseGrouping) {
                    createLooseWoodline(context, centerX, centerY, numberOfObjects, random);
                } else {
                    // Default to tight grouping
                    createTightWoodline(context, centerX, centerY, numberOfObjects, random);
                }

                placed = true;
                System.out.println("  Player " + playerLand.getPlayerNumber() + ": placed woodline at (" + centerX + "," + centerY + ")");
            }
        }

        if (!placed) {
            System.out.println("  Player " + playerLand.getPlayerNumber() + ": Warning - Could not place woodline");
        }
    }

    /**
     * Create a tight, connected woodline (solid wall of trees)
     * Uses blob growth to create natural but dense forest
     */
    private void createTightWoodline(MapGenerationContext context, int centerX, int centerY,
                                    int numberOfObjects, Random random) {
        Set<Long> placed = new HashSet<>();
        Queue<int[]> frontier = new LinkedList<>();

        // Start from center
        frontier.add(new int[]{centerX, centerY});
        placed.add(key(centerX, centerY));

        // Set initial tile
        int currentTerrain = context.getTerrainAt(centerX, centerY);
        if (isValidBaseTerrainForForest(currentTerrain)) {
            context.setTerrainAt(centerX, centerY, MapGenerationContext.TERRAIN_FOREST);
        }

        int tilesPlaced = 1;

        // Grow the woodline blob
        while (!frontier.isEmpty() && tilesPlaced < numberOfObjects) {
            int[] current = frontier.poll();
            int x = current[0];
            int y = current[1];

            // Try to expand in all directions
            int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
            List<int[]> validNeighbors = new ArrayList<>();

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                if (nx < 0 || nx >= context.getWidth() || ny < 0 || ny >= context.getHeight()) {
                    continue;
                }

                long k = key(nx, ny);
                if (placed.contains(k)) {
                    continue;
                }

                int terrain = context.getTerrainAt(nx, ny);
                if (isValidBaseTerrainForForest(terrain)) {
                    validNeighbors.add(new int[]{nx, ny});
                }
            }

            // Pick a random valid neighbor to expand to
            if (!validNeighbors.isEmpty()) {
                int[] next = validNeighbors.get(random.nextInt(validNeighbors.size()));
                int nx = next[0];
                int ny = next[1];

                placed.add(key(nx, ny));
                context.setTerrainAt(nx, ny, MapGenerationContext.TERRAIN_FOREST);
                frontier.add(new int[]{nx, ny});
                tilesPlaced++;
            }
        }

        // Now fill forest terrain with tree objects
        for (Long k : placed) {
            int x = (int) (k >> 32);
            int y = (int) (k & 0xffffffffL);

            if (context.getTerrainAt(x, y) == MapGenerationContext.TERRAIN_FOREST) {
                // Fill most forest tiles with trees (90% density)
                if (random.nextInt(100) < 90) {
                    context.setTerrainAt(x, y, MapGenerationContext.TERRAIN_TREE);
                }
            }
        }
    }

    /**
     * Create a loose, scattered woodline (individual trees spread out)
     */
    private void createLooseWoodline(MapGenerationContext context, int centerX, int centerY,
                                    int numberOfObjects, Random random) {
        Set<Long> placed = new HashSet<>();
        int radius = Math.max(5, (int) Math.sqrt(numberOfObjects));

        int treesPlaced = 0;
        while (treesPlaced < numberOfObjects) {
            // Random position within radius
            int x = centerX - radius + random.nextInt(radius * 2);
            int y = centerY - radius + random.nextInt(radius * 2);

            if (x < 0 || x >= context.getWidth() || y < 0 || y >= context.getHeight()) {
                continue;
            }

            long k = key(x, y);
            if (placed.contains(k)) {
                continue;
            }

            int terrain = context.getTerrainAt(x, y);
            if (isValidBaseTerrainForForest(terrain)) {
                // Place forest terrain then tree
                context.setTerrainAt(x, y, MapGenerationContext.TERRAIN_FOREST);
                if (random.nextInt(100) < 80) { // 80% chance of tree on forest tile
                    context.setTerrainAt(x, y, MapGenerationContext.TERRAIN_TREE);
                }
                placed.add(k);
                treesPlaced++;
            }
        }
    }

    /**
     * Check if terrain can be converted to forest
     */
    private boolean isValidBaseTerrainForForest(int terrain) {
        return terrain == MapGenerationContext.TERRAIN_GRASS ||
               terrain == MapGenerationContext.TERRAIN_DESERT ||
               terrain == MapGenerationContext.TERRAIN_SNOW ||
               terrain == MapGenerationContext.TERRAIN_FOREST; // Allow placing on existing forest terrain
    }

    private long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
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
}
