package com.rts.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete map grid
 *
 * Example JSON:
 * {
 *  "width":100,
 *   "height":100,
 *   "cells":[
 * {"x":0,"y":0, "terrain:"GRASS","elevation:0, "landId":1, "owner"":!"},
 * {"x":1,"y":0, "terrain:"GRASS","elevation:0, "landId":1, "owner"":!"},
 *   ]
 * }
 */

@Data
@NoArgsConstructor
public class MapGrid {

    /**
     * grid width in tiles
     */
    private int width;

    /**
     * grid height in tiles
     */
    private int height;

    private MapCell[] cells;

    /**
     * Buildings placed on this map
     */
    private List<Building> buildings = new ArrayList<>();

    /**
     * Units placed on this map
     */
    private List<Unit> units = new ArrayList<>();

    public MapGrid(int width, int height, MapCell[] cells) {
        this.width = width;
        this.height = height;
        this.cells = cells;
        this.buildings = new ArrayList<>();
        this.units = new ArrayList<>();
    }

    public void addBuilding(Building building) {
        this.buildings.add(building);
    }

    public void addUnit(Unit unit) {
        this.units.add(unit);
    }

    public MapGrid deepCopy(){
        MapCell[] copiedCells = new MapCell[cells.length];
        for(int i=0; i<cells.length;i++){
            copiedCells[i] = cells[i].copy();
        }
        return new MapGrid(width, height, copiedCells);
    }

    /**
     * Get cell at coordinate (x, y).
     * Uses row-major storage: cells[y * width + x]
     *
     * @param x column (horizontal position, 0 to width-1)
     * @param y row (vertical position, 0 to height-1)
     * @return the MapCell at the specified position
     */
    public MapCell getCell(int x, int y) {
        return cells[y * width + x];
    }
}


