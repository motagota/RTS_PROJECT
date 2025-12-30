package com.rts.service;

import com.rts.model.Game;
import com.rts.model.GeneratedMap;
import com.rts.model.MapCell;
import com.rts.model.ResourceNode;
import com.rts.repository.ResourceNodeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing resource nodes in the game
 */
@Service
public class ResourceNodeService {

    @Autowired
    private ResourceNodeRepository resourceNodeRepository;

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
                System.out.println("No cells array found in terrain data");
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
            System.out.println("Skipping unknown object type: " + cell.getObject());
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
     * Remove a depleted resource node from the map visually
     */
    private void removeNodeFromMap(ResourceNode node) {
        // TODO: Update the map data to remove the object from the cell
        // This will require updating the GeneratedMap's terrain data
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
}
