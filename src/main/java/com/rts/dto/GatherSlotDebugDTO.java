package com.rts.dto;

import java.util.List;

/**
 * Complete debug information for a resource node's gather slots
 */
public class GatherSlotDebugDTO {
    private ResourceNodeDTO resourceNode;
    private List<GatherSlotDTO> slots;
    private List<UnitDebugDTO> units;

    public GatherSlotDebugDTO(ResourceNodeDTO resourceNode, List<GatherSlotDTO> slots, List<UnitDebugDTO> units) {
        this.resourceNode = resourceNode;
        this.slots = slots;
        this.units = units;
    }

    // Getters
    public ResourceNodeDTO getResourceNode() {
        return resourceNode;
    }

    public List<GatherSlotDTO> getSlots() {
        return slots;
    }

    public List<UnitDebugDTO> getUnits() {
        return units;
    }
}
