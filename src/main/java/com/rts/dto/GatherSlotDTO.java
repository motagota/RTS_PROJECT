package com.rts.dto;

import com.rts.model.GatherSlot;

/**
 * DTO for gather slot information
 */
public class GatherSlotDTO {
    private int slotIndex;
    private double angleRadians;
    private double offsetX;
    private double offsetY;
    private int worldX;
    private int worldY;
    private boolean occupied;
    private Integer occupierUnitId;
    private boolean accessible; // Whether this slot is accessible (not blocked by terrain/buildings/resources)

    public GatherSlotDTO(GatherSlot slot, int resourceX, int resourceY) {
        this.slotIndex = slot.getSlotIndex();
        this.angleRadians = slot.getAngleRadians();
        this.offsetX = slot.getOffsetX();
        this.offsetY = slot.getOffsetY();

        int[] worldPos = slot.getWorldPosition(resourceX, resourceY);
        this.worldX = worldPos[0];
        this.worldY = worldPos[1];

        this.occupied = slot.isOccupied();
        this.occupierUnitId = slot.getOccupierUnitId();
        this.accessible = true; // Default to true, will be set by service
    }

    public void setAccessible(boolean accessible) {
        this.accessible = accessible;
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

    public int getWorldX() {
        return worldX;
    }

    public int getWorldY() {
        return worldY;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public Integer getOccupierUnitId() {
        return occupierUnitId;
    }

    public boolean isAccessible() {
        return accessible;
    }
}
