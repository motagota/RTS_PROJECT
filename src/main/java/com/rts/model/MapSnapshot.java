package com.rts.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Represents a snapshot of the map at a specific generation step
 * Used for step-by-step playback and visualisation
 * 
 * Example JSON:
 * {
 * "step":1,
 * "description":"create_player_lands {terrain_type grass}",
 * "grid":{
 * "width:100,
 * "height":100,
 * "cells":[..1000 cells..]}
 * },
 * "metadata":{
 * "playerCount":2,
 * "landsCreated":2,
 * "tilesModified":450}
 */
@Data
@NoArgsConstructor
public class MapSnapshot {
  
    private int step;
  
    private String description;

    private MapGrid grid;


    public MapSnapshot(int step, String description, MapGrid grid){
        this.step = step;
        this.description = description;
        this.grid = grid;
    }
}
