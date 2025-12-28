package com.rts.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rts.model.Terrain;
import com.rts.service.map.LandGenerator;
import com.rts.utils.GridInitializer;
import com.rts.utils.RNG;
import org.springframework.stereotype.Service;

import com.rts.dto.AstNode;
import com.rts.dto.ExecutionResult;
import com.rts.model.MapCell;
import com.rts.model.MapSnapshot;
import com.rts.model.MapGrid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapExecutor {

    private final LandGenerator landGenerator;

    public ExecutionResult execute(List<AstNode> ast, int mapSize, long seed){
        log.info("Executing {} AST nodes for map size {} with seed {}", ast.size(), mapSize, seed);

        RNG rng = new RNG(seed);

        // Initialize grid with empty cells
        MapGrid grid = GridInitializer.createEmptyGrid(mapSize);

        List<MapSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new MapSnapshot(0, "Initial state", grid.deepCopy()));

        int landIdCounter =1;
        List<PlayerOrigin> playerOrigins = new ArrayList<>();
        for (int i  =0; i< ast.size(); i++) {

            AstNode node = ast.get(i);
            String command = node.getType().toLowerCase();
            log.debug("Executing command {}: {}",i, command);
            try{
                switch (command) {
                    case "create_player_lands" ->{
                        // Get number of players to allocate land IDs correctly
                        Integer numPlayers = node.getAttributeAsInt("number_of_players");
                        if (numPlayers == null) numPlayers = 8;

                        executeCreatePlayerLands(grid, node,rng, mapSize,landIdCounter, playerOrigins);

                        landIdCounter += numPlayers;
                    }                    
                    default ->{
                        log.warn("Unknown command {}", command);
                    }

                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("commandIndex",i);
                metadata.put("commandType",command);
                //metadata.put("ladsCount",lands.size());

                snapshots.add( new MapSnapshot(
                        i+1,
                        node.getType(),
                        grid.deepCopy()
                ));

            }catch(Exception e){
                log.error("Error executing command {}: {}",command,e.getMessage());
                throw new RuntimeException("Error in command "+ i + " ( "+command +" ): "+e.getMessage(),e);
            }
        }

        log.info("Execution complete: {} snapshots generated", snapshots.size());
        return new ExecutionResult(snapshots,ast);
    }

    private void executeCreatePlayerLands(MapGrid grid, AstNode node, RNG rng, int mapSize, int landId, List<PlayerOrigin> playerOrigins) {

        log.debug("Create player lands");
        String terrainType = (String) node.getAttribute("terrain_type");
        Terrain terrain = terrainType != null ? Terrain.fromString(terrainType) : Terrain.NONE;

        Integer landPercent = node.getAttributeAsInt("land_percent");
        Integer baseSize =  node.getAttributeAsInt("base_size");

        // Get number of players from RMS script, default to 8 (standard AOE)
        Integer numPlayers = node.getAttributeAsInt("number_of_players");
        if (numPlayers == null) numPlayers = 8;

        List<?> circleRadius = (List<?>) node.getAttribute("circle_radius");

        int radiusPercent = 20;
        int variance = 10;

        log.debug("CircleRadius : {}", circleRadius);
        if( circleRadius != null && !circleRadius.isEmpty() ){
            radiusPercent = Integer.parseInt( circleRadius.get(0).toString());
            if( circleRadius.size() > 1 ){
                variance = Integer.parseInt(circleRadius.get(1).toString());
            }
        }

        log.debug("Circle radius: {}, variance {}",radiusPercent,  variance);

        int cx = mapSize / 2;
        int cy = mapSize / 2;
        int radiusTiles = (int) Math.round((radiusPercent / 100.0)* mapSize);

        playerOrigins.clear();
        for ( int p = 0; p< numPlayers; p++ ) {

            double angle = (( p/ (double) numPlayers ) * Math.PI*2) + ((rng.nextDouble() -0.5) *0.2);
            int r = Math.max(1, (int) Math.round(radiusTiles + (rng.nextDouble()*variance-variance/2.0)));
            int ox =Math.max(0, Math.min(mapSize-1, (int) Math.round(cx+Math.cos(angle)*r)));
            int oy =Math.max(0, Math.min(mapSize-1, (int) Math.round(cy+Math.sin(angle)*r)));

            MapCell originCell = grid.getCell(ox,oy);
            originCell.setTerrain(terrain);
            originCell.setOwner(p+1);
            playerOrigins.add( new PlayerOrigin(p+1, ox, oy, baseSize !=null ? baseSize : 3));

            log.debug("Create player land {} {} {}",p+1, ox, oy);

        }

        int totalTile = landPercent != null ? (int) ((landPercent/100)*mapSize*mapSize):
                (int)(0.18*mapSize*mapSize);
        int tilesPerPlayer = Math.max(20 , totalTile/numPlayers);

        log.debug("Tiles per player : {}", tilesPerPlayer);

        for(int p = 0; p< playerOrigins.size(); p++ ){
            PlayerOrigin playerOrigin = playerOrigins.get(p);

            int actualLandId = landId+p;

            int placed = landGenerator.growLand(
                    grid, rng, playerOrigin.x, playerOrigin.y,
                    tilesPerPlayer, terrain, actualLandId
            );

            log.debug("Player {} land {} at ({},{})",playerOrigin.playerId, placed, playerOrigin.x, playerOrigin.y);

        }


    }

    private static class PlayerOrigin {
        final int playerId;
        final int x;
        final int y;
        final int baseSize;
        PlayerOrigin(int playerId, int x, int y , int baseSize) {
            this.playerId = playerId;
            this.x = x;
            this.y =y;
            this.baseSize = baseSize;
        }

    }

}
