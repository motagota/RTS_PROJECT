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
    private PathfindingService pathfindingService;

    @Autowired
    private com.rts.repository.GamePlayerRepository gamePlayerRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private static final double GATHER_RANGE = 1.5;  // Units can gather from slots placed at radius 1.0
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
            return;
        }

        ResourceNode primaryNode = nodeOpt.get();

        // Initialize gather slots if not already done
        // Rebuild slot occupancy from units already gathering from this resource
        if (primaryNode.getGatherSlots() == null) {
            List<Unit> unitsGatheringHere = allUnits.stream()
                .filter(u -> primaryNode.getId().equals(u.getTargetResourceNodeId()))
                .collect(java.util.stream.Collectors.toList());
            primaryNode.initializeGatherSlotsFromUnits(8, unitsGatheringHere);
        }

        // Add staggered delay for squad movement (0-300ms = 0-3 ticks at 10 ticks/sec)
        java.util.Random random = new java.util.Random();

        // Command each unit to gather
        for (Unit unit : units) {
            // Only villagers can gather
            if (unit.getType() != Unit.UnitType.VILLAGER) {
                continue;
            }

            // Release any existing slot from previous gathering task
            if (unit.getTargetResourceNodeId() != null && unit.getAssignedSlotIndex() != null) {
                Optional<ResourceNode> oldNodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());
                oldNodeOpt.ifPresent(oldNode -> oldNode.releaseSlot(unit.getId()));
            }

            // Try to reserve an accessible slot on the primary resource node
            ResourceNode targetNode = primaryNode;
            com.rts.model.GatherSlot assignedSlot = null;

            // Try primary node first
            assignedSlot = reserveAccessibleSlotOnNode(targetNode, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);

            // If primary node is full or all slots blocked, try to find an alternative nearby resource of the same type
            if (assignedSlot == null) {
                ResourceNode alternativeNode = findNearbyAlternativeResourceWithFreeSlot(
                    gameId, targetNode.getX(), targetNode.getY(), targetNode.getType(),
                    unit, terrainData, buildings, allUnits, mapWidth, mapHeight);

                if (alternativeNode != null) {
                    targetNode = alternativeNode;
                    assignedSlot = reserveAccessibleSlotOnNode(targetNode, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);
                }
            }

            if (assignedSlot == null) {
                continue;
            }

            // Get the world position of the assigned slot
            int[] slotPosition = assignedSlot.getWorldPosition(targetNode.getX(), targetNode.getY());
            int slotX = slotPosition[0];
            int slotY = slotPosition[1];

            // Set staggered delay (random 0-3 ticks)
            int delay = random.nextInt(4);
            unit.setMovementDelayTicks(delay);

            // Store slot assignment
            unit.setAssignedSlotIndex(assignedSlot.getSlotIndex());
            unit.setTargetResourceNodeId(targetNode.getId());
            unit.setGatherState(Unit.GatherState.MOVING_TO_RESOURCE);

            // Set destination to the slot position
            movementService.setUnitDestination(unit, slotX, slotY,
                terrainData, buildings, allUnits, mapWidth, mapHeight, false);

            // Check if a path was found
            if (unit.getTargetX() == null || unit.getTargetY() == null) {
                targetNode.releaseSlot(unit.getId());
                unit.setAssignedSlotIndex(null);
                resetGatheringState(unit);
            }
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

    /**
     * Release a unit's gather slot (called when unit dies or receives new orders)
     * This should be called by MovementService or GameService when a unit is given a move order
     * or when a unit is removed from the game
     */
    public void releaseGatherSlot(Unit unit) {
        if (unit.getTargetResourceNodeId() != null && unit.getAssignedSlotIndex() != null) {
            Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());
            if (nodeOpt.isPresent()) {
                nodeOpt.get().releaseSlot(unit.getId());
            }
            unit.setAssignedSlotIndex(null);
        }
    }

    private void processUnitGathering(Unit unit, Long gameId, List<Building> buildings,
                                       byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        switch (unit.getGatherState()) {
            case IDLE:
                // Nothing to do
                break;
            case MOVING_TO_RESOURCE:
                handleMovingToResource(unit, gameId, buildings, terrainData, allUnits, mapWidth, mapHeight);
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

    private void handleMovingToResource(Unit unit, Long gameId, List<Building> buildings,
                                         byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        // Handle staggered movement delay
        if (unit.getMovementDelayTicks() > 0) {
            unit.setMovementDelayTicks(unit.getMovementDelayTicks() - 1);
            return; // Wait for delay to expire
        }

        // Get resource node by ID first to check if we should continue
        if (unit.getTargetResourceNodeId() == null) {
            resetGatheringState(unit);
            return;
        }

        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());
        if (nodeOpt.isEmpty()) {
            resetGatheringState(unit);
            return;
        }

        ResourceNode node = nodeOpt.get();
        if (node.isDepleted()) {
            resetGatheringState(unit);
            return;
        }

        // Rebuild slot state from all units targeting this resource
        // (slots are transient and don't persist between method calls)
        if (node.getGatherSlots() == null) {
            final Long nodeId = node.getId();
            List<Unit> unitsGatheringHere = allUnits.stream()
                .filter(u -> nodeId.equals(u.getTargetResourceNodeId()))
                .collect(java.util.stream.Collectors.toList());
            node.initializeGatherSlotsFromUnits(8, unitsGatheringHere);
        }

        // Check if pathfinding failed (MovementService cleared target because no path exists)
        if (!unit.isMoving() && unit.getTargetX() == null && unit.getTargetY() == null) {
            // Release current slot
            node.releaseSlot(unit.getId());

            // Try to find an alternative resource with free accessible slot
            ResourceNode alternativeNode = findNearbyAlternativeResourceWithFreeSlot(
                gameId, node.getX(), node.getY(), node.getType(),
                unit, terrainData, buildings, allUnits, mapWidth, mapHeight);

            if (alternativeNode != null && alternativeNode.getId() != node.getId()) {
                // Reserve accessible slot on new node
                com.rts.model.GatherSlot newSlot = reserveAccessibleSlotOnNode(
                    alternativeNode, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);
                if (newSlot != null) {
                    int[] slotPos = newSlot.getWorldPosition(alternativeNode.getX(), alternativeNode.getY());
                    movementService.setUnitDestination(unit, slotPos[0], slotPos[1],
                        terrainData, buildings, allUnits, mapWidth, mapHeight, false);

                    if (unit.getTargetX() != null && unit.getTargetY() != null) {
                        unit.setTargetResourceNodeId(alternativeNode.getId());
                        unit.setAssignedSlotIndex(newSlot.getSlotIndex());
                    } else {
                        alternativeNode.releaseSlot(unit.getId());
                        resetGatheringState(unit);
                    }
                }
            } else {
                resetGatheringState(unit);
            }
            return;
        }

        // Check if unit has reached resource
        if (unit.isMoving()) {
            return;  // Still moving
        }

        // Check if in range
        double distance = Math.sqrt(
            Math.pow(unit.getX() - node.getX(), 2) +
            Math.pow(unit.getY() - node.getY(), 2)
        );

        if (distance <= GATHER_RANGE) {
            // Start gathering
            unit.setGatherState(Unit.GatherState.GATHERING);
        } else {
            // Not in range - slot was blocked/inaccessible
            // Release current slot
            node.releaseSlot(unit.getId());

            // Try to find an alternative nearby resource with free accessible slot
            ResourceNode alternativeNode = findNearbyAlternativeResourceWithFreeSlot(
                gameId, node.getX(), node.getY(), node.getType(),
                unit, terrainData, buildings, allUnits, mapWidth, mapHeight);

            if (alternativeNode != null) {
                com.rts.model.GatherSlot newSlot = reserveAccessibleSlotOnNode(
                    alternativeNode, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);
                if (newSlot != null) {
                    int[] slotPos = newSlot.getWorldPosition(alternativeNode.getX(), alternativeNode.getY());
                    movementService.setUnitDestination(unit, slotPos[0], slotPos[1],
                        terrainData, buildings, allUnits, mapWidth, mapHeight, false);

                    if (unit.getTargetX() != null && unit.getTargetY() != null) {
                        unit.setTargetResourceNodeId(alternativeNode.getId());
                        unit.setAssignedSlotIndex(newSlot.getSlotIndex());
                    } else {
                        alternativeNode.releaseSlot(unit.getId());
                        resetGatheringState(unit);
                    }
                }
            } else {
                resetGatheringState(unit);
            }
        }
    }

    private void handleGathering(Unit unit, Long gameId, List<Building> buildings,
                                  byte[] terrainData, List<Unit> allUnits, int mapWidth, int mapHeight) {
        if (unit.getTargetResourceNodeId() == null) {
            resetGatheringState(unit);
            return;
        }

        // Get resource node by ID (not by coordinates, since unit may be at adjacent tile)
        Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());

        if (nodeOpt.isEmpty()) {
            resetGatheringState(unit);
            return;
        }

        ResourceNode node = nodeOpt.get();

        if (node.isDepleted()) {
            resetGatheringState(unit);
            return;
        }

        // Rebuild slot state from all units targeting this resource
        // (slots are transient and don't persist between method calls)
        if (node.getGatherSlots() == null) {
            final Long nodeId = node.getId();
            List<Unit> unitsGatheringHere = allUnits.stream()
                .filter(u -> nodeId.equals(u.getTargetResourceNodeId()))
                .collect(java.util.stream.Collectors.toList());
            node.initializeGatherSlotsFromUnits(8, unitsGatheringHere);
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
            int amountToGather = (int) Math.ceil(toGather);

            // Actually remove from node
            int actualGathered = resourceNodeService.gatherFromNode(node.getId(), amountToGather);

            // Update unit's carrying amount based on what was actually gathered
            unit.setCarryingAmount(unit.getCarryingAmount() + actualGathered);
            unit.setCarryingResourceType(node.getType().name());

            // Refresh node to check if depleted after gathering
            nodeOpt = resourceNodeService.getResourceNodeById(node.getId());
            if (nodeOpt.isEmpty()) {
                resetGatheringState(unit);
                return;
            }
            node = nodeOpt.get();

            // If full or resource depleted, find drop-off
            if (unit.getCarryingAmount() >= unit.getCarryCapacity() || node.isDepleted()) {
                // Release gather slot so other units can use it while we're away
                if (unit.getAssignedSlotIndex() != null) {
                    node.releaseSlot(unit.getId());
                }

                Building dropOff = findNearestDropOff(unit, buildings);
                if (dropOff != null) {
                    // Calculate best drop-off point based on unit's approach direction
                    int[] dropOffPoint = calculateDropOffPoint(unit, dropOff);
                    unit.setGatherState(Unit.GatherState.MOVING_TO_DROPOFF);
                    // Use findFreePosition=true for drop-off since buildings are obstacles
                    movementService.setUnitDestination(unit, dropOffPoint[0], dropOffPoint[1],
                        terrainData, buildings, allUnits, mapWidth, mapHeight, true);
                } else {
                    // No drop-off found, go idle
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
            // Drop-off gone or invalid, try to find another
            dropOff = findNearestDropOff(unit, buildings);
            if (dropOff != null) {
                // Calculate best drop-off point based on unit's approach direction
                int[] dropOffPoint = calculateDropOffPoint(unit, dropOff);
                // Use findFreePosition=true for buildings
                movementService.setUnitDestination(unit, dropOffPoint[0], dropOffPoint[1],
                    terrainData, buildings, allUnits, mapWidth, mapHeight, true);
            } else {
                // No valid drop-off, go idle
                resetGatheringState(unit);
            }
            return;
        }

        // Start depositing
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
                        break;
                    case "TREE":
                        resources.setWood(resources.getWood() + amount);
                        break;
                    case "STONE":
                        resources.setStone(resources.getStone() + amount);
                        break;
                    case "GOLD":
                        resources.setGold(resources.getGold() + amount);
                        break;
                }

                // Save the updated player
                gamePlayerRepository.save(player);

                // Broadcast resource update to client
                messagingTemplate.convertAndSend("/topic/game/" + gameId,
                    java.util.Map.of("type", "RESOURCES_UPDATE",
                        "playerName", player.getPlayerName(),
                        "resources", player.getResources()));
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

                // Try to reserve an accessible slot (the old slot was likely taken by now)
                com.rts.model.GatherSlot newSlot = reserveAccessibleSlotOnNode(
                    node, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);

                if (newSlot == null) {
                    // Node is full or all slots blocked, try to find an alternative
                    ResourceNode alternativeNode = findNearbyAlternativeResourceWithFreeSlot(
                        gameId, node.getX(), node.getY(), node.getType(),
                        unit, terrainData, buildings, allUnits, mapWidth, mapHeight);

                    if (alternativeNode != null) {
                        node = alternativeNode;
                        newSlot = reserveAccessibleSlotOnNode(
                            node, unit.getId(), gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);
                    }
                }

                if (newSlot != null) {
                    unit.setTargetResourceNodeId(node.getId());
                    unit.setAssignedSlotIndex(newSlot.getSlotIndex());
                    unit.setGatherState(Unit.GatherState.MOVING_TO_RESOURCE);

                    int[] slotPos = newSlot.getWorldPosition(node.getX(), node.getY());
                    movementService.setUnitDestination(unit, slotPos[0], slotPos[1],
                        terrainData, buildings, allUnits, mapWidth, mapHeight, false);
                    return;
                }
            }
        }

        // Resource gone or no slots available, go idle
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

    /**
     * Find a nearby alternative resource node of the same type that the unit can reach
     * Prioritizes resources with free adjacent gathering positions
     */
    private ResourceNode findNearbyAlternativeResource(Long gameId, int targetX, int targetY,
                                                        ResourceNode.ResourceType resourceType,
                                                        Unit unit, byte[] terrainData, List<Building> buildings,
                                                        List<Unit> allUnits, int mapWidth, int mapHeight) {
        // Search in expanding radius for alternative resources of the same type
        int maxRadius = 15; // Search up to 15 tiles away

        // Save original unit state to restore if no alternative is found
        Integer originalTargetX = unit.getTargetX();
        Integer originalTargetY = unit.getTargetY();
        java.util.List<com.rts.model.PathNode> originalPath = unit.getPath();

        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    // Only check positions on the edge of the current radius
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;

                    int checkX = targetX + dx;
                    int checkY = targetY + dy;

                    // Skip if out of bounds
                    if (checkX < 0 || checkX >= mapWidth || checkY < 0 || checkY >= mapHeight) {
                        continue;
                    }

                    // Check if there's a resource node at this position
                    Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeAt(gameId, checkX, checkY);
                    if (nodeOpt.isPresent() && !nodeOpt.get().isDepleted() &&
                        nodeOpt.get().getType() == resourceType) {

                        ResourceNode candidateNode = nodeOpt.get();

                        // Quick check: does this resource have at least one free adjacent spot?
                        // This filters out completely surrounded resources before expensive pathfinding
                        if (!hasFreeAdjacentSpot(candidateNode, terrainData, buildings, allUnits, mapWidth, mapHeight)) {
                            continue;
                        }

                        // Try to path to this resource node (this modifies unit state as a side effect)
                        movementService.setUnitDestination(unit, candidateNode.getX(), candidateNode.getY(),
                            terrainData, buildings, allUnits, mapWidth, mapHeight, true);

                        // If a path was found, this is a viable alternative
                        if (unit.getTargetX() != null && unit.getTargetY() != null) {
                            // Path was successfully set by setUnitDestination, return the candidate
                            return candidateNode;
                        }
                    }
                }
            }
        }

        // No alternative found - restore original unit state to prevent corruption
        unit.setTargetX(originalTargetX);
        unit.setTargetY(originalTargetY);
        unit.setPath(originalPath);
        return null;
    }

    /**
     * Check if a resource node has at least one free adjacent gathering position
     */
    private boolean hasFreeAdjacentSpot(ResourceNode node, byte[] terrainData, List<Building> buildings,
                                         List<Unit> allUnits, int mapWidth, int mapHeight) {
        // Check all 8 adjacent positions
        int[][] adjacentOffsets = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0},          {1,  0},
            {-1,  1}, {0,  1}, {1,  1}
        };

        for (int[] offset : adjacentOffsets) {
            int adjX = node.getX() + offset[0];
            int adjY = node.getY() + offset[1];

            // Check bounds
            if (adjX < 0 || adjX >= mapWidth || adjY < 0 || adjY >= mapHeight) {
                continue;
            }

            // Check if this position is walkable
            if (pathfindingService.isBlocked(adjX, adjY, terrainData, buildings, allUnits, mapWidth)) {
                continue;
            }

            // Check if occupied by a unit
            boolean occupied = false;
            for (Unit otherUnit : allUnits) {
                if (otherUnit.getX() == adjX && otherUnit.getY() == adjY) {
                    occupied = true;
                    break;
                }
            }

            if (!occupied) {
                return true; // Found at least one free spot
            }
        }

        return false; // No free adjacent spots
    }

    /**
     * Calculate the best drop-off point around a building based on the unit's position
     * This spreads units around the building instead of having them all target the same corner
     */
    private int[] calculateDropOffPoint(Unit unit, Building building) {
        int unitX = unit.getX();
        int unitY = unit.getY();

        // Calculate building center
        int buildingCenterX = building.getX() + building.getWidth() / 2;
        int buildingCenterY = building.getY() + building.getHeight() / 2;

        // Calculate direction from building center to unit
        int dx = unitX - buildingCenterX;
        int dy = unitY - buildingCenterY;

        // Determine which side of the building the unit is approaching from
        // and target a point on that side
        int targetX, targetY;

        if (Math.abs(dx) > Math.abs(dy)) {
            // Unit is more to the left or right of the building
            if (dx > 0) {
                // Unit is to the right, target right side of building
                targetX = building.getX() + building.getWidth() - 1;
                targetY = building.getY() + Math.max(0, Math.min(building.getHeight() - 1, unitY - building.getY()));
            } else {
                // Unit is to the left, target left side of building
                targetX = building.getX();
                targetY = building.getY() + Math.max(0, Math.min(building.getHeight() - 1, unitY - building.getY()));
            }
        } else {
            // Unit is more above or below the building
            if (dy > 0) {
                // Unit is below, target bottom side of building
                targetX = building.getX() + Math.max(0, Math.min(building.getWidth() - 1, unitX - building.getX()));
                targetY = building.getY() + building.getHeight() - 1;
            } else {
                // Unit is above, target top side of building
                targetX = building.getX() + Math.max(0, Math.min(building.getWidth() - 1, unitX - building.getX()));
                targetY = building.getY();
            }
        }

        return new int[]{targetX, targetY};
    }

    /**
     * Helper method to reserve an accessible slot on a specific resource node
     * Creates a validator that excludes the target resource from collision checks
     */
    private com.rts.model.GatherSlot reserveAccessibleSlotOnNode(ResourceNode node, int unitId, Long gameId,
                                                                  byte[] terrainData, List<Building> buildings,
                                                                  List<Unit> allUnits, int mapWidth, int mapHeight) {
        final Long nodeId = node.getId();

        // Create accessibility validator that excludes the target resource itself
        java.util.function.BiPredicate<Integer, Integer> isAccessible = (x, y) -> {
            // Check bounds
            if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) {
                return false;
            }

            // Check if blocked by terrain or buildings
            if (pathfindingService.isBlocked(x, y, terrainData, buildings, allUnits, mapWidth)) {
                return false;
            }

            // Check if position overlaps with any OTHER resource node (excluding the target resource)
            boolean blockedByResource = resourceNodeService.isPositionBlockedByResource(gameId, x, y, nodeId);
            return !blockedByResource;
        };

        return node.reserveFreeAccessibleSlot(unitId, isAccessible);
    }

    /**
     * Find a nearby alternative resource node with a free accessible gathering slot
     */
    private ResourceNode findNearbyAlternativeResourceWithFreeSlot(Long gameId, int targetX, int targetY,
                                                                    ResourceNode.ResourceType resourceType,
                                                                    Unit unit, byte[] terrainData, List<Building> buildings,
                                                                    List<Unit> allUnits, int mapWidth, int mapHeight) {
        int maxRadius = 15;

        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue;

                    int checkX = targetX + dx;
                    int checkY = targetY + dy;

                    if (checkX < 0 || checkX >= mapWidth || checkY < 0 || checkY >= mapHeight) {
                        continue;
                    }

                    Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeAt(gameId, checkX, checkY);
                    if (nodeOpt.isPresent() && !nodeOpt.get().isDepleted() &&
                        nodeOpt.get().getType() == resourceType) {

                        ResourceNode candidateNode = nodeOpt.get();

                        // Initialize slots if needed, rebuilding occupancy from units
                        if (candidateNode.getGatherSlots() == null) {
                            List<Unit> unitsGatheringHere = allUnits.stream()
                                .filter(u -> candidateNode.getId().equals(u.getTargetResourceNodeId()))
                                .collect(java.util.stream.Collectors.toList());
                            candidateNode.initializeGatherSlotsFromUnits(8, unitsGatheringHere);
                        }

                        // Check if this node has at least one free accessible slot using the helper
                        com.rts.model.GatherSlot testSlot = reserveAccessibleSlotOnNode(
                            candidateNode, -1, gameId, terrainData, buildings, allUnits, mapWidth, mapHeight);

                        if (testSlot != null) {
                            // Release the test reservation
                            candidateNode.releaseSlot(-1);
                            return candidateNode;
                        }
                    }
                }
            }
        }

        return null;
    }

    private void resetGatheringState(Unit unit) {
        // Release the gather slot if assigned
        if (unit.getTargetResourceNodeId() != null && unit.getAssignedSlotIndex() != null) {
            Optional<ResourceNode> nodeOpt = resourceNodeService.getResourceNodeById(unit.getTargetResourceNodeId());
            if (nodeOpt.isPresent()) {
                nodeOpt.get().releaseSlot(unit.getId());
            }
        }

        unit.setGatherState(Unit.GatherState.IDLE);
        unit.setTargetResourceNodeId(null);
        unit.setAssignedSlotIndex(null);
        unit.setCarryingAmount(0);
        unit.setCarryingResourceType(null);
        unit.setTargetX(null);
        unit.setTargetY(null);
        unit.setPath(null);
        unit.setMovementDelayTicks(0);
    }
}
