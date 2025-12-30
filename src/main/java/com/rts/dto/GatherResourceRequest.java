package com.rts.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for commanding units to gather from a resource node
 */
public class GatherResourceRequest {
    @NotEmpty(message = "Unit IDs list cannot be empty")
    private List<Integer> unitIds;

    @NotNull(message = "Resource node X coordinate is required")
    private Integer resourceX;

    @NotNull(message = "Resource node Y coordinate is required")
    private Integer resourceY;

    public GatherResourceRequest() {
    }

    public GatherResourceRequest(List<Integer> unitIds, Integer resourceX, Integer resourceY) {
        this.unitIds = unitIds;
        this.resourceX = resourceX;
        this.resourceY = resourceY;
    }

    public List<Integer> getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(List<Integer> unitIds) {
        this.unitIds = unitIds;
    }

    public Integer getResourceX() {
        return resourceX;
    }

    public void setResourceX(Integer resourceX) {
        this.resourceX = resourceX;
    }

    public Integer getResourceY() {
        return resourceY;
    }

    public void setResourceY(Integer resourceY) {
        this.resourceY = resourceY;
    }
}
