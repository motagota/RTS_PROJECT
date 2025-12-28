package com.rts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "generated_maps")
public class GeneratedMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String mapTemplateName;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Integer playerCount;

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] terrainData; // Compressed binary representation of the map terrain

    @Column(length = 10000)
    private String playerStarts; // JSON array of player starting positions

    @Column(length = 10000)
    private String buildings; // JSON array of buildings

    @Column(length = 50000)
    private String units; // JSON array of units

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public String getMapTemplateName() {
        return mapTemplateName;
    }

    public void setMapTemplateName(String mapTemplateName) {
        this.mapTemplateName = mapTemplateName;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public byte[] getTerrainData() {
        return terrainData;
    }

    public void setTerrainData(byte[] terrainData) {
        this.terrainData = terrainData;
    }

    public String getPlayerStarts() {
        return playerStarts;
    }

    public void setPlayerStarts(String playerStarts) {
        this.playerStarts = playerStarts;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getBuildings() {
        return buildings;
    }

    public void setBuildings(String buildings) {
        this.buildings = buildings;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }
}
