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

        // Find a free position near the target if target is occupied
        PathNode freePosition = pathfindingService.findNearestFreePosition(
            targetX, targetY, mapWidth, mapHeight, terrainData, buildings, units
        );

        if (freePosition == null) {
            System.out.println("No free position found near target (" + targetX + "," + targetY + ")");
            return;
        }

        unit.setTargetX(freePosition.getX());
        unit.setTargetY(freePosition.getY());

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

        // If very close to next waypoint, snap to it and move to next
        if (distance < 0.1) {
            unit.setX(nextNode.getX());
            unit.setY(nextNode.getY());
            path.remove(0);  // Remove reached waypoint

            // Check if we've reached the final destination
            if (path.isEmpty()) {
                unit.setTargetX(null);
                unit.setTargetY(null);
                unit.setPath(null);
            }
        } else {
            // Move towards next waypoint
            // Note: Since units are on a grid, we'll snap to grid positions
            // For smoother movement, we'd track float positions internally

            // For now, just teleport to next node if close enough (within movement speed)
            double movementSpeed = unit.getMovementSpeed();
            if (distance <= movementSpeed) {
                unit.setX(nextNode.getX());
                unit.setY(nextNode.getY());
                path.remove(0);
            } else {
                // For tile-based movement, we'll just move one step at a time
                // This gives us grid-aligned movement
                if (Math.abs(dx) > Math.abs(dy)) {
                    unit.setX(unit.getX() + (dx > 0 ? 1 : -1));
                } else if (Math.abs(dy) > 0) {
                    unit.setY(unit.getY() + (dy > 0 ? 1 : -1));
                }
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
