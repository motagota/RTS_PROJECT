package com.rts.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Represents a building placed on the map during generation
 */
public class Building {

    public enum BuildingType {
        HEADQUARTERS('H');

        private final char symbol;

        BuildingType(char symbol) {
            this.symbol = symbol;
        }

        public char getSymbol() {
            return symbol;
        }
    }

    private int x;
    private int y;
    private BuildingType type;
    private int playerNumber;
    private int width;  // Width in tiles (e.g., 2 for 2x2 building)
    private int height; // Height in tiles (e.g., 2 for 2x2 building)
    private Integer rallyPointX; // Rally point X coordinate (null = no rally point)
    private Integer rallyPointY; // Rally point Y coordinate (null = no rally point)


    public Building() { }

    @JsonCreator
    public Building(
            @com.fasterxml.jackson.annotation.JsonProperty("x") int x,
            @com.fasterxml.jackson.annotation.JsonProperty("y") int y,
            @com.fasterxml.jackson.annotation.JsonProperty("type") BuildingType type,
            @com.fasterxml.jackson.annotation.JsonProperty("playerNumber") int playerNumber) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.playerNumber = playerNumber;
        // Set default size based on building type
        if (type == BuildingType.HEADQUARTERS) {
            this.width = 2;
            this.height = 2;
        } else {
            this.width = 1;
            this.height = 1;
        }
    }

    public Building(int x, int y, BuildingType type, int playerNumber, int width, int height) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.playerNumber = playerNumber;
        this.width = width;
        this.height = height;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public BuildingType getType() {
        return type;
    }

    public void setType(BuildingType type) {
        this.type = type;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(int playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Integer getRallyPointX() {
        return rallyPointX;
    }

    public void setRallyPointX(Integer rallyPointX) {
        this.rallyPointX = rallyPointX;
    }

    public Integer getRallyPointY() {
        return rallyPointY;
    }

    public void setRallyPointY(Integer rallyPointY) {
        this.rallyPointY = rallyPointY;
    }
}
