package com.rts.service;

import com.rts.model.Building;
import com.rts.model.ResourceNode;
import com.rts.model.Unit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing resource gathering mechanics
 */
@Service
public class ResourceGatheringService {

    @Autowired
    private ResourceNodeService resourceNodeService;

    @Autowired
    private MovementService movementService;

    @Autowired
    private com.rts.repository.GamePlayerRepository gamePlayerRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private static final double GATHER_RANGE = 1.5;  // Units can gather from adjacent tiles
    private static final double GATHER_RATE = 0.31;  // Resources gathered per second (AoE2 berry rate)
    private static final int TICKS_PER_GATHER = 10;  // Gather once per second (at 10 ticks/sec)
    private static final double DROPOFF_RANGE = 2.0;  // Units can drop off resources when adjacent to building

    /**
     * Command units to gather from a resource node
     */
    public void commandGatherResource(List<Unit> units, Long gameId, int resourceX, int resourceY,
                                       byte[] terrainData, List<Building> buildings, List<Unit> allUnits,
                                       int mapWidth, int mapHeight) {
        // Find resource node at coordinates
        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeAt(gameId, resourceX, resourceY);

        if (nodeOpt.isEmpty() || nodeOpt.get().isDepleted()) {
            System.out.println("No resource node found at (" + resourceX + "," + resourceY + ")");
            return;
        }

        ResourceNode node = nodeOpt.get();

        // Command each unit to gather
        for (Unit unit : units) {
            // Only villagers can gather
            if (unit.getType() != Unit.UnitType.VILLAGER) {
                continue;
            }

            // Set gathering target
            unit.setTargetResourceNodeId(node.getId());
            unit.setGatherState(Unit.GatherState.MOVING_TO_RESOURCE);

            System.out.println("Unit " + unit.getId() + " at (" + unit.getX() + "," + unit.getY() +
                ") commanded to gather from resource node " + node.getId() + " at (" + resourceX + "," + resourceY + ")");

            // Move unit to resource node - use exact position, don't find free position
            // Resource nodes are not obstacles, so we want to go directly to them
            movementService.setUnitDestination(unit, resourceX, resourceY, terrainData, buildings, allUnits, mapWidth, mapHeight, false);

            System.out.println("After setUnitDestination: targetX=" + unit.getTargetX() + ", targetY=" + unit.getTargetY());
        }
    }

    /**
     * Process gathering for all units
     * Called each game tick
     */
    public void processGathering(List<Unit> units, Long gameId, List<Building> buildings,
                                   byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        for (Unit unit : units) {
            if (unit.getGatherState() == Unit.GatherState.IDLE) {
                continue;
            }

            processUnitGathering(unit, gameId, buildings, terrainData, allUnits, mapWidth, mapHeight);
        }
    }

    private void processUnitGathering(Unit unit, Long gameId, List<Building> buildings,
                                       byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        switch (unit.getGatherState()) {
            case IDLE:
                // Nothing to do
                break;
            case MOVING_TO_RESOURCE:
                handleMovingToResource(unit, gameId);
                break;
            case GATHERING:
                handleGathering(unit, gameId, buildings, terrainData, allUnits, mapWidth, mapHeight);
                break;
            case MOVING_TO_DROPOFF:
                handleMovingToDropoff(unit, gameId, buildings, terrainData, allUnits, mapWidth, mapHeight);
                break;
            case DEPOSITING:
                handleDepositing(unit, gameId, buildings, terrainData, allUnits, mapWidth, mapHeight);
                break;
        }
    }

    private void handleMovingToResource(Unit unit, Long gameId) {
        // Check if unit has reached resource
        if (unit.isMoving()) {
            return;  // Still moving
        }

        System.out.println("=== Unit " + unit.getId() + " finished moving to resource ===");
        System.out.println("  Position: (" + unit.getX() + "," + unit.getY() + ")");
        System.out.println("  Target: (" + unit.getTargetX() + "," + unit.getTargetY() + ")");
        System.out.println("  GatherState: " + unit.getGatherState());
        System.out.println("  TargetResourceNodeId: " + unit.getTargetResourceNodeId());

        // Get resource node - try both target position and current position
        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeAt(gameId,
            unit.getTargetX(), unit.getTargetY());

        if (nodeOpt.isEmpty()) {
            System.out.println("  No resource at target (" + unit.getTargetX() + "," + unit.getTargetY() + "), trying current position");
            nodeOpt = resourceNodeService.getResourceNodeAt(gameId, unit.getX(), unit.getY());
        }

        if (nodeOpt.isEmpty()) {
            System.out.println("  ERROR: No resource node found at target or current position");
            resetGatheringState(unit);
            return;
        }

        ResourceNode node = nodeOpt.get();
        System.out.println("  Found resource node " + node.getId() + " at (" + node.getX() + "," + node.getY() + ")");

        if (node.isDepleted()) {
            System.out.println("  Resource node is depleted");
            resetGatheringState(unit);
            return;
        }

        // Check if in range
        double distance = Math.sqrt(
            Math.pow(unit.getX() - node.getX(), 2) +
            Math.pow(unit.getY() - node.getY(), 2)
        );

        System.out.println("  Distance to resource: " + distance + " (range: " + GATHER_RANGE + ")");

        if (distance <= GATHER_RANGE) {
            // Start gathering
            System.out.println("  ✓ Starting to gather from resource node " + node.getId());
            unit.setGatherState(Unit.GatherState.GATHERING);
        } else {
            // Not in range, idle
            System.out.println("  ✗ Not in range, going idle");
            resetGatheringState(unit);
        }
    }

    private void handleGathering(Unit unit, Long gameId, List<Building> buildings,
                                  byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        if (unit.getTargetResourceNodeId() == null) {
            System.out.println("Unit " + unit.getId() + " in GATHERING state but no targetResourceNodeId, resetting");
            resetGatheringState(unit);
            return;
        }

        // Get resource node
        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeAt(gameId,
            unit.getTargetX(), unit.getTargetY());

        if (nodeOpt.isEmpty()) {
            System.out.println("Unit " + unit.getId() + " resource node not found at (" + unit.getTargetX() + "," + unit.getTargetY() + "), resetting");
            resetGatheringState(unit);
            return;
        }

        ResourceNode node = nodeOpt.get();

        if (node.isDepleted()) {
            System.out.println("Unit " + unit.getId() + " resource node " + node.getId() + " depleted, resetting");
            resetGatheringState(unit);
            return;
        }

        // Increment tick counter
        unit.setGatherTickCounter(unit.getGatherTickCounter() + 1);

        // Only gather every TICKS_PER_GATHER ticks (once per second at 10 ticks/sec)
        if (unit.getGatherTickCounter() >= TICKS_PER_GATHER) {
            // Reset counter
            unit.setGatherTickCounter(0);

            // Gather resources
            double spaceAvailable = unit.getCarryCapacity() - unit.getCarryingAmount();
            double toGather = Math.min(GATHER_RATE, spaceAvailable);

            // Gather the fractional amount
            double newCarrying = unit.getCarryingAmount() + toGather;
            unit.setCarryingAmount((int) Math.ceil(newCarrying));  // Round up for display
            unit.setCarryingResourceType(node.getType().name());

            // Actually remove from node (round down to ensure we don't over-gather)
            int actualGathered = resourceNodeService.gatherFromNode(node.getId(), (int) Math.ceil(toGather));

            // Refresh node to check if depleted after gathering
            nodeOpt = resourceNodeService.getResourceNodeById(node.getId());
            if (nodeOpt.isEmpty()) {
                System.out.println("Unit " + unit.getId() + " - resource node disappeared after gathering");
                resetGatheringState(unit);
                return;
            }
            node = nodeOpt.get();

            System.out.println("Unit " + unit.getId() + " gathered " + actualGathered + " " + node.getType().name() +
                ", now carrying " + unit.getCarryingAmount() + "/" + unit.getCarryCapacity() +
                ", node has " + node.getAmount() + "/" + node.getMaxAmount() + " remaining");

            // If full or resource depleted, find drop-off
            if (unit.getCarryingAmount() >= unit.getCarryCapacity() || node.isDepleted()) {
                Building dropOff = findNearestDropOff(unit, buildings);
                if (dropOff != null) {
                    System.out.println("Unit " + unit.getId() + " full/depleted, moving to dropoff at (" + dropOff.getX() + "," + dropOff.getY() + ")");
                    unit.setGatherState(Unit.GatherState.MOVING_TO_DROPOFF);
                    // Use findFreePosition=true for drop-off since buildings are obstacles
                    movementService.setUnitDestination(unit, dropOff.getX(), dropOff.getY(),
                        terrainData, buildings, allUnits, mapWidth, mapHeight, true);
                } else {
                    // No drop-off found, go idle
                    System.out.println("Unit " + unit.getId() + " no dropoff found, going idle");
                    resetGatheringState(unit);
                }
            }
        }
    }

    private void handleMovingToDropoff(Unit unit, Long gameId, List<Building> buildings,
                                        byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        // Check if unit has reached drop-off
        if (unit.isMoving()) {
            return;  // Still moving
        }

        // Find drop-off building within range of unit's current position
        Building dropOff = findDropOffInRange(unit, buildings);

        if (dropOff == null || !isValidDropOff(dropOff, unit.getCarryingResourceType())) {
            System.out.println("Unit " + unit.getId() + " at (" + unit.getX() + "," + unit.getY() +
                ") - no valid drop-off in range, searching for another");
            // Drop-off gone or invalid, try to find another
            dropOff = findNearestDropOff(unit, buildings);
            if (dropOff != null) {
                // Use findFreePosition=true for buildings
                movementService.setUnitDestination(unit, dropOff.getX(), dropOff.getY(),
                    terrainData, buildings, allUnits, mapWidth, mapHeight, true);
            } else {
                // No valid drop-off, go idle
                System.out.println("Unit " + unit.getId() + " no dropoff found anywhere, going idle");
                resetGatheringState(unit);
            }
            return;
        }

        // Start depositing
        System.out.println("Unit " + unit.getId() + " at (" + unit.getX() + "," + unit.getY() +
            ") reached drop-off building at (" + dropOff.getX() + "," + dropOff.getY() + "), depositing");
        unit.setGatherState(Unit.GatherState.DEPOSITING);
    }

    private void handleDepositing(Unit unit, Long gameId, List<Building> buildings,
                                    byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        // Add resources to player's stockpile
        int amount = unit.getCarryingAmount();
        String resourceType = unit.getCarryingResourceType();

        if (amount > 0 && resourceType != null) {
            // Get the player
            Optional<com.rts.model.GamePlayer> playerOpt = gamePlayerRepository.findByGameIdAndPlayerSlot(
                gameId, unit.getPlayerNumber()
            );

            if (playerOpt.isPresent()) {
                com.rts.model.GamePlayer player = playerOpt.get();
                com.rts.model.PlayerResources resources = player.getResources();

                // Add resources based on type
                switch (resourceType) {
                    case "BERRIES":
                        resources.setFood(resources.getFood() + amount);
                        System.out.println("Unit " + unit.getId() + " deposited " + amount + " food (berries). New total: " + resources.getFood());
                        break;
                    case "TREE":
                        resources.setWood(resources.getWood() + amount);
                        System.out.println("Unit " + unit.getId() + " deposited " + amount + " wood. New total: " + resources.getWood());
                        break;
                    case "STONE":
                        resources.setStone(resources.getStone() + amount);
                        System.out.println("Unit " + unit.getId() + " deposited " + amount + " stone. New total: " + resources.getStone());
                        break;
                    case "GOLD":
                        resources.setGold(resources.getGold() + amount);
                        System.out.println("Unit " + unit.getId() + " deposited " + amount + " gold. New total: " + resources.getGold());
                        break;
                    default:
                        System.out.println("Unknown resource type: " + resourceType);
                }

                // Save the updated player
                gamePlayerRepository.save(player);

                // Broadcast resource update to client
                messagingTemplate.convertAndSend("/topic/game/" + gameId,
                    java.util.Map.of("type", "RESOURCES_UPDATE",
                        "playerName", player.getPlayerName(),
                        "resources", player.getResources()));
            } else {
                System.out.println("WARNING: Could not find player with slot " + unit.getPlayerNumber() + " in game " + gameId);
            }
        }

        unit.setCarryingAmount(0);
        unit.setCarryingResourceType(null);

        // Return to gathering if resource still exists
        if (unit.getTargetResourceNodeId() != null) {
            // Look up the resource node by ID (not by target coordinates, which now point to drop-off)
            Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());

            if (nodeOpt.isPresent() && !nodeOpt.get().isDepleted()) {
                ResourceNode node = nodeOpt.get();
                System.out.println("Unit " + unit.getId() + " returning to gather from resource node " +
                    node.getId() + " at (" + node.getX() + "," + node.getY() + ")");
                unit.setGatherState(Unit.GatherState.MOVING_TO_RESOURCE);
                // Use exact position for resources (not obstacles)
                movementService.setUnitDestination(unit, node.getX(), node.getY(),
                    terrainData, buildings, allUnits, mapWidth, mapHeight, false);
                return;
            } else {
                System.out.println("Unit " + unit.getId() + " - resource node " +
                    unit.getTargetResourceNodeId() + " no longer exists or is depleted");
            }
        }

        // Resource gone, go idle
        resetGatheringState(unit);
    }

    /**
     * Find a drop-off building within drop-off range of the unit
     */
    private Building findDropOffInRange(Unit unit, List<Building> buildings) {
        for (Building building : buildings) {
            // Only consider player's own buildings
            if (building.getPlayerNumber() != unit.getPlayerNumber()) {
                continue;
            }

            // Check if building can accept this resource type
            if (!isValidDropOff(building, unit.getCarryingResourceType())) {
                continue;
            }

            // Calculate distance to nearest point on building
            double distance = distanceToBuilding(unit.getX(), unit.getY(), building);

            if (distance <= DROPOFF_RANGE) {
                return building;
            }
        }

        return null;
    }

    private Building findNearestDropOff(Unit unit, List<Building> buildings) {
        Building nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Building building : buildings) {
            // Only consider player's own buildings
            if (building.getPlayerNumber() != unit.getPlayerNumber()) {
                continue;
            }

            // Check if building can accept this resource type
            if (!isValidDropOff(building, unit.getCarryingResourceType())) {
                continue;
            }

            double distance = distanceToBuilding(unit.getX(), unit.getY(), building);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = building;
            }
        }

        return nearest;
    }

    /**
     * Calculate distance from a point to the nearest edge of a building
     */
    private double distanceToBuilding(int unitX, int unitY, Building building) {
        // Find the closest point on the building to the unit
        int closestX = Math.max(building.getX(), Math.min(unitX, building.getX() + building.getWidth() - 1));
        int closestY = Math.max(building.getY(), Math.min(unitY, building.getY() + building.getHeight() - 1));

        return Math.sqrt(
            Math.pow(unitX - closestX, 2) +
            Math.pow(unitY - closestY, 2)
        );
    }

    private boolean isValidDropOff(Building building, String resourceType) {
        // Town center accepts all resources
        if (building.getType() == Building.BuildingType.TOWN_CENTER) {
            return true;
        }

        // TODO: Add specialized drop-off buildings (lumber camp, mining camp, mill)

        return false;
    }

    private Building findBuildingAt(int x, int y, List<Building> buildings) {
        for (Building building : buildings) {
            if (building.containsPoint(x, y)) {
                return building;
            }
        }
        return null;
    }

    private void resetGatheringState(Unit unit) {
        unit.setGatherState(Unit.GatherState.IDLE);
        unit.setTargetResourceNodeId(null);
        unit.setCarryingAmount(0);
        unit.setCarryingResourceType(null);
        unit.setTargetX(null);
        unit.setTargetY(null);
        unit.setPath(null);
    }
}
