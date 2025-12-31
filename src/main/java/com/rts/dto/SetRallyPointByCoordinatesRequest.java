package com.rts.dto;

import jakarta.validation.constraints.NotNull;

public class SetRallyPointByCoordinatesRequest {
    @NotNull(message = "Building X coordinate is required")
    private Integer buildingX;

    @NotNull(message = "Building Y coordinate is required")
    private Integer buildingY;

    @NotNull(message = "Building type is required")
    private String buildingType;

    @NotNull(message = "Rally X coordinate is required")
    private Integer rallyX;

    @NotNull(message = "Rally Y coordinate is required")
    private Integer rallyY;

    public SetRallyPointByCoordinatesRequest() {
    }

    public SetRallyPointByCoordinatesRequest(Integer buildingX, Integer buildingY, String buildingType, Integer rallyX, Integer rallyY) {
        this.buildingX = buildingX;
        this.buildingY = buildingY;
        this.buildingType = buildingType;
        this.rallyX = rallyX;
        this.rallyY = rallyY;
    }

    public Integer getBuildingX() {
        return buildingX;
    }

    public void setBuildingX(Integer buildingX) {
        this.buildingX = buildingX;
    }

    public Integer getBuildingY() {
        return buildingY;
    }

    public void setBuildingY(Integer buildingY) {
        this.buildingY = buildingY;
    }

    public String getBuildingType() {
        return buildingType;
    }

    public void setBuildingType(String buildingType) {
        this.buildingType = buildingType;
    }

    public Integer getRallyX() {
        return rallyX;
    }

    public void setRallyX(Integer rallyX) {
        this.rallyX = rallyX;
    }

    public Integer getRallyY() {
        return rallyY;
    }

    public void setRallyY(Integer rallyY) {
        this.rallyY = rallyY;
    }
}
