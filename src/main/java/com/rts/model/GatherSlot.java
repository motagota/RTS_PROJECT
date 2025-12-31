package com.rts.model;

/**
 * Represents a gathering slot around a resource node
 * Villagers are assigned specific slots to prevent clustering
 */
public class GatherSlot {

    private int slotIndex;           // Unique index for this slot (0-based)
    private double angleRadians;     // Angle around the resource node (in radians)
    private double offsetX;          // Local-space X offset from resource center
    private double offsetY;          // Local-space Y offset from resource center
    private boolean occupied;        // Whether this slot is currently occupied
    private Integer occupierUnitId;  // ID of unit occupying this slot (null if free)

    public GatherSlot(int slotIndex, double angleRadians, double radius) {
        this.slotIndex = slotIndex;
        this.angleRadians = angleRadians;
        // Calculate offset in local space (relative to resource node center)
        this.offsetX = Math.cos(angleRadians) * radius;
        this.offsetY = Math.sin(angleRadians) * radius;
        this.occupied = false;
        this.occupierUnitId = null;
    }

    /**
     * Reserve this slot for a unit
     */
    public void reserve(int unitId) {
        this.occupied = true;
        this.occupierUnitId = unitId;
    }

    /**
     * Release this slot (unit died, changed orders, or finished)
     */
    public void release() {
        this.occupied = false;
        this.occupierUnitId = null;
    }

    /**
     * Get the world-space position of this slot given the resource node's position
     */
    public int[] getWorldPosition(int resourceX, int resourceY) {
        return new int[]{
            (int) Math.round(resourceX + offsetX),
            (int) Math.round(resourceY + offsetY)
        };
    }

    // Getters
    public int getSlotIndex() {
        return slotIndex;
    }

    public double getAngleRadians() {
        return angleRadians;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public Integer getOccupierUnitId() {
        return occupierUnitId;
    }

    public boolean isOccupiedBy(int unitId) {
        return occupied && occupierUnitId != null && occupierUnitId == unitId;
    }
}
