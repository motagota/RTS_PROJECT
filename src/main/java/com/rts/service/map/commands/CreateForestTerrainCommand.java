package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.*;

/**
 * RMS Command to create forest terrain patches (Phase 1 of woodline generation)
 *
 * This creates the base forest terrain layer before individual trees are placed.
 * Forest zones define WHERE trees can exist and create natural woodline shapes.
 *
 * Supports AoE2-style parameters:
 * - land_percent: Total map coverage by forest terrain (default 8%)
 * - number_of_groups: How many forest patches to create
 * - base_size: Average forest patch size
 * - border_fuzziness: Edge roughness (0-100, higher = more irregular edges)
 * - min_distance_to_players: Minimum distance from player starts
 * - avoid_player_start_areas: Safety radius around player starts
 */
public class CreateForestTerrainCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        // Get parameters
        int landPercent = getParameter(parameters, "land_percent", 8);
        int numberOfGroups = getParameter(parameters, "number_of_groups", 20);
        int baseSize = getParameter(parameters, "base_size", 4);
        int borderFuzziness = getParameter(parameters, "border_fuzziness", 30);
        int minDistanceToPlayers = getParameter(parameters, "min_distance_to_players", 0);
        int avoidPlayerStartAreas = getParameter(parameters, "avoid_player_start_areas", 5);

        // Calculate total tiles to convert to forest
        int totalTiles = context.getWidth() * context.getHeight();
        int targetForestTiles = (totalTiles * landPercent) / 100;
        int tilesPerGroup = Math.max(1, targetForestTiles / numberOfGroups);

        Random random = context.getRandom();
        int placedForestTiles = 0;

        // Place forest patches
        for (int i = 0; i < numberOfGroups && placedForestTiles < targetForestTiles; i++) {
            // Find valid center point
            int centerX = -1, centerY = -1;
            boolean foundValidCenter = false;

            for (int attempt = 0; attempt < 100 && !foundValidCenter; attempt++) {
                centerX = random.nextInt(context.getWidth());
                centerY = random.nextInt(context.getHeight());

                // Check distance to player starts
                boolean tooCloseToPlayer = false;
                for (MapGenerationContext.PlayerStartPosition playerStart : context.getPlayerStarts()) {
                    double distance = Math.sqrt(Math.pow(centerX - playerStart.x, 2) + Math.pow(centerY - playerStart.y, 2));
                    if (distance < Math.max(minDistanceToPlayers, avoidPlayerStartAreas)) {
                        tooCloseToPlayer = true;
                        break;
                    }
                }

                if (!tooCloseToPlayer) {
                    foundValidCenter = true;
                }
            }

            if (!foundValidCenter) {
                continue;
            }

            // Create forest patch using blob growth algorithm
            int patchTiles = createForestPatch(context, centerX, centerY, tilesPerGroup, baseSize, borderFuzziness, random);
            placedForestTiles += patchTiles;
        }
    }

    @Override
    public String getCommandName() {
        return "create_forest_terrain";
    }

    /**
     * Create a single forest patch with organic, irregular shape
     * Uses blob growth algorithm with fuzziness for natural edges
     */
    private int createForestPatch(MapGenerationContext context, int centerX, int centerY,
                                  int targetTiles, int baseSize, int fuzziness, Random random) {
        Set<Long> forestTiles = new HashSet<>();
        Queue<int[]> frontier = new LinkedList<>();

        // Start from center
        frontier.add(new int[]{centerX, centerY});
        forestTiles.add(key(centerX, centerY));

        // Calculate effective radius based on base size
        int radius = baseSize;
        int tilesPlaced = 0;

        // Grow the forest blob
        while (!frontier.isEmpty() && tilesPlaced < targetTiles) {
            int[] current = frontier.poll();
            int x = current[0];
            int y = current[1];

            // Set this tile to forest terrain
            if (context.getTerrainAt(x, y) >= 0) {
                // Only convert valid base terrain (grass, desert, snow)
                int currentTerrain = context.getTerrainAt(x, y);
                if (currentTerrain == MapGenerationContext.TERRAIN_GRASS ||
                    currentTerrain == MapGenerationContext.TERRAIN_DESERT ||
                    currentTerrain == MapGenerationContext.TERRAIN_SNOW) {
                    context.setTerrainAt(x, y, MapGenerationContext.TERRAIN_FOREST);
                    tilesPlaced++;
                }
            }

            // Expand to neighbors with probability based on distance and fuzziness
            int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                // Check bounds
                if (nx < 0 || nx >= context.getWidth() || ny < 0 || ny >= context.getHeight()) {
                    continue;
                }

                long k = key(nx, ny);
                if (forestTiles.contains(k)) {
                    continue;
                }

                // Calculate growth probability based on distance and fuzziness
                double newDistance = Math.sqrt(Math.pow(nx - centerX, 2) + Math.pow(ny - centerY, 2));

                // Base probability decreases with distance
                double baseProbability = 1.0 - (newDistance / (radius + 2));

                // Fuzziness adds randomness to edges (0-100 scale)
                double fuzzinessFactor = (fuzziness / 100.0);
                double randomFactor = random.nextDouble();

                // Higher fuzziness = more irregular edges
                double finalProbability = baseProbability * (1 - fuzzinessFactor) + randomFactor * fuzzinessFactor;

                if (finalProbability > 0.3) { // Threshold for growth
                    forestTiles.add(k);
                    frontier.add(new int[]{nx, ny});
                }
            }
        }

        return tilesPlaced;
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
