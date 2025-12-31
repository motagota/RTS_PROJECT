package com.rts.service;

import com.rts.dto.GatherSlotDTO;
import com.rts.dto.GatherSlotDebugDTO;
import com.rts.dto.ResourceNodeDTO;
import com.rts.dto.UnitDebugDTO;
import com.rts.model.Game;
import com.rts.model.GeneratedMap;
import com.rts.model.MapCell;
import com.rts.model.ResourceNode;
import com.rts.model.Unit;
import com.rts.repository.ResourceNodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing resource nodes in the game
 */
@Service
public class ResourceNodeService {

    @Autowired
    private ResourceNodeRepository resourceNodeRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * Initialize resource nodes from the generated map
     * Scans the map for objects and creates ResourceNode entities
     * @param game The game instance
     * @param terrainJson The already-decompressed terrain JSON data
     */
    @Transactional
    public void initializeResourceNodesFromMap(Game game, String terrainJson) {
        try {
            // Clear any existing resource nodes
            resourceNodeRepository.deleteByGameId(game.getId());

            ObjectMapper objectMapper = new ObjectMapper();

            // Parse as JSON object with "cells" array
            com.fasterxml.jackson.databind.JsonNode mapNode = objectMapper.readTree(terrainJson);
            com.fasterxml.jackson.databind.JsonNode cellsNode = mapNode.get("cells");

            if (cellsNode == null || !cellsNode.isArray()) {
                return;
            }

            // Parse cells array
            MapCell[] cells = objectMapper.treeToValue(cellsNode, MapCell[].class);

            // Create resource nodes for each cell with an object
            for (MapCell cell : cells) {
                if (cell.hasObject()) {
                    createResourceNodeFromCell(game, cell);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize resource nodes: " + e.getMessage(), e);
        }
    }

    /**
     * Create a resource node from a map cell
     */
    private void createResourceNodeFromCell(Game game, MapCell cell) {
        try {
            ResourceNode.ResourceType type = ResourceNode.fromObjectType(cell.getObject());
            ResourceNode node = new ResourceNode(game, cell.getX(), cell.getY(), type);
            resourceNodeRepository.save(node);
        } catch (IllegalArgumentException e) {
            // Unknown resource type, skip it
        }
    }

    /**
     * Get all resource nodes for a game
     */
    public List<ResourceNode> getResourceNodes(Long gameId) {
        return resourceNodeRepository.findByGameId(gameId);
    }

    /**
     * Get resource node at specific coordinates
     */
    public Optional<ResourceNode> getResourceNodeAt(Long gameId, int x, int y) {
        return resourceNodeRepository.findByGameIdAndXAndY(gameId, x, y);
    }

    /**
     * Get resource node by ID
     */
    public Optional<ResourceNode> getResourceNodeById(Long nodeId) {
        return resourceNodeRepository.findById(nodeId);
    }

    /**
     * Gather resources from a node
     * @param nodeId Resource node ID
     * @param gatherAmount Amount to gather
     * @return Amount actually gathered
     */
    @Transactional
    public int gatherFromNode(Long nodeId, int gatherAmount) {
        Optional<ResourceNode> nodeOpt = resourceNodeRepository.findById(nodeId);
        if (nodeOpt.isEmpty()) {
            return 0;
        }

        ResourceNode node = nodeOpt.get();
        int gathered = node.gather(gatherAmount);
        resourceNodeRepository.save(node);

        // If depleted, remove from map
        if (node.isDepleted()) {
            removeNodeFromMap(node);
        }

        return gathered;
    }

    /**
     * Remove a depleted resource node from the map
     * - Deletes from database
     * - Notifies clients to remove visual representation
     */
    private void removeNodeFromMap(ResourceNode node) {
        Long gameId = node.getGame().getId();
        Long nodeId = node.getId();
        int x = node.getX();
        int y = node.getY();

        // Delete from database
        resourceNodeRepository.delete(node);

        // Notify clients via WebSocket to remove the resource visually
        try {
            java.util.Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "RESOURCE_DEPLETED");
            message.put("nodeId", nodeId);
            message.put("x", x);
            message.put("y", y);

            messagingTemplate.convertAndSend("/topic/game/" + gameId, message);
        } catch (Exception e) {
            System.err.println("Error sending resource depletion notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get all non-depleted resource nodes
     */
    public List<ResourceNode> getNonDepletedNodes(Long gameId) {
        return resourceNodeRepository.findByGameIdAndDepletedFalse(gameId);
    }

    /**
     * Check if a resource node exists at coordinates
     */
    public boolean hasResourceAt(Long gameId, int x, int y) {
        Optional<ResourceNode> node = getResourceNodeAt(gameId, x, y);
        return node.isPresent() && !node.get().isDepleted();
    }

    /**
     * Check if a position collides with any resource node's bounding box
     * Resources are treated as 1x1 tiles, so we check if the position matches the resource position
     * @param gameId The game ID
     * @param x The x coordinate to check
     * @param y The y coordinate to check
     * @param excludeNodeId Optional node ID to exclude from the check (for checking slots of the resource itself)
     * @return true if the position collides with a resource node
     */
    public boolean isPositionBlockedByResource(Long gameId, int x, int y, Long excludeNodeId) {
        List<ResourceNode> allResources = getNonDepletedNodes(gameId);
        for (ResourceNode resource : allResources) {
            // Skip the excluded node (the resource we're checking slots for)
            if (excludeNodeId != null && resource.getId().equals(excludeNodeId)) {
                continue;
            }

            // Resources occupy a 1x1 tile at their x,y position
            if (resource.getX() == x && resource.getY() == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get gather slot debug information for a resource node
     * Returns slot positions, occupancy, and assigned units with accessibility info
     * @param resourceId The resource node ID
     * @param gameId The game ID
     * @param allUnits All units in the game (to filter which ones are gathering from this resource)
     */
    public Optional<GatherSlotDebugDTO> getGatherSlotDebugInfo(Long resourceId, Long gameId, List<Unit> allUnits) {
        Optional<ResourceNode> nodeOpt = getResourceNodeById(resourceId);
        if (nodeOpt.isEmpty()) {
            return Optional.empty();
        }

        ResourceNode node = nodeOpt.get();

        // Get units gathering from this resource to rebuild slot state
        List<Unit> unitsGatheringHere = allUnits.stream()
                .filter(u -> resourceId.equals(u.getTargetResourceNodeId()))
                .collect(Collectors.toList());

        // Ensure slots are initialized and rebuild occupancy from unit state
        if (node.getGatherSlots() == null) {
            node.initializeGatherSlotsFromUnits(8, unitsGatheringHere);
        }

        // Convert slots to DTOs and mark accessibility
        List<GatherSlotDTO> slotDTOs = new ArrayList<>();
        for (com.rts.model.GatherSlot slot : node.getGatherSlots()) {
            GatherSlotDTO dto = new GatherSlotDTO(slot, node.getX(), node.getY());

            // Check if this slot position collides with another resource
            boolean blockedByResource = isPositionBlockedByResource(gameId, dto.getWorldX(), dto.getWorldY(), node.getId());
            dto.setAccessible(!blockedByResource);

            slotDTOs.add(dto);
        }

        // Get units that are gathering from this resource
        List<UnitDebugDTO> unitDTOs = allUnits.stream()
                .filter(unit -> resourceId.equals(unit.getTargetResourceNodeId()))
                .map(UnitDebugDTO::new)
                .collect(Collectors.toList());

        // Create resource node DTO
        ResourceNodeDTO resourceDTO = new ResourceNodeDTO(node);

        return Optional.of(new GatherSlotDebugDTO(resourceDTO, slotDTOs, unitDTOs));
    }
}
