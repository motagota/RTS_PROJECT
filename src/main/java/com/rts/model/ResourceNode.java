package com.rts.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a resource node on the map (gold mine, stone deposit, berry bush, tree)
 * Each resource node has an amount that depletes as units gather from it
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "resource_nodes")
public class ResourceNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The game this resource node belongs to
     */
    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    @JsonIgnore
    private Game game;

    /**
     * X coordinate on the map
     */
    @Column(nullable = false)
    private int x;

    /**
     * Y coordinate on the map
     */
    @Column(nullable = false)
    private int y;

    /**
     * Type of resource: GOLD, STONE, BERRIES, TREE
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceType type;

    /**
     * Current amount of resources remaining
     */
    @Column(nullable = false)
    private int amount;

    /**
     * Maximum/initial amount of resources
     */
    @Column(nullable = false)
    private int maxAmount;

    /**
     * Whether this resource node has been depleted
     */
    @Column(nullable = false)
    private boolean depleted = false;

    /**
     * Gather slots arranged around this resource node (not persisted)
     * Villagers are assigned to specific slots to prevent overcrowding
     */
    @Transient
    @JsonIgnore
    private List<GatherSlot> gatherSlots;

    /**
     * Radius for gathering slots (distance from resource center)
     */
    @Transient
    @JsonIgnore
    private double gatherRadius = 1.0;

    public enum ResourceType {
        GOLD(800),      // Gold mines start with 800 gold
        STONE(350),     // Stone deposits start with 350 stone
        BERRIES(125),   // Berry bushes start with 125 food (per bush, typically 6 bushes)
        TREE(100),      // Trees start with 100 wood
        FORAGE(125);    // Alias for berries

        private final int defaultAmount;

        ResourceType(int defaultAmount) {
            this.defaultAmount = defaultAmount;
        }

        public int getDefaultAmount() {
            return defaultAmount;
        }
    }

    /**
     * Constructor with game, position, and type
     */
    public ResourceNode(Game game, int x, int y, ResourceType type) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.type = type;
        this.maxAmount = type.getDefaultAmount();
        this.amount = this.maxAmount;
        this.depleted = false;
    }

    /**
     * Constructor with custom amount
     */
    public ResourceNode(Game game, int x, int y, ResourceType type, int amount) {
        this.game = game;
        this.x = x;
        this.y = y;
        this.type = type;
        this.maxAmount = amount;
        this.amount = amount;
        this.depleted = false;
    }

    /**
     * Gather resources from this node
     * @param gatherAmount Amount to gather
     * @return Amount actually gathered (may be less if node doesn't have enough)
     */
    public int gather(int gatherAmount) {
        if (depleted || amount <= 0) {
            return 0;
        }

        int actuallyGathered = Math.min(gatherAmount, amount);
        amount -= actuallyGathered;

        if (amount <= 0) {
            amount = 0;
            depleted = true;
        }

        return actuallyGathered;
    }

    /**
     * Check if resource node is empty
     */
    public boolean isEmpty() {
        return depleted || amount <= 0;
    }

    /**
     * Get percentage of resources remaining
     */
    public int getPercentageRemaining() {
        if (maxAmount == 0) return 0;
        return (int) ((amount * 100.0) / maxAmount);
    }

    /**
     * Get the resource type string for map object compatibility
     */
    public String getObjectType() {
        return type.name();
    }

    /**
     * Create from object type string
     */
    public static ResourceType fromObjectType(String objectType) {
        return switch (objectType.toUpperCase()) {
            case "GOLD" -> ResourceType.GOLD;
            case "STONE" -> ResourceType.STONE;
            case "BERRIES", "FORAGE", "BERRY_BUSH" -> ResourceType.BERRIES;
            case "TREE" -> ResourceType.TREE;
            default -> throw new IllegalArgumentException("Unknown resource type: " + objectType);
        };
    }

    /**
     * Initialize gather slots around this resource node
     * Creates slots in a circle around the resource
     */
    public void initializeGatherSlots(int numSlots) {
        gatherSlots = new ArrayList<>();
        for (int i = 0; i < numSlots; i++) {
            // Distribute slots evenly around a circle
            double angle = (2 * Math.PI * i) / numSlots;
            gatherSlots.add(new GatherSlot(i, angle, gatherRadius));
        }
    }

    /**
     * Initialize gather slots and rebuild occupancy from unit states
     * This is necessary because slots are transient and don't persist between requests
     * @param numSlots Number of slots to create
     * @param unitsGatheringHere List of units that are gathering from this resource
     */
    public void initializeGatherSlotsFromUnits(int numSlots, List<Unit> unitsGatheringHere) {
        initializeGatherSlots(numSlots);

        // Rebuild slot occupancy from unit state
        // Only count units that are actively at the resource (not dropping off)
        for (Unit unit : unitsGatheringHere) {
            // Only occupy slot if unit is MOVING_TO_RESOURCE or GATHERING
            // Units in MOVING_TO_DROPOFF or DEPOSITING states should not occupy slots
            boolean shouldOccupy = unit.getGatherState() == Unit.GatherState.MOVING_TO_RESOURCE ||
                                   unit.getGatherState() == Unit.GatherState.GATHERING;

            if (shouldOccupy &&
                unit.getAssignedSlotIndex() != null &&
                unit.getAssignedSlotIndex() >= 0 &&
                unit.getAssignedSlotIndex() < gatherSlots.size()) {

                GatherSlot slot = gatherSlots.get(unit.getAssignedSlotIndex());
                slot.reserve(unit.getId());
            }
        }
    }

    /**
     * Find and reserve a free gather slot for a unit
     * @param unitId ID of the unit requesting the slot
     * @return The reserved slot, or null if no slots are available
     */
    public GatherSlot reserveFreeSlot(int unitId) {
        if (gatherSlots == null) {
            // Default: 8 slots around the resource (one per cardinal/diagonal direction)
            initializeGatherSlots(8);
        }

        for (GatherSlot slot : gatherSlots) {
            if (!slot.isOccupied()) {
                slot.reserve(unitId);
                return slot;
            }
        }
        return null; // No free slots
    }

    /**
     * Find and reserve a free gather slot that is also walkable/accessible
     * @param unitId ID of the unit requesting the slot
     * @param validator Function to check if a slot position is walkable
     * @return The reserved slot, or null if no accessible slots are available
     */
    public GatherSlot reserveFreeAccessibleSlot(int unitId, java.util.function.BiPredicate<Integer, Integer> isAccessible) {
        if (gatherSlots == null) {
            initializeGatherSlots(8);
        }

        for (GatherSlot slot : gatherSlots) {
            if (!slot.isOccupied()) {
                // Check if this slot's world position is accessible
                int[] worldPos = slot.getWorldPosition(this.x, this.y);
                if (isAccessible.test(worldPos[0], worldPos[1])) {
                    slot.reserve(unitId);
                    return slot;
                }
            }
        }
        return null; // No free accessible slots
    }

    /**
     * Release a slot occupied by a specific unit
     * @param unitId ID of the unit releasing the slot
     */
    public void releaseSlot(int unitId) {
        if (gatherSlots == null) {
            return;
        }

        for (GatherSlot slot : gatherSlots) {
            if (slot.isOccupiedBy(unitId)) {
                slot.release();
                return;
            }
        }
    }

    /**
     * Get the slot occupied by a specific unit
     * @param unitId ID of the unit
     * @return The slot occupied by this unit, or null if not found
     */
    public GatherSlot getSlotForUnit(int unitId) {
        if (gatherSlots == null) {
            return null;
        }

        for (GatherSlot slot : gatherSlots) {
            if (slot.isOccupiedBy(unitId)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Count how many slots are currently occupied
     */
    public int getOccupiedSlotCount() {
        if (gatherSlots == null) {
            return 0;
        }
        return (int) gatherSlots.stream().filter(GatherSlot::isOccupied).count();
    }

    /**
     * Check if all slots are occupied
     */
    public boolean areAllSlotsOccupied() {
        if (gatherSlots == null) {
            return false;
        }
        return gatherSlots.stream().allMatch(GatherSlot::isOccupied);
    }
}
