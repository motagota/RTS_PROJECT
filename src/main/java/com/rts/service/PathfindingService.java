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
     * Find a suitable position near the target that is unoccupied
     */
    public PathNode findNearestFreePosition(int targetX, int targetY,
                                             int mapWidth, int mapHeight,
                                             byte[] terrainData,
                                             List<Building> buildings,
                                             List<Unit> units) {

        // Check if target is already free
        if (!isBlocked(targetX, targetY, terrainData, buildings, units, mapWidth)) {
            return new PathNode(targetX, targetY);
        }

        // Search in expanding circles around the target
        for (int radius = 1; radius <= 10; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    // Only check positions on the edge of the circle
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;

                    int checkX = targetX + dx;
                    int checkY = targetY + dy;

                    if (checkX < 0 || checkX >= mapWidth || checkY < 0 || checkY >= mapHeight) {
                        continue;
                    }

                    if (!isBlocked(checkX, checkY, terrainData, buildings, units, mapWidth)) {
                        return new PathNode(checkX, checkY);
                    }
                }
            }
        }

        // No free position found nearby
        return null;
    }

    private boolean isBlocked(int x, int y, byte[] terrainData, List<Building> buildings, List<Unit> units, int mapWidth) {
        // Check terrain
        if (terrainData != null) {
            int index = y * mapWidth + x;
            if (index >= 0 && index < terrainData.length) {
                byte terrain = terrainData[index];
                // Terrain types from map-renderer.js:
                // 0=GRASS, 1=DESERT, 2=SNOW, 3=LAVA, 4=WATER, 5=STONE, 7=GOLD, 8=FOOD, 9=HUNT, 10=FOREST, 11=TREE
                // Not walkable: WATER (4), LAVA (3), TREE (11)
                if (terrain == 3 || terrain == 4 || terrain == 11) {
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
