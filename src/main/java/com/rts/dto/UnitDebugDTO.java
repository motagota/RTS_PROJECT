package com.rts.dto;

import com.rts.model.Unit;

/**
 * Simplified DTO for unit information in debug panel
 */
public class UnitDebugDTO {
    private int id;
    private int x;
    private int y;
    private String gatherState;
    private Integer assignedSlotIndex;
    private int carryingAmount;
    private int carryCapacity;
    private String carryingResourceType;
    private int movementDelayTicks;

    public UnitDebugDTO(Unit unit) {
        this.id = unit.getId();
        this.x = unit.getX();
        this.y = unit.getY();
        this.gatherState = unit.getGatherState().name();
        this.assignedSlotIndex = unit.getAssignedSlotIndex();
        this.carryingAmount = unit.getCarryingAmount();
        this.carryCapacity = unit.getCarryCapacity();
        this.carryingResourceType = unit.getCarryingResourceType();
        this.movementDelayTicks = unit.getMovementDelayTicks();
    }

    // Getters
    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getGatherState() {
        return gatherState;
    }

    public Integer getAssignedSlotIndex() {
        return assignedSlotIndex;
    }

    public int getCarryingAmount() {
        return carryingAmount;
    }

    public int getCarryCapacity() {
        return carryCapacity;
    }

    public String getCarryingResourceType() {
        return carryingResourceType;
    }

    public int getMovementDelayTicks() {
        return movementDelayTicks;
    }
}
