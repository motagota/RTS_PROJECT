package com.rts.utils;

import com.rts.model.MapCell;
import com.rts.model.MapGrid;
import com.rts.model.Terrain;

/**
 * Utility class for initializing MapGrid with consistent coordinate mapping.
 *
 * Grid Convention:
 * - Uses row-major storage: cells[y * width + x]
 * - getCell(x, y) retrieves cell at column x, row y
 * - Coordinates: x is horizontal (column), y is vertical (row)
 */
public class GridInitializer {

    /**
     * Creates a MapGrid with uninitialized cells (terrain = NONE).
     * Each cell is created with its x,y coordinates but no terrain or ownership.
     *
     * @param mapSize the width and height of the square map
     * @return initialized MapGrid with empty cells
     */
    public static MapGrid createEmptyGrid(int mapSize) {
        MapCell[] cells = new MapCell[mapSize * mapSize];

        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                cells[y * mapSize + x] = new MapCell(x, y);
            }
        }

        return new MapGrid(mapSize, mapSize, cells);
    }

    /**
     * Creates a MapGrid filled with a specific terrain type.
     * All cells are initialized with the given terrain and landId = 0.
     *
     * @param mapSize the width and height of the square map
     * @param terrain the terrain type to fill the grid with
     * @return initialized MapGrid filled with the specified terrain
     */
    public static MapGrid createFilledGrid(int mapSize, Terrain terrain) {
        MapCell[] cells = new MapCell[mapSize * mapSize];

        for (int y = 0; y < mapSize; y++) {
            for (int x = 0; x < mapSize; x++) {
                MapCell cell = new MapCell(x, y, terrain, 0,0,0,null,"INITIAL");
                cell.setLandId(0);
                cells[y * mapSize + x] = cell;
            }
        }

        return new MapGrid(mapSize, mapSize, cells);
    }

    /**
     * Creates a MapGrid filled with WATER terrain.
     * Convenience method for ocean/water-based maps.
     *
     * @param mapSize the width and height of the square map
     * @return initialized MapGrid filled with WATER
     */
    public static MapGrid createWaterGrid(int mapSize) {
        return createFilledGrid(mapSize, Terrain.WATER);
    }
}
