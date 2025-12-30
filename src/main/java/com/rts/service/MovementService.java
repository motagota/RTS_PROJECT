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

            // Move unit along path
            moveUnitAlongPath(unit);
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
                System.out.println("No free position found near target (" + targetX + "," + targetY + ")");
                return;
            }

            finalTargetX = freePosition.getX();
            finalTargetY = freePosition.getY();
            System.out.println("Found free position at (" + finalTargetX + "," + finalTargetY + ") near target (" + targetX + "," + targetY + ")");
        } else {
            System.out.println("Using exact target position (" + targetX + "," + targetY + ")");
        }

        unit.setTargetX(finalTargetX);
        unit.setTargetY(finalTargetY);

        // Calculate path
        calculatePath(unit, terrainData, buildings, units, mapWidth, mapHeight);
    }

    private void calculatePath(Unit unit, byte[] terrainData, List<Building> buildings,
                                List<Unit> units, int mapWidth, int mapHeight) {
        if (unit.getTargetX() == null || unit.getTargetY() == null) {
            return;
        }

        System.out.println("Calculating path for unit " + unit.getId() + " from (" + unit.getX() + "," + unit.getY() +
            ") to (" + unit.getTargetX() + "," + unit.getTargetY() + ")");

        List<PathNode> path = pathfindingService.findPath(
            unit.getX(), unit.getY(),
            unit.getTargetX(), unit.getTargetY(),
            mapWidth, mapHeight,
            terrainData, buildings, units
        );

        if (path == null) {
            System.out.println("Path is NULL - no path found!");
        } else if (path.isEmpty()) {
            System.out.println("Path is EMPTY - already at destination or no path exists");
        } else {
            System.out.println("Path found with " + path.size() + " nodes");
        }

        unit.setPath(path);
    }

    private void moveUnitAlongPath(Unit unit) {
        List<PathNode> path = unit.getPath();

        if (path == null || path.isEmpty()) {
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
            unit.setX(nextNode.getX());
            unit.setY(nextNode.getY());
            path.remove(0);  // Remove reached waypoint

            // Check if we've reached the final destination
            if (path.isEmpty()) {
                unit.setTargetX(null);
                unit.setTargetY(null);
                unit.setPath(null);
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
                // Move one tile towards the target
                if (Math.abs(dx) > Math.abs(dy)) {
                    unit.setX(unit.getX() + (dx > 0 ? 1 : -1));
                } else if (Math.abs(dy) > 0) {
                    unit.setY(unit.getY() + (dy > 0 ? 1 : -1));
                }
                // Reset progress (keep fractional remainder)
                unit.setMovementProgress(progress - 1.0);
            } else {
                // Not enough progress yet, just store it
                unit.setMovementProgress(progress);
            }
        }
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
