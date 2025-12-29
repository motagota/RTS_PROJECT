package com.rts.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class MoveUnitsRequest {
    @NotEmpty(message = "Unit IDs list cannot be empty")
    private List<Integer> unitIds;

    @NotNull(message = "Target X coordinate is required")
    private Integer targetX;

    @NotNull(message = "Target Y coordinate is required")
    private Integer targetY;

    public MoveUnitsRequest() {
    }

    public MoveUnitsRequest(List<Integer> unitIds, Integer targetX, Integer targetY) {
        this.unitIds = unitIds;
        this.targetX = targetX;
        this.targetY = targetY;
    }

    public List<Integer> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(List<Integer> unitIds) {
        this.unitIds = unitIds;
    }

    public Integer getTargetX() {
        return targetX;
    }

    public void setTargetX(Integer targetX) {
        this.targetX = targetX;
    }

    public Integer getTargetY() {
        return targetY;
    }

    public void setTargetY(Integer targetY) {
        this.targetY = targetY;
    }
}
