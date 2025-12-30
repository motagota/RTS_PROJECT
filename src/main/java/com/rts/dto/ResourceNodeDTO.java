package com.rts.dto;

import com.rts.model.ResourceNode;

/**
 * DTO for resource node information sent to the client
 */
public class ResourceNodeDTO {
    private Long id;
    private int x;
    private int y;
    private String type;
    private int amount;
    private int maxAmount;
    private boolean depleted;
    private int percentageRemaining;

    public ResourceNodeDTO() {
    }

    public ResourceNodeDTO(ResourceNode node) {
        this.id = node.getId();
        this.x = node.getX();
        this.y = node.getY();
        this.type = node.getType().name();
        this.amount = node.getAmount();
        this.maxAmount = node.getMaxAmount();
        this.depleted = node.isDepleted();
        this.percentageRemaining = node.getPercentageRemaining();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(int maxAmount) {
        this.maxAmount = maxAmount;
    }

    public boolean isDepleted() {
        return depleted;
    }

    public void setDepleted(boolean depleted) {
        this.depleted = depleted;
    }

    public int getPercentageRemaining() {
        return percentageRemaining;
    }

    public void setPercentageRemaining(int percentageRemaining) {
        this.percentageRemaining = percentageRemaining;
    }
}
