package com.rts.service;

import com.rts.model.Building;
import com.rts.model.PathNode;
import com.rts.model.Unit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling unit movement along paths
 */
@Service
public class MovementService {

    @Autowired
    private PathfindingService pathfindingService;

    /**
     * Process movement for all units
     * @param units List of all units
     * @param terrainData Terrain data for pathfinding
     * @param buildings Buildings on the map
     * @param mapWidth Map width
     * @param mapHeight Map height
     */
    public void processUnitMovement(List<Unit> units, byte[] terrainData, List<Building> buildings,
                                     int mapWidth, int mapHeight) {
        if (units == null || units.isEmpty()) {
            return;
        }

        for (Unit unit : units) {
            // Skip if unit is not moving
            if (!unit.isMoving()) {
                continue;
            }

            // If unit has been blocked for too long, recalculate path
            // Recalculate after 10 ticks (1 second) to be more responsive
            if (unit.getBlockedTicks() >= 10) {
                calculatePath(unit, terrainData, buildings, units, mapWidth, mapHeight);
                unit.setBlockedTicks(0);  // Reset counter after recalculation

                // Clear last position when recalculating to allow fresh movement attempts
                unit.setLastX(null);
                unit.setLastY(null);

                // If still no path after recalculation, unit is truly stuck
                if (unit.getPath() == null || unit.getPath().isEmpty()) {
                    unit.setTargetX(null);
                    unit.setTargetY(null);
                    continue;
                }
            }

            // If unit has no path, calculate one
            if (unit.getPath() == null || unit.getPath().isEmpty()) {
                calculatePath(unit, terrainData, buildings, units, mapWidth, mapHeight);

                // If still no path, unit cannot reach destination
                if (unit.getPath() == null || unit.getPath().isEmpty()) {
                    // Clear target - unit can't get there
                    unit.setTargetX(null);
                    unit.setTargetY(null);
                    continue;
                }
            }

            // Move unit along path (with collision detection)
            moveUnitAlongPath(unit, units, terrainData, buildings, mapWidth, mapHeight);
        }
    }

    /**
     * Set a unit to move to a target position
     */
    public void setUnitDestination(Unit unit, int targetX, int targetY,
                                     byte[] terrainData, List<Building> buildings, List<Unit> units,
                                     int mapWidth, int mapHeight) {
        setUnitDestination(unit, targetX, targetY, terrainData, buildings, units, mapWidth, mapHeight, true);
    }

    /**
     * Set a unit to move to a target position
     * @param findFreePosition If true, find nearest free position; if false, use exact target
     */
    public void setUnitDestination(Unit unit, int targetX, int targetY,
                                     byte[] terrainData, List<Building> buildings, List<Unit> units,
                                     int mapWidth, int mapHeight, boolean findFreePosition) {

        int finalTargetX = targetX;
        int finalTargetY = targetY;

        if (findFreePosition) {
            // Find a free position near the target if target is occupied
            PathNode freePosition = pathfindingService.findNearestFreePosition(
                targetX, targetY, mapWidth, mapHeight, terrainData, buildings, units
            );

            if (freePosition == null) {
                return;
            }

            finalTargetX = freePosition.getX();
            finalTargetY = freePosition.getY();
        }

        unit.setTargetX(finalTargetX);
        unit.setTargetY(finalTargetY);

        // Clear last position history when setting new destination
        unit.setLastX(null);
        unit.setLastY(null);

        // Calculate path
        calculatePath(unit, terrainData, buildings, units, mapWidth, mapHeight);
    }

    private void calculatePath(Unit unit, byte[] terrainData, List<Building> buildings,
                                List<Unit> units, int mapWidth, int mapHeight) {
        if (unit.getTargetX() == null || unit.getTargetY() == null) {
            return;
        }

        List<PathNode> path = pathfindingService.findPath(
            unit.getX(), unit.getY(),
            unit.getTargetX(), unit.getTargetY(),
            mapWidth, mapHeight,
            terrainData, buildings, units
        );

        unit.setPath(path);

        // Reset movement progress when calculating new path to prevent direction skipping
        unit.setMovementProgress(0);
    }

    private void moveUnitAlongPath(Unit unit, List<Unit> allUnits, byte[] terrainData,
                                    List<Building> buildings, int mapWidth, int mapHeight) {
        List<PathNode> path = unit.getPath();

        if (path == null || path.isEmpty()) {
            unit.setBlockedTicks(0);
            return;
        }

        // Get next waypoint
        PathNode nextNode = path.get(0);

        // Calculate direction to next node
        int dx = nextNode.getX() - unit.getX();
        int dy = nextNode.getY() - unit.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Movement speed (tiles per tick, e.g., 0.1 = 1 tile per second at 10 ticks/sec)
        double movementSpeed = unit.getMovementSpeed();

        // If very close to next waypoint, snap to it and move to next
        if (distance <= movementSpeed) {
            // Check if the destination is occupied by another unit
            if (!isPositionOccupied(nextNode.getX(), nextNode.getY(), allUnits, unit)) {
                // Store current position before moving
                unit.setLastX(unit.getX());
                unit.setLastY(unit.getY());

                unit.setX(nextNode.getX());
                unit.setY(nextNode.getY());
                path.remove(0);  // Remove reached waypoint
                unit.setBlockedTicks(0);  // Reset blocked counter

                // Check if we've reached the final destination
                if (path.isEmpty()) {
                    unit.setTargetX(null);
                    unit.setTargetY(null);
                    unit.setPath(null);
                    // Clear last position when reaching destination
                    unit.setLastX(null);
                    unit.setLastY(null);
                }
            } else {
                // Destination is occupied - try to move around the obstacle
                if (!tryMoveAround(unit, nextNode, allUnits, terrainData, buildings, mapWidth, mapHeight)) {
                    // Can't move around, increment blocked counter
                    unit.setBlockedTicks(unit.getBlockedTicks() + 1);
                }
            }
        }
        // If distance is greater than movement speed, accumulate movement progress
        // For tile-based games, we move tile-by-tile at the specified rate
        // With movementSpeed=0.1 and 10 ticks/sec, this means 1 tile per second
        else {
            // Accumulate movement progress
            double progress = unit.getMovementProgress() + movementSpeed;

            // If we've accumulated enough progress to move one tile
            if (progress >= 1.0) {
                // Calculate next position
                int nextX = unit.getX();
                int nextY = unit.getY();

                // Move one tile towards the target
                if (Math.abs(dx) > Math.abs(dy)) {
                    nextX = unit.getX() + (dx > 0 ? 1 : -1);
                } else if (Math.abs(dy) > 0) {
                    nextY = unit.getY() + (dy > 0 ? 1 : -1);
                }

                // Check if the next position is occupied by another unit
                if (!isPositionOccupied(nextX, nextY, allUnits, unit)) {
                    // Store current position before moving
                    unit.setLastX(unit.getX());
                    unit.setLastY(unit.getY());

                    unit.setX(nextX);
                    unit.setY(nextY);
                    // Reset progress (keep fractional remainder)
                    unit.setMovementProgress(progress - 1.0);
                    unit.setBlockedTicks(0);  // Reset blocked counter
                } else {
                    // Position is occupied - try to move around the obstacle
                    if (!tryMoveAround(unit, nextNode, allUnits, terrainData, buildings, mapWidth, mapHeight)) {
                        // Can't move around, increment blocked counter
                        unit.setBlockedTicks(unit.getBlockedTicks() + 1);
                    } else {
                        // Successfully moved around, reset progress
                        unit.setMovementProgress(0);
                        unit.setBlockedTicks(0);
                    }
                }
            } else {
                // Not enough progress yet, just store it
                unit.setMovementProgress(progress);
            }
        }
    }

    /**
     * Try to move around a blocking obstacle by checking adjacent tiles
     * This provides basic local collision avoidance with oscillation prevention
     *
     * @param unit The unit trying to move
     * @param targetNode The blocked target position
     * @param allUnits All units on the map
     * @param terrainData Terrain data for checking walkability
     * @param buildings Buildings on the map
     * @param mapWidth Map width
     * @param mapHeight Map height
     * @return true if unit successfully moved to an alternative position
     */
    private boolean tryMoveAround(Unit unit, PathNode targetNode, List<Unit> allUnits,
                                   byte[] terrainData, List<Building> buildings,
                                   int mapWidth, int mapHeight) {
        // Calculate direction to target
        int dx = targetNode.getX() - unit.getX();
        int dy = targetNode.getY() - unit.getY();

        // Try perpendicular directions first (preferred avoidance)
        int[][] avoidanceOffsets;

        if (Math.abs(dx) > Math.abs(dy)) {
            // Moving more horizontally - try vertical offsets
            avoidanceOffsets = new int[][] {
                {0, 1},   // Try moving down
                {0, -1},  // Try moving up
                {dx > 0 ? 1 : -1, 1},   // Diagonal forward-down
                {dx > 0 ? 1 : -1, -1}   // Diagonal forward-up
            };
        } else {
            // Moving more vertically - try horizontal offsets
            avoidanceOffsets = new int[][] {
                {1, 0},   // Try moving right
                {-1, 0},  // Try moving left
                {1, dy > 0 ? 1 : -1},   // Diagonal right-forward
                {-1, dy > 0 ? 1 : -1}   // Diagonal left-forward
            };
        }

        // Try each avoidance direction
        for (int[] offset : avoidanceOffsets) {
            int newX = unit.getX() + offset[0];
            int newY = unit.getY() + offset[1];

            // Check bounds
            if (newX < 0 || newX >= mapWidth || newY < 0 || newY >= mapHeight) {
                continue;
            }

            // ANTI-OSCILLATION: Don't move back to the position we just came from
            if (unit.getLastX() != null && unit.getLastY() != null &&
                newX == unit.getLastX() && newY == unit.getLastY()) {
                continue;
            }

            // Check if position is walkable (terrain, buildings, and units)
            if (isPositionBlocked(newX, newY, terrainData, buildings, allUnits, unit, mapWidth)) {
                continue;
            }

            // Calculate if this move brings us closer to or maintains distance to target
            double currentDist = Math.sqrt(
                Math.pow(unit.getX() - targetNode.getX(), 2) +
                Math.pow(unit.getY() - targetNode.getY(), 2)
            );
            double newDist = Math.sqrt(
                Math.pow(newX - targetNode.getX(), 2) +
                Math.pow(newY - targetNode.getY(), 2)
            );

            // Only move if we don't go significantly farther from target
            // Allow slight detours (within 1.5x current distance)
            if (newDist <= currentDist * 1.5) {
                // Store current position as last position before moving
                unit.setLastX(unit.getX());
                unit.setLastY(unit.getY());

                unit.setX(newX);
                unit.setY(newY);
                return true;
            }
        }

        return false; // No valid avoidance move found
    }

    /**
     * Check if a position is blocked by terrain, buildings, or units
     */
    private boolean isPositionBlocked(int x, int y, byte[] terrainData, List<Building> buildings,
                                       List<Unit> units, Unit excludeUnit, int mapWidth) {
        // Use pathfinding service to check terrain and buildings
        boolean blocked = pathfindingService.isBlocked(x, y, terrainData, buildings, units, mapWidth);

        // Also check if occupied by another unit
        if (!blocked && isPositionOccupied(x, y, units, excludeUnit)) {
            blocked = true;
        }

        return blocked;
    }

    /**
     * Check if a position is occupied by a unit
     */
    public boolean isPositionOccupied(int x, int y, List<Unit> units, Unit excludeUnit) {
        if (units == null) {
            return false;
        }

        for (Unit unit : units) {
            if (unit != excludeUnit && unit.getX() == x && unit.getY() == y) {
                return true;
            }
        }
        return false;
    }
}
