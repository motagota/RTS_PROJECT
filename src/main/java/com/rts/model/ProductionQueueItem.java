package com.rts.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an item in a building's production queue
 */
@Entity
@Table(name = "production_queue")
public class ProductionQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "game_id", nullable = false)
    @JsonBackReference
    private Game game;

    @Column(nullable = false)
    private String playerName;

    @Column(nullable = false)
    private Integer playerSlot;

    @Column(nullable = false)
    private Integer buildingX; // X coordinate of the building producing this unit

    @Column(nullable = false)
    private Integer buildingY; // Y coordinate of the building producing this unit

    @Column(nullable = false)
    private String buildingType; // HEADQUARTERS, etc.

    @Column(nullable = false)
    private String unitType; // VILLAGER, SOLDIER, etc.

    @Column(nullable = true)
    private LocalDateTime startedAt; // Null for QUEUED items

    @Column(nullable = true)
    private LocalDateTime completesAt; // Null for QUEUED items

    @Column(nullable = false)
    private Integer productionTimeSeconds = 25; // Default 25 seconds for villager

    @Column(nullable = false)
    private String status = "QUEUED"; // QUEUED, IN_PROGRESS, COMPLETED, CANCELLED

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getPlayerSlot() {
        return playerSlot;
    }

    public void setPlayerSlot(Integer playerSlot) {
        this.playerSlot = playerSlot;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletesAt() {
        return completesAt;
    }

    public void setCompletesAt(LocalDateTime completesAt) {
        this.completesAt = completesAt;
    }

    public Integer getProductionTimeSeconds() {
        return productionTimeSeconds;
    }

    public void setProductionTimeSeconds(Integer productionTimeSeconds) {
        this.productionTimeSeconds = productionTimeSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
