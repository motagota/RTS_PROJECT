package com.rts.dto;

public class ProduceUnitRequest {
    private String playerName;
    private Integer buildingX;
    private Integer buildingY;
    private String buildingType;
    private String unitType;

    // Getters and Setters

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }
}
