package com.rts.service.map;

import com.rts.model.Building;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Context object that holds the state during map generation
 */
public class MapGenerationContext {
    private int width;
    private int height;
    private int[][] terrainMap;
    private int[][] elevationMap;
    private List<PlayerStartPosition> playerStarts;
    private List<Building> buildings;
    private List<PlayerLand> playerLands;
    private Random random;
    private String baseTerrain;
    private int playerCount;
    private int edgeDistanceMin;
    private int startingAreaRadius;
    private int playerLandRadius;
    private boolean shouldGenerateHeadquarters;

    public MapGenerationContext(int width, int height, int playerCount, long seed) {
        this.width = width;
        this.height = height;
        this.playerCount = playerCount;
        this.terrainMap = new int[width][height];
        this.elevationMap = new int[width][height];
        this.playerStarts = new ArrayList<>();
        this.buildings = new ArrayList<>();
        this.playerLands = new ArrayList<>();
        this.random = new Random(seed);
        this.edgeDistanceMin = 10;
        this.startingAreaRadius = 5;
        this.playerLandRadius = 20; // Default player land radius
        this.shouldGenerateHeadquarters = false;
    }

    // Terrain type constants - Base terrains
    public static final int TERRAIN_GRASS = 0;
    public static final int TERRAIN_DESERT = 1;
    public static final int TERRAIN_SNOW = 2;
    public static final int TERRAIN_LAVA = 3;
    public static final int TERRAIN_WATER = 4;

    // Resource type constants - RTS resources (AoE2-style)
    public static final int TERRAIN_STONE = 5;   // Stone mine (strategic resource)
    public static final int TERRAIN_DIRT = 6;    // Wood/Forest (gathering resource) - DEPRECATED, use TREE
    public static final int TERRAIN_GOLD = 7;    // Gold mine (premium resource)
    public static final int TERRAIN_FOOD = 8;    // Food source (berries/farms)
    public static final int TERRAIN_HUNT = 9;    // Hunt animals (risky food)

    // Forest system (AoE2-style 2-phase woodline generation)
    public static final int TERRAIN_FOREST = 10; // Forest terrain (base layer, darker tiles)
    public static final int TERRAIN_TREE = 11;   // Tree objects (visual layer, placed on forest terrain)

    public static class PlayerStartPosition {
        public int x;
        public int y;
        public int playerNumber;

        public PlayerStartPosition(int x, int y, int playerNumber) {
            this.x = x;
            this.y = y;
            this.playerNumber = playerNumber;
        }
    }

    // Getters and Setters

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[][] getTerrainMap() {
        return terrainMap;
    }

    public int[][] getElevationMap() {
        return elevationMap;
    }

    public List<PlayerStartPosition> getPlayerStarts() {
        return playerStarts;
    }

    public Random getRandom() {
        return random;
    }

    public String getBaseTerrain() {
        return baseTerrain;
    }

    public void setBaseTerrain(String baseTerrain) {
        this.baseTerrain = baseTerrain;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getEdgeDistanceMin() {
        return edgeDistanceMin;
    }

    public void setEdgeDistanceMin(int edgeDistanceMin) {
        this.edgeDistanceMin = edgeDistanceMin;
    }

    public int getStartingAreaRadius() {
        return startingAreaRadius;
    }

    public void setStartingAreaRadius(int startingAreaRadius) {
        this.startingAreaRadius = startingAreaRadius;
    }

    public void setTerrainAt(int x, int y, int terrainType) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            terrainMap[x][y] = terrainType;
        }
    }

    public int getTerrainAt(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return terrainMap[x][y];
        }
        return -1;
    }

    public void setElevationAt(int x, int y, int elevation) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            elevationMap[x][y] = elevation;
        }
    }

    public int getElevationAt(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return elevationMap[x][y];
        }
        return 0;
    }

    public void addPlayerStart(int x, int y, int playerNumber) {
        playerStarts.add(new PlayerStartPosition(x, y, playerNumber));
    }

    public List<Building> getBuildings() {
        return buildings;
    }

    public void addBuilding(Building building) {
        this.buildings.add(building);
    }

    public boolean shouldGenerateHeadquarters() {
        return shouldGenerateHeadquarters;
    }

    public void setShouldGenerateHeadquarters(boolean shouldGenerateHeadquarters) {
        this.shouldGenerateHeadquarters = shouldGenerateHeadquarters;
    }

    public List<PlayerLand> getPlayerLands() {
        return playerLands;
    }

    public void addPlayerLand(PlayerLand playerLand) {
        this.playerLands.add(playerLand);
    }

    /**
     * Find which player land a point belongs to (if any)
     */
    public PlayerLand getPlayerLandAt(int x, int y) {
        for (PlayerLand land : playerLands) {
            if (land.contains(x, y)) {
                return land;
            }
        }
        return null;
    }

    public int getPlayerLandRadius() {
        return playerLandRadius;
    }

    public void setPlayerLandRadius(int playerLandRadius) {
        this.playerLandRadius = playerLandRadius;
    }
}
