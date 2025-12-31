package com.rts.service;

import com.rts.model.Building;
import com.rts.model.PathNode;
import com.rts.model.Unit;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for pathfinding using A* algorithm
 */
@Service
public class PathfindingService {

    /**
     * Find a path from start to end using A* algorithm
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Target X coordinate
     * @param endY Target Y coordinate
     * @param mapWidth Width of the map
     * @param mapHeight Height of the map
     * @param terrainData Terrain data (for collision detection)
     * @param buildings List of buildings (obstacles)
     * @param units List of units (to avoid)
     * @return List of PathNodes from start to end, or null if no path found
     */
    public List<PathNode> findPath(int startX, int startY, int endX, int endY,
                                    int mapWidth, int mapHeight,
                                    byte[] terrainData,
                                    List<Building> buildings,
                                    List<Unit> units) {

        // Create start node
        PathNode startNode = new PathNode(startX, startY);

        // If already at destination
        if (startX == endX && startY == endY) {
            return new ArrayList<>();
        }

        // Open and closed sets
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(PathNode::getFCost));
        Set<String> openSetLookup = new HashSet<>();
        Set<String> closedSet = new HashSet<>();

        startNode.setGCost(0);
        startNode.setHCost(calculateHeuristic(startX, startY, endX, endY));
        openSet.add(startNode);
        openSetLookup.add(getNodeKey(startNode));

        while (!openSet.isEmpty()) {
            PathNode currentNode = openSet.poll();
            openSetLookup.remove(getNodeKey(currentNode));
            closedSet.add(getNodeKey(currentNode));

            // Check if we reached the goal
            if (currentNode.getX() == endX && currentNode.getY() == endY) {
                return reconstructPath(currentNode);
            }

            // Check all neighbors (8 directions)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue; // Skip current position

                    int neighborX = currentNode.getX() + dx;
                    int neighborY = currentNode.getY() + dy;

                    // Skip if out of bounds
                    if (neighborX < 0 || neighborX >= mapWidth || neighborY < 0 || neighborY >= mapHeight) {
                        continue;
                    }

                    // Skip if in closed set
                    String neighborKey = neighborX + "," + neighborY;
                    if (closedSet.contains(neighborKey)) {
                        continue;
                    }

                    // Skip if blocked (unless it's the destination)
                    if (!(neighborX == endX && neighborY == endY)) {
                        if (isBlocked(neighborX, neighborY, terrainData, buildings, units, mapWidth)) {
                            continue;
                        }
                    }

                    // Calculate costs
                    double movementCost = (dx != 0 && dy != 0) ? 1.414 : 1.0; // Diagonal vs straight
                    double newGCost = currentNode.getGCost() + movementCost;

                    PathNode neighborNode = new PathNode(neighborX, neighborY);
                    neighborNode.setGCost(newGCost);
                    neighborNode.setHCost(calculateHeuristic(neighborX, neighborY, endX, endY));
                    neighborNode.setParent(currentNode);

                    // Add to open set if not already there or if this path is better
                    if (!openSetLookup.contains(neighborKey)) {
                        openSet.add(neighborNode);
                        openSetLookup.add(neighborKey);
                    } else {
                        // Check if this path is better
                        for (PathNode node : openSet) {
                            if (node.getX() == neighborX && node.getY() == neighborY) {
                                if (newGCost < node.getGCost()) {
                                    openSet.remove(node);
                                    openSet.add(neighborNode);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // No path found
        return null;
    }

    /**
     * Find a suitable position near the target that is unoccupied and reachable
     * Prioritizes positions that are adjacent (radius 1) for resource gathering
     */
    public PathNode findNearestFreePosition(int targetX, int targetY,
                                             int mapWidth, int mapHeight,
                                             byte[] terrainData,
                                             List<Building> buildings,
                                             List<Unit> units) {

        // Check if target is already free and has walkable neighbors (not trapped)
        if (!isBlockedIncludingMovingUnits(targetX, targetY, terrainData, buildings, units, mapWidth) &&
            hasWalkableNeighbor(targetX, targetY, terrainData, buildings, mapWidth, mapHeight)) {
            return new PathNode(targetX, targetY);
        }

        // First priority: Check immediately adjacent positions (8 surrounding tiles)
        // This ensures units gather from adjacent tiles
        int[][] adjacentOffsets = {
            {0, -1},  // North
            {1, 0},   // East
            {0, 1},   // South
            {-1, 0},  // West
            {1, -1},  // NE
            {1, 1},   // SE
            {-1, 1},  // SW
            {-1, -1}  // NW
        };

        for (int[] offset : adjacentOffsets) {
            int checkX = targetX + offset[0];
            int checkY = targetY + offset[1];

            if (checkX < 0 || checkX >= mapWidth || checkY < 0 || checkY >= mapHeight) {
                continue;
            }

            // For free positions, we need to check ALL units (including moving ones)
            // to ensure the spot is truly available for gathering
            // Also verify the position has at least one walkable neighbor (not trapped)
            if (!isBlockedIncludingMovingUnits(checkX, checkY, terrainData, buildings, units, mapWidth) &&
                hasWalkableNeighbor(checkX, checkY, terrainData, buildings, mapWidth, mapHeight)) {
                return new PathNode(checkX, checkY);
            }
        }

        // Second priority: Search in expanding circles if no adjacent position is free
        // This handles cases where all adjacent tiles are occupied
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    // Only check positions on the edge of the circle
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;

                    int checkX = targetX + dx;
                    int checkY = targetY + dy;

                    if (checkX < 0 || checkX >= mapWidth || checkY < 0 || checkY >= mapHeight) {
                        continue;
                    }

                    if (!isBlockedIncludingMovingUnits(checkX, checkY, terrainData, buildings, units, mapWidth) &&
                        hasWalkableNeighbor(checkX, checkY, terrainData, buildings, mapWidth, mapHeight)) {
                        return new PathNode(checkX, checkY);
                    }
                }
            }
        }

        // No free position found nearby
        return null;
    }

    /**
     * Check if a position has at least one walkable neighbor
     * This ensures the position is not completely trapped/surrounded
     */
    private boolean hasWalkableNeighbor(int x, int y, byte[] terrainData,
                                         List<Building> buildings, int mapWidth, int mapHeight) {
        // Check all 8 directions
        int[][] directions = {
            {0, -1}, {1, 0}, {0, 1}, {-1, 0},  // Cardinal
            {1, -1}, {1, 1}, {-1, 1}, {-1, -1} // Diagonals
        };

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];

            // Check bounds
            if (nx < 0 || nx >= mapWidth || ny < 0 || ny >= mapHeight) {
                continue;
            }

            // Check if this neighbor is walkable (ignore units for this check)
            if (!isTerrainOrBuildingBlocked(nx, ny, terrainData, buildings, mapWidth)) {
                return true; // Found at least one walkable neighbor
            }
        }

        return false; // No walkable neighbors - position is trapped
    }

    /**
     * Check if a position is blocked by terrain or buildings only (not units)
     * Used for checking basic walkability
     */
    private boolean isTerrainOrBuildingBlocked(int x, int y, byte[] terrainData,
                                                 List<Building> buildings, int mapWidth) {
        // Check terrain
        if (terrainData != null) {
            int index = y * mapWidth + x;
            if (index >= 0 && index < terrainData.length) {
                byte terrain = terrainData[index];
                // Not walkable: WATER (4), LAVA (3), TREE (11), STONE (5), GOLD (7), FOOD/BERRIES (8)
                if (terrain == 3 || terrain == 4 || terrain == 11 || terrain == 5 || terrain == 7 || terrain == 8) {
                    return true;
                }
            }
        }

        // Check buildings
        if (buildings != null) {
            for (Building building : buildings) {
                int bx = building.getX();
                int by = building.getY();
                int bw = building.getWidth();
                int bh = building.getHeight();

                if (x >= bx && x < bx + bw && y >= by && y < by + bh) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a position is blocked by terrain, buildings, or ANY units (including moving ones)
     * Used for finding truly free positions for gathering
     */
    private boolean isBlockedIncludingMovingUnits(int x, int y, byte[] terrainData,
                                                    List<Building> buildings, List<Unit> units, int mapWidth) {
        // Check terrain
        if (terrainData != null) {
            int index = y * mapWidth + x;
            if (index >= 0 && index < terrainData.length) {
                byte terrain = terrainData[index];
                // Not walkable: WATER (4), LAVA (3), TREE (11), STONE (5), GOLD (7), FOOD/BERRIES (8)
                if (terrain == 3 || terrain == 4 || terrain == 11 || terrain == 5 || terrain == 7 || terrain == 8) {
                    return true;
                }
            }
        }

        // Check buildings
        if (buildings != null) {
            for (Building building : buildings) {
                int bx = building.getX();
                int by = building.getY();
                int bw = building.getWidth();
                int bh = building.getHeight();

                if (x >= bx && x < bx + bw && y >= by && y < by + bh) {
                    return true;
                }
            }
        }

        // Check ALL units (including moving ones) to ensure position is truly free
        if (units != null) {
            for (Unit unit : units) {
                if (unit.getX() == x && unit.getY() == y) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isBlocked(int x, int y, byte[] terrainData, List<Building> buildings, List<Unit> units, int mapWidth) {
        // Check terrain
        if (terrainData != null) {
            int index = y * mapWidth + x;
            if (index >= 0 && index < terrainData.length) {
                byte terrain = terrainData[index];
                // Terrain types from map-renderer.js:
                // 0=GRASS, 1=DESERT, 2=SNOW, 3=LAVA, 4=WATER, 5=STONE, 7=GOLD, 8=FOOD, 9=HUNT, 10=FOREST, 11=TREE
                // Not walkable: WATER (4), LAVA (3), TREE (11), STONE (5), GOLD (7), FOOD/BERRIES (8)
                if (terrain == 3 || terrain == 4 || terrain == 11 || terrain == 5 || terrain == 7 || terrain == 8) {
                    return true;
                }
            }
        }

        // Check buildings
        if (buildings != null) {
            for (Building building : buildings) {
                int bx = building.getX();
                int by = building.getY();
                int bw = building.getWidth();
                int bh = building.getHeight();

                if (x >= bx && x < bx + bw && y >= by && y < by + bh) {
                    return true;
                }
            }
        }

        // Check other units (but only if they're not moving - to avoid blocking each other)
        if (units != null) {
            for (Unit unit : units) {
                if (unit.getX() == x && unit.getY() == y && !unit.isMoving()) {
                    return true;
                }
            }
        }

        return false;
    }

    private double calculateHeuristic(int x1, int y1, int x2, int y2) {
        // Euclidean distance
        int dx = x2 - x1;
        int dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private String getNodeKey(PathNode node) {
        return node.getX() + "," + node.getY();
    }

    private List<PathNode> reconstructPath(PathNode endNode) {
        List<PathNode> path = new ArrayList<>();
        PathNode current = endNode;

        while (current != null) {
            path.add(0, new PathNode(current.getX(), current.getY()));
            current = current.getParent();
        }

        // Remove the first node (starting position)
        if (!path.isEmpty()) {
            path.remove(0);
        }

        return path;
    }
}
