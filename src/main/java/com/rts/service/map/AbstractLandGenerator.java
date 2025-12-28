package com.rts.service.map;

import com.rts.model.MapCell;
import com.rts.model.MapGrid;
import com.rts.model.Terrain;
import com.rts.utils.RNG;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Abstract base class for land generation using frontier-based growth algorithm.
 *
 * This implements the core land growth algorithm used in Age of Empires 2:
 * - Frontier-based expansion (grows from outer edges)
 * - Clumping bias (prefers to grow near recently placed tiles)
 * - Adjacency bonus (grows better when surrounded by same terrain)
 * - Deterministic with RNG seed
 *
 * Subclasses can provide callbacks for progress updates (e.g., WebSocket streaming).
 */
@Slf4j
public abstract class AbstractLandGenerator {

    // 4 directional movement, no diagonals
    protected static final int[][] NEIGHBORS_4 = {
            {1, 0},  // E
            {-1, 0}, // W
            {0, 1},  // S
            {0, -1}, // N
    };

    /**
     * Base probability for tile placement
     */
    protected static final double BASE_PROBABILITY = 0.3;

    /**
     * Additional probability per adjacent same-terrain tile
     */
    protected static final double ADJACENT_BONUS_PROBABILITY = 0.12;

    /**
     * Point class for frontier tracking
     */
    protected static class Point {
        public int x;
        public int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        @Override
        public String toString() {
            return "Point{x=" + x + ",y=" + y + "}";
        }
    }

    /**
     * Grow land using frontier-based expansion algorithm.
     *
     * @param grid the map grid to modify
     * @param rng random number generator for deterministic generation
     * @param originX starting x coordinate (column)
     * @param originY starting y coordinate (row)
     * @param targetTiles number of tiles to place
     * @param terrainType terrain type to place
     * @param landId land identifier for grouping
     * @param owner optional player owner
     * @return number of tiles actually placed
     */
    public int growLand(MapGrid grid, RNG rng, int originX, int originY,
                        int targetTiles, Terrain terrainType, int landId, Integer owner) {

        log.debug("Growing land: origin=({},{}), target={}, terrain={}, landId={}",
                 originX, originY, targetTiles, terrainType, landId);

        MapCell originCell = grid.getCell(originX, originY);
        if (originCell == null) {
            log.warn("Invalid origin coordinates: ({},{})", originX, originY);
            return 0;
        }

        // Initialize origin cell
        originCell.setTerrain(terrainType);
        originCell.setLandId(landId);
        if (owner != null) {
            originCell.setOwner(owner);
        }

        List<Point> frontier = new ArrayList<>();
        Set<String> placed = new HashSet<>();

        frontier.add(new Point(originX, originY));
        placed.add(key(originX, originY));

        int placedTiles = 1;
        int iterations = 0;
        int maxIterations = targetTiles * 10;

        // Notify start
        onStart(placedTiles, targetTiles, landId, owner, originX, originY, terrainType);

        // Main growth loop
        while (placedTiles < targetTiles && !frontier.isEmpty() && iterations < maxIterations) {
            iterations++;

            // Pick random frontier point
            int idx = (int) Math.floor(rng.nextDouble() * frontier.size());
            idx = Math.min(idx, frontier.size() - 1);

            Point current = frontier.remove(idx);

            List<TilePlacement> newTiles = new ArrayList<>();

            // Try to expand to neighbors
            for (int[] dir : NEIGHBORS_4) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];

                // Bounds check
                if (nx < 0 || nx >= grid.getWidth() || ny < 0 || ny >= grid.getHeight()) {
                    continue;
                }

                String neighborKey = key(nx, ny);

                if (placed.contains(neighborKey)) {
                    continue;
                }

                // Calculate placement probability based on adjacency
                int adjacentSame = countAdjacentTerrain(grid, nx, ny, terrainType);
                double probability = BASE_PROBABILITY + (adjacentSame * ADJACENT_BONUS_PROBABILITY);

                if (rng.nextDouble() < probability) {
                    MapCell cell = grid.getCell(nx, ny);
                    if (cell != null) {
                        cell.setTerrain(terrainType);
                        cell.setLandId(landId);
                        if (owner != null) {
                            cell.setOwner(owner);
                        }
                        placed.add(neighborKey);

                        placedTiles++;
                        frontier.add(new Point(nx, ny));

                        newTiles.add(new TilePlacement(nx, ny, terrainType, landId, owner));

                        if (placedTiles >= targetTiles) {
                            break;
                        }
                    }
                } else {
                    // Add to frontier for potential future expansion
                    if (!placed.contains(neighborKey) && !frontier.contains(new Point(nx, ny))) {
                        frontier.add(new Point(nx, ny));
                    }
                }
            }

            // Notify progress
            if (!newTiles.isEmpty()) {
                onProgress(placedTiles, targetTiles, landId, owner, iterations, newTiles, frontier);
            }

            // Safety: prevent frontier from growing too large
            if (frontier.size() > grid.getWidth() * grid.getHeight() / 4) {
                log.warn("Frontier is too large ({}), cutting it down", frontier.size());
                int keepSize = grid.getWidth() * grid.getHeight() / 8;
                frontier = new ArrayList<>(
                        frontier.subList(Math.max(0, frontier.size() - keepSize), frontier.size())
                );
            }
        }

        if (iterations >= maxIterations) {
            log.warn("Hit iteration limit while growing land. Placed {}/{} tiles.", placedTiles, targetTiles);
        }

        // Notify completion
        onComplete(placedTiles, targetTiles, landId, owner, iterations);

        log.debug("Land growth complete: placed {} tiles in {} iterations", placedTiles, iterations);
        return placedTiles;
    }

    /**
     * Count adjacent tiles with the same terrain type
     */
    protected int countAdjacentTerrain(MapGrid grid, int x, int y, Terrain terrainType) {
        int count = 0;
        for (int[] dir : NEIGHBORS_4) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            if (nx >= 0 && nx < grid.getWidth() && ny >= 0 && ny < grid.getHeight()) {
                MapCell cell = grid.getCell(nx, ny);
                if (cell != null && cell.getTerrain() == terrainType) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Create unique key for coordinate tracking
     */
    protected String key(int x, int y) {
        return x + "," + y;
    }

    // ========== Callback Methods (Template Pattern) ==========

    /**
     * Called when land generation starts.
     * Subclasses can override to send initial updates.
     */
    protected void onStart(int placedTiles, int targetTiles, int landId, Integer owner,
                          int originX, int originY, Terrain terrainType) {
        // Default: no-op
    }

    /**
     * Called during land generation progress.
     * Subclasses can override to send periodic updates.
     */
    protected void onProgress(int placedTiles, int targetTiles, int landId, Integer owner,
                             int iterations, List<TilePlacement> newTiles, List<Point> frontier) {
        // Default: no-op
    }

    /**
     * Called when land generation completes.
     * Subclasses can override to send completion updates.
     */
    protected void onComplete(int placedTiles, int targetTiles, int landId, Integer owner, int iterations) {
        // Default: no-op
    }

    // ========== Helper Classes ==========

    /**
     * Represents a newly placed tile
     */
    protected static class TilePlacement {
        public final int x;
        public final int y;
        public final Terrain terrainType;
        public final int landId;
        public final Integer owner;

        public TilePlacement(int x, int y, Terrain terrainType, int landId, Integer owner) {
            this.x = x;
            this.y = y;
            this.terrainType = terrainType;
            this.landId = landId;
            this.owner = owner;
        }
    }
}
