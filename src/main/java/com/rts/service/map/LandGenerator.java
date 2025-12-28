package com.rts.service.map;

import com.rts.model.MapGrid;
import com.rts.model.Terrain;
import com.rts.utils.RNG;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for synchronous land generation without streaming updates.
 *
 * Extends AbstractLandGenerator and uses the shared frontier-based growth algorithm.
 * This version does not send any progress updates - it just generates the land silently.
 */
@Slf4j
@Service
public class LandGenerator extends AbstractLandGenerator {

    /**
     * Grow land without streaming updates.
     * Convenience method that wraps the base growLand() with null owner.
     *
     * @param grid the map grid to modify
     * @param rng random number generator
     * @param originX starting x coordinate
     * @param originY starting y coordinate
     * @param targetTiles number of tiles to place
     * @param terrainType terrain type to place
     * @param landId land identifier
     * @return number of tiles actually placed
     */
    public int growLand(MapGrid grid, RNG rng, int originX, int originY,
                        int targetTiles, Terrain terrainType, int landId) {
        return super.growLand(grid, rng, originX, originY, targetTiles, terrainType, landId, null);
    }

    // No need to override callback methods - default no-op behavior is what we want
}
