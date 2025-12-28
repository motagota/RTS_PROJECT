package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.PlayerLand;
import com.rts.service.map.RMSCommand;

import java.util.Map;

/**
 * RMS Command to place player starting positions
 * Places players in a circle around the map center
 * Opposing players are on opposite sides
 * Also creates player land zones for fair resource distribution
 */
public class PlayerStartsCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        int playerCount = context.getPlayerCount();
        int width = context.getWidth();
        int height = context.getHeight();
        int edgeMin = context.getEdgeDistanceMin();
        int startRadius = context.getStartingAreaRadius();

        // Calculate center of map
        int centerX = width / 2;
        int centerY = height / 2;

        // Calculate radius for player spawn circle
        // Ensure we're at least edgeMin away from edges
        int maxRadius = Math.min(centerX - edgeMin - startRadius, centerY - edgeMin - startRadius);

        System.out.println("Placing " + playerCount + " players in circle formation");
        System.out.println("Map size: " + width + "x" + height + ", Center: (" + centerX + "," + centerY + ")");
        System.out.println("Spawn radius: " + maxRadius);

        // Place players in a circle, with opposing players on opposite sides
        double angleIncrement = (2 * Math.PI) / playerCount;

        for (int i = 0; i < playerCount; i++) {
            // Calculate angle for this player (clockwise from top)
            double angle = i * angleIncrement - (Math.PI / 2); // Start from top

            // Calculate position on circle
            int x = centerX + (int) (maxRadius * Math.cos(angle));
            int y = centerY + (int) (maxRadius * Math.sin(angle));

            // Add player start position
            context.addPlayerStart(x, y, i + 1);

            // Create player land zone for this player
            PlayerLand playerLand = new PlayerLand(x, y, context.getPlayerLandRadius(), i + 1);
            context.addPlayerLand(playerLand);

            // Clear and flatten the starting area
            clearStartingArea(context, x, y, startRadius);

            System.out.println("Player " + (i + 1) + " start: (" + x + "," + y + ") at angle " + Math.toDegrees(angle));
            System.out.println("  Player land radius: " + context.getPlayerLandRadius());
        }
    }

    @Override
    public String getCommandName() {
        return "player_starts";
    }

    /**
     * Clears and flattens terrain in a radius around the starting position
     */
    private void clearStartingArea(MapGenerationContext context, int centerX, int centerY, int radius) {
        int baseTerrainValue = getTerrainValue(context.getBaseTerrain());

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                // Check if within radius using distance formula
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

                if (distance <= radius) {
                    // Set to base terrain and flatten elevation
                    context.setTerrainAt(x, y, baseTerrainValue);
                    context.setElevationAt(x, y, 0);
                }
            }
        }
    }

    private int getTerrainValue(String terrainType) {
        if (terrainType == null) return MapGenerationContext.TERRAIN_GRASS;

        return switch (terrainType) {
            case "DESERT" -> MapGenerationContext.TERRAIN_DESERT;
            case "SNOW" -> MapGenerationContext.TERRAIN_SNOW;
            case "LAVA" -> MapGenerationContext.TERRAIN_LAVA;
            case "WATER" -> MapGenerationContext.TERRAIN_WATER;
            case "STONE" -> MapGenerationContext.TERRAIN_STONE;
            case "DIRT" -> MapGenerationContext.TERRAIN_DIRT;
            default -> MapGenerationContext.TERRAIN_GRASS;
        };
    }
}
