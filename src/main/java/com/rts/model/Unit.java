package com.rts.model;

/**
 * Represents a unit (like villagers, soldiers) on the map
 */
public class Unit {

    public enum UnitType {
        VILLAGER('V', 1, 1, 50, 0, 0, 0),  // Symbol, width, height, food, wood, stone, gold
        SOLDIER('S', 1, 1, 60, 20, 0, 0);

        private final char symbol;
        private final int width;
        private final int height;
        private final int foodCost;
        private final int woodCost;
        private final int stoneCost;
        private final int goldCost;

        UnitType(char symbol, int width, int height, int foodCost, int woodCost, int stoneCost, int goldCost) {
            this.symbol = symbol;
            this.width = width;
            this.height = height;
            this.foodCost = foodCost;
            this.woodCost = woodCost;
            this.stoneCost = stoneCost;
            this.goldCost = goldCost;
        }

        public char getSymbol() {
            return symbol;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getFoodCost() {
            return foodCost;
        }

        public int getWoodCost() {
            return woodCost;
        }

        public int getStoneCost() {
            return stoneCost;
        }

        public int getGoldCost() {
            return goldCost;
        }
    }

    private int x;
    private int y;
    private UnitType type;
    private int playerNumber;
    private int health;
    private int maxHealth;

    // No-arg constructor for Jackson deserialization
    public Unit() {
    }

    public Unit(int x, int y, UnitType type, int playerNumber) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.playerNumber = playerNumber;

        // Set default health based on unit type
        switch (type) {
            case VILLAGER -> {
                this.maxHealth = 50;
                this.health = 50;
            }
            case SOLDIER -> {
                this.maxHealth = 100;
                this.health = 100;
            }
        }
    }

    // Getters and Setters
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

    public UnitType getType() {
        return type;
    }

    public void setType(UnitType type) {
        this.type = type;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public void setPlayerNumber(int playerNumber) {
        this.playerNumber = playerNumber;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public int getWidth() {
        return type.getWidth();
    }

    public int getHeight() {
        return type.getHeight();
    }
}
