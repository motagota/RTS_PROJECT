package com.rts.model;

import jakarta.persistence.Embeddable;

/**
 * Represents the resources available to a player (AOE-style)
 */
@Embeddable
public class PlayerResources {

    private int wood = 200;
    private int food = 200;
    private int stone = 100;
    private int gold = 100;

    public PlayerResources() {
        // Default constructor for JPA
    }

    public PlayerResources(int wood, int food, int stone, int gold) {
        this.wood = wood;
        this.food = food;
        this.stone = stone;
        this.gold = gold;
    }

    // Check if player can afford a cost
    public boolean canAfford(int woodCost, int foodCost, int stoneCost, int goldCost) {
        return this.wood >= woodCost
            && this.food >= foodCost
            && this.stone >= stoneCost
            && this.gold >= goldCost;
    }

    // Deduct resources (returns true if successful)
    public boolean deduct(int woodCost, int foodCost, int stoneCost, int goldCost) {
        if (!canAfford(woodCost, foodCost, stoneCost, goldCost)) {
            return false;
        }
        this.wood -= woodCost;
        this.food -= foodCost;
        this.stone -= stoneCost;
        this.gold -= goldCost;
        return true;
    }

    // Add resources
    public void add(int woodAmount, int foodAmount, int stoneAmount, int goldAmount) {
        this.wood += woodAmount;
        this.food += foodAmount;
        this.stone += stoneAmount;
        this.gold += goldAmount;
    }

    // Getters and Setters
    public int getWood() {
        return wood;
    }

    public void setWood(int wood) {
        this.wood = Math.max(0, wood);
    }

    public int getFood() {
        return food;
    }

    public void setFood(int food) {
        this.food = Math.max(0, food);
    }

    public int getStone() {
        return stone;
    }

    public void setStone(int stone) {
        this.stone = Math.max(0, stone);
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = Math.max(0, gold);
    }
}
