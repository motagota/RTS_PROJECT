package com.rts.dto;

import jakarta.validation.constraints.NotBlank;

public class EnqueueProductionRequest {
    @NotBlank(message = "Unit type is required")
    private String unitType;

    public EnqueueProductionRequest() {
    }

    public EnqueueProductionRequest(String unitType) {
        this.unitType = unitType;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }
}
