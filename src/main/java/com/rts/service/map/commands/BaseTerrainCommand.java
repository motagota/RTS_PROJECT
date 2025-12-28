package com.rts.service.map.commands;

import com.rts.service.map.MapGenerationContext;
import com.rts.service.map.RMSCommand;

import java.util.Map;

/**
 * RMS Command to set the base terrain for the entire map
 */
public class BaseTerrainCommand implements RMSCommand {

    @Override
    public void execute(MapGenerationContext context, Map<String, Object> parameters) {
        String terrainType = (String) parameters.getOrDefault("type", "GRASS");
        context.setBaseTerrain(terrainType);

        int terrainValue = getTerrainValue(terrainType);

        // Fill entire map with base terrain
        for (int x = 0; x < context.getWidth(); x++) {
            for (int y = 0; y < context.getHeight(); y++) {
                context.setTerrainAt(x, y, terrainValue);
                context.setElevationAt(x, y, 0);
            }
        }

        System.out.println("Applied base terrain: " + terrainType);
    }

    @Override
    public String getCommandName() {
        return "base_terrain";
    }

    private int getTerrainValue(String terrainType) {
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
